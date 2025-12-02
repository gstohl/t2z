/**
 * Example 3: Multiple Inputs (UTXO Consolidation)
 *
 * Demonstrates spending multiple UTXOs in a single transaction.
 * This is useful for consolidating small UTXOs into a larger one.
 */
package com.zcash.t2z.examples

import com.zcash.t2z.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println()
    println("=".repeat(70))
    println("  EXAMPLE 3: MULTIPLE INPUTS (UTXO Consolidation)")
    println("=".repeat(70))
    println()

    val client = ZebraClient()

    try {
        val testData = loadTestData()

        println("Configuration:")
        println("  Source address: ${testData.transparent.address}")

        println("Fetching mature coinbase UTXOs (need at least 2)...")
        val utxos = getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 2)

        if (utxos.size < 2) {
            throw Exception("Need at least 2 UTXOs. Run more blocks or wait for maturity.")
        }

        val inputs = utxos.take(2)
        val totalInput = inputs.fold(0UL) { sum, input -> sum + input.amount }
        println("  Selected ${inputs.size} UTXOs totaling ${zatoshiToZec(totalInput)} ZEC\n")

        // Consolidate to single output (back to ourselves)
        val destAddress = testData.transparent.address
        // Calculate fee: N inputs, 1 output, 0 orchard
        val fee = calculateFee(inputs.size, 1, 0).toULong()
        val paymentAmount = totalInput - fee

        val payments = listOf(Payment(address = destAddress, amount = paymentAmount))

        println("Creating TransactionRequest (consolidation)...")
        TransactionRequest(payments).use { request ->
            // Mainnet is the default, just set target height
            request.setTargetHeight(2_500_000)

            printWorkflowSummary(
                "TRANSACTION SUMMARY - CONSOLIDATION",
                inputs,
                payments.map { it.address to it.amount },
                fee
            )

            println("1. Proposing transaction with ${inputs.size} inputs...")
            val pczt = proposeTransaction(inputs, request)
            println("   PCZT created\n")

            println("2. Proving transaction...")
            val proved = proveTransaction(pczt)
            println("   Proofs generated\n")

            println("3. Verifying PCZT...")
            verifyBeforeSigning(proved, request, emptyList())
            println("   Verification passed\n")

            // Sign all inputs
            var currentPczt = proved
            for (i in inputs.indices) {
                println("4.${i + 1}. Signing input $i...")
                val sighash = getSighash(currentPczt, i)
                val signature = signCompact(sighash, TEST_KEYPAIR)
                currentPczt = appendSignature(currentPczt, i, signature)
                println("     Input $i signed\n")
            }

            println("5. Finalizing transaction...")
            val txBytes = finalizeAndExtract(currentPczt)
            val txHex = bytesToHex(txBytes)
            println("   Transaction finalized (${txBytes.size} bytes)\n")

            println("6. Broadcasting transaction...")
            val txid = client.sendRawTransaction(txHex)
            printBroadcastResult(txid, txHex)

            markUtxosSpent(inputs)

            println("Waiting for confirmation...")
            val currentHeight = client.getBlockchainInfo().blocks
            client.waitForBlocks(currentHeight + 1, 60000)
            println("   Confirmed!\n")

            println("SUCCESS! TXID: $txid")
            println("   ${inputs.size} UTXOs consolidated into 1\n")
        }
    } catch (e: Exception) {
        printError("EXAMPLE 3 FAILED", e)
        throw e
    } finally {
        client.close()
    }
}
