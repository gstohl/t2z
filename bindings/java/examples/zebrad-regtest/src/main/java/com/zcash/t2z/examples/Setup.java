/**
 * Setup script to initialize Zebra regtest environment
 *
 * This script:
 * 1. Waits for Zebra to be ready
 * 2. Waits for coinbase maturity (101 blocks)
 * 3. Saves test keypair data for examples
 */
package com.zcash.t2z.examples;

import java.time.Instant;
import java.util.List;

import com.zcash.t2z.TransparentInput;

import static com.zcash.t2z.examples.Keys.*;
import static com.zcash.t2z.examples.Utils.*;

public class Setup {
    public static void main(String[] args) {
        System.out.println("Setting up Zebra regtest environment...\n");

        // Clear spent UTXOs tracker from previous runs
        clearSpentUtxos();
        System.out.println("Cleared spent UTXO tracker\n");

        ZebraClient client = new ZebraClient();

        try {
            // Wait for Zebra to be ready
            System.out.println("Waiting for Zebra...");
            client.waitForReady();
            System.out.println("Zebra is ready\n");

            // Get blockchain info
            ZebraClient.BlockchainInfo info = client.getBlockchainInfo();
            System.out.println("Chain: " + info.chain);
            System.out.println("Current height: " + info.blocks + "\n");

            // Wait for coinbase maturity (101 blocks needed)
            int targetHeight = 101;
            if (info.blocks < targetHeight) {
                System.out.println("Waiting for internal miner to reach height " + targetHeight + "...");
                System.out.println("(Zebra internal miner auto-mines blocks every ~30 seconds)\n");

                int finalHeight = client.waitForBlocks(targetHeight, 600000); // 10 min timeout
                System.out.println("Reached height " + finalHeight + "\n");
            }

            // Use our pre-defined test keypair
            System.out.println("Using test keypair from Keys.java:");
            System.out.println("  Address: " + TEST_KEYPAIR.address);
            System.out.println("  WIF: " + TEST_KEYPAIR.wif + "\n");

            // Get final blockchain info
            ZebraClient.BlockchainInfo finalInfo = client.getBlockchainInfo();
            System.out.println("Final height: " + finalInfo.blocks + "\n");

            // Check for mature coinbase UTXOs
            System.out.println("Checking for mature coinbase UTXOs...\n");
            List<TransparentInput> utxos = getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 10);

            System.out.println("Found " + utxos.size() + " mature coinbase UTXOs for our address");
            for (int i = 0; i < utxos.size(); i++) {
                TransparentInput utxo = utxos.get(i);
                System.out.println("  [" + i + "] " + bytesToHex(utxo.getTxid()).substring(0, 16) +
                    "...:" + utxo.getVout() + " = " + zatoshiToZec(utxo.getAmount()) + " ZEC");
            }

            // Save test keypair data for examples
            TestData testData = new TestData();
            testData.transparent = new TransparentData();
            testData.transparent.address = TEST_KEYPAIR.address;
            testData.transparent.publicKey = bytesToHex(TEST_KEYPAIR.publicKey);
            testData.transparent.privateKey = bytesToHex(TEST_KEYPAIR.privateKey);
            testData.transparent.wif = TEST_KEYPAIR.wif;
            testData.network = info.chain;
            testData.setupHeight = finalInfo.blocks;
            testData.setupAt = Instant.now().toString();

            saveTestData(testData);
            System.out.println("\nSaved test data to data/test-addresses.json");

            System.out.println("\nSetup complete!");
            System.out.println("\nYou can now run the examples:");
            System.out.println("  ./gradlew example1  # Single transparent output (T→T)");
            System.out.println("  ./gradlew example2  # Multiple transparent outputs (T→T×2)");
            System.out.println("  ./gradlew example3  # UTXO consolidation (2 inputs → 1 output)");
            System.out.println("  ./gradlew example5  # Single shielded output (T→Z)");
            System.out.println("  ./gradlew example6  # Multiple shielded outputs (T→Z×2)");
            System.out.println("  ./gradlew example7  # Mixed transparent + shielded (T→T+Z)");
            System.out.println("  ./gradlew example8  # Combine workflow (parallel signing)");
            System.out.println("  ./gradlew example9  # Offline signing (hardware wallet)\n");

        } catch (Exception e) {
            System.out.println("Setup failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.close();
        }
    }
}
