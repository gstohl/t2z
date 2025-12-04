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
package com.zcash.t2z.examples;

import com.zcash.t2z.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.zcash.t2z.examples.Keys.*;
import static com.zcash.t2z.examples.Utils.*;

public class Example8CombineWorkflow {
    public static void main(String[] args) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("  EXAMPLE 8: COMBINE WORKFLOW (Parallel Signing)");
        System.out.println("=".repeat(70));
        System.out.println();
        System.out.println("This example demonstrates the combine() function for parallel signing.");
        System.out.println("In a real scenario, each signer would be on a different device.");
        System.out.println();

        ZebraClient client = new ZebraClient();

        try {
            ZcashKeypair keypair = TEST_KEYPAIR;

            System.out.println("Configuration:");
            System.out.println("  Address: " + keypair.address);

            // Fetch mature coinbase UTXOs
            System.out.println("\nFetching mature coinbase UTXOs...");
            List<TransparentInput> utxos = getMatureCoinbaseUtxos(client, keypair, 8);

            if (utxos.size() < 3) {
                throw new Exception("Need at least 3 mature UTXOs. Run setup and wait for maturity.");
            }

            // Use 3 inputs to demonstrate combine with multiple signers
            List<TransparentInput> inputs = utxos.subList(0, 3);
            long totalInput = 0;
            for (TransparentInput input : inputs) {
                totalInput += input.getAmount();
            }
            System.out.println("  Selected " + inputs.size() + " UTXOs totaling: " + zatoshiToZec(totalInput) + " ZEC\n");

            // Send back to ourselves
            String destAddress = keypair.address;
            long fee = T2z.calculateFee(inputs.size(), 2, 0);
            long paymentAmount = totalInput / 2;

            List<Payment> payments = Collections.singletonList(new Payment(destAddress, paymentAmount));

            System.out.println("Creating TransactionRequest...");
            try (TransactionRequest request = new TransactionRequest(payments)) {
                ZebraClient.BlockchainInfo info = client.getBlockchainInfo();
                System.out.println("  Current block height: " + info.blocks);

                int targetHeight = 2_500_000;
                request.setTargetHeight(targetHeight);
                System.out.println("  Target height set to " + targetHeight + "\n");

                printWorkflowSummary("TRANSACTION SUMMARY", inputs, payments, fee);

                System.out.println("--- PARALLEL SIGNING WORKFLOW ---\n");

                // Step 1: Create and prove the PCZT
                System.out.println("1. Creating and proving PCZT...");
                PCZT pczt = T2z.proposeTransaction(inputs, request);
                PCZT proved = T2z.proveTransaction(pczt);
                System.out.println("   PCZT created and proved\n");

                // Step 2: Serialize the proved PCZT
                System.out.println("2. Serializing PCZT for distribution to signers...");
                byte[] pcztBytes = T2z.serializePczt(proved);
                System.out.println("   Serialized PCZT: " + pcztBytes.length + " bytes\n");

                // Step 3: Simulate parallel signing by different parties
                System.out.println("3. Simulating parallel signing by 3 different parties...\n");

                // Signer A signs input 0
                System.out.println("   Signer A: Signing input 0...");
                PCZT pcztA = T2z.parsePczt(pcztBytes);
                byte[] sighashA = T2z.getSighash(pcztA, 0);
                byte[] signatureA = signCompact(sighashA, keypair);
                PCZT signedA = T2z.appendSignature(pcztA, 0, signatureA);
                byte[] bytesA = T2z.serializePczt(signedA);
                System.out.println("   Signer A: Done (signed input 0)\n");

                // Signer B signs input 1
                System.out.println("   Signer B: Signing input 1...");
                PCZT pcztB = T2z.parsePczt(pcztBytes);
                byte[] sighashB = T2z.getSighash(pcztB, 1);
                byte[] signatureB = signCompact(sighashB, keypair);
                PCZT signedB = T2z.appendSignature(pcztB, 1, signatureB);
                byte[] bytesB = T2z.serializePczt(signedB);
                System.out.println("   Signer B: Done (signed input 1)\n");

                // Signer C signs input 2
                System.out.println("   Signer C: Signing input 2...");
                PCZT pcztC = T2z.parsePczt(pcztBytes);
                byte[] sighashC = T2z.getSighash(pcztC, 2);
                byte[] signatureC = signCompact(sighashC, keypair);
                PCZT signedC = T2z.appendSignature(pcztC, 2, signatureC);
                byte[] bytesC = T2z.serializePczt(signedC);
                System.out.println("   Signer C: Done (signed input 2)\n");

                // Step 4: Combine all partially-signed PCZTs
                System.out.println("4. Combining partially-signed PCZTs...");
                PCZT combinedA = T2z.parsePczt(bytesA);
                PCZT combinedB = T2z.parsePczt(bytesB);
                PCZT combinedC = T2z.parsePczt(bytesC);

                PCZT fullySignedPczt = T2z.combine(Arrays.asList(combinedA, combinedB, combinedC));
                System.out.println("   All signatures combined into single PCZT\n");

                // Step 5: Finalize
                System.out.println("5. Finalizing transaction...");
                byte[] txBytes = T2z.finalizeAndExtract(fullySignedPczt);
                String txHex = bytesToHex(txBytes);
                System.out.println("   Transaction finalized (" + txBytes.length + " bytes)\n");

                // Step 6: Broadcast
                System.out.println("6. Broadcasting transaction to network...");
                String txid = client.sendRawTransaction(txHex);
                printBroadcastResult(txid, txHex);

                // Mark UTXOs as spent
                markUtxosSpent(inputs);

                // Wait for confirmation
                System.out.println("Waiting for confirmation...");
                int currentHeight = client.getBlockchainInfo().blocks;
                client.waitForBlocks(currentHeight + 1, 60000);
                System.out.println("   Confirmed!\n");

                System.out.println("SUCCESS! TXID: " + txid);
                System.out.println("\nThe combine() function merged signatures from 3 independent signers.\n");
                System.exit(0);
            }
        } catch (Exception e) {
            printError("EXAMPLE 8 FAILED", e);
            System.exit(1);
        } finally {
            client.close();
        }
    }
}
