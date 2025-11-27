// Example 7: Mixed Transparent and Shielded Outputs (T→T+Z)
//
// Demonstrates sending to both transparent and shielded recipients:
// - Use a transparent UTXO as input
// - Send to one transparent address AND one shielded (Orchard) address
// - Shows how the library handles mixed output types in a single transaction
//
// This is a common real-world scenario where you want to pay someone
// transparently while also shielding some funds.
//
// Run with: go run ./examples/zebrad/example7_mixed_outputs.go

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

// Deterministic mainnet unified address with Orchard receiver
// Generated from SpendingKey::from_bytes([42u8; 32])
const shieldedAddress7 = "u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz"

func createTestKeypair7() ([]byte, []byte) {
	privateKeyHex := "e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35"
	privateKeyBytes, _ := hex.DecodeString(privateKeyHex)

	privKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)
	pubKeyBytes := privKey.PubKey().SerializeCompressed()

	return privateKeyBytes, pubKeyBytes
}

func hash160_7(data []byte) []byte {
	sha256Hash := sha256.Sum256(data)
	ripemd160Hasher := ripemd160.New()
	ripemd160Hasher.Write(sha256Hash[:])
	return ripemd160Hasher.Sum(nil)
}

func createP2PKHScript7(pubkey []byte) []byte {
	pubkeyHash := hash160_7(pubkey)
	script := make([]byte, 25)
	script[0] = 0x76
	script[1] = 0xa9
	script[2] = 0x14
	copy(script[3:23], pubkeyHash)
	script[23] = 0x88
	script[24] = 0xac
	return script
}

func signMessage7(privateKey []byte, message [32]byte) [64]byte {
	privKey := secp256k1.PrivKeyFromBytes(privateKey)
	compact := ecdsa.SignCompact(privKey, message[:], true)

	var sigBytes [64]byte
	copy(sigBytes[:], compact[1:])
	return sigBytes
}

func zatoshiToZec7(zatoshi uint64) string {
	return fmt.Sprintf("%.8f", float64(zatoshi)/100_000_000)
}

func main() {
	fmt.Println()
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  EXAMPLE 7: MIXED TRANSPARENT + SHIELDED OUTPUTS (T->T+Z)")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	privateKey, pubkey := createTestKeypair7()

	// Transparent recipient address
	transparentRecipient := "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma"

	fmt.Println("Configuration:")
	fmt.Printf("  Source pubkey: %s...\n", hex.EncodeToString(pubkey)[:32])
	fmt.Printf("  Recipient 1 (transparent): %s\n", transparentRecipient)
	fmt.Printf("  Recipient 2 (shielded): %s...\n", shieldedAddress7[:30])
	fmt.Println("  Note: Mixed output types in single transaction")
	fmt.Println()

	// Create mock UTXO
	var txid [32]byte
	copy(txid[:], []byte("example7_mixed_outputs_txid0000"))
	scriptPubKey := createP2PKHScript7(pubkey)

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

	fmt.Printf("Using UTXO: %s ZEC\n\n", zatoshiToZec7(inputAmount))

	// Create mixed payments
	// ZIP-317 fee for 1 transparent + 1 Orchard output + change
	fee := uint64(30_000) // Higher fee for mixed outputs
	availableForPayments := inputAmount - fee

	// Split: 35% to transparent, 35% to shielded, 30% to change
	transparentPayment := availableForPayments * 35 / 100
	shieldedPayment := availableForPayments * 35 / 100

	payments := []t2z.Payment{
		{Address: transparentRecipient, Amount: transparentPayment},
		{Address: shieldedAddress7, Amount: shieldedPayment},
	}

	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  TRANSACTION SUMMARY - MIXED T+Z")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Printf("\nInput:       %s ZEC\n", zatoshiToZec7(inputAmount))
	fmt.Printf("Transparent: %s ZEC -> %s...\n", zatoshiToZec7(transparentPayment), transparentRecipient[:20])
	fmt.Printf("Shielded:    %s ZEC -> %s...\n", zatoshiToZec7(shieldedPayment), shieldedAddress7[:20])
	fmt.Printf("Fee:         %s ZEC\n", zatoshiToZec7(fee))
	fmt.Printf("Change:      %s ZEC\n", zatoshiToZec7(inputAmount-transparentPayment-shieldedPayment-fee))
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	fmt.Println("WHAT THIS DEMONSTRATES:")
	fmt.Println("   - Single transparent input")
	fmt.Println("   - One transparent output (publicly visible)")
	fmt.Println("   - One Orchard output (shielded/private)")
	fmt.Println("   - Change returned to source address")
	fmt.Println("   - Real-world use case: pay merchant + shield savings")
	fmt.Println()

	request, err := t2z.NewTransactionRequest(payments)
	if err != nil {
		log.Fatalf("Failed to create transaction request: %v", err)
	}
	defer request.Free()

	request.SetUseMainnet(true)
	request.SetTargetHeight(2_500_000)
	fmt.Println("Configured for mainnet branch ID (target height: 2,500,000)")
	fmt.Println()

	// Workflow
	fmt.Println("1. Proposing transaction...")
	pczt, err := t2z.ProposeTransaction(inputs, request)
	if err != nil {
		log.Fatalf("Failed to propose transaction: %v", err)
	}
	fmt.Println("   PCZT created with mixed outputs")
	fmt.Println()

	fmt.Println("2. Proving transaction (generating Orchard ZK proofs)...")
	fmt.Println("   This may take a few seconds...")
	proved, err := t2z.ProveTransaction(pczt)
	if err != nil {
		log.Fatalf("Failed to prove transaction: %v", err)
	}
	fmt.Println("   Proofs generated!")
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
	signature := signMessage7(privateKey, sighash)
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
	fmt.Println("   Mixed outputs = Orchard proofs + transparent outputs")
	fmt.Println()

	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  MIXED OUTPUTS TRANSACTION - READY")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	fmt.Println("Transaction breakdown:")
	fmt.Printf("  - Transparent input: %s ZEC\n", zatoshiToZec7(inputAmount))
	fmt.Printf("  - Transparent output: %s ZEC (publicly visible)\n", zatoshiToZec7(transparentPayment))
	fmt.Printf("  - Shielded output: %s ZEC (private)\n", zatoshiToZec7(shieldedPayment))
	fmt.Printf("  - Change: ~%s ZEC\n", zatoshiToZec7(inputAmount-transparentPayment-shieldedPayment-fee))
	fmt.Printf("  - Fee: %s ZEC\n", zatoshiToZec7(fee))
	fmt.Println()

	fmt.Println("Privacy analysis:")
	fmt.Println("   - Transparent recipient: amount and address are PUBLIC")
	fmt.Println("   - Shielded recipient: amount and address are PRIVATE")
	fmt.Println("   - Observer can see: input amount, transparent output")
	fmt.Println("   - Observer cannot see: shielded amount, shielded recipient")
	fmt.Println()

	fmt.Println("Use cases for mixed transactions:")
	fmt.Println("   - Pay merchant (transparent) + save remainder (shielded)")
	fmt.Println("   - Exchange withdrawal (transparent) + personal wallet (shielded)")
	fmt.Println("   - Tax-reportable payment + private savings")
	fmt.Println()

	fmt.Println("EXAMPLE 7 COMPLETED SUCCESSFULLY!")
	fmt.Println()
}
