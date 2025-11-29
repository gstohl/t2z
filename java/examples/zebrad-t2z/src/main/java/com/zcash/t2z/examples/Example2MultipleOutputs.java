/**
 * Example 2: Multiple Output Transaction (Transparent -> Transparent x2)
 *
 * Demonstrates sending to multiple transparent addresses in a single transaction:
 * - Use UTXOs as input
 * - Create multiple outputs (2 recipients)
 * - Show how change is handled
 */
package com.zcash.t2z.examples;

import com.zcash.t2z.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.zcash.t2z.examples.Keys.*;
import static com.zcash.t2z.examples.Utils.*;

public class Example2MultipleOutputs {
    public static void main(String[] args) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("  EXAMPLE 2: MULTIPLE OUTPUT TRANSACTION (T -> T x2)");
        System.out.println("=".repeat(70));
        System.out.println();

        ZebraClient client = new ZebraClient();

        try {
            TestData testData = loadTestData();

            // Generate additional destination addresses
            Keys.ZcashKeypair dest1Keypair = keypairFromPrivateKey(hexToBytes(
                    "1111111111111111111111111111111111111111111111111111111111111111"));
            Keys.ZcashKeypair dest2Keypair = keypairFromPrivateKey(hexToBytes(
                    "2222222222222222222222222222222222222222222222222222222222222222"));

            System.out.println("Configuration:");
            System.out.println("  Source: " + testData.transparent.address);
            System.out.println("  Destination 1: " + dest1Keypair.address);
            System.out.println("  Destination 2: " + dest2Keypair.address);

            // Fetch UTXOs
            System.out.println("\nFetching mature coinbase UTXOs...");
            List<TransparentInput> allUtxos = getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 20);

            if (allUtxos.isEmpty()) {
                throw new Exception("No mature UTXOs available.");
            }

            // Collect enough UTXOs
            long totalInput = 0;
            List<TransparentInput> inputs = new ArrayList<>();

            for (TransparentInput utxo : allUtxos) {
                inputs.add(utxo);
                totalInput += utxo.getAmount();
                long fee = estimateFee(inputs.size(), 2);
                if (totalInput > fee + 2000) break;
            }

            long fee = estimateFee(inputs.size(), 2);

            if (totalInput <= fee) {
                throw new Exception("Not enough funds.");
            }

            System.out.println("  Selected " + inputs.size() + " UTXOs totaling: " + zatoshiToZec(totalInput) + " ZEC\n");

            // Split payment between two recipients (30% each)
            long availableForPayments = totalInput - fee;
            long payment1Amount = availableForPayments * 30 / 100;
            long payment2Amount = availableForPayments * 30 / 100;

            List<Payment> payments = Arrays.asList(
                    new Payment(dest1Keypair.address, payment1Amount),
                    new Payment(dest2Keypair.address, payment2Amount)
            );

            System.out.println("Creating TransactionRequest with 2 payments...");
            try (TransactionRequest request = new TransactionRequest(payments)) {
                // Mainnet is the default, just set target height
                request.setTargetHeight(2_500_000);
                System.out.println("   Using mainnet parameters\n");

                printWorkflowSummary("TRANSACTION SUMMARY - TWO RECIPIENTS", inputs, payments, fee);

                long change = totalInput - payment1Amount - payment2Amount - fee;
                System.out.println("Expected change: " + zatoshiToZec(change) + " ZEC\n");

                // Workflow
                System.out.println("1. Proposing transaction...");
                PCZT pczt = T2z.proposeTransaction(inputs, request);
                System.out.println("   PCZT created with 2 outputs + change\n");

                System.out.println("2. Proving transaction...");
                PCZT proved = T2z.proveTransaction(pczt);
                System.out.println("   Proofs generated\n");

                System.out.println("3. Verifying PCZT...");
                T2z.verifyBeforeSigning(proved, request, Collections.emptyList());
                System.out.println("   Verified: both payments present\n");

                // Sign all inputs
                System.out.println("4-6. Signing " + inputs.size() + " input(s)...");
                PCZT currentPczt = proved;
                for (int i = 0; i < inputs.size(); i++) {
                    byte[] sighash = T2z.getSighash(currentPczt, i);
                    byte[] signature = signCompact(sighash, TEST_KEYPAIR);
                    currentPczt = T2z.appendSignature(currentPczt, i, signature);
                    System.out.println("   Input " + i + " signed");
                }
                System.out.println();

                System.out.println("7. Finalizing transaction...");
                byte[] txBytes = T2z.finalizeAndExtract(currentPczt);
                String txHex = bytesToHex(txBytes);
                System.out.println("   Transaction finalized (" + txBytes.length + " bytes)\n");

                System.out.println("8. Broadcasting transaction...");
                String txid = client.sendRawTransaction(txHex);
                printBroadcastResult(txid, txHex);

                markUtxosSpent(inputs);

                System.out.println("Waiting for confirmation (internal miner)...");
                int currentHeight = client.getBlockchainInfo().blocks;
                client.waitForBlocks(currentHeight + 1, 60000);
                System.out.println("   Transaction confirmed!\n");

                System.out.println("Transaction confirmed in the blockchain.");
                System.out.println("  TXID: " + txid);
                System.out.println("  Sent to 2 recipients + change\n");

                System.out.println("EXAMPLE 2 COMPLETED SUCCESSFULLY!\n");
            }
        } catch (Exception e) {
            printError("EXAMPLE 2 FAILED", e);
            System.exit(1);
        } finally {
            client.close();
        }
    }

    private static long estimateFee(int numInputs, int numOutputs) {
        return Math.max(2, numInputs + numOutputs + 1) * 5000L;
    }
}
