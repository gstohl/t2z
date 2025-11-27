/**
 * Example 1: Single Output Transaction (Transparent → Transparent)
 *
 * Demonstrates the basic t2z workflow using Zebra (no wallet):
 * - Load UTXO and keys from setup data
 * - Create a payment to another transparent address
 * - Propose, sign (client-side), and broadcast the transaction
 */
package com.zcash.t2z.examples

import com.zcash.t2z.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println()
    println("=".repeat(70))
    println("  EXAMPLE 1: SINGLE OUTPUT TRANSACTION (Transparent -> Transparent)")
    println("=".repeat(70))
    println()

    val client = ZebraClient()

    try {
        // Load test data for address info
        val testData = loadTestData()

        println("Configuration:")
        println("  Source address: ${testData.transparent.address}")

        // Fetch fresh mature coinbase UTXOs from the blockchain
        // Need multiple UTXOs since each coinbase is small (~4768 zatoshis)
        println("Fetching mature coinbase UTXOs...")
        val allUtxos = getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 20)

        if (allUtxos.isEmpty()) {
            throw Exception("No mature UTXOs available. Run setup and wait for maturity.")
        }

        // Collect UTXOs until we have enough for fee + minimum payment
        var totalInput = 0UL
        val inputs = mutableListOf<com.zcash.t2z.TransparentInput>()

        // Estimate fee based on number of inputs we'll use
        // ZIP-317: max(2, actions) * 5000, actions = inputs + 2 outputs
        fun estimateFee(numInputs: Int) = (maxOf(2, numInputs + 2) * 5000).toULong()

        for (utxo in allUtxos) {
            inputs.add(utxo)
            totalInput += utxo.amount
            val fee = estimateFee(inputs.size)
            // Need enough for fee + at least 1000 zatoshi payment
            if (totalInput > fee + 1000UL) break
        }

        val fee = estimateFee(inputs.size)

        if (totalInput <= fee) {
            throw Exception("Not enough funds: have ${totalInput} zatoshis, need ${fee} for fee. Wait for more blocks.")
        }

        println("  Selected ${inputs.size} UTXOs totaling: ${zatoshiToZec(totalInput)} ZEC")
        println("  Calculated fee: ${zatoshiToZec(fee)} ZEC (${inputs.size} inputs)\n")

        // For this example, send back to ourselves (transparent -> transparent)
        val destAddress = testData.transparent.address
        // Payment = (total - fee) / 2, leaving rest as change
        val paymentAmount = (totalInput - fee) / 2UL

        val payments = listOf(Payment(address = destAddress, amount = paymentAmount))

        println("Creating TransactionRequest...")
        TransactionRequest(payments).use { request ->
            // Get current block height for reference
            val info = client.getBlockchainInfo()
            println("  Current block height: ${info.blocks}")

            // IMPORTANT: Zebra regtest uses mainnet-like branch IDs
            request.setUseMainnet(true)
            request.setTargetHeight(2_500_000)
            println("  Target height set to 2500000 (mainnet post-NU5)")
            println("  Using mainnet branch ID for Zebra regtest\n")

            // Print workflow summary
            printWorkflowSummary(
                "TRANSACTION SUMMARY",
                inputs,
                payments.map { it.address to it.amount },
                fee
            )

            // Step 1: Propose transaction
            println("1. Proposing transaction...")
            val pczt = proposeTransaction(inputs, request)
            println("   PCZT created\n")

            // Step 2: Prove transaction
            println("2. Proving transaction...")
            val proved = proveTransaction(pczt)
            println("   Proofs generated\n")

            // Step 3: Verify before signing
            println("3. Verifying PCZT before signing...")
            verifyBeforeSigning(proved, request, emptyList())
            println("   Verification passed\n")

            // Step 4-6: Sign all inputs
            println("4-6. Signing ${inputs.size} input(s)...")
            var currentPczt = proved
            for (i in inputs.indices) {
                val sighash = getSighash(currentPczt, i)
                println("   Input $i sighash: ${bytesToHex(sighash).take(16)}...")
                val signature = signCompact(sighash, TEST_KEYPAIR)
                currentPczt = appendSignature(currentPczt, i, signature)
                println("   Input $i signed")
            }
            val signed = currentPczt
            println("   All signatures appended\n")

            // Step 7: Finalize
            println("7. Finalizing transaction...")
            val txBytes = finalizeAndExtract(signed)
            val txHex = bytesToHex(txBytes)
            println("   Transaction finalized (${txBytes.size} bytes)\n")

            // Step 8: Broadcast
            println("8. Broadcasting transaction to network...")
            val txid = client.sendRawTransaction(txHex)
            printBroadcastResult(txid, txHex)

            // Mark UTXOs as spent for subsequent examples
            markUtxosSpent(inputs)

            // Wait for confirmation
            println("Waiting for confirmation block (internal miner)...")
            val currentHeight = client.getBlockchainInfo().blocks
            println("  Block height: $currentHeight")
            client.waitForBlocks(currentHeight + 1, 60000)
            val newHeight = client.getBlockchainInfo().blocks
            println("  New block height: $newHeight")
            println("   Transaction confirmed!\n")

            println("Transaction confirmed in the blockchain.")
            println("  TXID: $txid\n")

            println("EXAMPLE 1 COMPLETED SUCCESSFULLY!\n")
        }
    } catch (e: Exception) {
        printError("EXAMPLE 1 FAILED", e)
        throw e
    } finally {
        client.close()
    }
}
