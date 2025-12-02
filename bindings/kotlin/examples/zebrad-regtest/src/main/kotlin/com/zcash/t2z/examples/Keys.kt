/**
 * Zcash Transparent Key Management
 *
 * Client-side key generation and signing for Zcash transparent addresses
 * Compatible with Zebra (no wallet required).
 */
package com.zcash.t2z.examples

import fr.acinq.bitcoin.Base58Check
import fr.acinq.bitcoin.Crypto
import fr.acinq.secp256k1.Secp256k1
import java.security.MessageDigest

// Zcash uses 2-byte version prefixes
// Testnet/Regtest P2PKH: 0x1D25 -> 'tm' prefix
// Mainnet P2PKH: 0x1CB8 -> 't1' prefix
private val ZCASH_TESTNET_P2PKH = byteArrayOf(0x1d, 0x25)
private val ZCASH_MAINNET_P2PKH = byteArrayOf(0x1c.toByte(), 0xb8.toByte())

// WIF version bytes
private const val WIF_TESTNET: Byte = 0xef.toByte()
private const val WIF_MAINNET: Byte = 0x80.toByte()

enum class NetworkType { MAINNET, TESTNET, REGTEST }

data class ZcashKeypair(
    val privateKey: ByteArray,
    val publicKey: ByteArray,
    val address: String,
    val wif: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ZcashKeypair) return false
        return privateKey.contentEquals(other.privateKey) &&
                publicKey.contentEquals(other.publicKey) &&
                address == other.address &&
                wif == other.wif
    }

    override fun hashCode(): Int {
        var result = privateKey.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + address.hashCode()
        result = 31 * result + wif.hashCode()
        return result
    }
}

/**
 * Create a keypair from an existing private key buffer
 */
fun keypairFromPrivateKey(
    privateKey: ByteArray,
    network: NetworkType = NetworkType.REGTEST
): ZcashKeypair {
    require(privateKey.size == 32) { "Private key must be 32 bytes" }

    // pubkeyCreate returns 65-byte uncompressed, compress it to 33 bytes
    val uncompressedPubkey = Secp256k1.pubkeyCreate(privateKey)
    val publicKey = Secp256k1.pubKeyCompress(uncompressedPubkey)
    val address = pubkeyToAddress(publicKey, network)
    val wif = privateKeyToWIF(privateKey, network)

    return ZcashKeypair(privateKey, publicKey, address, wif)
}

/**
 * Convert private key to WIF format
 */
fun privateKeyToWIF(privateKey: ByteArray, network: NetworkType = NetworkType.REGTEST): String {
    val version = if (network == NetworkType.MAINNET) WIF_MAINNET else WIF_TESTNET
    // Add compression flag (0x01 for compressed) to the data
    val data = privateKey + byteArrayOf(0x01)
    return Base58Check.encode(version, data)
}

/**
 * Convert public key to Zcash transparent address
 * Uses 2-byte version prefix for Zcash addresses
 */
fun pubkeyToAddress(publicKey: ByteArray, network: NetworkType = NetworkType.REGTEST): String {
    // Hash160 the public key (SHA256 then RIPEMD160)
    val hash = hash160(publicKey)

    // Zcash uses 2-byte version prefix
    val versionBytes = if (network == NetworkType.MAINNET) ZCASH_MAINNET_P2PKH else ZCASH_TESTNET_P2PKH

    // Base58check encode with 2-byte prefix
    return Base58Check.encode(versionBytes, hash)
}

/**
 * Hash160: SHA256 then RIPEMD160
 */
fun hash160(data: ByteArray): ByteArray {
    val sha256 = MessageDigest.getInstance("SHA-256").digest(data)
    return Crypto.ripemd160(sha256)
}

/**
 * Double SHA256
 */
fun doubleSha256(data: ByteArray): ByteArray {
    val sha256 = MessageDigest.getInstance("SHA-256")
    return sha256.digest(sha256.digest(data))
}

/**
 * SHA256
 */
fun sha256(data: ByteArray): ByteArray {
    return MessageDigest.getInstance("SHA-256").digest(data)
}

/**
 * Sign a message hash (sighash) with the keypair
 * Returns 64-byte compact signature (r || s format)
 */
fun signCompact(messageHash: ByteArray, keypair: ZcashKeypair): ByteArray {
    require(messageHash.size == 32) { "Message hash must be 32 bytes" }
    val signature = Secp256k1.sign(messageHash, keypair.privateKey)
    // Return first 64 bytes (r || s), dropping the recovery byte
    return signature.copyOfRange(0, 64)
}

/**
 * Convert bytes to hex string
 */
fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

/**
 * Convert hex string to bytes
 */
fun hexToBytes(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Hex string must have even length" }
    return ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}

// Pre-generated test keypair for regtest (deterministic for reproducibility)
// WARNING: Only use this for testing! Never use in production.
private val TEST_PRIVKEY_HEX = "e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35"

val TEST_KEYPAIR = keypairFromPrivateKey(hexToBytes(TEST_PRIVKEY_HEX), NetworkType.REGTEST)
