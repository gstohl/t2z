/**
 * Example 9: Offline Signing (Hardware Wallet / Air-Gapped Device Simulation)
 *
 * Demonstrates the serialize/parse workflow for offline signing:
 * - Online device: Creates PCZT, serializes it, outputs sighash
 * - Offline device: Signs the sighash (never sees full transaction)
 * - Online device: Parses PCZT, appends signature, finalizes
 *
 * Use case: Hardware wallets, air-gapped signing devices, or any scenario
 * where the signing key never touches an internet-connected device.
 */
package com.zcash.t2z.examples

import com.zcash.t2z.*
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println()
    println("=".repeat(70))
    println("  EXAMPLE 9: OFFLINE SIGNING (Hardware Wallet Simulation)")
    println("=".repeat(70))
    println()
    println("This example demonstrates the serialize/parse workflow for offline signing.")
    println("The private key NEVER touches the online device!")
    println()

    val client = ZebraClient()

    try {
        val keypair = TEST_KEYPAIR

        println("Configuration:")
        println("  Public key (online):  ${bytesToHex(keypair.publicKey).take(32)}...")
        println("  Private key (OFFLINE): ${bytesToHex(keypair.privateKey).take(16)}...")
        println("  Address: ${keypair.address}")

        // Fetch mature coinbase UTXOs
        println("\nFetching mature coinbase UTXOs...")
        val utxos = getMatureCoinbaseUtxos(client, keypair, 5)

        if (utxos.isEmpty()) {
            throw Exception("Need at least 1 mature UTXO. Run setup and wait for maturity.")
        }

        val input = utxos.first()
        val totalInput = input.amount
        println("  Selected UTXO: ${zatoshiToZec(totalInput)} ZEC\n")

        // Send back to ourselves
        val destAddress = keypair.address
        val fee = calculateFee(1, 2, 0)
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
            listOf(input),
            payments.map { it.address to it.amount },
            fee.toULong()
        )

        // ============================================================
        // ONLINE DEVICE: Build transaction, extract sighash
        // ============================================================
        println("=".repeat(70))
        println("  ONLINE DEVICE - Transaction Builder")
        println("=".repeat(70))
        println()

        println("1. Proposing transaction...")
        val pczt = proposeTransaction(listOf(input), request)
        println("   PCZT created")

        println("\n2. Proving transaction...")
        val proved = proveTransaction(pczt)
        println("   Proofs generated")

        println("\n3. Serializing PCZT for storage...")
        val pcztBytes = serializePczt(proved)
        println("   PCZT serialized: ${pcztBytes.size} bytes")

        println("\n4. Getting sighash for offline signing...")
        val sighash = getSighash(proved, 0)
        val sighashHex = bytesToHex(sighash)
        println("   Sighash: $sighashHex")

        println("\n   >>> Transfer this sighash to the OFFLINE device <<<")
        println()

        // ============================================================
        // OFFLINE DEVICE: Sign the sighash (air-gapped)
        // ============================================================
        println("=".repeat(70))
        println("  OFFLINE DEVICE - Air-Gapped Signer")
        println("=".repeat(70))
        println()

        println("1. Receiving sighash...")
        println("   Sighash: $sighashHex")

        println("\n2. Signing with private key (NEVER leaves this device)...")
        val signature = signCompact(sighash, keypair)
        val signatureHex = bytesToHex(signature)
        println("   Signature: $signatureHex")

        println("\n   >>> Transfer this signature back to the ONLINE device <<<")
        println()

        // ============================================================
        // ONLINE DEVICE: Append signature and finalize
        // ============================================================
        println("=".repeat(70))
        println("  ONLINE DEVICE - Finalization")
        println("=".repeat(70))
        println()

        println("1. Parsing stored PCZT...")
        val loadedPczt = parsePczt(pcztBytes)
        println("   PCZT restored from bytes")

        println("\n2. Receiving signature from offline device...")
        println("   Signature: ${signatureHex.take(32)}...")

        println("\n3. Appending signature to PCZT...")
        val signed = appendSignature(loadedPczt, 0, signature)
        println("   Signature appended")

        println("\n4. Finalizing transaction...")
        val txBytes = finalizeAndExtract(signed)
        val txHex = bytesToHex(txBytes)
        println("   Transaction finalized (${txBytes.size} bytes)")

        println("\n5. Broadcasting transaction to network...")
        val txid = client.sendRawTransaction(txHex)
        printBroadcastResult(txid, txHex)

        // Mark UTXOs as spent
        markUtxosSpent(listOf(input))

        // Wait for confirmation
        println("Waiting for confirmation...")
        val currentHeight = client.getBlockchainInfo().blocks
        client.waitForBlocks(currentHeight + 1, 60000)
        println("   Confirmed!\n")

        println("=".repeat(70))
        println("  SUCCESS!")
        println("=".repeat(70))
        println("\nTXID: $txid")
        println("\nKey security properties:")
        println("  - Private key NEVER touched the online device")
        println("  - PCZT can be serialized/parsed for transport")
        println("  - Sighash is safe to transfer (reveals no private data)")
        println()

        // Cleanup
        request.close()

    } catch (e: Exception) {
        printError("EXAMPLE 9 FAILED", e)
        System.exit(1)
    } finally {
        client.close()
    }
}
