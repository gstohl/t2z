// Example 8: Combine Workflow (Parallel Signing)
//
// Demonstrates the combine() function for multi-party signing workflows:
// - Create a transaction with multiple inputs
// - Serialize the PCZT and create copies for parallel signing
// - Each "signer" signs their input independently
// - Combine the partially-signed PCZTs into one
// - Finalize the transaction
//
// Use case: Multiple parties each control different UTXOs and need to
// co-sign a transaction without sharing private keys.
//
// Run with: go run ./8-combine-workflow

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
func createTestKeypair() ([]byte, []byte) {
	privateKeyHex := "e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35"
	privateKeyBytes, _ := hex.DecodeString(privateKeyHex)
	privKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)
	pubKeyBytes := privKey.PubKey().SerializeCompressed()
	return privateKeyBytes, pubKeyBytes
}

func hash160(data []byte) []byte {
	sha256Hash := sha256.Sum256(data)
	ripemd160Hasher := ripemd160.New()
	ripemd160Hasher.Write(sha256Hash[:])
	return ripemd160Hasher.Sum(nil)
}

func createP2PKHScript(pubkey []byte) []byte {
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

func signMessage(privateKey []byte, message [32]byte) [64]byte {
	privKey := secp256k1.PrivKeyFromBytes(privateKey)
	compact := ecdsa.SignCompact(privKey, message[:], true)
	var sigBytes [64]byte
	copy(sigBytes[:], compact[1:])
	return sigBytes
}

func zatoshiToZec(zatoshi uint64) string {
	return fmt.Sprintf("%.8f", float64(zatoshi)/100_000_000)
}

func main() {
	fmt.Println()
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  EXAMPLE 8: COMBINE WORKFLOW (Parallel Signing)")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()
	fmt.Println("This example demonstrates the Combine() function for parallel signing.")
	fmt.Println("In a real scenario, each signer would be on a different device.")
	fmt.Println()

	// Create test keypair (in production, each signer would have their own)
	privateKey, pubkey := createTestKeypair()
	destAddress := "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma"

	fmt.Println("Configuration:")
	fmt.Printf("  Pubkey: %s...\n", hex.EncodeToString(pubkey)[:32])
	fmt.Printf("  Destination: %s\n\n", destAddress)

	// Create 3 mock UTXOs (simulating 3 different parties' inputs)
	scriptPubKey := createP2PKHScript(pubkey)
	inputAmount := uint64(100_000_000) // 1 ZEC each

	var txid1, txid2, txid3 [32]byte
	copy(txid1[:], []byte("combine_example_input_1_txid____"))
	copy(txid2[:], []byte("combine_example_input_2_txid____"))
	copy(txid3[:], []byte("combine_example_input_3_txid____"))

	inputs := []t2z.TransparentInput{
		{Pubkey: pubkey, TxID: txid1, Vout: 0, Amount: inputAmount, ScriptPubKey: scriptPubKey},
		{Pubkey: pubkey, TxID: txid2, Vout: 0, Amount: inputAmount, ScriptPubKey: scriptPubKey},
		{Pubkey: pubkey, TxID: txid3, Vout: 0, Amount: inputAmount, ScriptPubKey: scriptPubKey},
	}

	totalInput := inputAmount * 3
	fmt.Printf("Selected 3 UTXOs totaling: %s ZEC\n\n", zatoshiToZec(totalInput))

	// Create payment
	fee := t2z.CalculateFee(3, 2, 0)
	paymentAmount := totalInput / 2

	payments := []t2z.Payment{{Address: destAddress, Amount: paymentAmount}}

	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  TRANSACTION SUMMARY")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Printf("\nInputs: 3 x %s ZEC = %s ZEC\n", zatoshiToZec(inputAmount), zatoshiToZec(totalInput))
	fmt.Printf("Output: %s ZEC -> %s...\n", zatoshiToZec(paymentAmount), destAddress[:20])
	fmt.Printf("Fee:    %s ZEC\n", zatoshiToZec(fee))
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	request, err := t2z.NewTransactionRequest(payments)
	if err != nil {
		log.Fatalf("Failed to create request: %v", err)
	}
	defer request.Free()
	request.SetTargetHeight(2_500_000)

	fmt.Println("--- PARALLEL SIGNING WORKFLOW ---\n")

	// Step 1: Create and prove the PCZT
	fmt.Println("1. Creating and proving PCZT...")
	pczt, err := t2z.ProposeTransaction(inputs, request)
	if err != nil {
		log.Fatalf("Failed to propose: %v", err)
	}
	proved, err := t2z.ProveTransaction(pczt)
	if err != nil {
		log.Fatalf("Failed to prove: %v", err)
	}
	fmt.Println("   PCZT created and proved\n")

	// Step 2: Serialize the proved PCZT
	fmt.Println("2. Serializing PCZT for distribution to signers...")
	pcztBytes, err := t2z.SerializePCZT(proved)
	if err != nil {
		log.Fatalf("Failed to serialize: %v", err)
	}
	fmt.Printf("   Serialized PCZT: %d bytes\n\n", len(pcztBytes))

	// Step 3: Simulate parallel signing by different parties
	fmt.Println("3. Simulating parallel signing by 3 different parties...\n")

	// Signer A signs input 0
	fmt.Println("   Signer A: Signing input 0...")
	pcztA, _ := t2z.ParsePCZT(pcztBytes)
	sighashA, _ := t2z.GetSighash(pcztA, 0)
	signatureA := signMessage(privateKey, sighashA)
	signedA, _ := t2z.AppendSignature(pcztA, 0, signatureA)
	bytesA, _ := t2z.SerializePCZT(signedA)
	fmt.Println("   Signer A: Done (signed input 0)\n")

	// Signer B signs input 1
	fmt.Println("   Signer B: Signing input 1...")
	pcztB, _ := t2z.ParsePCZT(pcztBytes)
	sighashB, _ := t2z.GetSighash(pcztB, 1)
	signatureB := signMessage(privateKey, sighashB)
	signedB, _ := t2z.AppendSignature(pcztB, 1, signatureB)
	bytesB, _ := t2z.SerializePCZT(signedB)
	fmt.Println("   Signer B: Done (signed input 1)\n")

	// Signer C signs input 2
	fmt.Println("   Signer C: Signing input 2...")
	pcztC, _ := t2z.ParsePCZT(pcztBytes)
	sighashC, _ := t2z.GetSighash(pcztC, 2)
	signatureC := signMessage(privateKey, sighashC)
	signedC, _ := t2z.AppendSignature(pcztC, 2, signatureC)
	bytesC, _ := t2z.SerializePCZT(signedC)
	fmt.Println("   Signer C: Done (signed input 2)\n")

	// Step 4: Combine all partially-signed PCZTs
	fmt.Println("4. Combining partially-signed PCZTs...")
	combinedA, _ := t2z.ParsePCZT(bytesA)
	combinedB, _ := t2z.ParsePCZT(bytesB)
	combinedC, _ := t2z.ParsePCZT(bytesC)

	fullySignedPczt, err := t2z.Combine([]*t2z.PCZT{combinedA, combinedB, combinedC})
	if err != nil {
		log.Fatalf("Failed to combine: %v", err)
	}
	fmt.Println("   All signatures combined into single PCZT\n")

	// Step 5: Finalize
	fmt.Println("5. Finalizing transaction...")
	txBytes, err := t2z.FinalizeAndExtract(fullySignedPczt)
	if err != nil {
		log.Fatalf("Failed to finalize: %v", err)
	}
	fmt.Printf("   Transaction finalized (%d bytes)\n\n", len(txBytes))

	fmt.Printf("SUCCESS! Transaction ready (%d bytes)\n", len(txBytes))
	fmt.Println("\nThe Combine() function merged signatures from 3 independent signers.\n")
}
