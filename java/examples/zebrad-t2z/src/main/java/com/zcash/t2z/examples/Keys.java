/**
 * Zcash Transparent Key Management
 *
 * Client-side key generation and signing for Zcash transparent addresses.
 * Compatible with Zebra (no wallet required).
 */
package com.zcash.t2z.examples;

import org.bitcoinj.core.Base58;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class Keys {
    // Zcash uses 2-byte version prefixes
    // Testnet/Regtest P2PKH: 0x1D25 -> 'tm' prefix
    private static final byte[] ZCASH_TESTNET_P2PKH = new byte[]{0x1d, 0x25};

    // WIF version byte for testnet
    private static final byte WIF_TESTNET = (byte) 0xef;

    // Pre-generated test keypair for regtest (deterministic for reproducibility)
    // WARNING: Only use this for testing! Never use in production.
    private static final String TEST_PRIVKEY_HEX = "e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35";

    public static final ZcashKeypair TEST_KEYPAIR = keypairFromPrivateKey(hexToBytes(TEST_PRIVKEY_HEX));

    /**
     * Zcash keypair containing private key, public key, address, and WIF.
     */
    public static class ZcashKeypair {
        public final byte[] privateKey;
        public final byte[] publicKey;
        public final String address;
        public final String wif;

        public ZcashKeypair(byte[] privateKey, byte[] publicKey, String address, String wif) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
            this.address = address;
            this.wif = wif;
        }
    }

    /**
     * Create a keypair from an existing private key buffer.
     */
    public static ZcashKeypair keypairFromPrivateKey(byte[] privateKey) {
        if (privateKey.length != 32) {
            throw new IllegalArgumentException("Private key must be 32 bytes");
        }

        ECParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
        BigInteger privKeyInt = new BigInteger(1, privateKey);
        ECPoint pubKeyPoint = spec.getG().multiply(privKeyInt);

        // Get compressed public key (33 bytes)
        byte[] publicKey = pubKeyPoint.getEncoded(true);
        String address = pubkeyToAddress(publicKey);
        String wif = privateKeyToWIF(privateKey);

        return new ZcashKeypair(privateKey, publicKey, address, wif);
    }

    /**
     * Convert private key to WIF format.
     */
    public static String privateKeyToWIF(byte[] privateKey) {
        // WIF format: [version][privkey][compression flag][checksum]
        // Add compression flag (0x01 for compressed)
        byte[] payload = new byte[privateKey.length + 1];
        System.arraycopy(privateKey, 0, payload, 0, privateKey.length);
        payload[payload.length - 1] = 0x01; // compressed flag

        // Use the same encoding approach as address (manual Base58Check)
        return base58CheckEncode(new byte[]{WIF_TESTNET}, payload);
    }

    /**
     * Convert public key to Zcash transparent address.
     * Uses 2-byte version prefix for Zcash addresses.
     */
    public static String pubkeyToAddress(byte[] publicKey) {
        byte[] hash = hash160(publicKey);
        return base58CheckEncode(ZCASH_TESTNET_P2PKH, hash);
    }

    /**
     * Base58Check encode with arbitrary prefix.
     */
    public static String base58CheckEncode(byte[] version, byte[] payload) {
        byte[] data = new byte[version.length + payload.length + 4];
        System.arraycopy(version, 0, data, 0, version.length);
        System.arraycopy(payload, 0, data, version.length, payload.length);

        byte[] checksum = doubleSha256(Arrays.copyOfRange(data, 0, version.length + payload.length));
        System.arraycopy(checksum, 0, data, version.length + payload.length, 4);

        return Base58.encode(data);
    }

    /**
     * Hash160: SHA256 then RIPEMD160.
     */
    public static byte[] hash160(byte[] data) {
        byte[] sha256 = sha256(data);
        RIPEMD160Digest ripemd = new RIPEMD160Digest();
        ripemd.update(sha256, 0, sha256.length);
        byte[] result = new byte[20];
        ripemd.doFinal(result, 0);
        return result;
    }

    /**
     * SHA256 hash.
     */
    public static byte[] sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Double SHA256.
     */
    public static byte[] doubleSha256(byte[] data) {
        return sha256(sha256(data));
    }

    /**
     * Sign a message hash (sighash) with the keypair.
     * Returns 64-byte compact signature (r || s format).
     */
    public static byte[] signCompact(byte[] messageHash, ZcashKeypair keypair) {
        if (messageHash.length != 32) {
            throw new IllegalArgumentException("Message hash must be 32 bytes");
        }

        ECParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
        BigInteger privKeyInt = new BigInteger(1, keypair.privateKey);
        ECPrivateKeyParameters privKeyParams = new ECPrivateKeyParameters(
                privKeyInt,
                new org.bouncycastle.crypto.params.ECDomainParameters(
                        spec.getCurve(), spec.getG(), spec.getN(), spec.getH()
                )
        );

        ECDSASigner signer = new ECDSASigner();
        signer.init(true, privKeyParams);

        BigInteger[] signature = signer.generateSignature(messageHash);
        BigInteger r = signature[0];
        BigInteger s = signature[1];

        // Ensure low-S value (BIP 62)
        BigInteger halfN = spec.getN().shiftRight(1);
        if (s.compareTo(halfN) > 0) {
            s = spec.getN().subtract(s);
        }

        // Convert to 64-byte format (32 bytes r + 32 bytes s)
        byte[] result = new byte[64];
        byte[] rBytes = r.toByteArray();
        byte[] sBytes = s.toByteArray();

        // Handle potential leading zero in BigInteger representation
        int rStart = rBytes.length > 32 ? 1 : 0;
        int sStart = sBytes.length > 32 ? 1 : 0;
        int rLen = Math.min(32, rBytes.length - rStart);
        int sLen = Math.min(32, sBytes.length - sStart);

        System.arraycopy(rBytes, rStart, result, 32 - rLen, rLen);
        System.arraycopy(sBytes, sStart, result, 64 - sLen, sLen);

        return result;
    }

    /**
     * Convert bytes to hex string.
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Convert hex string to bytes.
     */
    public static byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length");
        }
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }
}
