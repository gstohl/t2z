/**
 * Example 4: Attack Scenario - PCZT Malleation Detection
 *
 * Demonstrates how verifyBeforeSigning catches malicious modifications:
 * - Create a legitimate transaction request
 * - Simulate an attacker modifying the PCZT
 * - Show verification catching the attack
 *
 * This is a DEMO showing why verification is critical!
 */
package com.zcash.t2z.examples

import com.zcash.t2z.*

// Victim's intended recipient (same as sender for demo)
private const val VICTIM_ADDRESS = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma"
// Attacker's address (different address)
private const val ATTACKER_ADDRESS = "tmBsTi2xWTjUdEXnuTceL7fecEQKeWi4vxA"

fun main() {
    println()
    println("=".repeat(70))
    println("  EXAMPLE 4: ATTACK SCENARIO - PCZT Malleation Detection")
    println("=".repeat(70))
    println()

    println("WARNING: This demonstrates a security feature!")
    println("   This example shows why you MUST verify PCZTs before signing.")
    println()

    try {
        // Create mock UTXO (no network needed for this demo)
        val txid = ByteArray(32)
        "example4_attack_scenario_txid00".toByteArray().copyInto(txid, 0, 0, 31)

        val pubkey = TEST_KEYPAIR.publicKey
        val scriptPubKey = createP2PKHScript(pubkey)

        val inputAmount = 100_000_000UL // 1 ZEC

        val inputs = listOf(
            TransparentInput(
                pubkey = pubkey,
                txid = txid,
                vout = 0,
                amount = inputAmount,
                scriptPubKey = scriptPubKey
            )
        )

        println("Scenario Setup:")
        println("  Victim's address: $VICTIM_ADDRESS")
        println("  Legitimate recipient: $VICTIM_ADDRESS")
        println("  Attacker's address: $ATTACKER_ADDRESS")
        println()
        println("Using UTXO: ${zatoshiToZec(inputAmount)} ZEC\n")

        // =================================================================
        // SCENARIO 1: Legitimate Transaction
        // =================================================================
        println("=".repeat(70))
        println("  SCENARIO 1: Legitimate Transaction")
        println("=".repeat(70))
        println()

        val paymentAmount = inputAmount / 2UL // 50%

        val legitimatePayments = listOf(Payment(address = VICTIM_ADDRESS, amount = paymentAmount))

        println("User creates legitimate payment:")
        println("   Send ${zatoshiToZec(paymentAmount)} ZEC -> ${VICTIM_ADDRESS.take(30)}...\n")

        TransactionRequest(legitimatePayments).use { legitimateRequest ->
            // Mainnet is the default, just set target height
            legitimateRequest.setTargetHeight(2_500_000)

            println("1. Proposing legitimate transaction...")
            val pczt = proposeTransaction(inputs, legitimateRequest)
            println("   PCZT created\n")

            println("2. Proving transaction...")
            val proved = proveTransaction(pczt)
            println("   Proofs generated\n")

            println("3. Verifying PCZT (BEFORE signing)...")
            try {
                verifyBeforeSigning(proved, legitimateRequest, emptyList())
                println("   VERIFICATION PASSED - Safe to sign!\n")
            } catch (e: T2zException) {
                println("   Verification returned: ${e.message}\n")
            }

            // =================================================================
            // SCENARIO 2: Attack - Wrong Payment Amount
            // =================================================================
            println("=".repeat(70))
            println("  SCENARIO 2: Attack - Wrong Payment Amount")
            println("=".repeat(70))
            println()

            println("ATTACK: Attacker intercepts PCZT and creates different request")
            val wrongAmount = paymentAmount * 2UL
            println("   Attacker claims payment is ${zatoshiToZec(wrongAmount)} ZEC (instead of ${zatoshiToZec(paymentAmount)} ZEC)\n")

            val attackedPayments1 = listOf(Payment(address = VICTIM_ADDRESS, amount = wrongAmount))

            TransactionRequest(attackedPayments1).use { maliciousRequest1 ->
                println("User verifies PCZT before signing...")
                try {
                    verifyBeforeSigning(proved, maliciousRequest1, emptyList())
                    println("   DANGER: Verification passed (should not happen!)\n")
                } catch (e: T2zException) {
                    println("   ATTACK DETECTED! Verification failed:")
                    println("   Error: ${e.message}\n")
                    println("   Transaction NOT signed - funds are SAFE!\n")
                }
            }

            // =================================================================
            // SCENARIO 3: Attack - Wrong Recipient
            // =================================================================
            println("=".repeat(70))
            println("  SCENARIO 3: Attack - Wrong Recipient")
            println("=".repeat(70))
            println()

            println("ATTACK: Attacker replaces recipient with their own address\n")

            val attackedPayments2 = listOf(Payment(address = ATTACKER_ADDRESS, amount = paymentAmount))

            TransactionRequest(attackedPayments2).use { maliciousRequest2 ->
                println("User verifies PCZT before signing...")
                try {
                    verifyBeforeSigning(proved, maliciousRequest2, emptyList())
                    println("   DANGER: Verification passed (should not happen!)\n")
                } catch (e: T2zException) {
                    println("   ATTACK DETECTED! Verification failed:")
                    println("   Error: ${e.message}\n")
                    println("   Transaction NOT signed - funds are SAFE!\n")
                }
            }

            // =================================================================
            // SCENARIO 4: Attack - Lower Amount
            // =================================================================
            println("=".repeat(70))
            println("  SCENARIO 4: Attack - Different Payment Amount (Lower)")
            println("=".repeat(70))
            println()

            println("ATTACK: Attacker claims user only wants to send half the amount\n")
            val lowerAmount = paymentAmount / 2UL

            val attackedPayments3 = listOf(Payment(address = VICTIM_ADDRESS, amount = lowerAmount))

            TransactionRequest(attackedPayments3).use { maliciousRequest3 ->
                println("User verifies PCZT before signing...")
                try {
                    verifyBeforeSigning(proved, maliciousRequest3, emptyList())
                    println("   DANGER: Verification passed (should not happen!)\n")
                } catch (e: T2zException) {
                    println("   ATTACK DETECTED! Verification failed:")
                    println("   Error: ${e.message}\n")
                    println("   Transaction NOT signed - funds are SAFE!\n")
                }
            }
        }

        println("KEY TAKEAWAY: Always call verifyBeforeSigning() before signing!\n")

    } catch (e: Exception) {
        printError("EXAMPLE 4 FAILED", e)
        throw e
    }
}

/**
 * Create P2PKH script from public key.
 * Format: OP_DUP OP_HASH160 <20-byte hash> OP_EQUALVERIFY OP_CHECKSIG
 */
private fun createP2PKHScript(pubkey: ByteArray): ByteArray {
    val pubkeyHash = hash160(pubkey)
    return byteArrayOf(
        0x76,                   // OP_DUP
        0xa9.toByte(),          // OP_HASH160
        0x14,                   // Push 20 bytes
        *pubkeyHash,
        0x88.toByte(),          // OP_EQUALVERIFY
        0xac.toByte()           // OP_CHECKSIG
    )
}
