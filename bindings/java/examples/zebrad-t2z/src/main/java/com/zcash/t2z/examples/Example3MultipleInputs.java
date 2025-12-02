/**
 * Example 3: Multiple Inputs Transaction (UTXO Consolidation)
 *
 * Demonstrates consolidating multiple UTXOs into a single output:
 * - Use multiple UTXOs as inputs
 * - Sign each input with its own sighash
 * - Create single output (consolidation)
 */
package com.zcash.t2z.examples;

import com.zcash.t2z.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.zcash.t2z.examples.Keys.*;
import static com.zcash.t2z.examples.Utils.*;

public class Example3MultipleInputs {
    public static void main(String[] args) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("  EXAMPLE 3: MULTIPLE INPUTS TRANSACTION (UTXO Consolidation)");
        System.out.println("=".repeat(70));
        System.out.println();

        ZebraClient client = new ZebraClient();

        try {
            TestData testData = loadTestData();

            System.out.println("Configuration:");
            System.out.println("  Address: " + testData.transparent.address);

            System.out.println("\nFetching mature coinbase UTXOs...");
            List<TransparentInput> allUtxos = getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 10);

            if (allUtxos.size() < 2) {
                throw new Exception("Need at least 2 mature UTXOs for consolidation.");
            }

            // Use exactly 2 inputs for this example
            List<TransparentInput> inputs = new ArrayList<>(allUtxos.subList(0, 2));
            long totalInput = inputs.stream().mapToLong(TransparentInput::getAmount).sum();

            System.out.println("Found " + allUtxos.size() + " mature UTXO(s):");
            for (int i = 0; i < inputs.size(); i++) {
                System.out.println("  [" + i + "] " + zatoshiToZec(inputs.get(i).getAmount()) + " ZEC");
            }
            System.out.println("  Total: " + zatoshiToZec(totalInput) + " ZEC");

            System.out.println("\nUsing 2 inputs totaling " + zatoshiToZec(totalInput) + " ZEC");

            // Calculate fee: 2 inputs, 1 output, 0 orchard
            long fee = T2z.calculateFee(inputs.size(), 1, 0);
            long outputAmount = totalInput - fee;

            List<Payment> payments = Collections.singletonList(
                    new Payment(testData.transparent.address, outputAmount)
            );

            System.out.println("\nCreating TransactionRequest...");
            try (TransactionRequest request = new TransactionRequest(payments)) {
                // Mainnet is the default, just set target height
                request.setTargetHeight(2_500_000);
                System.out.println("   Using mainnet parameters\n");

                printWorkflowSummary("TRANSACTION SUMMARY - UTXO CONSOLIDATION", inputs, payments, fee);

                System.out.println("1. Proposing transaction with multiple inputs...");
                PCZT pczt = T2z.proposeTransaction(inputs, request);
                System.out.println("   PCZT created with 2 inputs\n");

                System.out.println("2. Proving transaction...");
                PCZT proved = T2z.proveTransaction(pczt);
                System.out.println("   Proofs generated\n");

                System.out.println("3. Verifying PCZT...");
                T2z.verifyBeforeSigning(proved, request, Collections.emptyList());
                System.out.println("   Verification passed\n");

                System.out.println("4. Getting sighashes and signing each input...");
                PCZT currentPczt = proved;
                for (int i = 0; i < inputs.size(); i++) {
                    byte[] sighash = T2z.getSighash(currentPczt, i);
                    System.out.println("   Input " + i + ":");
                    System.out.println("     Sighash: " + bytesToHex(sighash).substring(0, 24) + "...");
                    byte[] signature = signCompact(sighash, TEST_KEYPAIR);
                    System.out.println("     Signature: " + bytesToHex(signature).substring(0, 24) + "...");
                    currentPczt = T2z.appendSignature(currentPczt, i, signature);
                    System.out.println("     Signature appended");
                }
                System.out.println();

                System.out.println("5. Finalizing transaction...");
                byte[] txBytes = T2z.finalizeAndExtract(currentPczt);
                String txHex = bytesToHex(txBytes);
                System.out.println("   Transaction finalized (" + txBytes.length + " bytes)\n");

                System.out.println("6. Broadcasting transaction...");
                String txid = client.sendRawTransaction(txHex);
                printBroadcastResult(txid, txHex);

                markUtxosSpent(inputs);

                System.out.println("Waiting for confirmation (internal miner)...");
                int currentHeight = client.getBlockchainInfo().blocks;
                client.waitForBlocks(currentHeight + 1, 60000);
                System.out.println("   Confirmed!\n");

                System.out.println("SUCCESS! TXID: " + txid);
                System.out.println("   " + inputs.size() + " UTXOs consolidated into 1\n");
            }
        } catch (Exception e) {
            printError("EXAMPLE 3 FAILED", e);
            System.exit(1);
        } finally {
            client.close();
        }
    }
}
