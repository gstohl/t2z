/**
 * Example 7: Mixed Transparent + Shielded Outputs (T→T+Z)
 *
 * Demonstrates a transaction with both transparent and shielded outputs.
 * This is common when paying both privacy-aware and legacy recipients.
 */
package com.zcash.t2z.examples

import com.zcash.t2z.*
import kotlinx.coroutines.runBlocking

private const val SHIELDED_ADDRESS = "u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz"

fun main() = runBlocking {
    println()
    println("=".repeat(70))
    println("  EXAMPLE 7: MIXED TRANSPARENT + SHIELDED (T→T+Z)")
    println("=".repeat(70))
    println()

    val client = ZebraClient()

    try {
        val testData = loadTestData()

        println("Configuration:")
        println("  Source (transparent): ${testData.transparent.address}")
        println("  Dest 1 (transparent): ${testData.transparent.address}")
        println("  Dest 2 (shielded): ${SHIELDED_ADDRESS.take(30)}...\n")

        println("Fetching mature coinbase UTXOs...")
        val utxos = getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 1)

        if (utxos.isEmpty()) {
            throw Exception("No UTXOs available. Run setup first.")
        }

        val input = utxos[0]
        println("Using UTXO: ${zatoshiToZec(input.amount)} ZEC\n")

        // Create mixed payments
        val transparentAmount = input.amount / 4UL
        val shieldedAmount = input.amount / 4UL
        val fee = 15_000UL

        val payments = listOf(
            Payment(address = testData.transparent.address, amount = transparentAmount),
            Payment(address = SHIELDED_ADDRESS, amount = shieldedAmount)
        )

        println("Creating TransactionRequest with mixed outputs...")
        TransactionRequest(payments).use { request ->
            request.setUseMainnet(true)
            request.setTargetHeight(2_500_000)

            printWorkflowSummary(
                "TRANSACTION SUMMARY - T→T+Z MIXED",
                listOf(input),
                payments.map { it.address to it.amount },
                fee
            )

            println("Output Types:")
            println("   [0] Transparent: ${zatoshiToZec(transparentAmount)} ZEC (public)")
            println("   [1] Shielded: ${zatoshiToZec(shieldedAmount)} ZEC (private)\n")

            println("1. Proposing transaction...")
            val pczt = proposeTransaction(listOf(input), request)
            println("   PCZT created with mixed outputs\n")

            println("2. Proving transaction...")
            println("   Generating Orchard proofs for shielded output...")
            val proved = proveTransaction(pczt)
            println("   Proofs generated!\n")

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

            println("=".repeat(70))
            println("  MIXED OUTPUT TRANSACTION SUCCESSFUL")
            println("=".repeat(70))
            println("\nTransaction contains:")
            println("  - 1 transparent output (visible on blockchain)")
            println("  - 1 shielded Orchard output (private)")
            println("\nThis demonstrates t2z flexibility in supporting")
            println("both legacy transparent and privacy-preserving outputs.\n")

            println("EXAMPLE 7 COMPLETED SUCCESSFULLY!\n")
        }
    } catch (e: Exception) {
        printError("EXAMPLE 7 FAILED", e)
        throw e
    } finally {
        client.close()
    }
}
