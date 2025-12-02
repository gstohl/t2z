/**
 * Example 6: Multiple Shielded Outputs (T -> Z x2)
 *
 * Demonstrates sending to multiple shielded recipients:
 * - Use transparent UTXO as input
 * - Send to two different unified addresses with Orchard receivers
 * - Shows how the library handles multiple Orchard actions
 */
package com.zcash.t2z.examples;

import com.zcash.t2z.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.zcash.t2z.examples.Keys.*;
import static com.zcash.t2z.examples.Utils.*;

public class Example6MultipleShielded {
    // Using same address twice for simplicity - in real usage these would be different
    private static final String SHIELDED_ADDRESS_1 =
            "u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz";
    private static final String SHIELDED_ADDRESS_2 =
            "u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz";

    public static void main(String[] args) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("  EXAMPLE 6: MULTIPLE SHIELDED OUTPUTS (T -> Z x2)");
        System.out.println("=".repeat(70));
        System.out.println();

        ZebraClient client = new ZebraClient();

        try {
            TestData testData = loadTestData();

            System.out.println("Configuration:");
            System.out.println("  Source (transparent): " + testData.transparent.address);
            System.out.println("  Recipient 1 (shielded): " + SHIELDED_ADDRESS_1.substring(0, 25) + "...");
            System.out.println("  Recipient 2 (shielded): " + SHIELDED_ADDRESS_2.substring(0, 25) + "...");
            System.out.println("  Note: Both are Orchard addresses (u1... prefix)\n");

            System.out.println("Fetching mature coinbase UTXOs...");
            List<TransparentInput> allUtxos = getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 20);

            if (allUtxos.isEmpty()) {
                throw new Exception("No mature UTXOs available.");
            }

            long totalInput = 0;
            List<TransparentInput> inputs = new ArrayList<>();
            // Calculate fee: 1 input, 1 transparent change, 2 orchard outputs
            long fee = T2z.calculateFee(1, 1, 2);

            for (TransparentInput utxo : allUtxos) {
                inputs.add(utxo);
                totalInput += utxo.getAmount();
                if (totalInput > fee + 2000) break;
            }

            if (totalInput <= fee) {
                throw new Exception("Not enough funds.");
            }

            System.out.println("Using UTXO: " + zatoshiToZec(totalInput) + " ZEC\n");

            // Split: 33% each to two shielded outputs
            long availableForPayments = totalInput - fee;
            long payment1Amount = availableForPayments / 3;
            long payment2Amount = availableForPayments / 3;

            List<Payment> payments = Arrays.asList(
                    new Payment(SHIELDED_ADDRESS_1, payment1Amount),
                    new Payment(SHIELDED_ADDRESS_2, payment2Amount)
            );

            System.out.println("Creating TransactionRequest with 2 shielded outputs...");
            try (TransactionRequest request = new TransactionRequest(payments)) {
                // Mainnet is the default, just set target height
                request.setTargetHeight(2_500_000);
                System.out.println("   Using mainnet parameters\n");

                printWorkflowSummary("TRANSACTION SUMMARY - MULTIPLE SHIELDED", inputs, payments, fee);

                System.out.println("WHAT THIS DEMONSTRATES:");
                System.out.println("   - Single transparent input");
                System.out.println("   - Two Orchard outputs (shielded recipients)");
                System.out.println("   - Library creates multiple Orchard actions");
                System.out.println("   - Each recipient receives private funds\n");

                System.out.println("1. Proposing transaction...");
                PCZT pczt = T2z.proposeTransaction(inputs, request);
                System.out.println("   PCZT created with multiple Orchard outputs\n");

                System.out.println("2. Proving transaction (generating Orchard ZK proofs)...");
                System.out.println("   This takes longer with multiple outputs...");
                long startTime = System.currentTimeMillis();
                PCZT proved = T2z.proveTransaction(pczt);
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.println("   Orchard proofs generated! (" + (elapsed / 1000.0) + "s)\n");

                System.out.println("3. Verifying PCZT...");
                T2z.verifyBeforeSigning(proved, request, Collections.emptyList());
                System.out.println("   Verification passed\n");

                System.out.println("4. Getting sighash...");
                byte[] sighash = T2z.getSighash(proved, 0);
                System.out.println("   Sighash: " + bytesToHex(sighash).substring(0, 32) + "...\n");

                System.out.println("5. Signing transaction (client-side)...");
                byte[] signature = signCompact(sighash, TEST_KEYPAIR);
                System.out.println("   Signature: " + bytesToHex(signature).substring(0, 32) + "...\n");

                System.out.println("6. Appending signature...");
                PCZT signed = T2z.appendSignature(proved, 0, signature);
                System.out.println("   Signature appended\n");

                System.out.println("7. Finalizing transaction...");
                byte[] txBytes = T2z.finalizeAndExtract(signed);
                String txHex = bytesToHex(txBytes);
                System.out.println("   Transaction finalized (" + txBytes.length + " bytes)");
                System.out.println("   Multiple Orchard outputs = larger transaction\n");

                System.out.println("8. Broadcasting transaction...");
                String txid = client.sendRawTransaction(txHex);
                printBroadcastResult(txid, txHex);

                markUtxosSpent(inputs);

                System.out.println("Waiting for confirmation...");
                int currentHeight = client.getBlockchainInfo().blocks;
                client.waitForBlocks(currentHeight + 1, 60000);
                System.out.println("   Confirmed!\n");

                System.out.println("SUCCESS! TXID: " + txid);
                System.out.println("   Shielded to 2 Orchard recipients\n");
            }
        } catch (Exception e) {
            printError("EXAMPLE 6 FAILED", e);
            System.exit(1);
        } finally {
            client.close();
        }
    }
}
