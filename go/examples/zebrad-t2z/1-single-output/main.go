// Example 1: Single Output Transaction (Transparent -> Transparent)
//
// Demonstrates the basic t2z workflow:
// - Load UTXO and keys
// - Create a payment to another transparent address
// - Propose, sign (client-side), and create the transaction
//
// Run with: go run ./examples/zebrad/1-single-output/

package main

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"log"
	"strings"

	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/decred/dcrd/dcrec/secp256k1/v4/ecdsa"
	t2z "github.com/gstohl/t2z/go"
	"golang.org/x/crypto/ripemd160"
)

// Test keypair (same as TypeScript TEST_KEYPAIR)
// Private key: e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35
// Address: tmEUfekwCArJoFTMEL2kFwQyrsDMCNX5ZFf
func createTestKeypair() ([]byte, []byte) {
	privateKeyHex := "e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35"
	privateKeyBytes, _ := hex.DecodeString(privateKeyHex)

	privKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)
	pubKeyBytes := privKey.PubKey().SerializeCompressed()

	return privateKeyBytes, pubKeyBytes
}

// hash160 computes RIPEMD160(SHA256(data)) - standard Bitcoin/Zcash hash
func hash160(data []byte) []byte {
	sha256Hash := sha256.Sum256(data)
	ripemd160Hasher := ripemd160.New()
	ripemd160Hasher.Write(sha256Hash[:])
	return ripemd160Hasher.Sum(nil)
}

// Create a P2PKH script for the given public key
func createP2PKHScript(pubkey []byte) []byte {
	// Format: OP_DUP OP_HASH160 <20-byte-hash> OP_EQUALVERIFY OP_CHECKSIG
	pubkeyHash := hash160(pubkey)
	script := make([]byte, 25)
	script[0] = 0x76 // OP_DUP
	script[1] = 0xa9 // OP_HASH160
	script[2] = 0x14 // PUSH 20 bytes
	copy(script[3:23], pubkeyHash)
	script[23] = 0x88 // OP_EQUALVERIFY
	script[24] = 0xac // OP_CHECKSIG
	return script
}

// Sign a message hash using secp256k1
func signMessage(privateKey []byte, message [32]byte) [64]byte {
	privKey := secp256k1.PrivKeyFromBytes(privateKey)
	compact := ecdsa.SignCompact(privKey, message[:], true)

	var sigBytes [64]byte
	copy(sigBytes[:], compact[1:]) // Skip recovery ID
	return sigBytes
}

func zatoshiToZec(zatoshi uint64) string {
	return fmt.Sprintf("%.8f", float64(zatoshi)/100_000_000)
}

func main() {
	fmt.Println()
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  EXAMPLE 1: SINGLE OUTPUT TRANSACTION (Transparent -> Transparent)")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	// 1. Create test keypair (matches TypeScript TEST_KEYPAIR)
	privateKey, pubkey := createTestKeypair()
	destAddress := "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma" // Test recipient

	fmt.Println("Configuration:")
	fmt.Printf("  Source pubkey: %s...\n", hex.EncodeToString(pubkey)[:32])
	fmt.Printf("  Destination: %s\n\n", destAddress)

	// 2. Create a mock UTXO (in production, this comes from Zebra)
	// TypeScript: Uses coinbase UTXOs from regtest
	var txid [32]byte
	copy(txid[:], []byte("example1_single_output_test_txid"))
	scriptPubKey := createP2PKHScript(pubkey)

	// Input amount: 1 ZEC (like TypeScript uses coinbase rewards)
	inputAmount := uint64(100_000_000) // 1 ZEC

	inputs := []t2z.TransparentInput{
		{
			Pubkey:       pubkey,
			TxID:         txid,
			Vout:         0,
			Amount:       inputAmount,
			ScriptPubKey: scriptPubKey,
		},
	}

	fmt.Printf("UTXO: %s ZEC\n\n", zatoshiToZec(inputAmount))

	// 3. Create payment request (matches TypeScript Example 1)
	// TypeScript: paymentAmount = input.amount / 2n (50% of input)
	// TypeScript: fee = 10_000n for transparent-only tx
	paymentAmount := inputAmount / 2 // 50% of input
	fee := uint64(10_000)            // ZIP-317 fee for T→T

	payments := []t2z.Payment{
		{
			Address: destAddress,
			Amount:  paymentAmount,
		},
	}

	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  TRANSACTION SUMMARY")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Printf("\nInput:  %s ZEC\n", zatoshiToZec(inputAmount))
	fmt.Printf("Output: %s ZEC -> %s...\n", zatoshiToZec(paymentAmount), destAddress[:20])
	fmt.Printf("Fee:    %s ZEC\n", zatoshiToZec(fee))
	fmt.Printf("Change: %s ZEC\n", zatoshiToZec(inputAmount-paymentAmount-fee))
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	request, err := t2z.NewTransactionRequest(payments)
	if err != nil {
		log.Fatalf("Failed to create transaction request: %v", err)
	}
	defer request.Free()

	// Mainnet is the default, just set target height
	err = request.SetTargetHeight(2_500_000)
	if err != nil {
		log.Fatalf("Failed to set target height: %v", err)
	}
	fmt.Println("Using mainnet parameters (target height: 2,500,000)")
	fmt.Println()

	// Step 1: Propose transaction
	fmt.Println("1. Proposing transaction...")
	pczt, err := t2z.ProposeTransaction(inputs, request)
	if err != nil {
		log.Fatalf("Failed to propose transaction: %v", err)
	}
	fmt.Println("   PCZT created")
	fmt.Println()

	// Step 2: Prove transaction (for transparent-only, this is minimal)
	fmt.Println("2. Proving transaction...")
	proved, err := t2z.ProveTransaction(pczt)
	if err != nil {
		log.Fatalf("Failed to prove transaction: %v", err)
	}
	fmt.Println("   Proofs generated")
	fmt.Println()

	// Step 3: Verify before signing
	fmt.Println("3. Verifying PCZT before signing...")
	err = t2z.VerifyBeforeSigning(proved, request, []t2z.TransparentOutput{})
	if err != nil {
		fmt.Printf("   Note: Verification returned: %v\n", err)
	} else {
		fmt.Println("   Verification passed")
	}
	fmt.Println()

	// Step 4: Get sighash for the transparent input
	fmt.Println("4. Getting sighash for input 0...")
	sighash, err := t2z.GetSighash(proved, 0)
	if err != nil {
		log.Fatalf("Failed to get sighash: %v", err)
	}
	fmt.Printf("   Sighash: %s...\n", hex.EncodeToString(sighash[:])[:32])
	fmt.Println()

	// Step 5: Sign the sighash with our private key (client-side)
	fmt.Println("5. Signing transaction (client-side)...")
	signature := signMessage(privateKey, sighash)
	fmt.Printf("   Signature: %s...\n", hex.EncodeToString(signature[:])[:32])
	fmt.Println()

	// Step 6: Append signature to PCZT
	fmt.Println("6. Appending signature to PCZT...")
	signed, err := t2z.AppendSignature(proved, 0, signature)
	if err != nil {
		log.Fatalf("Failed to append signature: %v", err)
	}
	fmt.Println("   Signature appended")
	fmt.Println()

	// Step 7: Finalize and extract transaction bytes
	fmt.Println("7. Finalizing transaction...")
	txBytes, err := t2z.FinalizeAndExtract(signed)
	if err != nil {
		log.Fatalf("Failed to finalize: %v", err)
	}
	fmt.Printf("   Transaction finalized (%d bytes)\n", len(txBytes))
	fmt.Println()

	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  TRANSACTION READY FOR BROADCAST")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Printf("\nTransaction hex (first 100 chars): %s...\n", hex.EncodeToString(txBytes)[:100])
	fmt.Println()

	fmt.Println("EXAMPLE 1 COMPLETED SUCCESSFULLY!")
	fmt.Println()
	fmt.Println("Next steps (with live Zebra):")
	fmt.Println("  1. Connect to Zebra RPC")
	fmt.Println("  2. Fetch real UTXOs for your address")
	fmt.Println("  3. Broadcast transaction with sendrawtransaction")
	fmt.Println()
}
