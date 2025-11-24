// Package main demonstrates a basic transparent-to-transparent Zcash transaction
// using the t2z library with PCZT (Partially Constructed Zcash Transactions).
package main

import (
	"encoding/hex"
	"fmt"
	"log"

	t2z "github.com/gstohl/t2z/go"
	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/decred/dcrd/dcrec/secp256k1/v4/ecdsa"
)

func main() {
	fmt.Println("=== Basic t2z (Transparent to Zcash) Example ===\n")

	// Step 1: Create a payment request
	fmt.Println("1. Creating payment request...")
	payments := []t2z.Payment{
		{
			Address: "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", // Testnet transparent address
			Amount:  100_000,                               // 0.001 ZEC (100,000 zatoshis)
			Memo:    "Payment from t2z example",
		},
	}

	request, err := t2z.NewTransactionRequest(payments)
	if err != nil {
		log.Fatalf("Failed to create transaction request: %v", err)
	}
	defer request.Free()
	fmt.Printf("   ✓ Created request with %d payment(s)\n\n", len(payments))

	// Step 2: Prepare transparent inputs (UTXOs to spend)
	fmt.Println("2. Preparing transparent inputs...")

	// For this example, we'll use test keys (DO NOT USE IN PRODUCTION)
	privateKeyBytes := make([]byte, 32)
	for i := range privateKeyBytes {
		privateKeyBytes[i] = 1 // Test key: [1, 1, 1, ...]
	}

	// Derive public key from private key
	privKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)
	pubKey := privKey.PubKey()
	pubKeyBytes := pubKey.SerializeCompressed() // 33 bytes

	fmt.Printf("   Public key: %s\n", hex.EncodeToString(pubKeyBytes))

	// Create a P2PKH script for this public key
	// In production, this would come from your UTXO
	scriptPubKeyHex := "1976a91479b000887626b294a914501a4cd226b58b23598388ac"
	scriptPubKey, _ := hex.DecodeString(scriptPubKeyHex)

	// Create a fake UTXO for demonstration
	// In production, you'd get this from your wallet or blockchain query
	var txid [32]byte
	copy(txid[:], []byte("example_transaction_id_000000000")) // Fake txid

	inputs := []t2z.TransparentInput{
		{
			Pubkey:       pubKeyBytes,
			TxID:         txid,
			Vout:         0,
			Amount:       100_000_000, // 1 ZEC (enough to cover payment + fees)
			ScriptPubKey: scriptPubKey,
		},
	}
	fmt.Printf("   ✓ Prepared %d input(s) with total: 1.0 ZEC\n\n", len(inputs))

	// Step 3: Propose the transaction (creates PCZT)
	fmt.Println("3. Proposing transaction (creating PCZT)...")
	pczt, err := t2z.ProposeTransaction(inputs, request)
	if err != nil {
		log.Fatalf("Failed to propose transaction: %v", err)
	}
	// Note: pczt will be consumed by ProveTransaction, so don't Free() it
	fmt.Println("   ✓ PCZT created successfully\n")

	// Step 4: Add Orchard proofs (required even for transparent outputs)
	fmt.Println("4. Adding Orchard proofs...")
	proved, err := t2z.ProveTransaction(pczt)
	if err != nil {
		log.Fatalf("Failed to add proofs: %v", err)
	}
	// Note: proved will be consumed by AppendSignature
	fmt.Println("   ✓ Proofs added successfully\n")

	// Step 5: Get signature hash for input
	fmt.Println("5. Getting signature hash for signing...")
	sighash, err := t2z.GetSighash(proved, 0)
	if err != nil {
		log.Fatalf("Failed to get sighash: %v", err)
	}
	fmt.Printf("   Sighash: %s\n", hex.EncodeToString(sighash[:]))
	fmt.Println("   ✓ Sighash obtained\n")

	// Step 6: Sign the sighash using secp256k1
	fmt.Println("6. Signing transaction...")
	// Use SignCompact which returns 65 bytes: [recovery_id || r || s]
	compactSig := ecdsa.SignCompact(privKey, sighash[:], true)

	// Extract just the signature (skip recovery ID)
	var signature [64]byte
	copy(signature[:], compactSig[1:])

	fmt.Printf("   Signature: %s\n", hex.EncodeToString(signature[:]))
	fmt.Println("   ✓ Transaction signed\n")

	// Step 7: Append signature to PCZT
	fmt.Println("7. Appending signature to PCZT...")
	signed, err := t2z.AppendSignature(proved, 0, signature)
	if err != nil {
		log.Fatalf("Failed to append signature: %v", err)
	}
	// Note: signed will be consumed by FinalizeAndExtract
	fmt.Println("   ✓ Signature appended\n")

	// Step 8: Finalize and extract transaction bytes
	fmt.Println("8. Finalizing transaction...")
	txBytes, err := t2z.FinalizeAndExtract(signed)
	if err != nil {
		log.Fatalf("Failed to finalize transaction: %v", err)
	}
	fmt.Printf("   ✓ Transaction finalized (%d bytes)\n\n", len(txBytes))

	// Step 9: Display result
	fmt.Println("=== Transaction Ready ===")
	fmt.Printf("Transaction hex: %s\n", hex.EncodeToString(txBytes))
	fmt.Println("\nThis transaction can now be broadcast to the Zcash network using:")
	fmt.Println("  - zcash-cli sendrawtransaction <hex>")
	fmt.Println("  - lightwalletd API")
	fmt.Println("\n✓ Example completed successfully!")
}
