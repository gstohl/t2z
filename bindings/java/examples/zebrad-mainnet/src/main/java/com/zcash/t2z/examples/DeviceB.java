/**
 * Device B - Offline Signer (Hardware Wallet Simulation)
 * Signs sighash and returns signature
 */
package com.zcash.t2z.examples;

import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;

public class DeviceB {
    public static void main(String[] args) throws Exception {
        Map<String, String> env = loadEnv();
        String address = env.get("ADDRESS");

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  DEVICE B - OFFLINE SIGNER (Hardware Wallet Simulation)");
        System.out.println("=".repeat(60));
        System.out.println("\nThis device holds the private key and signs transactions.");
        System.out.println("In production, this would be an air-gapped hardware wallet.");
        System.out.println("\nWallet address: " + address + "\n");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Paste the sighash from Device A:\n");
        System.out.print("SIGHASH: ");
        String sighashHex = reader.readLine().trim();

        if (sighashHex.length() != 64) {
            System.out.println("\nInvalid sighash (expected 32 bytes / 64 hex chars). Exiting.");
            return;
        }

        byte[] sighash = hex(sighashHex);

        System.out.println("\nSigning...");

        byte[] privKey = hex(env.get("PRIVATE_KEY"));
        byte[] sig = sign(sighash, privKey);
        String sigHex = toHex(sig);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("  SIGNATURE READY");
        System.out.println("=".repeat(60));
        System.out.println("\nCopy this signature back to Device A:\n");
        System.out.println("SIGNATURE: " + sigHex);
        System.out.println("\n" + "=".repeat(60));
        System.out.println("\nThe private key stayed on this device!");
    }

    static byte[] sign(byte[] hash, byte[] priv) {
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
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

    static Map<String, String> loadEnv() throws Exception {
        Path envPath = Paths.get(System.getProperty("user.dir"), ".env");
        if (!Files.exists(envPath)) {
            System.out.println("No .env file found. Run GenerateWallet first.");
            System.exit(1);
        }
        Map<String, String> env = new HashMap<>();
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
