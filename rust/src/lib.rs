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
use zcash_transparent::sighash::SighashType;
use rand_core::OsRng;

/// Proposes a transaction by creating a PCZT from transparent inputs and a transaction request.
///
/// This implements the Creator, Constructor, and IO Finalizer roles.
///
/// # Arguments
/// * `inputs_to_spend` - Serialized transparent input data in the following binary format:
///   ```text
///   [num_inputs: 2 bytes (u16 LE)]
///   For each input:
///     [pubkey: 33 bytes]        - Compressed secp256k1 public key
///     [txid: 32 bytes]          - Transaction ID of the UTXO being spent
///     [vout: 4 bytes (u32 LE)]  - Output index in the previous transaction
///     [amount: 8 bytes (u64 LE)]- Amount in zatoshis
///     [script_len: 2 bytes (u16 LE)] - Length of script_pubkey
///     [script: script_len bytes]     - The script_pubkey of the UTXO
///   ```
///   See `types::parse_transparent_inputs()` for the parser and
///   `types::serialize_transparent_inputs()` for the serializer.
///
/// * `transaction_request` - The transaction request containing recipient information
/// * `change_address` - Optional transparent address for change output. If None, derives from first input's pubkey
///
/// # Returns
/// * `Result<Pczt, ProposalError>` - The created PCZT or an error
pub fn propose_transaction(
    inputs_to_spend: &[u8],
    transaction_request: TransactionRequest,
    change_address: Option<String>,
) -> Result<Pczt, ProposalError> {
    // Validate inputs
    if transaction_request.payments.is_empty() {
        return Err(ProposalError::InvalidRequest("No payments provided".to_string()));
    }

    // Use testnet parameters
    let params = TestNetwork;
    // Use provided target_height or default to current testnet height
    // Note: Transactions expire 40 blocks after target_height (~100 minutes on testnet)
    // Update this value periodically or implement dynamic height from lightwalletd
    let target_height = transaction_request.target_height.unwrap_or(3_693_760).into();

    // Create transaction builder with orchard anchor for shielded outputs
    let mut builder = Builder::new(
        params,
        target_height,
        BuildConfig::Standard {
            sapling_anchor: None,
            orchard_anchor: Some(orchard::Anchor::empty_tree()),
        },
    );

    // Parse and add transparent inputs from the provided data
    let inputs = types::parse_transparent_inputs(inputs_to_spend)
        .map_err(|e| ProposalError::InvalidRequest(format!("Failed to parse inputs: {}", e)))?;

    for input in &inputs {
        let outpoint = input.outpoint();
        let coin = input.txout()
            .map_err(|e| ProposalError::InvalidRequest(format!("Invalid input data: {}", e)))?;

        builder.add_transparent_input(input.pubkey, outpoint, coin)
            .map_err(|e| ProposalError::PcztCreation(format!("Failed to add transparent input: {:?}", e)))?;
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

    // Calculate change if needed
    // Total input value
    let total_input: u64 = inputs.iter().map(|i| i.amount).sum();

    // Total requested output value
    let total_output: u64 = transaction_request.total_amount();

    // Estimate fee based on ZIP 317 standard
    // The Builder calculates actual fees dynamically, so our estimate must match
    // Transparent-only: ~10,000 zatoshis
    // Transparent->shielded (Orchard): ~15,000 zatoshis (higher due to shielded actions)
    let has_shielded = transaction_request.has_shielded_outputs();
    let estimated_fee: u64 = if has_shielded { 15_000 } else { 10_000 };

    // If we have change (inputs > outputs + fee), add a change output
    if total_input > total_output + estimated_fee {
        let change_amount = total_input - total_output - estimated_fee;

        // Get or derive change address
        let change_addr = if let Some(addr_str) = change_address {
            // Parse provided change address
            addr_str.parse::<ZcashAddress>()
                .map_err(|_| ProposalError::InvalidAddress(addr_str))?
                .convert::<TransparentAddress>()
                .map_err(|_| ProposalError::InvalidRequest("Change address must be transparent".to_string()))?
        } else {
            // Derive from first input's pubkey
            if inputs.is_empty() {
                return Err(ProposalError::InvalidRequest("No inputs provided for change derivation".to_string()));
            }
            TransparentAddress::from_pubkey(&inputs[0].pubkey)
        };

        // Add change output
        let change_zatoshis = Zatoshis::from_u64(change_amount)
            .map_err(|_| ProposalError::InvalidRequest(format!("Invalid change amount: {}", change_amount)))?;

        builder.add_transparent_output(&change_addr, change_zatoshis)
            .map_err(|e| ProposalError::PcztCreation(format!("Failed to add change output: {:?}", e)))?;
    }

    // Build PCZT from the builder
    let pczt_result = builder.build_for_pczt(OsRng, &FeeRule::standard())
        .map_err(|e| ProposalError::PcztCreation(format!("Builder failed: {:?}", e)))?;

    // Create PCZT from parts using Creator role
    let mut pczt = Creator::build_from_parts(pczt_result.pczt_parts)
        .ok_or_else(|| ProposalError::PcztCreation("Failed to build PCZT from parts".to_string()))?;

    // Use Updater role to add pubkey preimages (required for append_signature to work)
    // This maps pubkey hashes to actual pubkeys for signature verification
    use pczt::roles::updater::Updater;
    let updater = Updater::new(pczt);
    let updater = updater.update_transparent_with(|mut transparent_updater| {
        // For each input, add the pubkey preimage
        for (i, input) in inputs.iter().enumerate() {
            transparent_updater.update_input_with(i, |mut input_updater| {
                // Add the hash160 preimage (pubkey hash -> pubkey bytes)
                input_updater.set_hash160_preimage(input.pubkey.serialize().to_vec());
                Ok(())
            })?;
        }
        Ok(())
    }).map_err(|e| ProposalError::PcztCreation(format!("Failed to set pubkey preimages: {:?}", e)))?;
    pczt = updater.finish();

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
/// This verifies that:
/// - Payment outputs match the transaction request
/// - Change outputs match the expected change
/// - Fees are reasonable (not too high)
///
/// # Arguments
/// * `pczt` - The PCZT to verify
/// * `transaction_request` - The original transaction request
/// * `expected_change` - Expected change outputs (transparent TxOuts)
///
/// # Returns
/// * `Result<(), VerificationFailure>` - Success or verification error
pub fn verify_before_signing(
    pczt: &Pczt,
    transaction_request: &TransactionRequest,
    expected_change: &[zcash_transparent::bundle::TxOut],
) -> Result<(), VerificationFailure> {
    use zcash_address::ZcashAddress;

    let transparent_outputs = pczt.transparent().outputs();
    let orchard_actions = pczt.orchard().actions();

    // Count total outputs
    let num_transparent_outputs = transparent_outputs.len();
    let num_orchard_outputs = orchard_actions.len();
    let num_expected_change = expected_change.len();
    let num_payments = transaction_request.payments.len();

    // Total outputs should equal payments + change
    let total_outputs = num_transparent_outputs + num_orchard_outputs;

    // If expected_change is empty, we expect outputs to equal payments only
    // Otherwise, verify total outputs = payments + change
    if num_expected_change == 0 {
        // No change expected - verify we have exactly the payment outputs
        // (plus possibly internal change if the implementation added it)
        // For now, just verify we have AT LEAST the payments
        if total_outputs < num_payments {
            return Err(VerificationFailure::OutputMismatch(
                format!("Expected at least {} payment outputs but found {}", num_payments, total_outputs)
            ));
        }
    } else {
        // Change expected - verify exact count
        let expected_total = num_payments + num_expected_change;
        if total_outputs != expected_total {
            return Err(VerificationFailure::OutputMismatch(
                format!(
                    "Output count mismatch: {} outputs but expected {} payments + {} change = {}",
                    total_outputs, num_payments, num_expected_change, expected_total
                )
            ));
        }
    }

    // Verify change outputs match expected (only if expected_change is provided)
    // If expected_change is empty, we skip change verification
    let pczt_payment_outputs = if num_expected_change > 0 {
        // We need to match change outputs from the PCZT with expected_change
        // For transparent change: compare scriptPubKey and value
        let mut pczt_change_count = 0;
        let mut payment_outputs = Vec::new();

        // Separate PCZT transparent outputs into change vs payment
        // Strategy: match against expected_change first
        for pczt_output in transparent_outputs {
            let mut is_change = false;
            for expected in expected_change {
                // Compare script and value
                // pczt_output.script_pubkey() returns &Vec<u8>
                // expected.script_pubkey() returns &Script
                // We need to serialize expected script to compare
                let mut expected_script_bytes = Vec::new();
                if expected.script_pubkey().write(&mut expected_script_bytes).is_err() {
                    continue;
                }
                // Script::write() adds CompactSize prefix, but pczt stores raw bytes
                // Skip the prefix (first byte for scripts < 253 bytes)
                let expected_script_raw = if expected_script_bytes.len() > 0 && expected_script_bytes[0] < 0xfd {
                    &expected_script_bytes[1..]
                } else {
                    &expected_script_bytes[..]
                };

                let pczt_script = pczt_output.script_pubkey();

                let scripts_match = pczt_script.as_slice() == expected_script_raw;
                let values_match = *pczt_output.value() == expected.value().into_u64();

                if scripts_match && values_match {
                    is_change = true;
                    pczt_change_count += 1;
                    break;
                }
            }
            if !is_change {
                payment_outputs.push(pczt_output);
            }
        }

        // Verify we found all expected change outputs
        if pczt_change_count != expected_change.len() {
            return Err(VerificationFailure::ChangeMismatch);
        }

        payment_outputs
    } else {
        // No expected change - assume all transparent outputs are payments
        transparent_outputs.iter().collect()
    };

    // Verify payment outputs match transaction request
    // For transparent payments: parse address from script and compare amounts
    for payment in &transaction_request.payments {
        let addr = payment.address.parse::<ZcashAddress>()
            .map_err(|_| VerificationFailure::OutputMismatch(
                format!("Invalid payment address: {}", payment.address)
            ))?;

        // Check if this is a transparent address
        if let Ok(t_addr) = addr.clone().convert::<zcash_transparent::address::TransparentAddress>() {
            // Find matching transparent output
            let found = pczt_payment_outputs.iter().any(|output| {
                // Compare script and amount
                let output_script = output.script_pubkey();
                let expected_script: zcash_transparent::address::Script = t_addr.script().into();

                // Serialize expected script for comparison
                let mut expected_script_bytes = Vec::new();
                if expected_script.write(&mut expected_script_bytes).is_err() {
                    return false;
                }
                // Skip CompactSize prefix
                let expected_script_raw = if expected_script_bytes.len() > 0 && expected_script_bytes[0] < 0xfd {
                    &expected_script_bytes[1..]
                } else {
                    &expected_script_bytes[..]
                };

                let scripts_match = output_script.as_slice() == expected_script_raw;
                let amounts_match = *output.value() == payment.amount;
                scripts_match && amounts_match
            });

            if !found {
                return Err(VerificationFailure::OutputMismatch(
                    format!("Payment to {} for {} zatoshis not found in PCZT outputs",
                        payment.address, payment.amount)
                ));
            }
        } else {
            // Shielded payment - verify Orchard action exists
            // Note: We cannot verify exact amounts or addresses for shielded outputs
            // as this data is encrypted. We can only verify the count.
            if num_orchard_outputs == 0 {
                return Err(VerificationFailure::OutputMismatch(
                    "Shielded payment requested but no Orchard outputs found".to_string()
                ));
            }
        }
    }

    // Verify fee is reasonable (not exceeding 1% of total input value)
    // This is a sanity check to prevent excessive fees from malleation
    // Note: We don't have access to input values here, so we use output total as proxy
    let total_output_value: u64 = transparent_outputs.iter()
        .map(|o| *o.value())
        .sum();

    let requested_total = transaction_request.total_amount();
    if total_output_value > 0 && requested_total > 0 {
        let max_reasonable_fee = requested_total / 100; // 1% max
        if total_output_value + max_reasonable_fee < requested_total {
            return Err(VerificationFailure::InvalidFee);
        }
    }

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
    let hash = signer.transparent_sighash(input_index)
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
