/**
 * Cryptographic signing utilities using secp256k1
 */
package com.zcash.t2z

import fr.acinq.secp256k1.Secp256k1

/**
 * Sign a 32-byte message hash with secp256k1
 * Returns a 64-byte compact signature (r || s)
 *
 * @param privateKey 32-byte private key
 * @param messageHash 32-byte message hash (e.g., sighash)
 * @return 64-byte signature (r || s)
 */
fun signMessage(privateKey: ByteArray, messageHash: ByteArray): ByteArray {
    require(privateKey.size == 32) { "Invalid private key length: expected 32, got ${privateKey.size}" }
    require(messageHash.size == 32) { "Invalid message hash length: expected 32, got ${messageHash.size}" }

    // Sign with secp256k1
    val signature = Secp256k1.sign(messageHash, privateKey)

    // Return compact format (r || s, 64 bytes) - drop the recovery byte
    return signature.copyOfRange(0, 64)
}

/**
 * Verify a secp256k1 signature
 *
 * @param publicKey 33-byte compressed public key
 * @param messageHash 32-byte message hash
 * @param signature 64-byte signature (r || s)
 * @return true if signature is valid
 */
fun verifySignature(publicKey: ByteArray, messageHash: ByteArray, signature: ByteArray): Boolean {
    require(publicKey.size == 33) { "Invalid public key length: expected 33, got ${publicKey.size}" }
    require(messageHash.size == 32) { "Invalid message hash length: expected 32, got ${messageHash.size}" }
    require(signature.size == 64) { "Invalid signature length: expected 64, got ${signature.size}" }

    return try {
        Secp256k1.verify(signature, messageHash, publicKey)
    } catch (e: Exception) {
        false
    }
}

/**
 * Derive compressed public key from private key
 *
 * @param privateKey 32-byte private key
 * @return 33-byte compressed public key
 */
fun getPublicKey(privateKey: ByteArray): ByteArray {
    require(privateKey.size == 32) { "Invalid private key length: expected 32, got ${privateKey.size}" }

    val uncompressedPubkey = Secp256k1.pubkeyCreate(privateKey)
    return Secp256k1.pubKeyCompress(uncompressedPubkey)
}
