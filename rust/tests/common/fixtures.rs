/// Test fixtures for PCZT library testing
use t2z::types::{Payment, TransactionRequest};

/// Test Zcash addresses (testnet)
pub mod addresses {
    /// Transparent address (P2PKH testnet)
    pub const TRANSPARENT: &str = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma";

    /// Unified address with Orchard receiver (testnet example)
    pub const UNIFIED_ORCHARD: &str = "u1test1234567890abcdefghijklmnopqrstuvwxyz";

    /// Another transparent address for testing multiple outputs
    pub const TRANSPARENT_2: &str = "tmBsTi2xWTjUdEXnuTceL7fecEQKeWi4vxA";
}

/// Test amounts in zatoshis
pub mod amounts {
    /// 0.001 ZEC = 100,000 zatoshis
    pub const SMALL: u64 = 100_000;

    /// 0.01 ZEC = 1,000,000 zatoshis
    pub const MEDIUM: u64 = 1_000_000;

    /// 0.1 ZEC = 10,000,000 zatoshis
    pub const LARGE: u64 = 10_000_000;

    /// 1 ZEC = 100,000,000 zatoshis
    pub const ONE_ZEC: u64 = 100_000_000;

    /// Typical fee
    pub const FEE: u64 = 10_000; // 0.0001 ZEC
}

/// Creates a simple single-payment transaction request
pub fn simple_payment_request() -> TransactionRequest {
    let payment = Payment::new(addresses::TRANSPARENT.to_string(), amounts::SMALL);
    TransactionRequest::new(vec![payment])
}

/// Creates a payment request with a shielded output
pub fn shielded_payment_request() -> TransactionRequest {
    let payment = Payment::new(addresses::UNIFIED_ORCHARD.to_string(), amounts::MEDIUM);
    TransactionRequest::new(vec![payment])
}

/// Creates a payment request with multiple outputs
pub fn multi_payment_request() -> TransactionRequest {
    let payments = vec![
        Payment::new(addresses::TRANSPARENT.to_string(), amounts::SMALL),
        Payment::new(addresses::TRANSPARENT_2.to_string(), amounts::SMALL),
    ];
    TransactionRequest::new(payments)
}

/// Creates a payment with memo
pub fn payment_with_memo() -> Payment {
    Payment::new(addresses::UNIFIED_ORCHARD.to_string(), amounts::SMALL)
        .with_memo("Test payment".to_string())
}

/// Sample transparent input data (serialized placeholder)
/// Returns serialized mock UTXO data with sufficient balance for tests
pub fn sample_transparent_inputs() -> Vec<u8> {
    // For now, return a magic marker that the propose_transaction function
    // will recognize to add mock inputs during testing
    // In production, this would contain actual UTXO data
    b"MOCK_INPUTS_FOR_TESTING".to_vec()
}

/// Expected signature hash for testing (placeholder)
pub fn expected_sighash() -> [u8; 32] {
    // This would be a known-good sighash from test vectors
    [0u8; 32]
}

/// Sample signature for testing
pub fn sample_signature() -> [u8; 64] {
    // Placeholder signature
    [0u8; 64]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_simple_payment_request() {
        let request = simple_payment_request();
        assert_eq!(request.payments.len(), 1);
        assert_eq!(request.total_amount(), amounts::SMALL);
    }

    #[test]
    fn test_multi_payment_request() {
        let request = multi_payment_request();
        assert_eq!(request.payments.len(), 2);
        assert_eq!(request.total_amount(), amounts::SMALL * 2);
    }

    #[test]
    fn test_payment_address_detection() {
        let t_payment = Payment::new(addresses::TRANSPARENT.to_string(), 1000);
        assert!(t_payment.is_transparent());
        assert!(!t_payment.is_unified());

        let u_payment = Payment::new(addresses::UNIFIED_ORCHARD.to_string(), 1000);
        assert!(u_payment.is_unified());
    }
}
