/// Test for external signature appending (hardware wallet use case)
use t2z::*;
use pczt::roles::signer::Signer;

mod common;
use common::fixtures::*;

#[test]
fn test_append_signature_external() {
    // Setup: Create transaction and get sighash
    let request = simple_payment_request();
    let inputs = sample_transparent_inputs();

    let pczt = propose_transaction(&inputs, request, None).expect("Failed to propose");
    let proved = prove_transaction(pczt).expect("Failed to prove");

    // Check what script_pubkey the PCZT has for input 0
    let pczt_input = proved.transparent().inputs().get(0).expect("Input 0 exists");
    let script_pubkey = pczt_input.script_pubkey();
    println!("PCZT script_pubkey: {:?}", hex::encode(script_pubkey));

    // Get sighash using our API
    let sighash = get_sighash(&proved, 0).expect("Failed to get sighash");
    println!("Sighash: {:?}", hex::encode(sighash.as_bytes()));

    // Sign externally (simulating hardware wallet) - use the SAME key that created the input
    let secp = secp256k1::Secp256k1::signing_only();
    let sk = secp256k1::SecretKey::from_slice(&[1u8; 32]).expect("Valid secret key");
    let pubkey = secp256k1::PublicKey::from_secret_key(&secp, &sk);
    println!("Our pubkey: {:?}", hex::encode(pubkey.serialize()));

    // Verify this pubkey creates the expected script_pubkey
    use zcash_transparent::address::TransparentAddress;
    let expected_addr = TransparentAddress::from_pubkey(&pubkey);
    let mut expected_script = Vec::new();
    let expected_script_obj: zcash_transparent::address::Script = expected_addr.script().into();
    expected_script_obj.write(&mut expected_script).unwrap();
    println!("Expected script: {:?}", hex::encode(&expected_script));
    if &expected_script[..] == script_pubkey {
        println!("✅ Pubkey matches script_pubkey!");
    } else {
        println!("❌ WARNING: Pubkey does NOT match script_pubkey!");
    }

    let msg = secp256k1::Message::from_digest(*sighash.as_bytes());
    let sig = secp.sign_ecdsa(&msg, &sk);
    let signature = sig.serialize_compact();

    println!("Signature: {:?}", hex::encode(&signature));

    // Try to append signature using our API
    let result = append_signature(proved.clone(), 0, signature);

    match result {
        Ok(signed) => {
            println!("✅ Signature appended successfully!");

            // Verify it works by finalizing
            let tx_bytes = finalize_and_extract(signed).expect("Failed to finalize");
            assert!(!tx_bytes.is_empty());
        }
        Err(e) => {
            println!("❌ Failed to append signature: {:?}", e);

            // Compare with direct signing
            println!("\n--- Comparing with direct sign_transparent() ---");
            let mut signer2 = Signer::new(proved.clone()).expect("Failed to create signer");
            signer2.sign_transparent(0, &sk).expect("Direct signing works");
            println!("✅ Direct signing works fine");

            panic!("append_signature failed: {:?}", e);
        }
    }
}
