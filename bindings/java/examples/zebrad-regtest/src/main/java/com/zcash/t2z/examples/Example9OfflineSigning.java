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
package com.zcash.t2z.examples;

import com.zcash.t2z.*;

import java.util.Collections;
import java.util.List;

import static com.zcash.t2z.examples.Keys.*;
import static com.zcash.t2z.examples.Utils.*;

public class Example9OfflineSigning {
    public static void main(String[] args) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("  EXAMPLE 9: OFFLINE SIGNING (Hardware Wallet Simulation)");
        System.out.println("=".repeat(70));
        System.out.println();
        System.out.println("This example demonstrates the serialize/parse workflow for offline signing.");
        System.out.println("The private key NEVER touches the online device!");
        System.out.println();

        ZebraClient client = new ZebraClient();

        try {
            ZcashKeypair keypair = TEST_KEYPAIR;

            System.out.println("Configuration:");
            System.out.println("  Public key (online):  " + bytesToHex(keypair.publicKey).substring(0, 32) + "...");
            System.out.println("  Private key (OFFLINE): " + bytesToHex(keypair.privateKey).substring(0, 16) + "...");
            System.out.println("  Address: " + keypair.address);

            // Fetch mature coinbase UTXOs
            System.out.println("\nFetching mature coinbase UTXOs...");
            List<TransparentInput> utxos = getMatureCoinbaseUtxos(client, keypair, 5);

            if (utxos.isEmpty()) {
                throw new Exception("Need at least 1 mature UTXO. Run setup and wait for maturity.");
            }

            TransparentInput input = utxos.get(0);
            long totalInput = input.getAmount();
            System.out.println("  Selected UTXO: " + zatoshiToZec(totalInput) + " ZEC\n");

            // Send back to ourselves
            String destAddress = keypair.address;
            long fee = T2z.calculateFee(1, 2, 0);
            long paymentAmount = totalInput / 2;

            List<Payment> payments = Collections.singletonList(new Payment(destAddress, paymentAmount));

            System.out.println("Creating TransactionRequest...");
            try (TransactionRequest request = new TransactionRequest(payments)) {
                ZebraClient.BlockchainInfo info = client.getBlockchainInfo();
                System.out.println("  Current block height: " + info.blocks);

                int targetHeight = 2_500_000;
                request.setTargetHeight(targetHeight);
                System.out.println("  Target height set to " + targetHeight + "\n");

                printWorkflowSummary("TRANSACTION SUMMARY", Collections.singletonList(input), payments, fee);

                // ============================================================
                // ONLINE DEVICE: Build transaction, extract sighash
                // ============================================================
                System.out.println("=".repeat(70));
                System.out.println("  ONLINE DEVICE - Transaction Builder");
                System.out.println("=".repeat(70));
                System.out.println();

                System.out.println("1. Proposing transaction...");
                PCZT pczt = T2z.proposeTransaction(Collections.singletonList(input), request);
                System.out.println("   PCZT created");

                System.out.println("\n2. Proving transaction...");
                PCZT proved = T2z.proveTransaction(pczt);
                System.out.println("   Proofs generated");

                System.out.println("\n3. Serializing PCZT for storage...");
                byte[] pcztBytes = T2z.serializePczt(proved);
                System.out.println("   PCZT serialized: " + pcztBytes.length + " bytes");

                System.out.println("\n4. Getting sighash for offline signing...");
                byte[] sighash = T2z.getSighash(proved, 0);
                String sighashHex = bytesToHex(sighash);
                System.out.println("   Sighash: " + sighashHex);

                System.out.println("\n   >>> Transfer this sighash to the OFFLINE device <<<");
                System.out.println();

                // ============================================================
                // OFFLINE DEVICE: Sign the sighash (air-gapped)
                // ============================================================
                System.out.println("=".repeat(70));
                System.out.println("  OFFLINE DEVICE - Air-Gapped Signer");
                System.out.println("=".repeat(70));
                System.out.println();

                System.out.println("1. Receiving sighash...");
                System.out.println("   Sighash: " + sighashHex);

                System.out.println("\n2. Signing with private key (NEVER leaves this device)...");
                byte[] signature = signCompact(sighash, keypair);
                String signatureHex = bytesToHex(signature);
                System.out.println("   Signature: " + signatureHex);

                System.out.println("\n   >>> Transfer this signature back to the ONLINE device <<<");
                System.out.println();

                // ============================================================
                // ONLINE DEVICE: Append signature and finalize
                // ============================================================
                System.out.println("=".repeat(70));
                System.out.println("  ONLINE DEVICE - Finalization");
                System.out.println("=".repeat(70));
                System.out.println();

                System.out.println("1. Parsing stored PCZT...");
                PCZT loadedPczt = T2z.parsePczt(pcztBytes);
                System.out.println("   PCZT restored from bytes");

                System.out.println("\n2. Receiving signature from offline device...");
                System.out.println("   Signature: " + signatureHex.substring(0, 32) + "...");

                System.out.println("\n3. Appending signature to PCZT...");
                PCZT signed = T2z.appendSignature(loadedPczt, 0, signature);
                System.out.println("   Signature appended");

                System.out.println("\n4. Finalizing transaction...");
                byte[] txBytes = T2z.finalizeAndExtract(signed);
                String txHex = bytesToHex(txBytes);
                System.out.println("   Transaction finalized (" + txBytes.length + " bytes)");

                System.out.println("\n5. Broadcasting transaction to network...");
                String txid = client.sendRawTransaction(txHex);
                printBroadcastResult(txid, txHex);

                // Mark UTXOs as spent
                markUtxosSpent(Collections.singletonList(input));

                // Wait for confirmation
                System.out.println("Waiting for confirmation...");
                int currentHeight = client.getBlockchainInfo().blocks;
                client.waitForBlocks(currentHeight + 1, 60000);
                System.out.println("   Confirmed!\n");

                System.out.println("=".repeat(70));
                System.out.println("  SUCCESS!");
                System.out.println("=".repeat(70));
                System.out.println("\nTXID: " + txid);
                System.out.println("\nKey security properties:");
                System.out.println("  - Private key NEVER touched the online device");
                System.out.println("  - PCZT can be serialized/parsed for transport");
                System.out.println("  - Sighash is safe to transfer (reveals no private data)");
                System.out.println();
                System.exit(0);
            }
        } catch (Exception e) {
            printError("EXAMPLE 9 FAILED", e);
            System.exit(1);
        } finally {
            client.close();
        }
    }
}
