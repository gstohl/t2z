/**
 * Example 1: Single Output Transaction (Transparent -> Transparent)
 *
 * Demonstrates the basic t2z workflow using Zebra (no wallet):
 * - Load UTXO and keys from setup data
 * - Create a payment to another transparent address
 * - Propose, sign (client-side), and broadcast the transaction
 */
package com.zcash.t2z.examples;

import com.zcash.t2z.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.zcash.t2z.examples.Keys.*;
import static com.zcash.t2z.examples.Utils.*;

public class Example1SingleOutput {
    public static void main(String[] args) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("  EXAMPLE 1: SINGLE OUTPUT TRANSACTION (Transparent -> Transparent)");
        System.out.println("=".repeat(70));
        System.out.println();

        ZebraClient client = new ZebraClient();

        try {
            // Load test data for address info
            TestData testData = loadTestData();

            System.out.println("Configuration:");
            System.out.println("  Source address: " + testData.transparent.address);

            // Fetch fresh mature coinbase UTXOs from the blockchain
            System.out.println("Fetching mature coinbase UTXOs...");
            List<TransparentInput> allUtxos = getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 20);

            if (allUtxos.isEmpty()) {
                throw new Exception("No mature UTXOs available. Run setup and wait for maturity.");
            }

            // Collect UTXOs until we have enough for fee + minimum payment
            long totalInput = 0;
            List<TransparentInput> inputs = new ArrayList<>();

            for (TransparentInput utxo : allUtxos) {
                inputs.add(utxo);
                totalInput += utxo.getAmount();
                // Calculate fee: N inputs, 2 outputs (1 payment + 1 change), 0 orchard
                long fee = T2z.calculateFee(inputs.size(), 2, 0);
                // Need enough for fee + at least 1000 zatoshi payment
                if (totalInput > fee + 1000) break;
            }

            // Calculate fee: N inputs, 2 outputs (1 payment + 1 change), 0 orchard
            long fee = T2z.calculateFee(inputs.size(), 2, 0);

            if (totalInput <= fee) {
                throw new Exception("Not enough funds: have " + totalInput + " zatoshis, need " + fee + " for fee. Wait for more blocks.");
            }

            System.out.println("  Selected " + inputs.size() + " UTXOs totaling: " + zatoshiToZec(totalInput) + " ZEC");
            System.out.println("  Calculated fee: " + zatoshiToZec(fee) + " ZEC (" + inputs.size() + " inputs)\n");

            // For this example, send back to ourselves (transparent -> transparent)
            String destAddress = testData.transparent.address;
            // Payment = (total - fee) / 2, leaving rest as change
            long paymentAmount = (totalInput - fee) / 2;

            List<Payment> payments = Collections.singletonList(
                    new Payment(destAddress, paymentAmount)
            );

            System.out.println("Creating TransactionRequest...");
            try (TransactionRequest request = new TransactionRequest(payments)) {
                // Get current block height for reference
                ZebraClient.BlockchainInfo info = client.getBlockchainInfo();
                System.out.println("  Current block height: " + info.blocks);

                // Mainnet is the default, just set target height
                request.setTargetHeight(2_500_000);
                System.out.println("  Target height set to 2500000 (mainnet post-NU5)\n");

                // Print workflow summary
                printWorkflowSummary("TRANSACTION SUMMARY", inputs, payments, fee);

                // Step 1: Propose transaction
                System.out.println("1. Proposing transaction...");
                PCZT pczt = T2z.proposeTransaction(inputs, request);
                System.out.println("   PCZT created\n");

                // Step 2: Prove transaction
                System.out.println("2. Proving transaction...");
                PCZT proved = T2z.proveTransaction(pczt);
                System.out.println("   Proofs generated\n");

                // Step 3: Verify before signing
                System.out.println("3. Verifying PCZT before signing...");
                T2z.verifyBeforeSigning(proved, request, Collections.emptyList());
                System.out.println("   Verification passed\n");

                // Step 4-6: Sign all inputs
                System.out.println("4-6. Signing " + inputs.size() + " input(s)...");
                PCZT currentPczt = proved;
                for (int i = 0; i < inputs.size(); i++) {
                    byte[] sighash = T2z.getSighash(currentPczt, i);
                    System.out.println("   Input " + i + " sighash: " + bytesToHex(sighash).substring(0, 16) + "...");
                    byte[] signature = signCompact(sighash, TEST_KEYPAIR);
                    currentPczt = T2z.appendSignature(currentPczt, i, signature);
                    System.out.println("   Input " + i + " signed");
                }
                PCZT signed = currentPczt;
                System.out.println("   All signatures appended\n");

                // Step 7: Finalize
                System.out.println("7. Finalizing transaction...");
                byte[] txBytes = T2z.finalizeAndExtract(signed);
                String txHex = bytesToHex(txBytes);
                System.out.println("   Transaction finalized (" + txBytes.length + " bytes)\n");

                // Step 8: Broadcast
                System.out.println("8. Broadcasting transaction to network...");
                String txid = client.sendRawTransaction(txHex);
                printBroadcastResult(txid, txHex);

                // Mark UTXOs as spent for subsequent examples
                markUtxosSpent(inputs);

                // Wait for confirmation
                System.out.println("Waiting for confirmation...");
                int currentHeight = client.getBlockchainInfo().blocks;
                client.waitForBlocks(currentHeight + 1, 60000);
                System.out.println("   Confirmed!\n");

                System.out.println("SUCCESS! TXID: " + txid + "\n");
                System.exit(0);
            }
        } catch (Exception e) {
            printError("EXAMPLE 1 FAILED", e);
            System.exit(1);
        } finally {
            client.close();
        }
    }

}
