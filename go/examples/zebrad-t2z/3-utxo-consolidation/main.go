// Example 3: Multiple Inputs Transaction (UTXO Consolidation)
//
// Demonstrates combining multiple UTXOs in a single transaction:
// - Use multiple UTXOs as inputs
// - Sign each input separately
// - Consolidate funds into fewer outputs
//
// Run with: go run ./examples/zebrad/example3_utxo_consolidation.go

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

func createTestKeypair3() ([]byte, []byte) {
	privateKeyHex := "e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35"
	privateKeyBytes, _ := hex.DecodeString(privateKeyHex)

	privKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)
	pubKeyBytes := privKey.PubKey().SerializeCompressed()

	return privateKeyBytes, pubKeyBytes
}

func hash160_3(data []byte) []byte {
	sha256Hash := sha256.Sum256(data)
	ripemd160Hasher := ripemd160.New()
	ripemd160Hasher.Write(sha256Hash[:])
	return ripemd160Hasher.Sum(nil)
}

func createP2PKHScript3(pubkey []byte) []byte {
	pubkeyHash := hash160_3(pubkey)
	script := make([]byte, 25)
	script[0] = 0x76
	script[1] = 0xa9
	script[2] = 0x14
	copy(script[3:23], pubkeyHash)
	script[23] = 0x88
	script[24] = 0xac
	return script
}

func signMessage3(privateKey []byte, message [32]byte) [64]byte {
	privKey := secp256k1.PrivKeyFromBytes(privateKey)
	compact := ecdsa.SignCompact(privKey, message[:], true)

	var sigBytes [64]byte
	copy(sigBytes[:], compact[1:])
	return sigBytes
}

func zatoshiToZec3(zatoshi uint64) string {
	return fmt.Sprintf("%.8f", float64(zatoshi)/100_000_000)
}

func main() {
	fmt.Println()
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  EXAMPLE 3: MULTIPLE INPUTS (UTXO Consolidation)")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	privateKey, pubkey := createTestKeypair3()
	destAddress := "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma"
	scriptPubKey := createP2PKHScript3(pubkey)

	fmt.Println("Configuration:")
	fmt.Printf("  Address: %s\n\n", destAddress)

	// Create 2 mock UTXOs (simulating multiple coinbase rewards)
	var txid1, txid2 [32]byte
	copy(txid1[:], []byte("example3_utxo1_consolidation_00"))
	copy(txid2[:], []byte("example3_utxo2_consolidation_00"))

	// Each UTXO is 1 ZEC
	input1Amount := uint64(100_000_000) // 1 ZEC
	input2Amount := uint64(100_000_000) // 1 ZEC
	totalInput := input1Amount + input2Amount

	inputs := []t2z.TransparentInput{
		{
			Pubkey:       pubkey,
			TxID:         txid1,
			Vout:         0,
			Amount:       input1Amount,
			ScriptPubKey: scriptPubKey,
		},
		{
			Pubkey:       pubkey,
			TxID:         txid2,
			Vout:         0,
			Amount:       input2Amount,
			ScriptPubKey: scriptPubKey,
		},
	}

	fmt.Printf("Found %d UTXOs:\n", len(inputs))
	for i, inp := range inputs {
		fmt.Printf("  [%d] %s ZEC\n", i, zatoshiToZec3(inp.Amount))
	}
	fmt.Printf("  Total: %s ZEC\n\n", zatoshiToZec3(totalInput))

	// TypeScript: fee = 10_000n for consolidation
	fee := uint64(10_000)
	outputAmount := totalInput - fee

	payments := []t2z.Payment{
		{Address: destAddress, Amount: outputAmount},
	}

	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  TRANSACTION SUMMARY - UTXO CONSOLIDATION")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Printf("\nInputs:  %d UTXOs totaling %s ZEC\n", len(inputs), zatoshiToZec3(totalInput))
	fmt.Printf("Output:  %s ZEC (consolidated)\n", zatoshiToZec3(outputAmount))
	fmt.Printf("Fee:     %s ZEC\n", zatoshiToZec3(fee))
	fmt.Printf("Result:  %d UTXOs -> 1 UTXO\n", len(inputs))
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	request, err := t2z.NewTransactionRequest(payments)
	if err != nil {
		log.Fatalf("Failed to create transaction request: %v", err)
	}
	defer request.Free()

	request.SetUseMainnet(true)
	request.SetTargetHeight(2_500_000)

	// Workflow
	fmt.Println("1. Proposing transaction with multiple inputs...")
	pczt, err := t2z.ProposeTransaction(inputs, request)
	if err != nil {
		log.Fatalf("Failed to propose transaction: %v", err)
	}
	fmt.Printf("   PCZT created with %d inputs\n", len(inputs))
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
		fmt.Printf("   Note: %v\n", err)
	} else {
		fmt.Println("   Verification passed")
	}
	fmt.Println()

	// Sign each input separately (key difference for multiple inputs!)
	fmt.Println("4. Getting sighashes and signing each input...")
	currentPczt := proved
	for i := range inputs {
		fmt.Printf("   Input %d:\n", i)

		sighash, err := t2z.GetSighash(currentPczt, uint(i))
		if err != nil {
			log.Fatalf("Failed to get sighash for input %d: %v", i, err)
		}
		fmt.Printf("     Sighash: %s...\n", hex.EncodeToString(sighash[:])[:24])

		signature := signMessage3(privateKey, sighash)
		fmt.Printf("     Signature: %s...\n", hex.EncodeToString(signature[:])[:24])

		currentPczt, err = t2z.AppendSignature(currentPczt, uint(i), signature)
		if err != nil {
			log.Fatalf("Failed to append signature for input %d: %v", i, err)
		}
		fmt.Println("     Signature appended")
	}
	fmt.Println()

	fmt.Println("5. Finalizing transaction...")
	txBytes, err := t2z.FinalizeAndExtract(currentPczt)
	if err != nil {
		log.Fatalf("Failed to finalize: %v", err)
	}
	fmt.Printf("   Transaction finalized (%d bytes)\n", len(txBytes))
	fmt.Println()

	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  TRANSACTION READY")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Printf("\nInputs consolidated: %d\n", len(inputs))
	fmt.Printf("Output UTXOs: 1 (consolidation)\n")
	fmt.Printf("Fee paid: %s ZEC\n", zatoshiToZec3(fee))
	fmt.Println()

	fmt.Println("Key Takeaway:")
	fmt.Println("   Multiple UTXOs can be combined into fewer outputs.")
	fmt.Println("   Each input requires its own sighash and signature.")
	fmt.Println()

	fmt.Println("EXAMPLE 3 COMPLETED SUCCESSFULLY!")
	fmt.Println()
}
