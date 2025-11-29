// Example 5: Transparent to Shielded Transaction (T→Z)
//
// Demonstrates the core t2z workflow - sending from transparent to shielded:
// - Use a transparent UTXO as input
// - Send to a unified address with Orchard receiver
// - The library creates Orchard proofs automatically
//
// Note: This demonstrates creating shielded outputs. The transaction
// creates an Orchard action with zero-knowledge proofs.
//
// Run with: go run ./examples/zebrad/example5_shielded_output.go

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
const shieldedAddress5 = "u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz"

func createTestKeypair5() ([]byte, []byte) {
	privateKeyHex := "e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35"
	privateKeyBytes, _ := hex.DecodeString(privateKeyHex)

	privKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)
	pubKeyBytes := privKey.PubKey().SerializeCompressed()

	return privateKeyBytes, pubKeyBytes
}

func hash160_5(data []byte) []byte {
	sha256Hash := sha256.Sum256(data)
	ripemd160Hasher := ripemd160.New()
	ripemd160Hasher.Write(sha256Hash[:])
	return ripemd160Hasher.Sum(nil)
}

func createP2PKHScript5(pubkey []byte) []byte {
	pubkeyHash := hash160_5(pubkey)
	script := make([]byte, 25)
	script[0] = 0x76
	script[1] = 0xa9
	script[2] = 0x14
	copy(script[3:23], pubkeyHash)
	script[23] = 0x88
	script[24] = 0xac
	return script
}

func signMessage5(privateKey []byte, message [32]byte) [64]byte {
	privKey := secp256k1.PrivKeyFromBytes(privateKey)
	compact := ecdsa.SignCompact(privKey, message[:], true)

	var sigBytes [64]byte
	copy(sigBytes[:], compact[1:])
	return sigBytes
}

func zatoshiToZec5(zatoshi uint64) string {
	return fmt.Sprintf("%.8f", float64(zatoshi)/100_000_000)
}

func main() {
	fmt.Println()
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  EXAMPLE 5: TRANSPARENT TO SHIELDED (T->Z)")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	privateKey, pubkey := createTestKeypair5()

	fmt.Println("Configuration:")
	fmt.Printf("  Source pubkey: %s...\n", hex.EncodeToString(pubkey)[:32])
	fmt.Printf("  Destination (shielded): %s...\n", shieldedAddress5[:30])
	fmt.Println("  Note: This is an Orchard address (u1... prefix = mainnet)")
	fmt.Println()

	// Create mock UTXO
	var txid [32]byte
	copy(txid[:], []byte("example5_shielded_output_txid00"))
	scriptPubKey := createP2PKHScript5(pubkey)

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

	fmt.Printf("Using UTXO: %s ZEC\n\n", zatoshiToZec5(inputAmount))

	// Create shielded payment (50% of input)
	paymentAmount := inputAmount / 2
	fee := uint64(15_000) // ZIP-317 fee for T->Z (includes Orchard action cost)

	payments := []t2z.Payment{
		{Address: shieldedAddress5, Amount: paymentAmount},
	}

	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  TRANSACTION SUMMARY - T->Z SHIELDED")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Printf("\nInput:   %s ZEC\n", zatoshiToZec5(inputAmount))
	fmt.Printf("Output:  %s ZEC -> %s...\n", zatoshiToZec5(paymentAmount), shieldedAddress5[:25])
	fmt.Printf("Fee:     %s ZEC\n", zatoshiToZec5(fee))
	fmt.Printf("Change:  %s ZEC\n", zatoshiToZec5(inputAmount-paymentAmount-fee))
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	fmt.Println("KEY DIFFERENCE from T->T:")
	fmt.Println("   - Payment address is a unified address (u1...)")
	fmt.Println("   - Library creates Orchard actions with zero-knowledge proofs")
	fmt.Println("   - Funds become private after this transaction")
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
	fmt.Println("   PCZT created with Orchard output")
	fmt.Println()

	fmt.Println("2. Proving transaction (generating Orchard ZK proofs)...")
	fmt.Println("   This may take a few seconds...")
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
	signature := signMessage5(privateKey, sighash)
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
	fmt.Println("   Note: T->Z transactions are larger due to Orchard proofs")
	fmt.Println()

	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  T->Z TRANSACTION READY")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	fmt.Println("What this demonstrates:")
	fmt.Printf("  - Transparent input: %s ZEC\n", zatoshiToZec5(inputAmount))
	fmt.Printf("  - Shielded output: %s ZEC\n", zatoshiToZec5(paymentAmount))
	fmt.Println("  - Change: returned to transparent address")
	fmt.Printf("  - Fee: %s ZEC\n", zatoshiToZec5(fee))
	fmt.Println()
	fmt.Println("The shielded output is now private - only the recipient")
	fmt.Println("with the viewing key can see the amount and memo.")
	fmt.Println()

	fmt.Println("EXAMPLE 5 COMPLETED SUCCESSFULLY!")
	fmt.Println()
}
