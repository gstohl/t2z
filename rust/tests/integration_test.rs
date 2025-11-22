/// Integration tests for PCZT library
///
/// These tests verify the complete workflow of creating, signing, and finalizing
/// a Zcash transaction with shielded outputs.

mod common;

use common::*;
use t2z::{*, types::*};

#[test]
fn test_payment_request_creation() {
    let request = simple_payment_request();
    assert_eq!(request.payments.len(), 1);
    assert_eq!(request.total_amount(), amounts::SMALL);
    assert!(!request.has_shielded_outputs());
}

#[test]
fn test_shielded_payment_request() {
    let request = shielded_payment_request();
    assert_eq!(request.payments.len(), 1);
    // Note: is_transparent() is a simple heuristic, may need refinement
    assert!(!request.payments[0].is_transparent());
}

#[test]
fn test_multi_payment_request() {
    let request = multi_payment_request();
    assert_eq!(request.payments.len(), 2);
    assert_eq!(request.total_amount(), amounts::SMALL * 2);
}

#[test]
fn test_propose_transaction() {
    let request = simple_payment_request();
    let inputs = sample_transparent_inputs();

    let result = propose_transaction(&inputs, request.clone());

    // Should now succeed with basic implementation
    match result {
        Ok(pczt) => {
            // Verify we got a valid PCZT back
            let serialized = serialize_pczt(&pczt);
            assert!(!serialized.is_empty(), "PCZT should serialize to non-empty bytes");

            // REAL TEST: Check if the PCZT actually contains our payment
            // TODO: Once Builder integration is complete, this should validate:
            // - Number of outputs matches payments
            // - Output values match payment amounts
            // - Output addresses match payment addresses

            // For now, just verify the structure is valid
            let parsed_back = parse_pczt(&serialized);
            assert!(parsed_back.is_ok(), "PCZT should parse back correctly");
        }
        Err(e) => panic!("propose_transaction failed: {:?}", e),
    }
}

#[test]
#[ignore] // Ignored until implementation is complete
fn test_full_transaction_workflow() {
    // This test demonstrates the complete workflow
    let request = shielded_payment_request();
    let inputs = sample_transparent_inputs();

    // 1. Propose transaction
    let pczt = propose_transaction(&inputs, request).expect("Failed to propose");

    // 2. Add proofs
    let proved = prove_transaction(pczt).expect("Failed to prove");

    // 3. Get sighash
    let sighash = get_sighash(&proved, 0).expect("Failed to get sighash");
    assert_eq!(sighash.as_bytes().len(), 32);

    // 4. Sign (mock signing for now)
    let signature = sample_signature();

    // 5. Append signature
    let signed = append_signature(proved, 0, signature).expect("Failed to append signature");

    // 6. Finalize and extract
    let tx_bytes = finalize_and_extract(signed).expect("Failed to finalize");
    assert!(!tx_bytes.is_empty());
}

#[test]
fn test_pczt_serialization_roundtrip() {
    // Create a PCZT
    let request = simple_payment_request();
    let inputs = sample_transparent_inputs();
    let pczt = propose_transaction(&inputs, request).expect("Failed to propose");

    // Serialize
    let serialized = serialize_pczt(&pczt);
    assert!(!serialized.is_empty(), "Serialized PCZT should not be empty");

    // Deserialize
    let deserialized = parse_pczt(&serialized).expect("Failed to parse serialized PCZT");

    // Serialize again and compare - should be identical
    let reserialized = serialize_pczt(&deserialized);
    assert_eq!(
        serialized, reserialized,
        "Serialization should be deterministic and roundtrip correctly"
    );
}

#[test]
fn test_parse_invalid_pczt() {
    let invalid_data = vec![0xFF; 100];
    let result = parse_pczt(&invalid_data);
    assert!(result.is_err());
}

#[test]
fn test_payment_with_memo() {
    let payment = payment_with_memo();
    assert!(payment.memo.is_some());
    assert_eq!(payment.memo.unwrap(), "Test payment");
}

#[test]
fn test_transaction_request_builder() {
    let request = TransactionRequest::new(vec![
        Payment::new(addresses::TRANSPARENT.to_string(), amounts::SMALL)
            .with_label("Alice".to_string())
            .with_message("Coffee payment".to_string()),
    ]).with_memo("Batch payment".to_string());

    assert!(request.memo.is_some());
    assert!(request.payments[0].label.is_some());
    assert!(request.payments[0].message.is_some());
}

// Mock crypto tests (only run when feature is enabled)
#[test]
#[cfg(feature = "mock-crypto")]
fn test_mock_workflow() {
    use common::mock::*;

    // Test that mock functions work for rapid iteration
    let mock_sighash = mock_get_sighash(&create_mock_pczt().unwrap(), 0);
    assert!(mock_sighash.is_ok());

    let sighash = mock_sighash.unwrap();
    let signature = sample_signature();
    assert!(mock_verify_signature(&sighash, &signature));
}

#[test]
fn test_sighash_type() {
    use t2z::types::SigHash;

    let hash = SigHash([1u8; 32]);
    assert_eq!(hash.as_bytes().len(), 32);
    assert_eq!(hash.to_vec().len(), 32);
}
