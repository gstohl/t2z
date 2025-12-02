/**
 * Testnet Demo: Mixed T→T+Z
 * Usage: ./gradlew run --args="<privateKeyHex>"
 */
package com.zcash.t2z.examples;

import com.zcash.t2z.*;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECPoint;
import com.google.gson.*;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;

public class Demo {
    static final String ZEBRA = "http://localhost:18232";
    static final String SHIELDED = "u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("t2z Testnet Demo - Mixed T→T+Z\n");
            System.out.println("Usage: ./gradlew run --args=\"<privateKeyHex>\"");
            System.out.println("\nThe demo will automatically fetch UTXOs from your Zebra node.");
            System.exit(1);
        }

        byte[] privKey = hex(args[0]);
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
        byte[] pubkey = spec.getG().multiply(new BigInteger(1, privKey)).getEncoded(true);
        String address = toTestnetAddress(pubkey);

        System.out.println("Address: " + address);
        System.out.print("Fetching UTXOs... ");

        JsonArray utxos = getUTXOs(address);
        if (utxos.isEmpty()) {
            System.out.println("✗ No UTXOs. Get testnet ZEC from faucet.zecpages.com");
            System.exit(1);
        }
        System.out.println("✓ Found " + utxos.size() + " UTXO(s)\n");

        JsonObject utxo = utxos.get(0).getAsJsonObject();
        byte[] txid = hex(utxo.get("txid").getAsString());
        int vout = utxo.get("outputIndex").getAsInt();
        long amount = utxo.get("satoshis").getAsLong();

        byte[] pkh = hash160(pubkey);
        byte[] script = new byte[25];
        script[0] = 0x76; script[1] = (byte)0xa9; script[2] = 0x14;
        System.arraycopy(pkh, 0, script, 3, 20);
        script[23] = (byte)0x88; script[24] = (byte)0xac;

        TransparentInput input = new TransparentInput(pubkey, txid, vout, amount, script);

        long fee = T2z.calculateFee(1, 2, 1);
        long available = amount - fee;
        long tAmt = available * 40 / 100;
        long zAmt = available * 40 / 100;

        System.out.printf("Input:       %.8f ZEC%n", amount / 1e8);
        System.out.printf("Transparent: %.8f ZEC → %s...%n", tAmt / 1e8, address.substring(0, 20));
        System.out.printf("Shielded:    %.8f ZEC → %s...%n", zAmt / 1e8, SHIELDED.substring(0, 20));
        System.out.printf("Fee:         %.8f ZEC%n%n", fee / 1e8);

        try (TransactionRequest request = new TransactionRequest(Arrays.asList(
                new Payment(address, tAmt), new Payment(SHIELDED, zAmt)))) {
            request.setUseMainnet(false);
            request.setTargetHeight(3_000_000);

            System.out.print("Proposing... ");
            PCZT pczt = T2z.proposeTransaction(Collections.singletonList(input), request);
            System.out.println("✓");

            System.out.print("Proving... ");
            PCZT proved = T2z.proveTransaction(pczt);
            System.out.println("✓");

            System.out.print("Signing... ");
            byte[] sighash = T2z.getSighash(proved, 0);
            byte[] sig = sign(sighash, privKey, spec);
            PCZT signed = T2z.appendSignature(proved, 0, sig);
            System.out.println("✓");

            System.out.print("Finalizing... ");
            byte[] txBytes = T2z.finalizeAndExtract(signed);
            System.out.println("✓");

            System.out.print("Broadcasting... ");
            String txidResult = broadcast(toHex(txBytes));
            System.out.println("✓\n\nTXID: " + txidResult);
        }
    }

    static JsonArray getUTXOs(String addr) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"getaddressutxos\",\"params\":[{\"addresses\":[\"" + addr + "\"]}],\"id\":1}";
        return post(body).getAsJsonArray("result");
    }

    static String broadcast(String txHex) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"sendrawtransaction\",\"params\":[\"" + txHex + "\"],\"id\":1}";
        return post(body).get("result").getAsString();
    }

    static JsonObject post(String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(ZEBRA).openConnection();
        c.setRequestMethod("POST"); c.setRequestProperty("Content-Type", "application/json"); c.setDoOutput(true);
        c.getOutputStream().write(body.getBytes());
        return JsonParser.parseReader(new InputStreamReader(c.getInputStream())).getAsJsonObject();
    }

    static byte[] sign(byte[] hash, byte[] priv, ECNamedCurveParameterSpec spec) {
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

    static String toTestnetAddress(byte[] pub) throws Exception {
        byte[] pkh = hash160(pub), data = new byte[22];
        data[0] = 0x1d; data[1] = 0x25;
        System.arraycopy(pkh, 0, data, 2, 20);
        byte[] check = sha256(sha256(data)), full = new byte[26];
        System.arraycopy(data, 0, full, 0, 22);
        System.arraycopy(check, 0, full, 22, 4);
        return base58(full);
    }

    static byte[] hash160(byte[] d) throws Exception {
        RIPEMD160Digest r = new RIPEMD160Digest(); byte[] sha = sha256(d), out = new byte[20];
        r.update(sha, 0, sha.length); r.doFinal(out, 0); return out;
    }

    static byte[] sha256(byte[] d) throws Exception { return MessageDigest.getInstance("SHA-256").digest(d); }

    static String base58(byte[] d) {
        String a = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        BigInteger n = new BigInteger(1, d); StringBuilder sb = new StringBuilder();
        while (n.compareTo(BigInteger.ZERO) > 0) { sb.insert(0, a.charAt(n.mod(BigInteger.valueOf(58)).intValue())); n = n.divide(BigInteger.valueOf(58)); }
        for (byte b : d) { if (b != 0) break; sb.insert(0, '1'); }
        return sb.toString();
    }

    static byte[] hex(String s) { byte[] b = new byte[s.length()/2]; for (int i = 0; i < b.length; i++) b[i] = (byte)Integer.parseInt(s.substring(i*2, i*2+2), 16); return b; }
    static String toHex(byte[] b) { StringBuilder sb = new StringBuilder(); for (byte x : b) sb.append(String.format("%02x", x)); return sb.toString(); }
}
