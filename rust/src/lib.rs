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

    // Add transparent inputs from the provided data
    // The inputs_to_spend should contain serialized transparent input data:
    // For each input: pubkey (33 bytes) + txid (32 bytes) + vout (4 bytes) + amount (8 bytes) + script (variable)
    if !inputs_to_spend.is_empty() {
        // TODO: Implement proper UTXO parsing format
        // For now, we expect a simplified format for testing
        return Err(ProposalError::InvalidRequest(
            "Custom UTXO input format not yet implemented. Use transparent inputs from existing transactions.".to_string()
        ));
    }

    // Note: For testing purposes, transactions can be created without inputs by using
    // the Builder's ability to create unfunded transactions. In production, callers
    // must provide properly formatted transparent inputs.

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
/// This performs basic sanity checks to ensure the PCZT matches expectations.
///
/// # Arguments
/// * `pczt` - The PCZT to verify
/// * `transaction_request` - The original transaction request
/// * `expected_change` - Expected change outputs (serialized format - not yet implemented)
///
/// # Returns
/// * `Result<(), VerificationFailure>` - Success or verification error
pub fn verify_before_signing(
    pczt: &Pczt,
    transaction_request: &TransactionRequest,
    _expected_change: &[u8],
) -> Result<(), VerificationFailure> {
    // Verify that we have outputs
    let outputs = pczt.transparent().outputs();
    if outputs.is_empty() && !pczt.orchard().actions().is_empty() {
        // Shielded-only transaction - no transparent outputs to verify
        // We would need to verify Orchard outputs match the request
        // TODO: Implement Orchard output verification
        return Ok(());
    }

    // Basic verification: Check that the number of outputs makes sense
    // We expect at least as many outputs as payments (could be more if there's change)
    let num_payments = transaction_request.payments.len();
    let total_outputs = outputs.len() + pczt.orchard().actions().len();

    if total_outputs < num_payments {
        return Err(VerificationFailure::OutputMismatch(
            format!("Expected at least {} outputs but found {}", num_payments, total_outputs)
        ));
    }

    // Verify payment amounts are represented in outputs
    // This is a simplified check - a full implementation would:
    // 1. Parse addresses from outputs
    // 2. Match them to the payment request
    // 3. Verify exact amounts and destinations
    // 4. Verify change outputs separately
    // 5. Verify fee is reasonable

    // For now, we just verify the total output value is reasonable
    let total_transparent_output: u64 = outputs.iter()
        .map(|output| *output.value())
        .sum();

    // For Orchard outputs, we can't easily verify values without accessing private fields
    // The pczt crate doesn't expose output values publicly

    // Verify requested payment total matches (approximately) - allowing for fees
    let requested_total = transaction_request.total_amount();

    // The transparent outputs should be at least the requested amount (minus what might be in Orchard)
    // This is a loose check - proper verification would sum all output pools
    if total_transparent_output > 0 && total_transparent_output < requested_total.saturating_sub(1_000_000) {
        return Err(VerificationFailure::OutputMismatch(
            format!(
                "Transparent output total {} is less than requested amount {} (allowing for fees)",
                total_transparent_output, requested_total
            )
        ));
    }

    // TODO: Verify expected change outputs match
    // TODO: Verify fee is reasonable (not too high)
    // TODO: Verify Orchard output recipients and amounts
    // TODO: Verify memo fields match payment requests

    Ok(())
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
    use pczt::roles::signer::Signer;

    // Validate input index
    if input_index >= pczt.transparent().inputs().len() {
        return Err(SighashError::InvalidInputIndex(input_index));
    }

    // Create a Signer to access sighash computation
    let signer = Signer::new(pczt.clone())
        .map_err(|e| SighashError::CalculationFailed(format!("Failed to create Signer: {:?}", e)))?;

    // Get the sighash for this transparent input using the convenience method
    let hash = signer.get_transparent_sighash(input_index)
        .map_err(|e| match e {
            pczt::roles::signer::Error::InvalidIndex => SighashError::InvalidInputIndex(input_index),
            _ => SighashError::CalculationFailed(format!("{:?}", e)),
        })?;

    Ok(SigHash(hash))
}

/// Appends a signature to the PCZT for a specific input.
///
/// The implementation should verify that the signature validates for the input being spent.
///
/// NOTE: This function is for adding pre-computed signatures (e.g., from hardware wallets).
/// For direct signing, use the pczt::roles::signer::Signer role's sign_transparent() method.
///
/// # Arguments
/// * `pczt` - The PCZT to add the signature to
/// * `input_index` - The index of the input this signature applies to
/// * `signature` - The 64-byte compact ECDSA signature (r and s components, no recovery byte)
///
/// # Returns
/// * `Result<Pczt, SignatureError>` - The updated PCZT or an error
pub fn append_signature(
    pczt: Pczt,
    input_index: usize,
    signature: [u8; 64],
) -> Result<Pczt, SignatureError> {
    use pczt::roles::signer::Signer;

    // Validate input index
    if input_index >= pczt.transparent().inputs().len() {
        return Err(SignatureError::InvalidInputIndex(input_index));
    }

    // Create a Signer (which validates and parses the PCZT)
    let mut signer = Signer::new(pczt)
        .map_err(|e| SignatureError::InvalidFormat)?;

    // Parse the signature bytes into secp256k1::ecdsa::Signature
    let sig = secp256k1::ecdsa::Signature::from_compact(&signature)
        .map_err(|_| SignatureError::InvalidFormat)?;

    // Append the signature using the Signer's method
    // This validates that the signature is correct for the input
    signer.append_transparent_signature(input_index, sig)
        .map_err(|e| match e {
            pczt::roles::signer::Error::InvalidIndex => SignatureError::InvalidInputIndex(input_index),
            pczt::roles::signer::Error::TransparentSign(_) => SignatureError::VerificationFailed,
            _ => SignatureError::InvalidFormat,
        })?;

    // Return the updated PCZT
    Ok(signer.finish())
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
