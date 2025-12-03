/**
 * Interactive Send - Reads wallet from .env, prompts for recipients, sends transaction
 */
package com.zcash.t2z.examples;

import com.zcash.t2z.*;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import com.google.gson.*;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

public class Send {
    static String zebraRPC;
    static ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");

    public static void main(String[] args) throws Exception {
        Map<String, String> env = loadEnv();
        zebraRPC = "http://" + env.get("ZEBRA_HOST") + ":" + env.get("ZEBRA_PORT");

        byte[] privKey = hex(env.get("PRIVATE_KEY"));
        byte[] pubkey = spec.getG().multiply(new BigInteger(1, privKey)).getEncoded(true);
        String address = env.get("ADDRESS");

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  t2z Mainnet Send");
        System.out.println("=".repeat(60));
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

        String suffix = utxos.size() == 1 ? "" : "s";
        System.out.printf("%nBalance: %.8f ZEC (%d UTXO%s)%n%n", totalSats / 1e8, utxos.size(), suffix);

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        List<Payment> recipients = new ArrayList<>();
        List<String> memos = new ArrayList<>();

        System.out.println("Enter recipients (shielded addresses starting with 'u' recommended)");
        System.out.println("Press Enter with empty address to finish.\n");

        while (true) {
            System.out.printf("Recipient %d address: ", recipients.size() + 1);
            String addr = reader.readLine().trim();
            if (addr.isEmpty()) break;

            System.out.print("Amount in ZEC: ");
            double amountZec;
            try {
                amountZec = Double.parseDouble(reader.readLine().trim());
            } catch (Exception e) {
                System.out.println("Invalid amount, skipping.\n");
                continue;
            }
            if (amountZec <= 0) {
                System.out.println("Invalid amount, skipping.\n");
                continue;
            }

            long amountSats = (long)(amountZec * 1e8);
            String memo = "";

            if (!addr.startsWith("t")) {
                System.out.print("Memo (optional, press Enter to skip): ");
                memo = reader.readLine().trim();
            }

            recipients.add(new Payment(addr, amountSats, memo));
            memos.add(memo);
            String memoInfo = memo.isEmpty() ? "" : " [memo: \"" + memo.substring(0, Math.min(20, memo.length())) + "\"]";
            System.out.printf("Added: %.8f ZEC → %s...%s%n%n", amountZec, addr.substring(0, Math.min(30, addr.length())), memoInfo);
        }

        if (recipients.isEmpty()) {
            System.out.println("\nNo recipients entered. Exiting.");
            return;
        }

        int numTransparent = 0, numShielded = 0;
        for (Payment p : recipients) {
            if (p.getAddress().startsWith("t")) numTransparent++;
            else numShielded++;
        }
        long fee = T2z.calculateFee(utxos.size(), numTransparent + 1, numShielded);

        long totalSend = 0;
        for (Payment p : recipients) totalSend += p.getAmount();
        long totalNeeded = totalSend + fee;

        System.out.println("\n--- Transaction Summary ---");
        for (int i = 0; i < recipients.size(); i++) {
            Payment r = recipients.get(i);
            String memoInfo = memos.get(i).isEmpty() ? "" : " [memo]";
            System.out.printf("  %.8f ZEC → %s...%s%n", r.getAmount() / 1e8, r.getAddress().substring(0, Math.min(40, r.getAddress().length())), memoInfo);
        }
        System.out.printf("  Fee: %.8f ZEC%n", fee / 1e8);
        System.out.printf("  Total: %.8f ZEC%n", totalNeeded / 1e8);

        if (totalNeeded > totalSats) {
            System.out.printf("%nInsufficient balance! Need %.8f ZEC%n", totalNeeded / 1e8);
            return;
        }

        // Build inputs
        byte[] pkh = hash160(pubkey);
        byte[] script = new byte[25];
        script[0] = 0x76; script[1] = (byte)0xa9; script[2] = 0x14;
        System.arraycopy(pkh, 0, script, 3, 20);
        script[23] = (byte)0x88; script[24] = (byte)0xac;

        List<TransparentInput> inputs = new ArrayList<>();
        long inputTotal = 0;
        for (JsonElement u : utxos) {
            JsonObject utxo = u.getAsJsonObject();
            byte[] txid = reverseBytes(hex(utxo.get("txid").getAsString()));
            int vout = utxo.get("outputIndex").getAsInt();
            long amount = utxo.get("satoshis").getAsLong();

            inputs.add(new TransparentInput(pubkey, txid, vout, amount, script));
            inputTotal += amount;
            if (inputTotal >= totalNeeded) break;
        }

        int blockHeight = getBlockHeight();

        System.out.println("\nBuilding transaction...");

        try (TransactionRequest request = new TransactionRequest(recipients)) {
            request.setTargetHeight(blockHeight + 10);

            System.out.print("  Proposing... ");
            PCZT pczt = T2z.proposeTransaction(inputs, request);
            System.out.println("done");

            System.out.print("  Proving... ");
            PCZT proved = T2z.proveTransaction(pczt);
            System.out.println("done");

            System.out.print("  Signing... ");
            PCZT signed = proved;
            for (int i = 0; i < inputs.size(); i++) {
                byte[] sighash = T2z.getSighash(signed, i);
                byte[] sig = sign(sighash, privKey);
                signed = T2z.appendSignature(signed, i, sig);
            }
            System.out.println("done");

            System.out.print("  Finalizing... ");
            byte[] txBytes = T2z.finalizeAndExtract(signed);
            System.out.println("done");

            System.out.print("  Broadcasting... ");
            String txid = broadcast(toHex(txBytes));
            System.out.println("done");

            System.out.println("\nTransaction sent!");
            System.out.println("TXID: " + txid);
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

    static byte[] sign(byte[] hash, byte[] priv) {
        ECDSASigner s = new ECDSASigner();
        s.init(true, new ECPrivateKeyParameters(new BigInteger(1, priv),
            new org.bouncycastle.crypto.params.ECDomainParameters(spec.getCurve(), spec.getG(), spec.getN(), spec.getH())));
        BigInteger[] rs = s.generateSignature(hash);
        byte[] r = to32(rs[0]), sig = new byte[64];
        byte[] sVal = rs[1].compareTo(spec.getN().shiftRight(1)) > 0 ? spec.getN().subtract(rs[1]) : rs[1];
        System.arraycopy(r, 0, sig, 0, 32);
        System.arraycopy(to32(sVal), 0, sig, 32, 32);
        return sig;
    }

    static byte[] to32(BigInteger bi) {
        byte[] b = bi.toByteArray(), r = new byte[32];
        if (b.length > 32) System.arraycopy(b, b.length - 32, r, 0, 32);
        else System.arraycopy(b, 0, r, 32 - b.length, b.length);
        return r;
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
