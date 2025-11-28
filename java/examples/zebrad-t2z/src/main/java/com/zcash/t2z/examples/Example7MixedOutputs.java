/**
 * Example 7: Mixed Transparent and Shielded Outputs (T -> T + Z)
 *
 * Demonstrates sending to both transparent and shielded recipients:
 * - Use transparent UTXO as input
 * - Send to one transparent address AND one shielded (Orchard) address
 * - Shows how the library handles mixed output types in a single transaction
 *
 * This is a common real-world scenario where you want to pay someone
 * transparently while also shielding some funds.
 */
package com.zcash.t2z.examples;

import com.zcash.t2z.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.zcash.t2z.examples.Keys.*;
import static com.zcash.t2z.examples.Utils.*;

public class Example7MixedOutputs {
    // Shielded recipient (Orchard address)
    private static final String SHIELDED_ADDRESS =
            "u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz";

    public static void main(String[] args) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("  EXAMPLE 7: MIXED TRANSPARENT + SHIELDED OUTPUTS (T -> T + Z)");
        System.out.println("=".repeat(70));
        System.out.println();

        ZebraClient client = new ZebraClient();

        try {
            TestData testData = loadTestData();

            // Generate transparent recipient address
            Keys.ZcashKeypair transparentRecipient = keypairFromPrivateKey(hexToBytes(
                    "3333333333333333333333333333333333333333333333333333333333333333"));

            System.out.println("Configuration:");
            System.out.println("  Source (transparent): " + testData.transparent.address);
            System.out.println("  Recipient 1 (transparent): " + transparentRecipient.address);
            System.out.println("  Recipient 2 (shielded): " + SHIELDED_ADDRESS.substring(0, 30) + "...");
            System.out.println("  Note: Mixed output types in single transaction\n");

            System.out.println("Fetching mature coinbase UTXOs...");
            List<TransparentInput> allUtxos = getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 20);

            if (allUtxos.isEmpty()) {
                throw new Exception("No mature UTXOs available.");
            }

            long totalInput = 0;
            List<TransparentInput> inputs = new ArrayList<>();
            long fee = 30_000L; // Higher fee for mixed outputs

            for (TransparentInput utxo : allUtxos) {
                inputs.add(utxo);
                totalInput += utxo.getAmount();
                if (totalInput > fee + 2000) break;
            }

            if (totalInput <= fee) {
                throw new Exception("Not enough funds.");
            }

            System.out.println("Using UTXO: " + zatoshiToZec(totalInput) + " ZEC\n");

            // Split: 35% to transparent, 35% to shielded
            long availableForPayments = totalInput - fee;
            long transparentPayment = availableForPayments * 35 / 100;
            long shieldedPayment = availableForPayments * 35 / 100;

            List<Payment> payments = Arrays.asList(
                    new Payment(transparentRecipient.address, transparentPayment),
                    new Payment(SHIELDED_ADDRESS, shieldedPayment)
            );

            System.out.println("Creating TransactionRequest with mixed outputs...");
            try (TransactionRequest request = new TransactionRequest(payments)) {
                request.setUseMainnet(true);
                request.setTargetHeight(2_500_000);
                System.out.println("   Using mainnet branch ID for Zebra regtest\n");

                printWorkflowSummary("TRANSACTION SUMMARY - MIXED T+Z", inputs, payments, fee);

                System.out.println("WHAT THIS DEMONSTRATES:");
                System.out.println("   - Single transparent input");
                System.out.println("   - One transparent output (publicly visible)");
                System.out.println("   - One Orchard output (shielded/private)");
                System.out.println("   - Change returned to source address");
                System.out.println("   - Real-world use case: pay merchant + shield savings\n");

                System.out.println("1. Proposing transaction...");
                PCZT pczt = T2z.proposeTransaction(inputs, request);
                System.out.println("   PCZT created with mixed outputs\n");

                System.out.println("2. Proving transaction (generating Orchard ZK proofs)...");
                System.out.println("   This may take a few seconds...");
                long startTime = System.currentTimeMillis();
                PCZT proved = T2z.proveTransaction(pczt);
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.println("   Proofs generated! (" + (elapsed / 1000.0) + "s)\n");

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
                System.out.println("   Mixed outputs = Orchard proofs + transparent outputs\n");

                System.out.println("8. Broadcasting transaction...");
                String txid = client.sendRawTransaction(txHex);
                printBroadcastResult(txid, txHex);

                markUtxosSpent(inputs);

                System.out.println("Waiting for confirmation (internal miner)...");
                int currentHeight = client.getBlockchainInfo().blocks;
                client.waitForBlocks(currentHeight + 1, 60000);
                System.out.println("   Transaction confirmed!\n");

                System.out.println("=".repeat(70));
                System.out.println("  MIXED OUTPUTS TRANSACTION - SUCCESS");
                System.out.println("=".repeat(70));
                System.out.println("\nTXID: " + txid);
                System.out.println("\nTransaction breakdown:");
                System.out.println("  - Transparent input: " + zatoshiToZec(totalInput) + " ZEC");
                System.out.println("  - Transparent output: " + zatoshiToZec(transparentPayment) + " ZEC (publicly visible)");
                System.out.println("  - Shielded output: " + zatoshiToZec(shieldedPayment) + " ZEC (private)");
                System.out.println("  - Change: ~" + zatoshiToZec(totalInput - transparentPayment - shieldedPayment - fee) + " ZEC");
                System.out.println("  - Fee: " + zatoshiToZec(fee) + " ZEC");
                System.out.println("\nPrivacy analysis:");
                System.out.println("   - Transparent recipient: amount and address are PUBLIC");
                System.out.println("   - Shielded recipient: amount and address are PRIVATE");
                System.out.println("   - Observer can see: input amount, transparent output");
                System.out.println("   - Observer cannot see: shielded amount, shielded recipient");
                System.out.println("\nUse cases for mixed transactions:");
                System.out.println("   - Pay merchant (transparent) + save remainder (shielded)");
                System.out.println("   - Exchange withdrawal (transparent) + personal wallet (shielded)");
                System.out.println("   - Tax-reportable payment + private savings\n");

                System.out.println("EXAMPLE 7 COMPLETED SUCCESSFULLY!\n");
            }
        } catch (Exception e) {
            printError("EXAMPLE 7 FAILED", e);
            System.exit(1);
        } finally {
            client.close();
        }
    }
}
