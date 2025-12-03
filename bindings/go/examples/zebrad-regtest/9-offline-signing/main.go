// Example 9: Offline Signing (Hardware Wallet / Air-Gapped Device Simulation)
//
// Demonstrates the serialize/parse workflow for offline signing:
// - Online device: Creates PCZT, serializes it, outputs sighash
// - Offline device: Signs the sighash (never sees full transaction)
// - Online device: Parses PCZT, appends signature, finalizes
//
// Use case: Hardware wallets, air-gapped signing devices, or any scenario
// where the signing key never touches an internet-connected device.
//
// Run with: go run ./9-offline-signing

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
	script[0] = 0x76
	script[1] = 0xa9
	script[2] = 0x14
	copy(script[3:23], pubkeyHash)
	script[23] = 0x88
	script[24] = 0xac
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
	fmt.Println("  EXAMPLE 9: OFFLINE SIGNING (Hardware Wallet Simulation)")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()
	fmt.Println("This example demonstrates the serialize/parse workflow for offline signing.")
	fmt.Println("The private key NEVER touches the online device!")
	fmt.Println()

	// Split keypair: public key on online device, private key on offline device
	privateKey, pubkey := createTestKeypair()
	destAddress := "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma"

	fmt.Println("Configuration:")
	fmt.Printf("  Public key (online):  %s...\n", hex.EncodeToString(pubkey)[:32])
	fmt.Printf("  Private key (OFFLINE): %s...\n", hex.EncodeToString(privateKey)[:16])
	fmt.Printf("  Destination: %s\n\n", destAddress)

	// Create mock UTXO
	var txid [32]byte
	copy(txid[:], []byte("offline_signing_example_txid____"))
	scriptPubKey := createP2PKHScript(pubkey)
	inputAmount := uint64(100_000_000)

	inputs := []t2z.TransparentInput{
		{Pubkey: pubkey, TxID: txid, Vout: 0, Amount: inputAmount, ScriptPubKey: scriptPubKey},
	}

	fee := t2z.CalculateFee(1, 2, 0)
	paymentAmount := inputAmount / 2

	payments := []t2z.Payment{{Address: destAddress, Amount: paymentAmount}}

	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  TRANSACTION SUMMARY")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Printf("\nInput:  %s ZEC\n", zatoshiToZec(inputAmount))
	fmt.Printf("Output: %s ZEC -> %s...\n", zatoshiToZec(paymentAmount), destAddress[:20])
	fmt.Printf("Fee:    %s ZEC\n", zatoshiToZec(fee))
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	// ============================================================
	// ONLINE DEVICE: Build transaction, extract sighash
	// ============================================================
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  ONLINE DEVICE - Transaction Builder")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	request, err := t2z.NewTransactionRequest(payments)
	if err != nil {
		log.Fatalf("Failed to create request: %v", err)
	}
	defer request.Free()
	request.SetTargetHeight(2_500_000)

	fmt.Println("1. Proposing transaction...")
	pczt, err := t2z.ProposeTransaction(inputs, request)
	if err != nil {
		log.Fatalf("Failed to propose: %v", err)
	}
	fmt.Println("   PCZT created")

	fmt.Println("\n2. Proving transaction...")
	proved, err := t2z.ProveTransaction(pczt)
	if err != nil {
		log.Fatalf("Failed to prove: %v", err)
	}
	fmt.Println("   Proofs generated")

	fmt.Println("\n3. Serializing PCZT for storage...")
	pcztBytes, err := t2z.SerializePCZT(proved)
	if err != nil {
		log.Fatalf("Failed to serialize: %v", err)
	}
	fmt.Printf("   PCZT serialized: %d bytes\n", len(pcztBytes))

	fmt.Println("\n4. Getting sighash for offline signing...")
	sighash, err := t2z.GetSighash(proved, 0)
	if err != nil {
		log.Fatalf("Failed to get sighash: %v", err)
	}
	sighashHex := hex.EncodeToString(sighash[:])
	fmt.Printf("   Sighash: %s\n", sighashHex)

	fmt.Println("\n   >>> Transfer this sighash to the OFFLINE device <<<")
	fmt.Println()

	// ============================================================
	// OFFLINE DEVICE: Sign the sighash (air-gapped)
	// ============================================================
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  OFFLINE DEVICE - Air-Gapped Signer")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	fmt.Println("1. Receiving sighash...")
	fmt.Printf("   Sighash: %s\n", sighashHex)

	fmt.Println("\n2. Signing with private key (NEVER leaves this device)...")
	signature := signMessage(privateKey, sighash)
	signatureHex := hex.EncodeToString(signature[:])
	fmt.Printf("   Signature: %s\n", signatureHex)

	fmt.Println("\n   >>> Transfer this signature back to the ONLINE device <<<")
	fmt.Println()

	// ============================================================
	// ONLINE DEVICE: Append signature and finalize
	// ============================================================
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  ONLINE DEVICE - Finalization")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	fmt.Println("1. Parsing stored PCZT...")
	loadedPczt, err := t2z.ParsePCZT(pcztBytes)
	if err != nil {
		log.Fatalf("Failed to parse: %v", err)
	}
	fmt.Println("   PCZT restored from bytes")

	fmt.Println("\n2. Receiving signature from offline device...")
	fmt.Printf("   Signature: %s...\n", signatureHex[:32])

	fmt.Println("\n3. Appending signature to PCZT...")
	signed, err := t2z.AppendSignature(loadedPczt, 0, signature)
	if err != nil {
		log.Fatalf("Failed to append signature: %v", err)
	}
	fmt.Println("   Signature appended")

	fmt.Println("\n4. Finalizing transaction...")
	txBytes, err := t2z.FinalizeAndExtract(signed)
	if err != nil {
		log.Fatalf("Failed to finalize: %v", err)
	}
	fmt.Printf("   Transaction finalized (%d bytes)\n", len(txBytes))

	fmt.Println()
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  SUCCESS!")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()
	fmt.Printf("Transaction ready: %d bytes\n", len(txBytes))
	fmt.Println("\nKey security properties:")
	fmt.Println("  - Private key NEVER touched the online device")
	fmt.Println("  - PCZT can be serialized/parsed for transport")
	fmt.Println("  - Sighash is safe to transfer (reveals no private data)")
	fmt.Println()
}
