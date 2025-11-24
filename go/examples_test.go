package t2z_test

import (
	"encoding/hex"
	"fmt"
	"log"

	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	t2z "github.com/gstohl/t2z/go"
)

// ExampleNewTransactionRequest demonstrates creating a payment request.
func ExampleNewTransactionRequest() {
	payments := []t2z.Payment{
		{
			Address: "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
			Amount:  100_000, // 0.001 ZEC
			Memo:    "Example payment",
		},
	}

	request, err := t2z.NewTransactionRequest(payments)
	if err != nil {
		log.Fatal(err)
	}
	defer request.Free()

	fmt.Printf("Created request with %d payment(s)\n", len(request.Payments))
	// Output: Created request with 1 payment(s)
}

// ExampleProposeTransaction demonstrates creating a PCZT from inputs and a payment request.
func ExampleProposeTransaction() {
	// Create payment request
	payments := []t2z.Payment{
		{Address: "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", Amount: 100_000},
	}
	request, _ := t2z.NewTransactionRequest(payments)
	defer request.Free()

	// Setup test input
	privateKeyBytes := make([]byte, 32)
	for i := range privateKeyBytes {
		privateKeyBytes[i] = 1
	}
	privKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)
	pubKeyBytes := privKey.PubKey().SerializeCompressed()

	scriptPubKeyHex := "1976a91479b000887626b294a914501a4cd226b58b23598388ac"
	scriptPubKey, _ := hex.DecodeString(scriptPubKeyHex)

	var txid [32]byte
	inputs := []t2z.TransparentInput{
		{
			Pubkey:       pubKeyBytes,
			TxID:         txid,
			Vout:         0,
			Amount:       100_000_000,
			ScriptPubKey: scriptPubKey,
		},
	}

	// Propose transaction (creates PCZT)
	_, err := t2z.ProposeTransaction(inputs, request)
	if err != nil {
		log.Fatal(err)
	}
	// Note: pczt is consumed by next operations, don't Free() it

	fmt.Println("PCZT created successfully")
	// Output: PCZT created successfully
}

// ExampleSerialize demonstrates serializing a PCZT for transmission or storage.
func ExampleSerialize() {
	// ... setup code from ExampleProposeTransaction ...
	payments := []t2z.Payment{
		{Address: "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", Amount: 100_000},
	}
	request, _ := t2z.NewTransactionRequest(payments)
	defer request.Free()

	privateKeyBytes := make([]byte, 32)
	for i := range privateKeyBytes {
		privateKeyBytes[i] = 1
	}
	privKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)
	pubKeyBytes := privKey.PubKey().SerializeCompressed()
	scriptPubKey, _ := hex.DecodeString("1976a91479b000887626b294a914501a4cd226b58b23598388ac")

	var txid [32]byte
	inputs := []t2z.TransparentInput{
		{Pubkey: pubKeyBytes, TxID: txid, Vout: 0, Amount: 100_000_000, ScriptPubKey: scriptPubKey},
	}

	pczt, _ := t2z.ProposeTransaction(inputs, request)

	// Serialize PCZT (does not consume it)
	pcztBytes, err := t2z.Serialize(pczt)
	if err != nil {
		log.Fatal(err)
	}

	// Free after serialization
	pczt.Free()

	fmt.Printf("Serialized PCZT: %d bytes\n", len(pcztBytes))
	// Output: Serialized PCZT: 364 bytes
}

// ExampleParse demonstrates parsing a serialized PCZT.
func ExampleParse() {
	// Assume we have serialized PCZT bytes (e.g., from hardware wallet)
	// This would come from Serialize() in production
	pcztBytesHex := "..." // Placeholder

	pcztBytes, _ := hex.DecodeString(pcztBytesHex)
	if len(pcztBytes) == 0 {
		// For example purposes, create valid bytes
		payments := []t2z.Payment{
			{Address: "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", Amount: 100_000},
		}
		request, _ := t2z.NewTransactionRequest(payments)
		defer request.Free()

		privateKeyBytes := make([]byte, 32)
		for i := range privateKeyBytes {
			privateKeyBytes[i] = 1
		}
		privKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)
		pubKeyBytes := privKey.PubKey().SerializeCompressed()
		scriptPubKey, _ := hex.DecodeString("1976a91479b000887626b294a914501a4cd226b58b23598388ac")

		var txid [32]byte
		inputs := []t2z.TransparentInput{
			{Pubkey: pubKeyBytes, TxID: txid, Vout: 0, Amount: 100_000_000, ScriptPubKey: scriptPubKey},
		}

		pczt, _ := t2z.ProposeTransaction(inputs, request)
		pcztBytes, _ = t2z.Serialize(pczt)
		pczt.Free()
	}

	// Parse PCZT from bytes
	pczt, err := t2z.Parse(pcztBytes)
	if err != nil {
		log.Fatal(err)
	}
	defer pczt.Free()

	fmt.Println("PCZT parsed successfully")
	// Output: PCZT parsed successfully
}

// ExampleGetSighash demonstrates getting a signature hash for signing.
func ExampleGetSighash() {
	// ... setup PCZT ...
	payments := []t2z.Payment{
		{Address: "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", Amount: 100_000},
	}
	request, _ := t2z.NewTransactionRequest(payments)
	defer request.Free()

	privateKeyBytes := make([]byte, 32)
	for i := range privateKeyBytes {
		privateKeyBytes[i] = 1
	}
	privKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)
	pubKeyBytes := privKey.PubKey().SerializeCompressed()
	scriptPubKey, _ := hex.DecodeString("1976a91479b000887626b294a914501a4cd226b58b23598388ac")

	var txid [32]byte
	inputs := []t2z.TransparentInput{
		{Pubkey: pubKeyBytes, TxID: txid, Vout: 0, Amount: 100_000_000, ScriptPubKey: scriptPubKey},
	}

	pczt, _ := t2z.ProposeTransaction(inputs, request)
	proved, _ := t2z.ProveTransaction(pczt)

	// Get sighash for input 0 (does not consume PCZT)
	sighash, err := t2z.GetSighash(proved, 0)
	if err != nil {
		log.Fatal(err)
	}

	fmt.Printf("Sighash length: %d bytes\n", len(sighash))
	// Output: Sighash length: 32 bytes
}
