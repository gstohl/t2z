/**
 * Example 8: Combine Workflow (Parallel Signing)
 *
 * Demonstrates the combine() function for multi-party signing workflows:
 * - Create a transaction with multiple inputs
 * - Serialize the PCZT and create copies for parallel signing
 * - Each "signer" signs their input independently
 * - Combine the partially-signed PCZTs into one
 * - Finalize and broadcast
 *
 * Use case: Multiple parties each control different UTXOs and need to
 * co-sign a transaction without sharing private keys.
 */
package com.zcash.t2z.examples

import com.zcash.t2z.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println()
    println("=".repeat(70))
    println("  EXAMPLE 8: COMBINE WORKFLOW (Parallel Signing)")
    println("=".repeat(70))
    println()
    println("This example demonstrates the combine() function for parallel signing.")
    println("In a real scenario, each signer would be on a different device.")
    println()

    val client = ZebraClient()

    try {
        val keypair = TEST_KEYPAIR

        println("Configuration:")
        println("  Address: ${keypair.address}")

        // Fetch mature coinbase UTXOs
        println("\nFetching mature coinbase UTXOs...")
        val utxos = getMatureCoinbaseUtxos(client, keypair, 8)

        if (utxos.size < 3) {
            throw Exception("Need at least 3 mature UTXOs. Run setup and wait for maturity.")
        }

        // Use 3 inputs to demonstrate combine with multiple signers
        val inputs = utxos.take(3)
        val totalInput = inputs.fold(0UL) { sum, u -> sum + u.amount }
        println("  Selected ${inputs.size} UTXOs totaling: ${zatoshiToZec(totalInput)} ZEC\n")

        // Send back to ourselves
        val destAddress = keypair.address
        val fee = calculateFee(inputs.size, 2, 0)
        val paymentAmount = totalInput / 2UL

        val payments = listOf(Payment(destAddress, paymentAmount))

        println("Creating TransactionRequest...")
        val request = TransactionRequest(payments)

        val info = client.getBlockchainInfo()
        println("  Current block height: ${info.blocks}")

        val targetHeight = 2_500_000
        request.setTargetHeight(targetHeight)
        println("  Target height set to $targetHeight\n")

        printWorkflowSummary(
            "TRANSACTION SUMMARY",
            inputs,
            payments.map { it.address to it.amount },
            fee.toULong()
        )

        println("--- PARALLEL SIGNING WORKFLOW ---\n")

        // Step 1: Create and prove the PCZT
        println("1. Creating and proving PCZT...")
        val pczt = proposeTransaction(inputs, request)
        val proved = proveTransaction(pczt)
        println("   PCZT created and proved\n")

        // Step 2: Serialize the proved PCZT
        println("2. Serializing PCZT for distribution to signers...")
        val pcztBytes = serializePczt(proved)
        println("   Serialized PCZT: ${pcztBytes.size} bytes\n")

        // Step 3: Simulate parallel signing by different parties
        println("3. Simulating parallel signing by 3 different parties...\n")

        // Signer A signs input 0
        println("   Signer A: Signing input 0...")
        val pcztA = parsePczt(pcztBytes)
        val sighashA = getSighash(pcztA, 0)
        val signatureA = signCompact(sighashA, keypair)
        val signedA = appendSignature(pcztA, 0, signatureA)
        val bytesA = serializePczt(signedA)
        println("   Signer A: Done (signed input 0)\n")

        // Signer B signs input 1
        println("   Signer B: Signing input 1...")
        val pcztB = parsePczt(pcztBytes)
        val sighashB = getSighash(pcztB, 1)
        val signatureB = signCompact(sighashB, keypair)
        val signedB = appendSignature(pcztB, 1, signatureB)
        val bytesB = serializePczt(signedB)
        println("   Signer B: Done (signed input 1)\n")

        // Signer C signs input 2
        println("   Signer C: Signing input 2...")
        val pcztC = parsePczt(pcztBytes)
        val sighashC = getSighash(pcztC, 2)
        val signatureC = signCompact(sighashC, keypair)
        val signedC = appendSignature(pcztC, 2, signatureC)
        val bytesC = serializePczt(signedC)
        println("   Signer C: Done (signed input 2)\n")

        // Step 4: Combine all partially-signed PCZTs
        println("4. Combining partially-signed PCZTs...")
        val combinedA = parsePczt(bytesA)
        val combinedB = parsePczt(bytesB)
        val combinedC = parsePczt(bytesC)

        val fullySignedPczt = combine(listOf(combinedA, combinedB, combinedC))
        println("   All signatures combined into single PCZT\n")

        // Step 5: Finalize
        println("5. Finalizing transaction...")
        val txBytes = finalizeAndExtract(fullySignedPczt)
        val txHex = bytesToHex(txBytes)
        println("   Transaction finalized (${txBytes.size} bytes)\n")

        // Step 6: Broadcast
        println("6. Broadcasting transaction to network...")
        val txid = client.sendRawTransaction(txHex)
        printBroadcastResult(txid, txHex)

        // Mark UTXOs as spent
        markUtxosSpent(inputs)

        // Wait for confirmation
        println("Waiting for confirmation...")
        val currentHeight = client.getBlockchainInfo().blocks
        client.waitForBlocks(currentHeight + 1, 60000)
        println("   Confirmed!\n")

        println("SUCCESS! TXID: $txid")
        println("\nThe combine() function merged signatures from 3 independent signers.\n")

        // Cleanup
        request.close()

    } catch (e: Exception) {
        printError("EXAMPLE 8 FAILED", e)
        System.exit(1)
    } finally {
        client.close()
    }
}
