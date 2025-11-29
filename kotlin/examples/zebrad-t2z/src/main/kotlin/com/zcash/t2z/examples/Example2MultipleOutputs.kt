/**
 * Example 2: Multiple Output Transaction (Transparent → 2×Transparent)
 *
 * Demonstrates sending to multiple recipients in a single transaction.
 */
package com.zcash.t2z.examples

import com.zcash.t2z.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println()
    println("=".repeat(70))
    println("  EXAMPLE 2: MULTIPLE OUTPUTS (Transparent -> 2×Transparent)")
    println("=".repeat(70))
    println()

    val client = ZebraClient()

    try {
        val testData = loadTestData()

        println("Configuration:")
        println("  Source address: ${testData.transparent.address}")

        println("Fetching mature coinbase UTXOs...")
        val utxos = getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 1)

        if (utxos.isEmpty()) {
            throw Exception("No mature UTXOs available. Run setup first.")
        }

        val input = utxos[0]
        println("  Selected UTXO: ${zatoshiToZec(input.amount)} ZEC\n")

        // Send to two recipients (both to ourselves for demo)
        val destAddress = testData.transparent.address
        val payment1Amount = input.amount / 4UL
        val payment2Amount = input.amount / 4UL
        val fee = 10_000UL

        val payments = listOf(
            Payment(address = destAddress, amount = payment1Amount),
            Payment(address = destAddress, amount = payment2Amount)
        )

        println("Creating TransactionRequest with 2 outputs...")
        TransactionRequest(payments).use { request ->
            // Mainnet is the default, just set target height
            request.setTargetHeight(2_500_000)

            printWorkflowSummary(
                "TRANSACTION SUMMARY - 2 OUTPUTS",
                listOf(input),
                payments.map { it.address to it.amount },
                fee
            )

            println("1. Proposing transaction...")
            val pczt = proposeTransaction(listOf(input), request)
            println("   PCZT created with 2 outputs\n")

            println("2. Proving transaction...")
            val proved = proveTransaction(pczt)
            println("   Proofs generated\n")

            println("3. Verifying PCZT...")
            verifyBeforeSigning(proved, request, emptyList())
            println("   Verification passed\n")

            println("4. Getting sighash...")
            val sighash = getSighash(proved, 0)
            println("   Sighash: ${bytesToHex(sighash).take(32)}...\n")

            println("5. Signing transaction...")
            val signature = signCompact(sighash, TEST_KEYPAIR)
            println("   Signature: ${bytesToHex(signature).take(32)}...\n")

            println("6. Appending signature...")
            val signed = appendSignature(proved, 0, signature)
            println("   Signature appended\n")

            println("7. Finalizing transaction...")
            val txBytes = finalizeAndExtract(signed)
            val txHex = bytesToHex(txBytes)
            println("   Transaction finalized (${txBytes.size} bytes)\n")

            println("8. Broadcasting transaction...")
            val txid = client.sendRawTransaction(txHex)
            printBroadcastResult(txid, txHex)

            markUtxosSpent(listOf(input))

            println("Waiting for confirmation...")
            val currentHeight = client.getBlockchainInfo().blocks
            client.waitForBlocks(currentHeight + 1, 60000)
            println("   Transaction confirmed!\n")

            println("EXAMPLE 2 COMPLETED SUCCESSFULLY!\n")
        }
    } catch (e: Exception) {
        printError("EXAMPLE 2 FAILED", e)
        throw e
    } finally {
        client.close()
    }
}
