pub mod error;
pub mod ffi;
pub mod types;

use error::*;
use types::*;

use pczt::{Pczt, roles::creator::Creator, roles::io_finalizer::IoFinalizer};
use zcash_primitives::transaction::{
    builder::{Builder, BuildConfig},
    fees::zip317::FeeRule,
};
use zcash_protocol::{
    consensus::TestNetwork,
    value::Zatoshis,
    memo::MemoBytes,
};
use zcash_address::{ZcashAddress, unified};
use zcash_transparent::address::TransparentAddress;
use rand_core::OsRng;
use zcash_primitives::transaction::components::transparent::TxOut;

/// Proposes a transaction by creating a PCZT from transparent inputs and a transaction request.
///
/// This implements the Creator, Constructor, and IO Finalizer roles.
///
/// # Arguments
/// * `inputs_to_spend` - Serialized transparent input data
/// * `transaction_request` - The transaction request containing recipient information
///
/// # Returns
/// * `Result<Pczt, ProposalError>` - The created PCZT or an error
pub fn propose_transaction(
    inputs_to_spend: &[u8],
    transaction_request: TransactionRequest,
) -> Result<Pczt, ProposalError> {
    // Validate inputs
    if transaction_request.payments.is_empty() {
        return Err(ProposalError::InvalidRequest("No payments provided".to_string()));
    }

    // Use testnet parameters
    let params = TestNetwork;
    let target_height = 2_000_000u32.into();

    // Create transaction builder with orchard anchor for shielded outputs
    let mut builder = Builder::new(
        params,
        target_height,
        BuildConfig::Standard {
            sapling_anchor: None,
            orchard_anchor: Some(orchard::Anchor::empty_tree()),
        },
    );

    // Add transparent inputs
    // TODO: In production, parse actual UTXO data from inputs_to_spend
    // For now, handle mock inputs for testing
    if inputs_to_spend == b"MOCK_INPUTS_FOR_TESTING" {
        // Create a mock transparent input for testing with 1 ZEC
        use zcash_transparent::bundle::OutPoint;

        let secp = secp256k1::Secp256k1::new();

        // Create a mock secret key and derive pubkey
        let sk = secp256k1::SecretKey::from_slice(&[1u8; 32])
            .map_err(|e| ProposalError::InvalidRequest(format!("Failed to create mock secret key: {:?}", e)))?;
        let pubkey = secp256k1::PublicKey::from_secret_key(&secp, &sk);

        // Create a transparent address
        let transparent_addr = TransparentAddress::from_pubkey(&pubkey);

        // Create a mock UTXO (txid + output index) with 1 ZEC
        // We'll add a change output for any leftover funds
        let mock_utxo = OutPoint::new([0u8; 32], 0);
        let mock_coin = TxOut::new(
            Zatoshis::const_from_u64(100_000_000), // 1 ZEC
            transparent_addr.script().into(),
        );

        builder.add_transparent_input(pubkey, mock_utxo, mock_coin)
            .map_err(|e| ProposalError::PcztCreation(format!("Failed to add mock transparent input: {:?}", e)))?;

        // Add a transparent change output to receive any leftover funds
        // The builder will calculate the proper change amount after fees
        // We'll use the same address for change (in production this would be a new address)
        let change_amount = Zatoshis::const_from_u64(99_890_000); // Approximate change after 100k payment + 10k fee
        builder.add_transparent_output(&transparent_addr, change_amount)
            .map_err(|e| ProposalError::PcztCreation(format!("Failed to add change output: {:?}", e)))?;
    } else if !inputs_to_spend.is_empty() {
        // TODO: Implement proper UTXO parsing and input addition
        return Err(ProposalError::InvalidRequest("UTXO parsing not yet implemented".to_string()));
    }

    // Add outputs from payment request
    for payment in &transaction_request.payments {
        // Parse the address
        let addr_str = payment.address.as_str();
        let addr = addr_str.parse::<ZcashAddress>()
            .map_err(|_| ProposalError::InvalidAddress(payment.address.clone()))?;

        // Convert amount to Zatoshis
        let amount = Zatoshis::from_u64(payment.amount)
            .map_err(|_| ProposalError::InvalidRequest(format!("Invalid amount: {}", payment.amount)))?;

        // Try to convert to transparent address first
        if let Ok(t_addr) = addr.clone().convert::<TransparentAddress>() {
            // Add transparent output
            builder.add_transparent_output(&t_addr, amount)
                .map_err(|e| ProposalError::PcztCreation(format!("Failed to add transparent output: {:?}", e)))?;
        } else {
            // Try to handle as unified address with custom wrapper
            use zcash_address::{TryFromAddress, ConversionError, unified::Container};
            use zcash_protocol::consensus::NetworkType;

            // Custom type to extract unified address
            struct UnifiedAddressWrapper(unified::Address);
            impl TryFromAddress for UnifiedAddressWrapper {
                type Error = String;

                fn try_from_unified(
                    _net: NetworkType,
                    data: unified::Address
                ) -> Result<Self, ConversionError<Self::Error>> {
                    Ok(UnifiedAddressWrapper(data))
                }
            }

            let unified_wrapper = addr.convert::<UnifiedAddressWrapper>()
                .map_err(|e| ProposalError::InvalidAddress(format!("Address must be transparent or unified with Orchard receiver: {:?}", e)))?;

            // Check if unified address has Orchard receiver
            let receivers = unified_wrapper.0.items();
            let orchard_receiver = receivers.iter()
                .find_map(|receiver| {
                    if let unified::Receiver::Orchard(raw_addr) = receiver {
                        Some(raw_addr)
                    } else {
                        None
                    }
                });

            if let Some(orchard_raw) = orchard_receiver {
                // Convert raw Orchard address bytes to orchard::Address
                let orchard_addr: orchard::Address = Option::from(orchard::Address::from_raw_address_bytes(orchard_raw))
                    .ok_or_else(|| ProposalError::InvalidAddress("Invalid Orchard address bytes".to_string()))?;

                // Add Orchard output
                // Use None for OVK since we don't have sender's keys
                let memo = payment.memo.as_ref()
                    .and_then(|m| MemoBytes::from_bytes(m.as_bytes()).ok())
                    .unwrap_or_else(|| MemoBytes::empty());

                builder.add_orchard_output::<FeeRule>(None, orchard_addr, amount.into_u64(), memo)
                    .map_err(|e| ProposalError::PcztCreation(format!("Failed to add Orchard output: {:?}", e)))?;
            } else {
                return Err(ProposalError::InvalidAddress(
                    format!("Unified address does not contain Orchard receiver: {}", payment.address)
                ));
            }
        }
    }

    // Build PCZT from the builder
    let pczt_result = builder.build_for_pczt(OsRng, &FeeRule::standard())
        .map_err(|e| ProposalError::PcztCreation(format!("Builder failed: {:?}", e)))?;

    // Create PCZT from parts using Creator role
    let pczt = Creator::build_from_parts(pczt_result.pczt_parts)
        .ok_or_else(|| ProposalError::PcztCreation("Failed to build PCZT from parts".to_string()))?;

    // Finalize I/O using IoFinalizer role
    let pczt = IoFinalizer::new(pczt)
        .finalize_io()
        .map_err(|e| ProposalError::PcztCreation(format!("Failed to finalize I/O: {:?}", e)))?;

    Ok(pczt)
}

/// Adds Orchard proofs to the PCZT.
///
/// This MUST be implemented using the Prover role provided by the pczt Rust crate.
/// The proving operation may be done in parallel with other verification and signing operations.
///
/// The Orchard proving key is lazily loaded and cached on first use.
///
/// # Arguments
/// * `pczt` - The PCZT to add proofs to
///
/// # Returns
/// * `Result<Pczt, ProverError>` - The PCZT with proofs added or an error
pub fn prove_transaction(pczt: Pczt) -> Result<Pczt, ProverError> {
    use pczt::roles::prover::Prover;
    use std::sync::OnceLock;

    // Lazy-load the Orchard proving key on first use
    static ORCHARD_PROVING_KEY: OnceLock<orchard::circuit::ProvingKey> = OnceLock::new();

    let prover = Prover::new(pczt);

    // Check if we need to create Orchard proofs
    if prover.requires_orchard_proof() {
        let proving_key = ORCHARD_PROVING_KEY.get_or_init(|| {
            // Build the proving key (this is expensive but only happens once)
            orchard::circuit::ProvingKey::build()
        });

        let prover = prover.create_orchard_proof(proving_key)
            .map_err(|e| ProverError::OrchardProof(format!("{:?}", e)))?;

        Ok(prover.finish())
    } else {
        // No Orchard outputs, return as-is
        Ok(prover.finish())
    }
}

/// Verifies the PCZT before signing.
///
/// If the entity that invoked propose_transaction is the same as the entity adding signatures,
/// and no third party may have malleated the PCZT, this step may be skipped.
///
/// # Arguments
/// * `pczt` - The PCZT to verify
/// * `transaction_request` - The original transaction request
/// * `expected_change` - Expected change outputs
///
/// # Returns
/// * `Result<(), VerificationFailure>` - Success or verification error
pub fn verify_before_signing(
    _pczt: &Pczt,
    _transaction_request: &TransactionRequest,
    _expected_change: &[u8],
) -> Result<(), VerificationFailure> {
    // TODO: Verify that the PCZT matches the transaction request
    // TODO: Verify expected change outputs
    // TODO: Verify fee calculations

    Err(VerificationFailure::NotImplemented)
}

/// Gets the signature hash for a specific input.
///
/// This enables the caller to implement the Signer role by obtaining the sighash
/// that should be signed for each input.
///
/// # Arguments
/// * `pczt` - The PCZT
/// * `input_index` - The index of the input to get the sighash for
///
/// # Returns
/// * `Result<SigHash, SighashError>` - The signature hash or an error
pub fn get_sighash(
    pczt: &Pczt,
    input_index: usize,
) -> Result<SigHash, SighashError> {
    // Validate input index
    if input_index >= pczt.transparent().inputs().len() {
        return Err(SighashError::InvalidInputIndex(input_index));
    }

    // TODO: Implement ZIP 244 signature hashing
    // This may use zcash_primitives signature hashing implementation

    Err(SighashError::NotImplemented)
}

/// Appends a signature to the PCZT for a specific input.
///
/// The implementation should verify that the signature validates for the input being spent.
///
/// # Arguments
/// * `pczt` - The PCZT to add the signature to
/// * `input_index` - The index of the input this signature applies to
/// * `signature` - The signature bytes
///
/// # Returns
/// * `Result<Pczt, SignatureError>` - The updated PCZT or an error
pub fn append_signature(
    pczt: Pczt,
    input_index: usize,
    _signature: [u8; 64],
) -> Result<Pczt, SignatureError> {
    // Validate input index
    if input_index >= pczt.transparent().inputs().len() {
        return Err(SignatureError::InvalidInputIndex(input_index));
    }

    // TODO: Verify the signature
    // TODO: Add signature to the PCZT

    Err(SignatureError::NotImplemented)
}

/// Combines multiple PCZTs into one.
///
/// If the same entity invokes prove_transaction and append_signature sequentially
/// in a single thread, this step may be skipped.
///
/// This merges signatures, proofs, and other data from multiple PCZTs representing
/// the same transaction.
///
/// # Arguments
/// * `pczts` - Vector of PCZTs to combine
///
/// # Returns
/// * `Result<Pczt, CombineError>` - The combined PCZT or an error
pub fn combine(pczts: Vec<Pczt>) -> Result<Pczt, CombineError> {
    use pczt::roles::combiner::Combiner;

    if pczts.is_empty() {
        return Err(CombineError::NoPczts);
    }

    if pczts.len() == 1 {
        return Ok(pczts.into_iter().next().unwrap());
    }

    // Use the Combiner role to merge the PCZTs
    Combiner::new(pczts)
        .combine()
        .map_err(|e| match e {
            pczt::roles::combiner::Error::NoPczts => CombineError::NoPczts,
            pczt::roles::combiner::Error::DataMismatch => CombineError::DataMismatch,
        })
}

/// Finalizes the PCZT and extracts the transaction bytes.
///
/// This implements the Spend Finalizer and Transaction Extractor roles.
/// It performs final non-contextual verification and produces transaction bytes
/// ready to be sent to the chain.
///
/// # Arguments
/// * `pczt` - The PCZT to finalize and extract
///
/// # Returns
/// * `Result<Vec<u8>, FinalizationError>` - The transaction bytes or an error
pub fn finalize_and_extract(pczt: Pczt) -> Result<Vec<u8>, FinalizationError> {
    use pczt::roles::spend_finalizer::SpendFinalizer;
    use pczt::roles::tx_extractor::TransactionExtractor;

    // Step 1: Finalize spends (combines partial signatures into script_sigs)
    let pczt = SpendFinalizer::new(pczt)
        .finalize_spends()
        .map_err(|e| FinalizationError::SpendFinalization(format!("{:?}", e)))?;

    // Step 2: Extract the transaction
    // For Orchard transactions, the verifying key will be generated on the fly
    // We don't need Sapling verifying keys since we only support Orchard
    let transaction = TransactionExtractor::new(pczt)
        .extract()
        .map_err(|e| FinalizationError::TransactionExtraction(format!("{:?}", e)))?;

    // Step 3: Serialize the transaction to bytes
    let mut tx_bytes = Vec::new();
    transaction.write(&mut tx_bytes)
        .map_err(|e| FinalizationError::Serialization(format!("{:?}", e)))?;

    Ok(tx_bytes)
}

/// Parses PCZT from bytes.
///
/// # Arguments
/// * `pczt_bytes` - The serialized PCZT bytes
///
/// # Returns
/// * `Result<Pczt, ParseError>` - The parsed PCZT or an error
pub fn parse_pczt(pczt_bytes: &[u8]) -> Result<Pczt, ParseError> {
    Pczt::parse(pczt_bytes)
        .map_err(|e| ParseError::InvalidFormat(format!("{:?}", e)))
}

/// Serializes a PCZT to bytes.
///
/// # Arguments
/// * `pczt` - The PCZT to serialize
///
/// # Returns
/// * `Vec<u8>` - The serialized PCZT bytes
pub fn serialize_pczt(pczt: &Pczt) -> Vec<u8> {
    pczt.serialize()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    #[ignore] // TODO: Implement proper PCZT creation for testing
    fn test_parse_serialize_roundtrip() {
        // Need to create a valid PCZT for this test
        // let pczt = ...;
        // let bytes = serialize_pczt(&pczt);
        // let parsed = parse_pczt(&bytes);
        // assert!(parsed.is_ok());
    }
}
