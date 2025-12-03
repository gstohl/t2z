/**
 * Generate Wallet - Creates a new wallet and saves to .env file
 */
package com.zcash.t2z.examples;

import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;

public class GenerateWallet {
    public static void main(String[] args) throws Exception {
        Path envPath = Paths.get(System.getProperty("user.dir"), ".env");

        if (Files.exists(envPath)) {
            System.out.println("Wallet already exists at .env");
            System.out.println("Delete .env first if you want to generate a new wallet.");
            for (String line : Files.readAllLines(envPath)) {
                if (line.startsWith("ADDRESS=")) {
                    System.out.println("\nCurrent address: " + line.substring(8));
                }
            }
            return;
        }

        // Generate random private key
        byte[] privKey = new byte[32];
        SecureRandom.getInstanceStrong().nextBytes(privKey);

        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
        byte[] pubkey = spec.getG().multiply(new BigInteger(1, privKey)).getEncoded(true);
        String address = toMainnetAddress(pubkey);

        String envContent = String.format(
            "# Zcash Mainnet Wallet%n" +
            "# Generated: %s%n" +
            "# WARNING: Keep this file secret! Never commit to git.%n" +
            "%n" +
            "PRIVATE_KEY=%s%n" +
            "PUBLIC_KEY=%s%n" +
            "ADDRESS=%s%n" +
            "%n" +
            "# Zebra RPC (mainnet default port)%n" +
            "ZEBRA_HOST=localhost%n" +
            "ZEBRA_PORT=8232%n",
            Instant.now(), toHex(privKey), toHex(pubkey), address);

        Files.writeString(envPath, envContent);
        // Set file permissions to 600
        envPath.toFile().setReadable(false, false);
        envPath.toFile().setReadable(true, true);
        envPath.toFile().setWritable(false, false);
        envPath.toFile().setWritable(true, true);

        System.out.println("New wallet generated!\n");
        System.out.println("Address: " + address);
        System.out.println("\nSaved to: " + envPath.toAbsolutePath());
        System.out.println("\nIMPORTANT: Back up your private key securely!");
    }

    static String toMainnetAddress(byte[] pub) throws Exception {
        byte[] pkh = hash160(pub);
        byte[] data = new byte[22];
        data[0] = 0x1c; data[1] = (byte)0xb8;  // mainnet prefix
        System.arraycopy(pkh, 0, data, 2, 20);
        byte[] check = sha256(sha256(data));
        byte[] full = new byte[26];
        System.arraycopy(data, 0, full, 0, 22);
        System.arraycopy(check, 0, full, 22, 4);
        return base58(full);
    }

    static byte[] hash160(byte[] d) throws Exception {
        RIPEMD160Digest r = new RIPEMD160Digest();
        byte[] sha = sha256(d), out = new byte[20];
        r.update(sha, 0, sha.length);
        r.doFinal(out, 0);
        return out;
    }

    static byte[] sha256(byte[] d) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(d);
    }

    static String base58(byte[] d) {
        String a = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        BigInteger n = new BigInteger(1, d);
        StringBuilder sb = new StringBuilder();
        while (n.compareTo(BigInteger.ZERO) > 0) {
            sb.insert(0, a.charAt(n.mod(BigInteger.valueOf(58)).intValue()));
            n = n.divide(BigInteger.valueOf(58));
        }
        for (byte b : d) { if (b != 0) break; sb.insert(0, '1'); }
        return sb.toString();
    }

    static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
