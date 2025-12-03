/**
 * Device A - Online Device (Hardware Wallet Simulation)
 * Builds transaction, outputs sighash, waits for signature, broadcasts
 */
package com.zcash.t2z.examples;

import com.zcash.t2z.*;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import com.google.gson.*;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public class DeviceA {
    static String zebraRPC;

    public static void main(String[] args) throws Exception {
        Map<String, String> env = loadEnv();
        zebraRPC = "http://" + env.get("ZEBRA_HOST") + ":" + env.get("ZEBRA_PORT");

        byte[] pubkey = hex(env.get("PUBLIC_KEY"));
        String address = env.get("ADDRESS");

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  DEVICE A - ONLINE DEVICE (Hardware Wallet Simulation)");
        System.out.println("=".repeat(60));
        System.out.println("\nThis device builds transactions but NEVER sees the private key!");
        System.out.println("\nYour address: " + address + "\n");

        System.out.print("Fetching balance... ");
        JsonArray utxos = getUTXOs(address);
        long totalSats = 0;
        for (JsonElement u : utxos) totalSats += u.getAsJsonObject().get("satoshis").getAsLong();
        System.out.println("done");

        if (utxos.isEmpty()) {
            System.out.println("\nNo UTXOs found. Send ZEC to this address first.");
            return;
        }

        System.out.printf("%nBalance: %.8f ZEC%n%n", totalSats / 1e8);

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Recipient address: ");
        String recipientAddr = reader.readLine().trim();
        if (recipientAddr.isEmpty()) {
            System.out.println("No address entered. Exiting.");
            return;
        }

        System.out.print("Amount in ZEC: ");
        double amountZec;
        try {
            amountZec = Double.parseDouble(reader.readLine().trim());
        } catch (Exception e) {
            System.out.println("Invalid amount. Exiting.");
            return;
        }
        if (amountZec <= 0) {
            System.out.println("Invalid amount. Exiting.");
            return;
        }

        long amountSats = (long)(amountZec * 1e8);
        String memo = "";

        if (!recipientAddr.startsWith("t")) {
            System.out.print("Memo (optional, press Enter to skip): ");
            memo = reader.readLine().trim();
        }

        boolean isShielded = !recipientAddr.startsWith("t");
        long fee = isShielded ? T2z.calculateFee(1, 1, 1) : T2z.calculateFee(1, 2, 0);
        long totalNeeded = amountSats + fee;

        if (totalNeeded > totalSats) {
            System.out.printf("%nInsufficient balance! Need %.8f ZEC%n", totalNeeded / 1e8);
            return;
        }

        System.out.println("\n--- Transaction Summary ---");
        System.out.println("  To: " + recipientAddr);
        System.out.printf("  Amount: %.8f ZEC%n", amountZec);
        if (!memo.isEmpty()) System.out.println("  Memo: \"" + memo + "\"");
        System.out.printf("  Fee: %.8f ZEC%n", fee / 1e8);

        // Build input
        byte[] pkh = hash160(pubkey);
        byte[] script = new byte[25];
        script[0] = 0x76; script[1] = (byte)0xa9; script[2] = 0x14;
        System.arraycopy(pkh, 0, script, 3, 20);
        script[23] = (byte)0x88; script[24] = (byte)0xac;

        JsonObject utxo = utxos.get(0).getAsJsonObject();
        byte[] txid = reverseBytes(hex(utxo.get("txid").getAsString()));
        int vout = utxo.get("outputIndex").getAsInt();
        long amount = utxo.get("satoshis").getAsLong();

        TransparentInput input = new TransparentInput(pubkey, txid, vout, amount, script);
        Payment payment = new Payment(recipientAddr, amountSats, memo);

        int blockHeight = getBlockHeight();

        System.out.println("\nBuilding transaction...");

        try (TransactionRequest request = new TransactionRequest(Collections.singletonList(payment))) {
            request.setTargetHeight(blockHeight + 10);

            System.out.print("  Proposing... ");
            PCZT pczt = T2z.proposeTransaction(Collections.singletonList(input), request);
            System.out.println("done");

            System.out.print("  Proving... ");
            PCZT proved = T2z.proveTransaction(pczt);
            System.out.println("done");

            byte[] sighash = T2z.getSighash(proved, 0);
            String sighashHex = toHex(sighash);

            // Serialize PCZT
            byte[] psztBytes = T2z.serialize(proved);
            Path tempFile = Paths.get(System.getProperty("user.dir"), ".pczt-temp");
            Files.writeString(tempFile, toHex(psztBytes));

            System.out.println("\n" + "=".repeat(60));
            System.out.println("  SIGHASH READY FOR OFFLINE SIGNING");
            System.out.println("=".repeat(60));
            System.out.println("\nCopy this sighash to Device B:\n");
            System.out.println("SIGHASH: " + sighashHex);
            System.out.println("\n" + "=".repeat(60));

            System.out.println("\nRun Device B with the sighash, then paste the signature here.\n");
            System.out.print("Paste signature from Device B: ");
            String sigHex = reader.readLine().trim();

            if (sigHex.length() != 128) {
                System.out.println("\nInvalid signature (expected 64 bytes / 128 hex chars). Exiting.");
                return;
            }

            byte[] sig = hex(sigHex);

            System.out.println("\nFinalizing transaction...");
            PCZT loadedPczt = T2z.parse(hex(Files.readString(tempFile)));
            PCZT signed = T2z.appendSignature(loadedPczt, 0, sig);

            System.out.print("  Extracting... ");
            byte[] txBytes = T2z.finalizeAndExtract(signed);
            System.out.println("done");

            System.out.print("  Broadcasting... ");
            String txidResult = broadcast(toHex(txBytes));
            System.out.println("done");

            Files.deleteIfExists(tempFile);

            System.out.println("\n" + "=".repeat(60));
            System.out.println("  TRANSACTION BROADCAST SUCCESSFUL!");
            System.out.println("=".repeat(60));
            System.out.println("\nTXID: " + txidResult);
            System.out.println("\nView: https://zcashblockexplorer.com/transactions/" + txidResult);
            System.out.println("\nThe private key NEVER touched this device!");
        }
    }

    static JsonArray getUTXOs(String addr) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"getaddressutxos\",\"params\":[{\"addresses\":[\"" + addr + "\"]}],\"id\":1}";
        return post(body).getAsJsonArray("result");
    }

    static int getBlockHeight() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"getblockchaininfo\",\"params\":[],\"id\":1}";
        return post(body).getAsJsonObject("result").get("blocks").getAsInt();
    }

    static String broadcast(String txHex) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"sendrawtransaction\",\"params\":[\"" + txHex + "\"],\"id\":1}";
        return post(body).get("result").getAsString();
    }

    static JsonObject post(String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(zebraRPC).openConnection();
        c.setRequestMethod("POST"); c.setRequestProperty("Content-Type", "application/json"); c.setDoOutput(true);
        c.getOutputStream().write(body.getBytes());
        return JsonParser.parseReader(new InputStreamReader(c.getInputStream())).getAsJsonObject();
    }

    static byte[] hash160(byte[] d) throws Exception {
        RIPEMD160Digest r = new RIPEMD160Digest(); byte[] sha = sha256(d), out = new byte[20];
        r.update(sha, 0, sha.length); r.doFinal(out, 0); return out;
    }

    static byte[] sha256(byte[] d) throws Exception { return MessageDigest.getInstance("SHA-256").digest(d); }

    static byte[] reverseBytes(byte[] b) {
        byte[] r = new byte[b.length];
        for (int i = 0; i < b.length; i++) r[i] = b[b.length - 1 - i];
        return r;
    }

    static Map<String, String> loadEnv() throws Exception {
        Path envPath = Paths.get(System.getProperty("user.dir"), ".env");
        if (!Files.exists(envPath)) {
            System.out.println("No .env file found. Run GenerateWallet first.");
            System.exit(1);
        }
        Map<String, String> env = new HashMap<>();
        env.put("ZEBRA_HOST", "localhost");
        env.put("ZEBRA_PORT", "8232");
        for (String line : Files.readAllLines(envPath)) {
            if (!line.startsWith("#") && line.contains("=")) {
                String[] parts = line.split("=", 2);
                env.put(parts[0].trim(), parts[1].trim().replaceAll("^\"|\"$|^'|'$", ""));
            }
        }
        return env;
    }

    static byte[] hex(String s) { byte[] b = new byte[s.length()/2]; for (int i = 0; i < b.length; i++) b[i] = (byte)Integer.parseInt(s.substring(i*2, i*2+2), 16); return b; }
    static String toHex(byte[] b) { StringBuilder sb = new StringBuilder(); for (byte x : b) sb.append(String.format("%02x", x)); return sb.toString(); }
}
