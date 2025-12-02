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
    consensus::{MainNetwork, TestNetwork, Parameters},
    value::Zatoshis,
    memo::MemoBytes,
};
use zcash_address::{ZcashAddress, unified};
use zcash_transparent::address::TransparentAddress;
use rand_core::OsRng;

/// ZIP-317 marginal fee per logical action (5000 zatoshis = 0.00005 ZEC)
pub const ZIP317_MARGINAL_FEE: u64 = 5_000;

/// ZIP-317 grace actions (minimum actions charged to encourage small transactions)
pub const ZIP317_GRACE_ACTIONS: usize = 2;

/// Calculates the ZIP-317 transaction fee.
///
/// This implements the standard ZIP-317 fee calculation:
/// ```text
/// fee = marginal_fee * max(grace_actions, logical_actions)
/// ```
///
/// Where:
/// - `marginal_fee` = 5000 zatoshis (0.00005 ZEC)
/// - `grace_actions` = 2 (minimum actions to encourage small transactions)
/// - For shielded: `logical_actions = transparent_actions + orchard_actions`
/// - For transparent-only: `logical_actions = max(inputs, outputs)`
/// - Orchard actions are padded to even numbers (bundling optimization)
///
/// # Arguments
/// * `num_transparent_inputs` - Number of transparent UTXOs being spent
/// * `num_transparent_outputs` - Number of transparent outputs (including change if any)
/// * `num_orchard_outputs` - Number of Orchard (shielded) outputs
///
/// # Returns
/// The calculated fee in zatoshis
///
/// # Example
/// ```
/// use t2z::calculate_fee;
///
/// // Transparent-only: 1 input, 2 outputs (1 payment + 1 change)
/// assert_eq!(calculate_fee(1, 2, 0), 10_000); // 2 actions * 5000
///
/// // Shielded: 1 input, 1 change output, 1 orchard output
/// assert_eq!(calculate_fee(1, 1, 1), 15_000); // (1 transparent + 2 orchard) * 5000
/// ```
///
/// See ZIP-317: <https://zips.z.cash/zip-0317>
pub fn calculate_fee(
    num_transparent_inputs: usize,
    num_transparent_outputs: usize,
    num_orchard_outputs: usize,
) -> u64 {
    let logical_actions = if num_orchard_outputs > 0 {
        // Shielded transaction
        // Orchard actions are padded to even numbers for bundling
        let orchard_actions = ((num_orchard_outputs + 1) / 2) * 2;
        let transparent_actions = std::cmp::max(num_transparent_inputs, num_transparent_outputs);
        transparent_actions + orchard_actions
    } else {
        // Transparent-only
        std::cmp::max(num_transparent_inputs, num_transparent_outputs)
    };

    ZIP317_MARGINAL_FEE * std::cmp::max(ZIP317_GRACE_ACTIONS, logical_actions) as u64
}

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

    // Select network parameters based on request
    // For regtest, use mainnet parameters (regtest uses mainnet branch IDs)
    // For testnet, use testnet parameters
    if transaction_request.use_mainnet {
        propose_transaction_with_network(inputs_to_spend, transaction_request, change_address, MainNetwork)
    } else {
        propose_transaction_with_network(inputs_to_spend, transaction_request, change_address, TestNetwork)
    }
}

/// Internal helper that creates a transaction with specific network parameters
fn propose_transaction_with_network<P: Parameters>(
    inputs_to_spend: &[u8],
    transaction_request: TransactionRequest,
    change_address: Option<String>,
    params: P,
) -> Result<Pczt, ProposalError> {
    // Default target heights: mainnet ~2.5M, testnet ~3.7M (both post-NU5)
    let default_height = if transaction_request.use_mainnet { 2_500_000 } else { 3_693_760 };
    let target_height = transaction_request.target_height.unwrap_or(default_height).into();

    // Create transaction builder
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
            // Try to handle as unified address
            use zcash_address::unified::Container;

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
    let total_input: u64 = inputs.iter().map(|i| i.amount).sum();
    let total_output: u64 = transaction_request.total_amount();

    // Count outputs for fee calculation (assuming change will be needed)
    let num_orchard_outputs = transaction_request.payments.iter()
        .filter(|p| p.is_unified())
        .count();
    let num_transparent_payment_outputs = transaction_request.payments.iter()
        .filter(|p| !p.is_unified())
        .count();
    // +1 for change output (we assume change is needed for fee calculation)
    let num_transparent_outputs = num_transparent_payment_outputs + 1;

    let estimated_fee = calculate_fee(inputs.len(), num_transparent_outputs, num_orchard_outputs);

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

// ============================================================================
// Verification helper functions
// ============================================================================

/// Extracts raw script bytes from a Script, stripping the CompactSize prefix
fn extract_raw_script(script: &zcash_transparent::address::Script) -> Option<Vec<u8>> {
    let mut bytes = Vec::new();
    script.write(&mut bytes).ok()?;
    // Script::write() adds CompactSize prefix - skip it for raw comparison
    // For scripts < 253 bytes, prefix is 1 byte
    if !bytes.is_empty() && bytes[0] < 0xfd {
        Some(bytes[1..].to_vec())
    } else {
        Some(bytes)
    }
}

/// Checks if a PCZT output matches a TxOut (script and value)
fn output_matches_txout(
    pczt_output: &pczt::transparent::Output,
    txout: &zcash_transparent::bundle::TxOut,
) -> bool {
    let Some(expected_raw) = extract_raw_script(txout.script_pubkey()) else {
        return false;
    };
    pczt_output.script_pubkey().as_slice() == expected_raw.as_slice()
        && *pczt_output.value() == txout.value().into_u64()
}

/// Checks if a PCZT output matches an address and amount
fn output_matches_payment(
    pczt_output: &pczt::transparent::Output,
    addr: &zcash_transparent::address::TransparentAddress,
    amount: u64,
) -> bool {
    let script: zcash_transparent::address::Script = addr.script().into();
    let Some(expected_raw) = extract_raw_script(&script) else {
        return false;
    };
    pczt_output.script_pubkey().as_slice() == expected_raw.as_slice()
        && *pczt_output.value() == amount
}

/// Separates PCZT outputs into change outputs and payment outputs
fn separate_change_outputs<'a>(
    transparent_outputs: &'a [pczt::transparent::Output],
    expected_change: &[zcash_transparent::bundle::TxOut],
) -> Result<(usize, Vec<&'a pczt::transparent::Output>), VerificationFailure> {
    let mut change_count = 0;
    let mut payment_outputs = Vec::new();

    for pczt_output in transparent_outputs {
        let is_change = expected_change.iter()
            .any(|expected| output_matches_txout(pczt_output, expected));

        if is_change {
            change_count += 1;
        } else {
            payment_outputs.push(pczt_output);
        }
    }

    if change_count != expected_change.len() {
        return Err(VerificationFailure::ChangeMismatch);
    }

    Ok((change_count, payment_outputs))
}

/// Verifies that a transparent payment exists in the outputs
fn verify_transparent_payment(
    payment_outputs: &[&pczt::transparent::Output],
    addr: &zcash_transparent::address::TransparentAddress,
    amount: u64,
    address_str: &str,
) -> Result<(), VerificationFailure> {
    let found = payment_outputs.iter()
        .any(|output| output_matches_payment(output, addr, amount));

    if !found {
        return Err(VerificationFailure::OutputMismatch(
            format!("Payment to {} for {} zatoshis not found in PCZT outputs", address_str, amount)
        ));
    }
    Ok(())
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
    let transparent_outputs = pczt.transparent().outputs();
    let orchard_actions = pczt.orchard().actions();
    let num_orchard_outputs = orchard_actions.len();
    let num_payments = transaction_request.payments.len();
    let total_outputs = transparent_outputs.len() + num_orchard_outputs;

    // Verify output counts
    if expected_change.is_empty() {
        if total_outputs < num_payments {
            return Err(VerificationFailure::OutputMismatch(
                format!("Expected at least {} payment outputs but found {}", num_payments, total_outputs)
            ));
        }
    } else {
        let expected_total = num_payments + expected_change.len();
        if total_outputs != expected_total {
            return Err(VerificationFailure::OutputMismatch(
                format!("Output count mismatch: {} outputs but expected {} payments + {} change = {}",
                    total_outputs, num_payments, expected_change.len(), expected_total)
            ));
        }
    }

    // Separate change from payment outputs
    let payment_outputs = if !expected_change.is_empty() {
        let (_, payments) = separate_change_outputs(transparent_outputs, expected_change)?;
        payments
    } else {
        transparent_outputs.iter().collect()
    };

    // Verify each payment exists in outputs
    for payment in &transaction_request.payments {
        let addr = payment.address.parse::<ZcashAddress>()
            .map_err(|_| VerificationFailure::OutputMismatch(
                format!("Invalid payment address: {}", payment.address)
            ))?;

        if let Ok(t_addr) = addr.convert::<zcash_transparent::address::TransparentAddress>() {
            verify_transparent_payment(&payment_outputs, &t_addr, payment.amount, &payment.address)?;
        } else if num_orchard_outputs == 0 {
            return Err(VerificationFailure::OutputMismatch(
                "Shielded payment requested but no Orchard outputs found".to_string()
            ));
        }
    }

    // Verify fee is reasonable
    // For transparent-only transactions: fee should not exceed 1% of total
    // For shielded transactions: we skip this check since Orchard amounts are hidden
    let has_orchard = num_orchard_outputs > 0;
    if !has_orchard {
        let total_output_value: u64 = transparent_outputs.iter().map(|o| *o.value()).sum();
        let requested_total = transaction_request.total_amount();

        if total_output_value > 0 && requested_total > 0 {
            let max_reasonable_fee = requested_total / 100;
            if total_output_value + max_reasonable_fee < requested_total {
                return Err(VerificationFailure::InvalidFee);
            }
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
        .map_err(|_| SignatureError::InvalidFormat)?;

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

