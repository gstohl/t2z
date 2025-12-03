/**
 * Device B - Offline Signer (Hardware Wallet Simulation)
 * Signs sighash and returns signature
 */
package com.zcash.t2z.examples

import fr.acinq.secp256k1.Secp256k1
import java.io.File

fun main() {
    val env = loadEnvB()
    val address = env["ADDRESS"]!!

    println("\n${"=".repeat(60)}")
    println("  DEVICE B - OFFLINE SIGNER (Hardware Wallet Simulation)")
    println("=".repeat(60))
    println("\nThis device holds the private key and signs transactions.")
    println("In production, this would be an air-gapped hardware wallet.")
    println("\nWallet address: $address\n")

    println("Paste the sighash from Device A:\n")
    print("SIGHASH: ")
    val sighashHex = readLine()?.trim() ?: ""

    if (sighashHex.length != 64) {
        println("\nInvalid sighash (expected 32 bytes / 64 hex chars). Exiting.")
        return
    }

    val sighash = sighashHex.hexToBytesB()

    println("\nSigning...")

    val privKey = env["PRIVATE_KEY"]!!.hexToBytesB()
    val sig = Secp256k1.sign(sighash, privKey).copyOf(64)
    val sigHex = sig.toHexB()

    println("\n${"=".repeat(60)}")
    println("  SIGNATURE READY")
    println("=".repeat(60))
    println("\nCopy this signature back to Device A:\n")
    println("SIGNATURE: $sigHex")
    println("\n${"=".repeat(60)}")
    println("\nThe private key stayed on this device!")
}

private fun loadEnvB(): Map<String, String> {
    val envFile = File(System.getProperty("user.dir"), ".env")
    if (!envFile.exists()) {
        println("No .env file found. Run GenerateWallet first.")
        System.exit(1)
    }
    val env = mutableMapOf<String, String>()
    envFile.readLines().filter { !it.startsWith("#") && it.contains("=") }.forEach { line ->
        val (key, value) = line.split("=", limit = 2)
        env[key.trim()] = value.trim().trim('"', '\'')
    }
    return env
}

private fun String.hexToBytesB() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
private fun ByteArray.toHexB() = joinToString("") { "%02x".format(it) }
