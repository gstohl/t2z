/**
 * Generate Wallet - Creates a new wallet and saves to .env file
 */
package com.zcash.t2z.examples

import fr.acinq.secp256k1.Secp256k1
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

fun main() {
    val envFile = File(System.getProperty("user.dir"), ".env")

    if (envFile.exists()) {
        println("Wallet already exists at .env")
        println("Delete .env first if you want to generate a new wallet.")
        envFile.readLines().find { it.startsWith("ADDRESS=") }?.let {
            println("\nCurrent address: ${it.substringAfter("=")}")
        }
        return
    }

    // Generate random private key
    val privKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
    val pubkey = Secp256k1.pubkeyCreate(privKey)
    val address = pubkeyToMainnetAddress(pubkey)

    val envContent = """
        |# Zcash Mainnet Wallet
        |# Generated: ${Instant.now()}
        |# WARNING: Keep this file secret! Never commit to git.
        |
        |PRIVATE_KEY=${privKey.toHex()}
        |PUBLIC_KEY=${pubkey.toHex()}
        |ADDRESS=$address
        |
        |# Zebra RPC (mainnet default port)
        |ZEBRA_HOST=localhost
        |ZEBRA_PORT=8232
    """.trimMargin()

    envFile.writeText(envContent)

    println("New wallet generated!\n")
    println("Address: $address")
    println("\nSaved to: ${envFile.absolutePath}")
    println("\nIMPORTANT: Back up your private key securely!")
}

private fun pubkeyToMainnetAddress(pubkey: ByteArray): String {
    val pkh = hash160(pubkey)
    val data = byteArrayOf(0x1c, 0xb8.toByte()) + pkh  // mainnet prefix
    val check = MessageDigest.getInstance("SHA-256").digest(
        MessageDigest.getInstance("SHA-256").digest(data)
    ).take(4).toByteArray()
    return base58Encode(data + check)
}

private fun hash160(data: ByteArray): ByteArray {
    val sha = MessageDigest.getInstance("SHA-256").digest(data)
    val ripemd = org.bouncycastle.crypto.digests.RIPEMD160Digest()
    ripemd.update(sha, 0, sha.size)
    val out = ByteArray(20)
    ripemd.doFinal(out, 0)
    return out
}

private fun base58Encode(data: ByteArray): String {
    val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    var num = java.math.BigInteger(1, data)
    val sb = StringBuilder()
    while (num > java.math.BigInteger.ZERO) {
        sb.insert(0, alphabet[num.mod(java.math.BigInteger.valueOf(58)).toInt()])
        num = num.divide(java.math.BigInteger.valueOf(58))
    }
    data.takeWhile { it == 0.toByte() }.forEach { sb.insert(0, '1') }
    return sb.toString()
}

private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
