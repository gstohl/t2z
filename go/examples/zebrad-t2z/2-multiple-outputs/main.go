// Example 2: Multiple Output Transaction
//
// Demonstrates sending to multiple transparent addresses in a single transaction:
// - Use a UTXO as input
// - Create multiple outputs (2 recipients)
// - Show how change is handled
//
// Run with: go run ./examples/zebrad/example2_multiple_outputs.go

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

func createTestKeypair2() ([]byte, []byte) {
	privateKeyHex := "e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35"
	privateKeyBytes, _ := hex.DecodeString(privateKeyHex)

	privKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)
	pubKeyBytes := privKey.PubKey().SerializeCompressed()

	return privateKeyBytes, pubKeyBytes
}

func hash160_2(data []byte) []byte {
	sha256Hash := sha256.Sum256(data)
	ripemd160Hasher := ripemd160.New()
	ripemd160Hasher.Write(sha256Hash[:])
	return ripemd160Hasher.Sum(nil)
}

func createP2PKHScript2(pubkey []byte) []byte {
	pubkeyHash := hash160_2(pubkey)
	script := make([]byte, 25)
	script[0] = 0x76
	script[1] = 0xa9
	script[2] = 0x14
	copy(script[3:23], pubkeyHash)
	script[23] = 0x88
	script[24] = 0xac
	return script
}

func signMessage2(privateKey []byte, message [32]byte) [64]byte {
	privKey := secp256k1.PrivKeyFromBytes(privateKey)
	compact := ecdsa.SignCompact(privKey, message[:], true)

	var sigBytes [64]byte
	copy(sigBytes[:], compact[1:])
	return sigBytes
}

func zatoshiToZec2(zatoshi uint64) string {
	return fmt.Sprintf("%.8f", float64(zatoshi)/100_000_000)
}

func main() {
	fmt.Println()
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  EXAMPLE 2: MULTIPLE OUTPUT TRANSACTION (T→T×2)")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	privateKey, pubkey := createTestKeypair2()

	// Two destination addresses (testnet format)
	// Note: Using same address for simplicity - demonstrates multiple outputs
	dest1 := "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma"
	dest2 := "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma"

	fmt.Println("Configuration:")
	fmt.Printf("  Source pubkey: %s...\n", hex.EncodeToString(pubkey)[:32])
	fmt.Printf("  Destination 1: %s\n", dest1)
	fmt.Printf("  Destination 2: %s\n\n", dest2)

	// Create mock UTXO
	var txid [32]byte
	copy(txid[:], []byte("example2_multiple_outputs_txid00"))
	scriptPubKey := createP2PKHScript2(pubkey)

	// Match TypeScript amounts: 1 ZEC input
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

	// Calculate fee: 1 input, 3 outputs (2 payments + 1 change), 0 orchard
	fee := t2z.CalculateFee(1, 3, 0)
	availableForPayments := inputAmount - fee
	payment1Amount := availableForPayments * 30 / 100 // 30%
	payment2Amount := availableForPayments * 30 / 100 // 30%

	payments := []t2z.Payment{
		{Address: dest1, Amount: payment1Amount},
		{Address: dest2, Amount: payment2Amount},
	}

	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  TRANSACTION SUMMARY - TWO RECIPIENTS")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Printf("\nInput:   %s ZEC\n", zatoshiToZec2(inputAmount))
	fmt.Printf("Output 1: %s ZEC -> %s...\n", zatoshiToZec2(payment1Amount), dest1[:20])
	fmt.Printf("Output 2: %s ZEC -> %s...\n", zatoshiToZec2(payment2Amount), dest2[:20])
	fmt.Printf("Fee:      %s ZEC\n", zatoshiToZec2(fee))
	fmt.Printf("Change:   %s ZEC\n", zatoshiToZec2(inputAmount-payment1Amount-payment2Amount-fee))
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	request, err := t2z.NewTransactionRequest(payments)
	if err != nil {
		log.Fatalf("Failed to create transaction request: %v", err)
	}
	defer request.Free()

	// Mainnet is the default
	request.SetTargetHeight(2_500_000)
	fmt.Println("Using mainnet parameters")
	fmt.Println()

	// Workflow
	fmt.Println("1. Proposing transaction...")
	pczt, err := t2z.ProposeTransaction(inputs, request)
	if err != nil {
		log.Fatalf("Failed to propose transaction: %v", err)
	}
	fmt.Println("   PCZT created with 2 outputs + change")
	fmt.Println()

	fmt.Println("2. Proving transaction...")
	proved, err := t2z.ProveTransaction(pczt)
	if err != nil {
		log.Fatalf("Failed to prove transaction: %v", err)
	}
	fmt.Println("   Proofs generated")
	fmt.Println()

	fmt.Println("3. Verifying PCZT...")
	err = t2z.VerifyBeforeSigning(proved, request, []t2z.TransparentOutput{})
	if err != nil {
		fmt.Printf("   Note: Verification: %v\n", err)
	} else {
		fmt.Println("   Verified: both payments present")
	}
	fmt.Println()

	fmt.Println("4. Getting sighash...")
	sighash, err := t2z.GetSighash(proved, 0)
	if err != nil {
		log.Fatalf("Failed to get sighash: %v", err)
	}
	fmt.Printf("   Sighash: %s...\n", hex.EncodeToString(sighash[:])[:32])
	fmt.Println()

	fmt.Println("5. Signing transaction...")
	signature := signMessage2(privateKey, sighash)
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
	fmt.Printf("   Transaction finalized (%d bytes)\n\n", len(txBytes))

	fmt.Printf("SUCCESS! Transaction ready (%d bytes)\n\n", len(txBytes))
}
