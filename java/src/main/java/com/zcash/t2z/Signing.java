package com.zcash.t2z;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Cryptographic signing utilities using secp256k1.
 */
public final class Signing {
    private static final X9ECParameters CURVE_PARAMS = CustomNamedCurves.getByName("secp256k1");
    private static final ECDomainParameters CURVE = new ECDomainParameters(
        CURVE_PARAMS.getCurve(),
        CURVE_PARAMS.getG(),
        CURVE_PARAMS.getN(),
        CURVE_PARAMS.getH()
    );

    private Signing() {
        // Static utility class
    }

    /**
     * Sign a 32-byte message hash with secp256k1.
     * Returns a 64-byte compact signature (r || s).
     *
     * @param privateKey  32-byte private key
     * @param messageHash 32-byte message hash (e.g., sighash)
     * @return 64-byte signature (r || s)
     */
    public static byte[] signMessage(byte[] privateKey, byte[] messageHash) {
        if (privateKey.length != 32) {
            throw new IllegalArgumentException("Invalid private key length: expected 32, got " + privateKey.length);
        }
        if (messageHash.length != 32) {
            throw new IllegalArgumentException("Invalid message hash length: expected 32, got " + messageHash.length);
        }

        BigInteger privKey = new BigInteger(1, privateKey);
        ECPrivateKeyParameters privateKeyParams = new ECPrivateKeyParameters(privKey, CURVE);

        ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
        signer.init(true, privateKeyParams);

        BigInteger[] signature = signer.generateSignature(messageHash);
        BigInteger r = signature[0];
        BigInteger s = signature[1];

        // Ensure low S value (BIP-62)
        BigInteger halfN = CURVE.getN().shiftRight(1);
        if (s.compareTo(halfN) > 0) {
            s = CURVE.getN().subtract(s);
        }

        // Encode as 64-byte compact format (r || s)
        byte[] result = new byte[64];
        byte[] rBytes = bigIntegerTo32Bytes(r);
        byte[] sBytes = bigIntegerTo32Bytes(s);
        System.arraycopy(rBytes, 0, result, 0, 32);
        System.arraycopy(sBytes, 0, result, 32, 32);

        return result;
    }

    /**
     * Verify a secp256k1 signature.
     *
     * @param publicKey   33-byte compressed public key
     * @param messageHash 32-byte message hash
     * @param signature   64-byte signature (r || s)
     * @return true if signature is valid
     */
    public static boolean verifySignature(byte[] publicKey, byte[] messageHash, byte[] signature) {
        if (publicKey.length != 33) {
            throw new IllegalArgumentException("Invalid public key length: expected 33, got " + publicKey.length);
        }
        if (messageHash.length != 32) {
            throw new IllegalArgumentException("Invalid message hash length: expected 32, got " + messageHash.length);
        }
        if (signature.length != 64) {
            throw new IllegalArgumentException("Invalid signature length: expected 64, got " + signature.length);
        }

        try {
            ECPoint point = CURVE.getCurve().decodePoint(publicKey);
            ECPublicKeyParameters publicKeyParams = new ECPublicKeyParameters(point, CURVE);

            BigInteger r = new BigInteger(1, Arrays.copyOfRange(signature, 0, 32));
            BigInteger s = new BigInteger(1, Arrays.copyOfRange(signature, 32, 64));

            ECDSASigner signer = new ECDSASigner();
            signer.init(false, publicKeyParams);

            return signer.verifySignature(messageHash, r, s);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Derive compressed public key from private key.
     *
     * @param privateKey 32-byte private key
     * @return 33-byte compressed public key
     */
    public static byte[] getPublicKey(byte[] privateKey) {
        if (privateKey.length != 32) {
            throw new IllegalArgumentException("Invalid private key length: expected 32, got " + privateKey.length);
        }

        BigInteger privKey = new BigInteger(1, privateKey);
        ECPoint point = new FixedPointCombMultiplier().multiply(CURVE.getG(), privKey);
        return point.getEncoded(true); // compressed
    }

    /**
     * Convert BigInteger to exactly 32 bytes, padding with leading zeros if needed.
     */
    private static byte[] bigIntegerTo32Bytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == 32) {
            return bytes;
        } else if (bytes.length == 33 && bytes[0] == 0) {
            // Remove leading zero byte
            return Arrays.copyOfRange(bytes, 1, 33);
        } else if (bytes.length < 32) {
            // Pad with leading zeros
            byte[] result = new byte[32];
            System.arraycopy(bytes, 0, result, 32 - bytes.length, bytes.length);
            return result;
        } else {
            throw new IllegalArgumentException("BigInteger too large for 32 bytes");
        }
    }
}
