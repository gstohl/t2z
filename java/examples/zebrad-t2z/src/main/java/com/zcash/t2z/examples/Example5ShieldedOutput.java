/**
 * Example 5: Transparent to Shielded (T -> Z)
 *
 * Demonstrates sending to a shielded Orchard address:
 * - Use transparent UTXO as input
 * - Send to a unified address with Orchard receiver
 * - Shows the shielding process
 */
package com.zcash.t2z.examples;

import com.zcash.t2z.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.zcash.t2z.examples.Keys.*;
import static com.zcash.t2z.examples.Utils.*;

public class Example5ShieldedOutput {
    // Deterministic mainnet unified address with Orchard receiver
    // Generated from SpendingKey::from_bytes([42u8; 32])
    private static final String SHIELDED_ADDRESS =
            "u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz";

    public static void main(String[] args) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("  EXAMPLE 5: TRANSPARENT TO SHIELDED (T -> Z)");
        System.out.println("=".repeat(70));
        System.out.println();

        ZebraClient client = new ZebraClient();

        try {
            TestData testData = loadTestData();

            System.out.println("Configuration:");
            System.out.println("  Source (transparent): " + testData.transparent.address);
            System.out.println("  Destination (shielded): " + SHIELDED_ADDRESS.substring(0, 30) + "...");
            System.out.println("  Note: This is an Orchard address (u1... prefix = mainnet)\n");

            System.out.println("Fetching mature coinbase UTXOs...");
            List<TransparentInput> allUtxos = getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 20);

            if (allUtxos.isEmpty()) {
                throw new Exception("No mature UTXOs available.");
            }

            // Collect enough UTXOs for shielded fee
            long totalInput = 0;
            List<TransparentInput> inputs = new ArrayList<>();
            // Calculate fee: 1 input, 1 transparent change, 1 orchard output
            long fee = T2z.calculateFee(1, 1, 1);

            for (TransparentInput utxo : allUtxos) {
                inputs.add(utxo);
                totalInput += utxo.getAmount();
                if (totalInput > fee + 1000) break;
            }

            if (totalInput <= fee) {
                throw new Exception("Not enough funds.");
            }

            System.out.println("Using UTXO: " + zatoshiToZec(totalInput) + " ZEC\n");

            // Send 50% to shielded, rest goes to change
            long paymentAmount = (totalInput - fee) / 2;

            List<Payment> payments = Collections.singletonList(
                    new Payment(SHIELDED_ADDRESS, paymentAmount)
            );

            System.out.println("Creating TransactionRequest for shielded output...");
            try (TransactionRequest request = new TransactionRequest(payments)) {
                // Mainnet is the default, just set target height
                request.setTargetHeight(2_500_000);
                System.out.println("   Using mainnet parameters\n");

                printWorkflowSummary("TRANSACTION SUMMARY - T->Z SHIELDED", inputs, payments, fee);

                System.out.println("KEY DIFFERENCE from T->T:");
                System.out.println("   - Payment address is a unified address (u1...)");
                System.out.println("   - Library creates Orchard actions with zero-knowledge proofs");
                System.out.println("   - Funds become private after this transaction\n");

                System.out.println("1. Proposing transaction...");
                PCZT pczt = T2z.proposeTransaction(inputs, request);
                System.out.println("   PCZT created with Orchard output\n");

                System.out.println("2. Proving transaction (generating Orchard ZK proofs)...");
                System.out.println("   This may take a few seconds...");
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
                System.out.println("   Note: T->Z transactions are larger due to Orchard proofs\n");

                System.out.println("8. Broadcasting transaction...");
                String txid = client.sendRawTransaction(txHex);
                printBroadcastResult(txid, txHex);

                markUtxosSpent(inputs);

                System.out.println("Waiting for confirmation...");
                int currentHeight = client.getBlockchainInfo().blocks;
                client.waitForBlocks(currentHeight + 1, 60000);
                System.out.println("   Confirmed!\n");

                System.out.println("SUCCESS! TXID: " + txid);
                System.out.println("   Shielded " + zatoshiToZec(paymentAmount) + " ZEC to Orchard\n");
            }
        } catch (Exception e) {
            printError("EXAMPLE 5 FAILED", e);
            System.exit(1);
        } finally {
            client.close();
        }
    }
}
