/**
 * Setup script to initialize Zebra regtest environment
 *
 * This script:
 * 1. Waits for Zebra to be ready
 * 2. Waits for coinbase maturity (101 blocks)
 * 3. Saves test keypair data for examples
 */
package com.zcash.t2z.examples

import kotlinx.coroutines.runBlocking
import java.time.Instant

fun main() = runBlocking {
    println("Setting up Zebra regtest environment...\n")

    // Clear spent UTXOs tracker from previous runs
    clearSpentUtxos()
    println("Cleared spent UTXO tracker\n")

    val client = ZebraClient()

    try {
        // Wait for Zebra to be ready
        println("Waiting for Zebra...")
        client.waitForReady()
        println("Zebra is ready\n")

        // Get blockchain info
        val info = client.getBlockchainInfo()
        println("Chain: ${info.chain}")
        println("Current height: ${info.blocks}\n")

        // Wait for coinbase maturity (101 blocks needed)
        val targetHeight = 101
        if (info.blocks < targetHeight) {
            println("Waiting for internal miner to reach height $targetHeight...")
            println("(Zebra internal miner auto-mines blocks every ~30 seconds)\n")

            val finalHeight = client.waitForBlocks(targetHeight, 600000) // 10 min timeout
            println("Reached height $finalHeight\n")
        }

        // Use our pre-defined test keypair
        println("Using test keypair from Keys.kt:")
        println("  Address: ${TEST_KEYPAIR.address}")
        println("  WIF: ${TEST_KEYPAIR.wif}\n")

        // Get final blockchain info
        val finalInfo = client.getBlockchainInfo()
        println("Final height: ${finalInfo.blocks}\n")

        // Check for mature coinbase UTXOs
        println("Checking for mature coinbase UTXOs...\n")
        val utxos = getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 10)

        println("Found ${utxos.size} mature coinbase UTXOs for our address")
        utxos.forEachIndexed { i, utxo ->
            println("  [$i] ${bytesToHex(utxo.txid).take(16)}...:${utxo.vout} = ${zatoshiToZec(utxo.amount)} ZEC")
        }

        // Save test keypair data for examples
        val testData = TestData(
            transparent = TransparentData(
                address = TEST_KEYPAIR.address,
                publicKey = bytesToHex(TEST_KEYPAIR.publicKey),
                privateKey = bytesToHex(TEST_KEYPAIR.privateKey),
                wif = TEST_KEYPAIR.wif
            ),
            network = info.chain,
            setupHeight = finalInfo.blocks,
            setupAt = Instant.now().toString()
        )

        saveTestData(testData)
        println("\nSaved test data to data/test-addresses.json")

        println("\nSetup complete!")
        println("\nYou can now run the examples:")
        println("  ./gradlew example1  # Single transparent output (T→T)")
        println("  ./gradlew example2  # Multiple transparent outputs (T→T×2)")
        println("  ./gradlew example3  # UTXO consolidation (2 inputs → 1 output)")
        println("  ./gradlew example5  # Single shielded output (T→Z)")
        println("  ./gradlew example6  # Multiple shielded outputs (T→Z×2)")
        println("  ./gradlew example7  # Mixed transparent + shielded (T→T+Z)\n")

    } catch (e: Exception) {
        println("Setup failed: ${e.message}")
        e.printStackTrace()
    } finally {
        client.close()
    }
}
