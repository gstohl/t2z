/**
 * Example 6: Multiple Shielded Outputs (T→2×Z)
 *
 * Demonstrates sending to multiple shielded recipients in one transaction.
 */
package com.zcash.t2z.examples

import com.zcash.t2z.*
import kotlinx.coroutines.runBlocking

// Two different mainnet unified addresses with Orchard receivers
private const val SHIELDED_ADDRESS_1 = "u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz"
private const val SHIELDED_ADDRESS_2 = "u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz"

fun main() = runBlocking {
    println()
    println("=".repeat(70))
    println("  EXAMPLE 6: MULTIPLE SHIELDED OUTPUTS (T→2×Z)")
    println("=".repeat(70))
    println()

    val client = ZebraClient()

    try {
        val testData = loadTestData()

        println("Configuration:")
        println("  Source (transparent): ${testData.transparent.address}")
        println("  Dest 1 (shielded): ${SHIELDED_ADDRESS_1.take(30)}...")
        println("  Dest 2 (shielded): ${SHIELDED_ADDRESS_2.take(30)}...\n")

        println("Fetching mature coinbase UTXOs...")
        val utxos = getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 1)

        if (utxos.isEmpty()) {
            throw Exception("No UTXOs available. Run setup first.")
        }

        val input = utxos[0]
        println("Using UTXO: ${zatoshiToZec(input.amount)} ZEC\n")

        // Create two shielded payments
        // Calculate fee: 1 input, 1 transparent change, 2 orchard outputs
        val fee = calculateFee(1, 1, 2).toULong()
        val payment1Amount = input.amount / 4UL
        val payment2Amount = input.amount / 4UL

        val payments = listOf(
            Payment(address = SHIELDED_ADDRESS_1, amount = payment1Amount),
            Payment(address = SHIELDED_ADDRESS_2, amount = payment2Amount)
        )

        println("Creating TransactionRequest with 2 shielded outputs...")
        TransactionRequest(payments).use { request ->
            // Mainnet is the default, just set target height
            request.setTargetHeight(2_500_000)

            printWorkflowSummary(
                "TRANSACTION SUMMARY - T→2×Z",
                listOf(input),
                payments.map { it.address to it.amount },
                fee
            )

            println("1. Proposing transaction...")
            val pczt = proposeTransaction(listOf(input), request)
            println("   PCZT created with 2 Orchard outputs\n")

            println("2. Proving transaction (2 Orchard actions)...")
            println("   This may take a bit longer...")
            val proved = proveTransaction(pczt)
            println("   Orchard proofs generated!\n")

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
            println("   Confirmed!\n")

            println("SUCCESS! TXID: $txid")
            println("   Shielded to 2 Orchard recipients\n")
        }
    } catch (e: Exception) {
        printError("EXAMPLE 6 FAILED", e)
        throw e
    } finally {
        client.close()
    }
}
