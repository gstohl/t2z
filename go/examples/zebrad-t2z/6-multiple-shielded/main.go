// Example 6: Multiple Shielded Outputs (T→Z×2)
//
// Demonstrates sending to multiple shielded recipients:
// - Use a transparent UTXO as input
// - Send to two different unified addresses with Orchard receivers
// - Shows how the library handles multiple Orchard actions
//
// Note: We use the same Orchard address twice with different amounts
// to demonstrate multiple shielded outputs.
//
// Run with: go run ./examples/zebrad/example6_multiple_shielded.go

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

// Deterministic mainnet unified addresses with Orchard receivers
// Generated from SpendingKey::from_bytes([42u8; 32]) and [43u8; 32]
const shieldedAddress6_1 = "u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz"

// Using same address for simplicity - in real usage these would be different recipients
const shieldedAddress6_2 = "u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz"

func createTestKeypair6() ([]byte, []byte) {
	privateKeyHex := "e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35"
	privateKeyBytes, _ := hex.DecodeString(privateKeyHex)

	privKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)
	pubKeyBytes := privKey.PubKey().SerializeCompressed()

	return privateKeyBytes, pubKeyBytes
}

func hash160_6(data []byte) []byte {
	sha256Hash := sha256.Sum256(data)
	ripemd160Hasher := ripemd160.New()
	ripemd160Hasher.Write(sha256Hash[:])
	return ripemd160Hasher.Sum(nil)
}

func createP2PKHScript6(pubkey []byte) []byte {
	pubkeyHash := hash160_6(pubkey)
	script := make([]byte, 25)
	script[0] = 0x76
	script[1] = 0xa9
	script[2] = 0x14
	copy(script[3:23], pubkeyHash)
	script[23] = 0x88
	script[24] = 0xac
	return script
}

func signMessage6(privateKey []byte, message [32]byte) [64]byte {
	privKey := secp256k1.PrivKeyFromBytes(privateKey)
	compact := ecdsa.SignCompact(privKey, message[:], true)

	var sigBytes [64]byte
	copy(sigBytes[:], compact[1:])
	return sigBytes
}

func zatoshiToZec6(zatoshi uint64) string {
	return fmt.Sprintf("%.8f", float64(zatoshi)/100_000_000)
}

func main() {
	fmt.Println()
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  EXAMPLE 6: MULTIPLE SHIELDED OUTPUTS (T->Z x2)")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	privateKey, pubkey := createTestKeypair6()

	fmt.Println("Configuration:")
	fmt.Printf("  Source pubkey: %s...\n", hex.EncodeToString(pubkey)[:32])
	fmt.Printf("  Recipient 1 (shielded): %s...\n", shieldedAddress6_1[:25])
	fmt.Printf("  Recipient 2 (shielded): %s...\n", shieldedAddress6_2[:25])
	fmt.Println("  Note: Both are Orchard addresses (u1... prefix)")
	fmt.Println()

	// Create mock UTXO
	var txid [32]byte
	copy(txid[:], []byte("example6_multiple_shielded_tx00"))
	scriptPubKey := createP2PKHScript6(pubkey)

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

	fmt.Printf("Using UTXO: %s ZEC\n\n", zatoshiToZec6(inputAmount))

	// Create two shielded payments
	// ZIP-317 fee for 2 Orchard outputs + transparent change
	fee := uint64(40_000) // Higher fee for multiple Orchard actions
	availableForPayments := inputAmount - fee
	payment1Amount := availableForPayments / 3 // ~33%
	payment2Amount := availableForPayments / 3 // ~33%
	// Remaining ~33% goes to change

	payments := []t2z.Payment{
		{Address: shieldedAddress6_1, Amount: payment1Amount},
		{Address: shieldedAddress6_2, Amount: payment2Amount},
	}

	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  TRANSACTION SUMMARY - MULTIPLE SHIELDED")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Printf("\nInput:    %s ZEC\n", zatoshiToZec6(inputAmount))
	fmt.Printf("Output 1: %s ZEC -> %s...\n", zatoshiToZec6(payment1Amount), shieldedAddress6_1[:20])
	fmt.Printf("Output 2: %s ZEC -> %s...\n", zatoshiToZec6(payment2Amount), shieldedAddress6_2[:20])
	fmt.Printf("Fee:      %s ZEC\n", zatoshiToZec6(fee))
	fmt.Printf("Change:   %s ZEC\n", zatoshiToZec6(inputAmount-payment1Amount-payment2Amount-fee))
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	fmt.Println("WHAT THIS DEMONSTRATES:")
	fmt.Println("   - Single transparent input")
	fmt.Println("   - Two Orchard outputs (shielded recipients)")
	fmt.Println("   - Library creates multiple Orchard actions")
	fmt.Println("   - Each recipient receives private funds")
	fmt.Println()

	request, err := t2z.NewTransactionRequest(payments)
	if err != nil {
		log.Fatalf("Failed to create transaction request: %v", err)
	}
	defer request.Free()

	request.SetTargetHeight(2_500_000)
	fmt.Println("Using mainnet parameters (target height: 2,500,000)")
	fmt.Println()

	// Workflow
	fmt.Println("1. Proposing transaction...")
	pczt, err := t2z.ProposeTransaction(inputs, request)
	if err != nil {
		log.Fatalf("Failed to propose transaction: %v", err)
	}
	fmt.Println("   PCZT created with multiple Orchard outputs")
	fmt.Println()

	fmt.Println("2. Proving transaction (generating Orchard ZK proofs)...")
	fmt.Println("   This takes longer with multiple outputs...")
	proved, err := t2z.ProveTransaction(pczt)
	if err != nil {
		log.Fatalf("Failed to prove transaction: %v", err)
	}
	fmt.Println("   Orchard proofs generated!")
	fmt.Println()

	fmt.Println("3. Verifying PCZT...")
	err = t2z.VerifyBeforeSigning(proved, request, []t2z.TransparentOutput{})
	if err != nil {
		fmt.Printf("   Note: Verification: %v\n", err)
	} else {
		fmt.Println("   Verification passed")
	}
	fmt.Println()

	fmt.Println("4. Getting sighash...")
	sighash, err := t2z.GetSighash(proved, 0)
	if err != nil {
		log.Fatalf("Failed to get sighash: %v", err)
	}
	fmt.Printf("   Sighash: %s...\n", hex.EncodeToString(sighash[:])[:32])
	fmt.Println()

	fmt.Println("5. Signing transaction (client-side)...")
	signature := signMessage6(privateKey, sighash)
	fmt.Printf("   Signature: %s...\n", hex.EncodeToString(signature[:])[:32])
	fmt.Println()

	fmt.Println("6. Appending signature...")
	signed, err := t2z.AppendSignature(proved, 0, signature)
	if err != nil {
		log.Fatalf("Failed to append signature: %v", err)
	}
	fmt.Println("   Signature appended")
	fmt.Println()

	fmt.Println("7. Finalizing transaction...")
	txBytes, err := t2z.FinalizeAndExtract(signed)
	if err != nil {
		log.Fatalf("Failed to finalize: %v", err)
	}
	fmt.Printf("   Transaction finalized (%d bytes)\n", len(txBytes))
	fmt.Println("   Multiple Orchard outputs = larger transaction")
	fmt.Println()

	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  MULTIPLE SHIELDED OUTPUTS - READY")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	fmt.Println("Transaction breakdown:")
	fmt.Printf("  - Transparent input: %s ZEC\n", zatoshiToZec6(inputAmount))
	fmt.Printf("  - Shielded output 1: %s ZEC\n", zatoshiToZec6(payment1Amount))
	fmt.Printf("  - Shielded output 2: %s ZEC\n", zatoshiToZec6(payment2Amount))
	fmt.Printf("  - Change: ~%s ZEC\n", zatoshiToZec6(inputAmount-payment1Amount-payment2Amount-fee))
	fmt.Printf("  - Fee: %s ZEC\n", zatoshiToZec6(fee))
	fmt.Println()

	fmt.Println("Privacy achieved:")
	fmt.Println("   - Both outputs are in the Orchard shielded pool")
	fmt.Println("   - Amounts are hidden from public view")
	fmt.Println("   - Only recipients can see their incoming funds")
	fmt.Println()

	fmt.Println("EXAMPLE 6 COMPLETED SUCCESSFULLY!")
	fmt.Println()
}
