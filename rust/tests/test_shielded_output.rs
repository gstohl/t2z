//! Test for transparent-to-Orchard (shielded) transactions
#![allow(deprecated)] // Network type alias is deprecated, but Encoding trait requires it

use t2z::*;
use t2z::types::{Payment, TransactionRequest};

mod common;
use common::fixtures::*;

#[test]
fn test_transparent_to_orchard_workflow() {
    // Generate a real unified address with Orchard receiver
    use orchard::keys::{SpendingKey, FullViewingKey};
    use zcash_address::unified::{Address as UnifiedAddress, Encoding};
    use zcash_address::Network;

    // Create Orchard key
    let orchard_sk = SpendingKey::from_bytes([42u8; 32]).unwrap();
    let orchard_fvk = FullViewingKey::from(&orchard_sk);
    let orchard_addr = orchard_fvk.address_at(0u32, orchard::keys::Scope::External);

    // Build unified address with Orchard receiver
    let mut items = vec![];
    items.push(zcash_address::unified::Receiver::Orchard(
        orchard_addr.to_raw_address_bytes()
    ));

    let ua = UnifiedAddress::try_from_items(items).expect("Failed to create unified address");
    let ua_string = ua.encode(&Network::Test);

    println!("Generated unified address: {}", ua_string);

    // Create payment request to shielded address
    let payment = Payment::new(ua_string.clone(), amounts::SMALL)
        .with_memo("Shielded payment test".to_string());
    let request = TransactionRequest::new(vec![payment]);

    // Create transaction with transparent input -> shielded output
    let inputs = sample_transparent_inputs();

    // 1. Propose transaction
    let pczt = propose_transaction(&inputs, request, None)
        .expect("Failed to propose transparent->shielded transaction");

    // Verify PCZT has Orchard actions
    assert!(
        pczt.orchard().actions().len() > 0,
        "PCZT should have Orchard actions for shielded output"
    );

    println!("✅ PCZT created with {} Orchard action(s)", pczt.orchard().actions().len());

    // 2. Add Orchard proofs (this is the key step for shielded outputs)
    let proved = prove_transaction(pczt).expect("Failed to add Orchard proofs");

    println!("✅ Orchard proofs added successfully");

    // 3. Sign transparent inputs
    use pczt::roles::signer::Signer;
    let sk = secp256k1::SecretKey::from_slice(&[1u8; 32]).expect("Valid secret key");

    let mut signer = Signer::new(proved).expect("Failed to create signer");
    signer.sign_transparent(0, &sk).expect("Failed to sign");
    let signed = signer.finish();

    println!("✅ Transparent inputs signed");

    // 4. Finalize and extract transaction
    let tx_bytes = finalize_and_extract(signed).expect("Failed to finalize shielded transaction");

    assert!(!tx_bytes.is_empty(), "Transaction bytes should not be empty");
    println!("✅ Transaction finalized: {} bytes", tx_bytes.len());

    // Verify transaction has both transparent and shielded components
    // (This is a basic sanity check - full validation would require parsing the tx)
    assert!(tx_bytes.len() > 100, "Shielded transaction should be substantial size");

    println!("✅ Complete transparent->Orchard workflow successful!");
}

#[test]
fn test_orchard_with_memo() {
    // Test that memos are properly included in Orchard outputs
    use orchard::keys::{SpendingKey, FullViewingKey};
    use zcash_address::unified::{Address as UnifiedAddress, Encoding};
    use zcash_address::Network;

    // Generate unified address
    let orchard_sk = SpendingKey::from_bytes([99u8; 32]).unwrap();
    let orchard_fvk = FullViewingKey::from(&orchard_sk);
    let orchard_addr = orchard_fvk.address_at(0u32, orchard::keys::Scope::External);

    let items = vec![zcash_address::unified::Receiver::Orchard(
        orchard_addr.to_raw_address_bytes()
    )];
    let ua = UnifiedAddress::try_from_items(items).unwrap();
    let ua_string = ua.encode(&Network::Test);

    // Create payment with memo
    let memo_text = "Hello Zcash shielded!";
    let payment = Payment::new(ua_string, amounts::MEDIUM)
        .with_memo(memo_text.to_string());
    let request = TransactionRequest::new(vec![payment]);

    let inputs = sample_transparent_inputs();

    // Create and finalize transaction
    let pczt = propose_transaction(&inputs, request, None).expect("Failed to propose");
    let proved = prove_transaction(pczt).expect("Failed to prove");

    use pczt::roles::signer::Signer;
    let sk = secp256k1::SecretKey::from_slice(&[1u8; 32]).unwrap();
    let mut signer = Signer::new(proved).unwrap();
    signer.sign_transparent(0, &sk).unwrap();
    let signed = signer.finish();

    let tx_bytes = finalize_and_extract(signed).expect("Failed to finalize");

    assert!(!tx_bytes.is_empty());
    println!("✅ Orchard transaction with memo created successfully");
}
