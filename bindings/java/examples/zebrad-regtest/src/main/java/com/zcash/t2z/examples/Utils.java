/**
 * Utility functions for t2z examples
 */
package com.zcash.t2z.examples;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zcash.t2z.Payment;
import com.zcash.t2z.TransparentInput;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

import static com.zcash.t2z.examples.Keys.*;

public class Utils {
    private static final File DATA_DIR = new File(System.getProperty("user.dir"), "data");
    private static final File SPENT_UTXOS_FILE = new File(DATA_DIR, "spent-utxos.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Load spent UTXOs from file.
     */
    public static Set<String> loadSpentUtxos() {
        try {
            if (!SPENT_UTXOS_FILE.exists()) {
                return new HashSet<>();
            }
            String content = Files.readString(SPENT_UTXOS_FILE.toPath());
            String[] array = gson.fromJson(content, String[].class);
            return new HashSet<>(Arrays.asList(array != null ? array : new String[0]));
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    /**
     * Save spent UTXOs to file.
     */
    public static void saveSpentUtxos(Set<String> spent) {
        try {
            DATA_DIR.mkdirs();
            Files.writeString(SPENT_UTXOS_FILE.toPath(), gson.toJson(spent.toArray(new String[0])));
        } catch (Exception e) {
            System.err.println("Warning: Could not save spent UTXOs: " + e.getMessage());
        }
    }

    /**
     * Mark UTXOs as spent (call after successful broadcast).
     */
    public static void markUtxosSpent(List<TransparentInput> inputs) {
        Set<String> spent = loadSpentUtxos();
        for (TransparentInput input : inputs) {
            String key = bytesToHex(input.getTxid()) + ":" + input.getVout();
            spent.add(key);
        }
        saveSpentUtxos(spent);
    }

    /**
     * Clear spent UTXOs tracking (call on setup).
     */
    public static void clearSpentUtxos() {
        saveSpentUtxos(new HashSet<>());
    }

    /**
     * Convert zatoshis to ZEC string.
     */
    public static String zatoshiToZec(long zatoshi) {
        return String.format("%.8f", zatoshi / 100_000_000.0);
    }

    /**
     * Reverse a hex string (for txid endianness).
     */
    public static String reverseHex(String hex) {
        byte[] bytes = hexToBytes(hex);
        byte[] reversed = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            reversed[i] = bytes[bytes.length - 1 - i];
        }
        return bytesToHex(reversed);
    }

    /**
     * Parse Zcash transaction hex to extract outputs.
     */
    private static List<TxOutput> parseTxOutputs(String txHex) {
        byte[] tx = hexToBytes(txHex);
        int offset = 0;

        // Skip header (4 bytes version + 4 bytes version group id)
        offset += 8;

        // Read vin count (varint - simplified, assuming single byte)
        int vinCount = tx[offset] & 0xFF;
        offset += 1;

        // Skip all inputs
        for (int i = 0; i < vinCount; i++) {
            offset += 32; // prev txid
            offset += 4;  // prev vout
            int scriptLen = tx[offset] & 0xFF;
            offset += 1 + scriptLen; // script length + script
            offset += 4; // sequence
        }

        // Read vout count
        int voutCount = tx[offset] & 0xFF;
        offset += 1;

        List<TxOutput> outputs = new ArrayList<>();

        for (int i = 0; i < voutCount; i++) {
            // Read value (8 bytes, little-endian)
            long value = 0;
            for (int j = 0; j < 8; j++) {
                value |= ((long) (tx[offset + j] & 0xFF)) << (j * 8);
            }
            offset += 8;

            // Read script length and script
            int scriptLen = tx[offset] & 0xFF;
            offset += 1;
            byte[] scriptPubKey = Arrays.copyOfRange(tx, offset, offset + scriptLen);
            offset += scriptLen;

            outputs.add(new TxOutput(value, scriptPubKey));
        }

        return outputs;
    }

    /**
     * Compute txid from raw transaction hex.
     */
    private static String computeTxid(String txHex) {
        byte[] tx = hexToBytes(txHex);
        byte[] hash = doubleSha256(tx);
        byte[] reversed = new byte[hash.length];
        for (int i = 0; i < hash.length; i++) {
            reversed[i] = hash[hash.length - 1 - i];
        }
        return bytesToHex(reversed);
    }

    /**
     * Get coinbase UTXO from a block.
     */
    public static TransparentInput getCoinbaseUtxo(
            ZebraClient client,
            int blockHeight,
            Keys.ZcashKeypair keypair
    ) {
        try {
            String blockHash = client.getBlockHash(blockHeight);
            JsonObject block = client.getBlock(blockHash, 2); // verbosity 2 for tx data

            JsonArray txArray = block.getAsJsonArray("tx");
            if (txArray == null || txArray.isEmpty()) return null;

            JsonObject coinbaseTx = txArray.get(0).getAsJsonObject();

            // Check for Zebra format (has hex field)
            if (!coinbaseTx.has("hex")) return null;
            String txHex = coinbaseTx.get("hex").getAsString();

            List<TxOutput> outputs = parseTxOutputs(txHex);
            byte[] expectedPubkeyHash = hash160(keypair.publicKey);

            for (int index = 0; index < outputs.size(); index++) {
                TxOutput output = outputs.get(index);
                byte[] scriptPubKey = output.scriptPubKey;

                // Check if this is a P2PKH output matching our pubkey
                // P2PKH: OP_DUP OP_HASH160 <20 bytes> OP_EQUALVERIFY OP_CHECKSIG
                if (scriptPubKey.length == 25 &&
                        scriptPubKey[0] == 0x76 &&
                        scriptPubKey[1] == (byte) 0xa9 &&
                        scriptPubKey[2] == 0x14 &&
                        scriptPubKey[23] == (byte) 0x88 &&
                        scriptPubKey[24] == (byte) 0xac) {

                    byte[] pubkeyHashInScript = Arrays.copyOfRange(scriptPubKey, 3, 23);
                    if (Arrays.equals(pubkeyHashInScript, expectedPubkeyHash)) {
                        byte[] txid = hexToBytes(reverseHex(computeTxid(txHex)));
                        return new TransparentInput(
                                keypair.publicKey,
                                txid,
                                index,
                                output.value,
                                scriptPubKey
                        );
                    }
                }
            }

            return null;
        } catch (Exception e) {
            System.out.println("Error getting coinbase UTXO at height " + blockHeight + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Get mature coinbase UTXOs (100+ confirmations).
     */
    public static List<TransparentInput> getMatureCoinbaseUtxos(
            ZebraClient client,
            Keys.ZcashKeypair keypair,
            int maxCount
    ) throws Exception {
        ZebraClient.BlockchainInfo info = client.getBlockchainInfo();
        int currentHeight = info.blocks;
        int matureHeight = currentHeight - 100;

        Set<String> spentUtxos = loadSpentUtxos();
        List<TransparentInput> utxos = new ArrayList<>();

        // Scan from most recent mature blocks backwards
        for (int height = matureHeight; height >= 1 && utxos.size() < maxCount; height--) {
            TransparentInput utxo = getCoinbaseUtxo(client, height, keypair);
            if (utxo != null) {
                String key = bytesToHex(utxo.getTxid()) + ":" + utxo.getVout();
                if (!spentUtxos.contains(key)) {
                    utxos.add(utxo);
                }
            }
        }

        return utxos;
    }

    /**
     * Print workflow summary.
     */
    public static void printWorkflowSummary(
            String title,
            List<TransparentInput> inputs,
            List<Payment> payments,
            long fee
    ) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println(title);
        System.out.println("=".repeat(70));

        long totalInput = 0;
        for (TransparentInput input : inputs) {
            totalInput += input.getAmount();
        }

        long totalOutput = 0;
        for (Payment payment : payments) {
            totalOutput += payment.getAmount();
        }

        System.out.println("\nInputs: " + inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            System.out.println("  [" + i + "] " + zatoshiToZec(inputs.get(i).getAmount()) + " ZEC");
        }
        System.out.println("  Total: " + zatoshiToZec(totalInput) + " ZEC");

        System.out.println("\nOutputs: " + payments.size());
        for (int i = 0; i < payments.size(); i++) {
            Payment p = payments.get(i);
            String addrShort = p.getAddress().substring(0, Math.min(20, p.getAddress().length()));
            System.out.println("  [" + i + "] " + addrShort + "... -> " + zatoshiToZec(p.getAmount()) + " ZEC");
        }
        System.out.println("  Total: " + zatoshiToZec(totalOutput) + " ZEC");

        System.out.println("\nFee: " + zatoshiToZec(fee) + " ZEC");
        System.out.println("=".repeat(70));
        System.out.println();
    }

    /**
     * Print broadcast result.
     */
    public static void printBroadcastResult(String txid, String txHex) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("TRANSACTION BROADCAST SUCCESSFUL");
        System.out.println("=".repeat(70));
        System.out.println("\nTXID: " + txid);
        if (txHex != null) {
            System.out.println("\nRaw Transaction (" + (txHex.length() / 2) + " bytes):");
            System.out.println(txHex.substring(0, Math.min(100, txHex.length())) + "...");
        }
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println();
    }

    /**
     * Print error.
     */
    public static void printError(String title, Exception error) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("ERROR: " + title);
        System.out.println("=".repeat(70));
        System.out.println("\nError: " + error.getMessage());
        error.printStackTrace();
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println();
    }

    /**
     * Load test data from file.
     */
    public static TestData loadTestData() throws Exception {
        File file = new File(DATA_DIR, "test-addresses.json");
        String content = Files.readString(file.toPath());
        return gson.fromJson(content, TestData.class);
    }

    /**
     * Save test data to file.
     */
    public static void saveTestData(TestData testData) throws Exception {
        DATA_DIR.mkdirs();
        File file = new File(DATA_DIR, "test-addresses.json");
        Files.writeString(file.toPath(), gson.toJson(testData));
    }

    // Helper classes

    private static class TxOutput {
        final long value;
        final byte[] scriptPubKey;

        TxOutput(long value, byte[] scriptPubKey) {
            this.value = value;
            this.scriptPubKey = scriptPubKey;
        }
    }

    public static class TestData {
        public TransparentData transparent;
        public String network;
        public Integer setupHeight;
        public String setupAt;
    }

    public static class TransparentData {
        public String address;
        public String publicKey;
        public String privateKey;
        public String wif;
    }
}
