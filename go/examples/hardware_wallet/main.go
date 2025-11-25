// Package main demonstrates hardware wallet integration with the t2z library.
//
// This example shows how to use PCZT serialization to perform external signing,
// which is essential for hardware wallet support where private keys never leave
// the secure device.
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
	fmt.Println("=== Hardware Wallet Integration Example ===\n")
	fmt.Println("This example demonstrates the PCZT workflow for hardware wallets:")
	fmt.Println("  1. Coordinator creates unsigned PCZT")
	fmt.Println("  2. PCZT is serialized and sent to hardware wallet")
	fmt.Println("  3. Hardware wallet signs and returns signed PCZT")
	fmt.Println("  4. Coordinator finalizes and extracts transaction\n")

	// ============================================================
	// STEP 1: Coordinator creates the unsigned transaction
	// ============================================================
	fmt.Println("📱 COORDINATOR: Creating payment request...")

	payments := []t2z.Payment{
		{
			Address: "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
			Amount:  50_000, // 0.0005 ZEC
			Label:   "Hardware wallet test",
		},
	}

	request, err := t2z.NewTransactionRequest(payments)
	if err != nil {
		log.Fatalf("Failed to create request: %v", err)
	}
	defer request.Free()
	fmt.Printf("   ✓ Payment to: %s\n", payments[0].Address)
	fmt.Printf("   ✓ Amount: %d zatoshis (0.0005 ZEC)\n\n", payments[0].Amount)

	// Setup test UTXO (in production, this comes from wallet)
	fmt.Println("📱 COORDINATOR: Preparing UTXOs...")
	privateKeyBytes := make([]byte, 32)
	for i := range privateKeyBytes {
		privateKeyBytes[i] = 1
	}
	privKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)
	pubKey := privKey.PubKey()
	pubKeyBytes := pubKey.SerializeCompressed()

	scriptPubKeyHex := "1976a91479b000887626b294a914501a4cd226b58b23598388ac"
	scriptPubKey, _ := hex.DecodeString(scriptPubKeyHex)

	var txid [32]byte
	copy(txid[:], []byte("hardware_wallet_example_txid_00"))

	inputs := []t2z.TransparentInput{
		{
			Pubkey:       pubKeyBytes,
			TxID:         txid,
			Vout:         0,
			Amount:       100_000_000, // 1 ZEC
			ScriptPubKey: scriptPubKey,
		},
	}
	fmt.Printf("   ✓ Using UTXO: %s:%d\n", hex.EncodeToString(txid[:8]), 0)
	fmt.Printf("   ✓ Amount: 1.0 ZEC\n\n")

	// Create and prove the transaction
	fmt.Println("📱 COORDINATOR: Creating PCZT...")
	pczt, err := t2z.ProposeTransaction(inputs, request)
	if err != nil {
		log.Fatalf("Failed to propose: %v", err)
	}

	proved, err := t2z.ProveTransaction(pczt)
	if err != nil {
		log.Fatalf("Failed to prove: %v", err)
	}
	fmt.Println("   ✓ PCZT created with proofs\n")

	// ============================================================
	// STEP 2: Serialize PCZT for transmission to hardware wallet
	// ============================================================
	fmt.Println("📱 COORDINATOR: Serializing PCZT for hardware wallet...")
	pcztBytes, err := t2z.Serialize(proved)
	if err != nil {
		log.Fatalf("Failed to serialize: %v", err)
	}
	fmt.Printf("   ✓ Serialized PCZT: %d bytes\n", len(pcztBytes))
	fmt.Printf("   ✓ PCZT hex (first 32 bytes): %s...\n\n", hex.EncodeToString(pcztBytes[:32]))

	// At this point, you would transmit pcztBytes to the hardware wallet
	// via USB, Bluetooth, or QR code
	fmt.Println("📡 TRANSMITTING: PCZT → Hardware Wallet")
	fmt.Println("   (In production: USB/Bluetooth/QR code)\n")

	// ============================================================
	// STEP 3: Hardware wallet receives, signs, and returns PCZT
	// ============================================================
	fmt.Println("🔐 HARDWARE WALLET: Receiving PCZT...")

	// Parse the received PCZT
	hwPczt, err := t2z.Parse(pcztBytes)
	if err != nil {
		log.Fatalf("Hardware wallet failed to parse: %v", err)
	}
	fmt.Println("   ✓ PCZT parsed successfully")

	// Verify PCZT before signing (optional but recommended for hardware wallets)
	fmt.Println("🔐 HARDWARE WALLET: Verifying transaction...")
	// In production, you would verify against expected change outputs
	// For this example, we pass empty expected change
	err = t2z.VerifyBeforeSigning(hwPczt, request, nil)
	if err != nil {
		// Verification might fail without expected change, which is okay for demo
		fmt.Printf("   ⚠ Verification note: %v\n", err)
	} else {
		fmt.Println("   ✓ Verification passed")
	}

	// Get sighash for the input
	fmt.Println("🔐 HARDWARE WALLET: Computing sighash...")
	sighash, err := t2z.GetSighash(hwPczt, 0)
	if err != nil {
		log.Fatalf("Failed to get sighash: %v", err)
	}
	fmt.Printf("   ✓ Sighash: %s\n", hex.EncodeToString(sighash[:16]))

	// Display sighash to user for verification (optional)
	fmt.Println("🔐 HARDWARE WALLET: [Display on screen]")
	fmt.Println("   ┌─────────────────────────────────┐")
	fmt.Println("   │ Sign Transaction?               │")
	fmt.Printf("   │ Sighash: %s... │\n", hex.EncodeToString(sighash[:8]))
	fmt.Println("   │ [Confirm] [Reject]              │")
	fmt.Println("   └─────────────────────────────────┘")

	// User confirms on hardware wallet
	fmt.Println("👤 USER: [Presses CONFIRM button]\n")

	// Hardware wallet signs with secure key (simulated here)
	fmt.Println("🔐 HARDWARE WALLET: Signing with secure key...")
	compactSig := ecdsa.SignCompact(privKey, sighash[:], true)
	var signature [64]byte
	copy(signature[:], compactSig[1:])
	fmt.Printf("   ✓ Signature: %s...\n", hex.EncodeToString(signature[:16]))

	// Append signature to PCZT
	fmt.Println("🔐 HARDWARE WALLET: Adding signature to PCZT...")
	signedPczt, err := t2z.AppendSignature(hwPczt, 0, signature)
	if err != nil {
		log.Fatalf("Failed to append signature: %v", err)
	}
	fmt.Println("   ✓ Signature appended")

	// Serialize signed PCZT for return transmission
	fmt.Println("🔐 HARDWARE WALLET: Serializing signed PCZT...")
	signedPcztBytes, err := t2z.Serialize(signedPczt)
	if err != nil {
		log.Fatalf("Failed to serialize signed PCZT: %v", err)
	}
	fmt.Printf("   ✓ Serialized: %d bytes\n\n", len(signedPcztBytes))

	// Free the signed PCZT after serialization
	signedPczt.Free()

	fmt.Println("📡 TRANSMITTING: Signed PCZT → Coordinator")
	fmt.Println("   (In production: USB/Bluetooth/QR code)\n")

	// ============================================================
	// STEP 4: Coordinator receives and finalizes transaction
	// ============================================================
	fmt.Println("📱 COORDINATOR: Receiving signed PCZT...")

	// Parse the signed PCZT
	finalPczt, err := t2z.Parse(signedPcztBytes)
	if err != nil {
		log.Fatalf("Failed to parse signed PCZT: %v", err)
	}
	fmt.Println("   ✓ Signed PCZT received")

	// Finalize and extract transaction
	fmt.Println("📱 COORDINATOR: Finalizing transaction...")
	txBytes, err := t2z.FinalizeAndExtract(finalPczt)
	if err != nil {
		log.Fatalf("Failed to finalize: %v", err)
	}
	fmt.Printf("   ✓ Transaction finalized (%d bytes)\n\n", len(txBytes))

	// ============================================================
	// STEP 5: Broadcast to network
	// ============================================================
	fmt.Println("=== Transaction Ready for Broadcast ===")
	fmt.Printf("Transaction hex: %s\n", hex.EncodeToString(txBytes))
	fmt.Println("\n✅ SUCCESS: Hardware wallet signing complete!")
	fmt.Println("\nThe transaction is now ready to broadcast to the Zcash network.")
	fmt.Println("\n🔒 Security benefits:")
	fmt.Println("   • Private keys never left the hardware wallet")
	fmt.Println("   • User confirmed transaction details on secure device")
	fmt.Println("   • PCZT format allows secure multi-party signing")
}
