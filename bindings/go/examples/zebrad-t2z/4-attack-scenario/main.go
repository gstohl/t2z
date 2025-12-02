// Example 4: Attack Scenario - PCZT Malleation Detection
//
// Demonstrates how verify_before_signing catches malicious modifications:
// - Create a legitimate transaction request
// - Simulate an attacker modifying the PCZT
// - Show verification catching the attack
//
// This is a DEMO showing why verification is critical!
//
// Run with: go run ./examples/zebrad/example4_attack_scenario.go

package main

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"strings"

	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	t2z "github.com/gstohl/t2z/go"
	"golang.org/x/crypto/ripemd160"
)

func createTestKeypair4() ([]byte, []byte) {
	privateKeyHex := "e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35"
	privateKeyBytes, _ := hex.DecodeString(privateKeyHex)

	privKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)
	pubKeyBytes := privKey.PubKey().SerializeCompressed()

	return privateKeyBytes, pubKeyBytes
}

func hash160_4(data []byte) []byte {
	sha256Hash := sha256.Sum256(data)
	ripemd160Hasher := ripemd160.New()
	ripemd160Hasher.Write(sha256Hash[:])
	return ripemd160Hasher.Sum(nil)
}

func createP2PKHScript4(pubkey []byte) []byte {
	pubkeyHash := hash160_4(pubkey)
	script := make([]byte, 25)
	script[0] = 0x76
	script[1] = 0xa9
	script[2] = 0x14
	copy(script[3:23], pubkeyHash)
	script[23] = 0x88
	script[24] = 0xac
	return script
}

func zatoshiToZec4(zatoshi uint64) string {
	return fmt.Sprintf("%.8f", float64(zatoshi)/100_000_000)
}

func main() {
	fmt.Println()
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  EXAMPLE 4: ATTACK SCENARIO - PCZT Malleation Detection")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	fmt.Println("WARNING: This demonstrates a security feature!")
	fmt.Println("   This example shows why you MUST verify PCZTs before signing.")
	fmt.Println()

	_, pubkey := createTestKeypair4()

	// Addresses
	victimAddress := "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma"
	attackerAddress := "tmBsTi2xWTjUdEXnuTceL7fecEQKeWi4vxA"

	fmt.Println("Scenario Setup:")
	fmt.Printf("  Victim's address: %s\n", victimAddress)
	fmt.Printf("  Legitimate recipient: %s\n", victimAddress)
	fmt.Printf("  Attacker's address: %s\n\n", attackerAddress)

	// Create mock UTXO
	var txid [32]byte
	copy(txid[:], []byte("example4_attack_scenario_txid00"))
	scriptPubKey := createP2PKHScript4(pubkey)

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

	fmt.Printf("Using UTXO: %s ZEC\n\n", zatoshiToZec4(inputAmount))

	// SCENARIO 1: Legitimate Transaction
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  SCENARIO 1: Legitimate Transaction")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	paymentAmount := inputAmount / 2 // 50%

	legitimatePayments := []t2z.Payment{
		{Address: victimAddress, Amount: paymentAmount},
	}

	fmt.Println("User creates legitimate payment:")
	fmt.Printf("   Send %s ZEC -> %s...\n\n", zatoshiToZec4(paymentAmount), victimAddress[:30])

	legitimateRequest, _ := t2z.NewTransactionRequest(legitimatePayments)
	defer legitimateRequest.Free()

	legitimateRequest.SetTargetHeight(2_500_000)

	fmt.Println("1. Proposing legitimate transaction...")
	pczt, err := t2z.ProposeTransaction(inputs, legitimateRequest)
	if err != nil {
		fmt.Printf("   Failed: %v\n", err)
		return
	}
	fmt.Println("   PCZT created")
	fmt.Println()

	fmt.Println("2. Proving transaction...")
	proved, err := t2z.ProveTransaction(pczt)
	if err != nil {
		fmt.Printf("   Failed: %v\n", err)
		return
	}
	fmt.Println("   Proofs generated")
	fmt.Println()

	fmt.Println("3. Verifying PCZT (BEFORE signing)...")
	err = t2z.VerifyBeforeSigning(proved, legitimateRequest, []t2z.TransparentOutput{})
	if err != nil {
		fmt.Printf("   Verification returned: %v\n", err)
	} else {
		fmt.Println("   VERIFICATION PASSED - Safe to sign!")
	}
	fmt.Println()

	// SCENARIO 2: Attack - Wrong Payment Amount
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  SCENARIO 2: Attack - Wrong Payment Amount")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	fmt.Println("ATTACK: Attacker intercepts PCZT and creates different request")
	wrongAmount := paymentAmount * 2
	fmt.Printf("   Attacker claims payment is %s ZEC (instead of %s ZEC)\n\n",
		zatoshiToZec4(wrongAmount), zatoshiToZec4(paymentAmount))

	attackedPayments1 := []t2z.Payment{
		{Address: victimAddress, Amount: wrongAmount},
	}

	maliciousRequest1, _ := t2z.NewTransactionRequest(attackedPayments1)
	defer maliciousRequest1.Free()

	fmt.Println("User verifies PCZT before signing...")
	err = t2z.VerifyBeforeSigning(proved, maliciousRequest1, []t2z.TransparentOutput{})
	if err != nil {
		fmt.Println("   ATTACK DETECTED! Verification failed:")
		fmt.Printf("   Error: %v\n\n", err)
		fmt.Println("   Transaction NOT signed - funds are SAFE!")
	} else {
		fmt.Println("   DANGER: Verification passed (should not happen!)")
	}
	fmt.Println()

	// SCENARIO 3: Attack - Wrong Recipient
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  SCENARIO 3: Attack - Wrong Recipient")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	fmt.Println("ATTACK: Attacker replaces recipient with their own address")
	fmt.Println()

	attackedPayments2 := []t2z.Payment{
		{Address: attackerAddress, Amount: paymentAmount},
	}

	maliciousRequest2, _ := t2z.NewTransactionRequest(attackedPayments2)
	defer maliciousRequest2.Free()

	fmt.Println("User verifies PCZT before signing...")
	err = t2z.VerifyBeforeSigning(proved, maliciousRequest2, []t2z.TransparentOutput{})
	if err != nil {
		fmt.Println("   ATTACK DETECTED! Verification failed:")
		fmt.Printf("   Error: %v\n\n", err)
		fmt.Println("   Transaction NOT signed - funds are SAFE!")
	} else {
		fmt.Println("   DANGER: Verification passed (should not happen!)")
	}
	fmt.Println()

	// SCENARIO 4: Attack - Lower Amount
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println("  SCENARIO 4: Attack - Different Payment Amount (Lower)")
	fmt.Println(strings.Repeat("=", 70))
	fmt.Println()

	fmt.Println("ATTACK: Attacker claims user only wants to send half the amount")
	lowerAmount := paymentAmount / 2
	fmt.Println()

	attackedPayments3 := []t2z.Payment{
		{Address: victimAddress, Amount: lowerAmount},
	}

	maliciousRequest3, _ := t2z.NewTransactionRequest(attackedPayments3)
	defer maliciousRequest3.Free()

	fmt.Println("User verifies PCZT before signing...")
	err = t2z.VerifyBeforeSigning(proved, maliciousRequest3, []t2z.TransparentOutput{})
	if err != nil {
		fmt.Println("   ATTACK DETECTED! Verification failed:")
		fmt.Printf("   Error: %v\n\n", err)
		fmt.Println("   Transaction NOT signed - funds are SAFE!")
	} else {
		fmt.Println("   DANGER: Verification passed (should not happen!)")
	}
	fmt.Println()

	// Clean up the original PCZT
	proved.Free()

	fmt.Println("KEY TAKEAWAY: Always call VerifyBeforeSigning() before signing!\n")
}
