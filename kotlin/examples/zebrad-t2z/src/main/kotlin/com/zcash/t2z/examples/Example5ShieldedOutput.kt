/**
 * Example 5: Transparent to Shielded Transaction (T→Z)
 *
 * Demonstrates the core t2z workflow - sending from transparent to shielded:
 * - Use a transparent UTXO as input
 * - Send to a unified address with Orchard receiver
 * - The library creates Orchard proofs automatically
 */
package com.zcash.t2z.examples

import com.zcash.t2z.*
import kotlinx.coroutines.runBlocking

// Deterministic mainnet unified address with Orchard receiver
// Generated from SpendingKey::from_bytes([42u8; 32])
private const val SHIELDED_ADDRESS = "u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz"

fun main() = runBlocking {
    println()
    println("=".repeat(70))
    println("  EXAMPLE 5: TRANSPARENT TO SHIELDED (T→Z)")
    println("=".repeat(70))
    println()

    val client = ZebraClient()

    try {
        val testData = loadTestData()

        println("Configuration:")
        println("  Source (transparent): ${testData.transparent.address}")
        println("  Destination (shielded): ${SHIELDED_ADDRESS.take(30)}...")
        println("  Note: This is an Orchard address (u1... prefix = mainnet)\n")

        println("Fetching mature coinbase UTXOs...")
        val utxos = getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 1)

        if (utxos.isEmpty()) {
            throw Exception("No UTXOs available. Run setup first.")
        }

        val input = utxos[0]
        println("Using UTXO: ${zatoshiToZec(input.amount)} ZEC\n")

        // Create shielded payment (use 50% of UTXO for payment)
        val paymentAmount = input.amount / 2UL
        val fee = 15_000UL // ZIP-317 fee for T→Z (includes Orchard action cost)

        val payments = listOf(Payment(address = SHIELDED_ADDRESS, amount = paymentAmount))

        println("Creating TransactionRequest for shielded output...")
        TransactionRequest(payments).use { request ->
            request.setUseMainnet(true)
            request.setTargetHeight(2_500_000)
            println("   Using mainnet branch ID for Zebra regtest\n")

            printWorkflowSummary(
                "TRANSACTION SUMMARY - T→Z SHIELDED",
                listOf(input),
                payments.map { it.address to it.amount },
                fee
            )

            println("KEY DIFFERENCE from T→T:")
            println("   - Payment address is a unified address (u1...)")
            println("   - Library creates Orchard actions with zero-knowledge proofs")
            println("   - Funds become private after this transaction\n")

            println("1. Proposing transaction...")
            val pczt = proposeTransaction(listOf(input), request)
            println("   PCZT created with Orchard output\n")

            println("2. Proving transaction (generating Orchard ZK proofs)...")
            println("   This may take a few seconds...")
            val proved = proveTransaction(pczt)
            println("   Orchard proofs generated!\n")

            println("3. Verifying PCZT...")
            verifyBeforeSigning(proved, request, emptyList())
            println("   Verification passed\n")

            println("4. Getting sighash...")
            val sighash = getSighash(proved, 0)
            println("   Sighash: ${bytesToHex(sighash).take(32)}...\n")

            println("5. Signing transaction (client-side)...")
            val signature = signCompact(sighash, TEST_KEYPAIR)
            println("   Signature: ${bytesToHex(signature).take(32)}...\n")

            println("6. Appending signature...")
            val signed = appendSignature(proved, 0, signature)
            println("   Signature appended\n")

            println("7. Finalizing transaction...")
            val txBytes = finalizeAndExtract(signed)
            val txHex = bytesToHex(txBytes)
            println("   Transaction finalized (${txBytes.size} bytes)")
            println("   Note: T→Z transactions are larger due to Orchard proofs\n")

            println("8. Broadcasting transaction...")
            val txid = client.sendRawTransaction(txHex)
            printBroadcastResult(txid, txHex)

            markUtxosSpent(listOf(input))

            println("Waiting for confirmation (internal miner)...")
            val currentHeight = client.getBlockchainInfo().blocks
            client.waitForBlocks(currentHeight + 1, 60000)
            println("   Transaction confirmed!\n")

            println("=".repeat(70))
            println("  T→Z TRANSACTION SUCCESSFUL")
            println("=".repeat(70))
            println("\nTXID: $txid")
            println("\nWhat happened:")
            println("  - Transparent input: ${zatoshiToZec(input.amount)} ZEC")
            println("  - Shielded output: ${zatoshiToZec(paymentAmount)} ZEC")
            println("  - Change: returned to transparent address")
            println("  - Fee: ${zatoshiToZec(fee)} ZEC\n")
            println("The shielded output is now private - only the recipient")
            println("with the viewing key can see the amount and memo.\n")

            println("EXAMPLE 5 COMPLETED SUCCESSFULLY!\n")
        }
    } catch (e: Exception) {
        printError("EXAMPLE 5 FAILED", e)
        throw e
    } finally {
        client.close()
    }
}
