//! Test fixtures for PCZT library testing
#![allow(dead_code)]
#![allow(deprecated)]

use t2z::types::{Payment, TransactionRequest};

/// Test Zcash addresses (testnet)
pub mod addresses {
    /// Transparent address (P2PKH testnet)
    pub const TRANSPARENT: &str = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma";

    /// Another transparent address for testing multiple outputs
    pub const TRANSPARENT_2: &str = "tmBsTi2xWTjUdEXnuTceL7fecEQKeWi4vxA";

    /// Generate a valid testnet unified address with Orchard receiver
    pub fn unified_orchard() -> String {
        use orchard::keys::{SpendingKey, FullViewingKey};
        use zcash_address::unified::{Address as UnifiedAddress, Receiver, Encoding};
        use zcash_address::Network;

        let orchard_sk = SpendingKey::from_bytes([42u8; 32]).unwrap();
        let orchard_fvk = FullViewingKey::from(&orchard_sk);
        let orchard_addr = orchard_fvk.address_at(0u32, orchard::keys::Scope::External);

        let items = vec![Receiver::Orchard(orchard_addr.to_raw_address_bytes())];
        let ua = UnifiedAddress::try_from_items(items).unwrap();
        ua.encode(&Network::Test)
    }
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
    let payment = Payment::new(addresses::unified_orchard(), amounts::MEDIUM);
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
    Payment::new(addresses::unified_orchard(), amounts::SMALL)
        .with_memo("Test payment".to_string())
}

/// Sample transparent input data
/// Creates a realistic test input with proper serialization format
pub fn sample_transparent_inputs() -> Vec<u8> {
    use t2z::types::{TransparentInput, serialize_transparent_inputs};
    use zcash_transparent::address::TransparentAddress;

    // Create a test keypair (same as used in create_test_pczt)
    let secp = secp256k1::Secp256k1::new();
    let sk = secp256k1::SecretKey::from_slice(&[1u8; 32]).unwrap();
    let pubkey = secp256k1::PublicKey::from_secret_key(&secp, &sk);
    let transparent_addr = TransparentAddress::from_pubkey(&pubkey);

    // Get the script and write it to get the bytes (includes CompactSize prefix)
    // Then extract just the script part (skip the CompactSize length prefix)
    let script: zcash_transparent::address::Script = transparent_addr.script().into();
    let mut script_with_prefix = Vec::new();
    script.write(&mut script_with_prefix).unwrap();

    // Script::write() outputs: [CompactSize length][script bytes]
    // We need to skip the CompactSize prefix to get raw script bytes
    // For P2PKH scripts (25 bytes), the prefix is 1 byte (0x19)
    let script_bytes = if script_with_prefix.len() > 0 && script_with_prefix[0] < 0xfd {
        // Single-byte length prefix
        script_with_prefix[1..].to_vec()
    } else {
        // Shouldn't happen for standard P2PKH scripts, but handle it anyway
        script_with_prefix
    };

    // Create a sample UTXO input with 1 ZEC
    // Use a realistic-looking txid (sha256 of some test data)
    use sha2::{Sha256, Digest};
    let mut hasher = Sha256::new();
    hasher.update(b"test transaction for t2z");
    let txid: [u8; 32] = hasher.finalize().into();

    let input = TransparentInput {
        pubkey,
        txid,  // Valid-looking txid
        vout: 0,
        amount: amounts::ONE_ZEC, // 1 ZEC
        script_pubkey: script_bytes,
    };

    // Serialize using the standard format
    serialize_transparent_inputs(&[input])
}

/// Test-only helper to create a PCZT with realistic transparent inputs
/// This bypasses propose_transaction and uses Builder API directly
pub fn create_test_pczt(
    transaction_request: &t2z::types::TransactionRequest,
) -> pczt::Pczt {
    use zcash_primitives::transaction::{
        builder::{Builder, BuildConfig},
        fees::zip317::FeeRule,
        components::transparent::TxOut,
    };
    use zcash_protocol::{
        consensus::TestNetwork,
        value::Zatoshis,
        memo::MemoBytes,
    };
    use zcash_address::{ZcashAddress, TryFromAddress, unified, unified::Container};
    use zcash_transparent::{address::TransparentAddress, bundle::OutPoint};
    use pczt::roles::{creator::Creator, io_finalizer::IoFinalizer};
    use rand_core::OsRng;

    let params = TestNetwork;
    let target_height = 2_000_000u32.into();

    let mut builder = Builder::new(
        params,
        target_height,
        BuildConfig::Standard {
            sapling_anchor: None,
            orchard_anchor: Some(orchard::Anchor::empty_tree()),
        },
    );

    // Add a transparent input with sufficient funds (test-only)
    let secp = secp256k1::Secp256k1::new();
    let sk = secp256k1::SecretKey::from_slice(&[1u8; 32]).unwrap();
    let pubkey = secp256k1::PublicKey::from_secret_key(&secp, &sk);
    let transparent_addr = TransparentAddress::from_pubkey(&pubkey);

    let utxo = OutPoint::new([0u8; 32], 0);
    let coin = TxOut::new(
        Zatoshis::const_from_u64(100_000_000), // 1 ZEC
        transparent_addr.script().into(),
    );

    builder.add_transparent_input(pubkey, utxo, coin).unwrap();

    // Add change output
    let change_amount = Zatoshis::const_from_u64(99_890_000);
    builder.add_transparent_output(&transparent_addr, change_amount).unwrap();

    // Add outputs from payment request
    for payment in &transaction_request.payments {
        let addr: ZcashAddress = payment.address.parse().unwrap();
        let amount = Zatoshis::from_u64(payment.amount).unwrap();

        if let Ok(t_addr) = addr.clone().convert::<TransparentAddress>() {
            builder.add_transparent_output(&t_addr, amount).unwrap();
        } else {
            // Handle unified address with Orchard
            struct UnifiedWrapper(unified::Address);
            impl TryFromAddress for UnifiedWrapper {
                type Error = String;
                fn try_from_unified(
                    _: zcash_protocol::consensus::NetworkType,
                    data: unified::Address
                ) -> Result<Self, zcash_address::ConversionError<Self::Error>> {
                    Ok(UnifiedWrapper(data))
                }
            }

            let wrapper = addr.convert::<UnifiedWrapper>().unwrap();
            let receivers = wrapper.0.items();
            let orchard_raw = receivers.iter()
                .find_map(|r| if let unified::Receiver::Orchard(a) = r { Some(a) } else { None })
                .unwrap();

            let orchard_addr = Option::from(orchard::Address::from_raw_address_bytes(orchard_raw)).unwrap();
            let memo = payment.memo.as_ref()
                .and_then(|m| MemoBytes::from_bytes(m.as_bytes()).ok())
                .unwrap_or_else(|| MemoBytes::empty());

            builder.add_orchard_output::<FeeRule>(None, orchard_addr, amount.into_u64(), memo).unwrap();
        }
    }

    // Build PCZT
    let pczt_result = builder.build_for_pczt(OsRng, &FeeRule::standard()).unwrap();
    let pczt = Creator::build_from_parts(pczt_result.pczt_parts).unwrap();
    IoFinalizer::new(pczt).finalize_io().unwrap()
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

        let u_payment = Payment::new(addresses::unified_orchard(), 1000);
        assert!(u_payment.is_unified());
        assert!(!u_payment.is_transparent());
    }
}
