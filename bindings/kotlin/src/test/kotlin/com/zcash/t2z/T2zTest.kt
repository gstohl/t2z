package com.zcash.t2z

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for t2z Kotlin bindings
 * Matching the TypeScript integration tests
 */
class T2zTest {

    companion object {
        // Test keys matching Go, TypeScript, and Rust tests
        private val TEST_PRIVATE_KEY = ByteArray(32) { 1 } // [1u8; 32]
        private val TEST_PUBLIC_KEY = hexToBytes(
            "031b84c5567b126440995d3ed5aaba0565d71e1834604819ff9c17f5e9d5dd078f"
        )
        // Script pubkey WITHOUT CompactSize prefix (raw 25 bytes for P2PKH)
        private val TEST_SCRIPT_PUBKEY = hexToBytes(
            "76a91479b000887626b294a914501a4cd226b58b23598388ac"
        )
        // Generate a realistic-looking txid
        private val TEST_TXID = sha256("test transaction for t2z".toByteArray())

        private fun hexToBytes(hex: String): ByteArray {
            val len = hex.length
            val data = ByteArray(len / 2)
            for (i in 0 until len step 2) {
                data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            }
            return data
        }

        private fun sha256(input: ByteArray): ByteArray {
            return MessageDigest.getInstance("SHA-256").digest(input)
        }
    }

    @Nested
    inner class TransactionRequestTests {

        @Test
        fun `should create transaction request with single payment`() {
            val payments = listOf(
                Payment(
                    address = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
                    amount = 100_000UL
                )
            )

            TransactionRequest(payments).use { request ->
                assertNotNull(request)
            }
        }

        @Test
        fun `should create transaction request with multiple payments`() {
            val payments = listOf(
                Payment(
                    address = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
                    amount = 50_000UL
                ),
                Payment(
                    address = "tmBsTi2xWTjUdEXnuTceL7fecEQKeWzzhan",
                    amount = 30_000UL
                )
            )

            TransactionRequest(payments).use { request ->
                assertNotNull(request)
            }
        }

        @Test
        fun `should create transaction request with memo`() {
            val payments = listOf(
                Payment(
                    address = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
                    amount = 100_000UL,
                    memo = "Test payment",
                    label = "Alice",
                    message = "Hello from Kotlin"
                )
            )

            TransactionRequest(payments).use { request ->
                assertNotNull(request)
            }
        }

        @Test
        fun `should reject empty payment list`() {
            assertThrows<IllegalArgumentException> {
                TransactionRequest(emptyList())
            }
        }
    }

    @Nested
    inner class FullTransparentWorkflowTests {

        @Test
        fun `should create, sign, and finalize transparent transaction`() {
            // Create payment request
            val payments = listOf(
                Payment(
                    address = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
                    amount = 100_000UL
                )
            )

            TransactionRequest(payments).use { request ->
                // Create transparent input
                val inputs = listOf(
                    TransparentInput(
                        pubkey = TEST_PUBLIC_KEY,
                        txid = TEST_TXID,
                        vout = 0,
                        amount = 100_000_000UL, // 1 ZEC
                        scriptPubKey = TEST_SCRIPT_PUBKEY
                    )
                )

                // Propose transaction
                val pczt = proposeTransaction(inputs, request)
                assertNotNull(pczt)

                // Prove transaction
                val proved = proveTransaction(pczt)
                assertNotNull(proved)

                // Verify before signing (no change expected)
                verifyBeforeSigning(proved, request, emptyList())

                // Get sighash
                val sighash = getSighash(proved, 0)
                assertEquals(32, sighash.size)

                // Sign
                val signature = signMessage(TEST_PRIVATE_KEY, sighash)
                assertEquals(64, signature.size)

                // Append signature
                val signed = appendSignature(proved, 0, signature)
                assertNotNull(signed)

                // Finalize and extract
                val txBytes = finalizeAndExtract(signed)
                assertTrue(txBytes.isNotEmpty())
            }
        }

        @Test
        fun `should handle multiple inputs`() {
            val payments = listOf(
                Payment(
                    address = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
                    amount = 150_000UL
                )
            )

            TransactionRequest(payments).use { request ->
                val inputs = listOf(
                    TransparentInput(
                        pubkey = TEST_PUBLIC_KEY,
                        txid = TEST_TXID,
                        vout = 0,
                        amount = 100_000_000UL,
                        scriptPubKey = TEST_SCRIPT_PUBKEY
                    ),
                    TransparentInput(
                        pubkey = TEST_PUBLIC_KEY,
                        txid = sha256("second test transaction".toByteArray()),
                        vout = 1,
                        amount = 100_000_000UL,
                        scriptPubKey = TEST_SCRIPT_PUBKEY
                    )
                )

                val pczt = proposeTransaction(inputs, request)
                val proved = proveTransaction(pczt)

                // Sign both inputs
                val sighash1 = getSighash(proved, 0)
                val sig1 = signMessage(TEST_PRIVATE_KEY, sighash1)
                val signed1 = appendSignature(proved, 0, sig1)

                val sighash2 = getSighash(signed1, 1)
                val sig2 = signMessage(TEST_PRIVATE_KEY, sighash2)
                val signed2 = appendSignature(signed1, 1, sig2)

                val txBytes = finalizeAndExtract(signed2)
                assertTrue(txBytes.isNotEmpty())
            }
        }
    }

    @Nested
    inner class PcztSerializationTests {

        @Test
        fun `should serialize and parse PCZT`() {
            val payments = listOf(
                Payment(
                    address = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
                    amount = 100_000UL
                )
            )

            TransactionRequest(payments).use { request ->
                val inputs = listOf(
                    TransparentInput(
                        pubkey = TEST_PUBLIC_KEY,
                        txid = TEST_TXID,
                        vout = 0,
                        amount = 100_000_000UL,
                        scriptPubKey = TEST_SCRIPT_PUBKEY
                    )
                )

                val pczt = proposeTransaction(inputs, request)
                val proved = proveTransaction(pczt)

                // Serialize
                val serialized = serializePczt(proved)
                assertTrue(serialized.isNotEmpty())

                // Parse
                val parsed = parsePczt(serialized)
                assertNotNull(parsed)

                // Should be able to continue workflow with parsed PCZT
                val sighash = getSighash(parsed, 0)
                assertEquals(32, sighash.size)

                parsed.close()
                proved.close()
            }
        }
    }

    @Nested
    inner class ErrorHandlingTests {

        @Test
        fun `should reject invalid input index for getSighash`() {
            val payments = listOf(
                Payment(
                    address = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
                    amount = 100_000UL
                )
            )

            TransactionRequest(payments).use { request ->
                val inputs = listOf(
                    TransparentInput(
                        pubkey = TEST_PUBLIC_KEY,
                        txid = TEST_TXID,
                        vout = 0,
                        amount = 100_000_000UL,
                        scriptPubKey = TEST_SCRIPT_PUBKEY
                    )
                )

                val pczt = proposeTransaction(inputs, request)
                val proved = proveTransaction(pczt)

                // Index 999 doesn't exist
                assertThrows<T2zException> {
                    getSighash(proved, 999)
                }

                proved.close()
            }
        }

        @Test
        fun `should reject invalid signature length`() {
            val payments = listOf(
                Payment(
                    address = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
                    amount = 100_000UL
                )
            )

            TransactionRequest(payments).use { request ->
                val inputs = listOf(
                    TransparentInput(
                        pubkey = TEST_PUBLIC_KEY,
                        txid = TEST_TXID,
                        vout = 0,
                        amount = 100_000_000UL,
                        scriptPubKey = TEST_SCRIPT_PUBKEY
                    )
                )

                val pczt = proposeTransaction(inputs, request)
                val proved = proveTransaction(pczt)

                // Wrong signature length
                val badSig = ByteArray(32) // Should be 64
                assertThrows<IllegalArgumentException> {
                    appendSignature(proved, 0, badSig)
                }

                proved.close()
            }
        }

        @Test
        fun `should reject invalid pubkey length`() {
            val payments = listOf(
                Payment(
                    address = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
                    amount = 100_000UL
                )
            )

            TransactionRequest(payments).use { request ->
                assertThrows<IllegalArgumentException> {
                    TransparentInput(
                        pubkey = ByteArray(32), // Should be 33
                        txid = TEST_TXID,
                        vout = 0,
                        amount = 100_000_000UL,
                        scriptPubKey = TEST_SCRIPT_PUBKEY
                    )
                }
            }
        }

        @Test
        fun `should reject invalid txid length`() {
            val payments = listOf(
                Payment(
                    address = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
                    amount = 100_000UL
                )
            )

            TransactionRequest(payments).use { request ->
                assertThrows<IllegalArgumentException> {
                    TransparentInput(
                        pubkey = TEST_PUBLIC_KEY,
                        txid = ByteArray(16), // Should be 32
                        vout = 0,
                        amount = 100_000_000UL,
                        scriptPubKey = TEST_SCRIPT_PUBKEY
                    )
                }
            }
        }
    }

    @Nested
    inner class VerificationTests {

        @Test
        fun `should pass verification for valid transaction without change`() {
            val payments = listOf(
                Payment(
                    address = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
                    amount = 100_000UL
                )
            )

            TransactionRequest(payments).use { request ->
                val inputs = listOf(
                    TransparentInput(
                        pubkey = TEST_PUBLIC_KEY,
                        txid = TEST_TXID,
                        vout = 0,
                        amount = 100_000_000UL,
                        scriptPubKey = TEST_SCRIPT_PUBKEY
                    )
                )

                val pczt = proposeTransaction(inputs, request)
                val proved = proveTransaction(pczt)

                // No change expected - empty list
                verifyBeforeSigning(proved, request, emptyList())

                proved.close()
            }
        }

        @Test
        fun `should pass verification for valid transaction with change`() {
            val payments = listOf(
                Payment(
                    address = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
                    amount = 50_000UL
                )
            )

            TransactionRequest(payments).use { request ->
                val inputs = listOf(
                    TransparentInput(
                        pubkey = TEST_PUBLIC_KEY,
                        txid = TEST_TXID,
                        vout = 0,
                        amount = 100_000_000UL,
                        scriptPubKey = TEST_SCRIPT_PUBKEY
                    )
                )

                val pczt = proposeTransaction(inputs, request)
                val proved = proveTransaction(pczt)

                // When change goes to shielded address (default behavior),
                // we don't provide expectedChange - just verify payments match
                verifyBeforeSigning(proved, request, emptyList())

                proved.close()
            }
        }

        @Test
        fun `should fail verification when payment amount is wrong`() {
            // Create transaction with one payment
            val payments = listOf(
                Payment(
                    address = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
                    amount = 100_000UL
                )
            )

            TransactionRequest(payments).use { request ->
                val inputs = listOf(
                    TransparentInput(
                        pubkey = TEST_PUBLIC_KEY,
                        txid = TEST_TXID,
                        vout = 0,
                        amount = 100_000_000UL,
                        scriptPubKey = TEST_SCRIPT_PUBKEY
                    )
                )

                val pczt = proposeTransaction(inputs, request)
                val proved = proveTransaction(pczt)

                // Now verify against a DIFFERENT payment amount
                val wrongPayments = listOf(
                    Payment(
                        address = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", // Same address
                        amount = 50_000UL // Different amount
                    )
                )
                TransactionRequest(wrongPayments).use { wrongRequest ->
                    // Should fail because the amounts don't match
                    val exception = assertThrows<T2zException> {
                        verifyBeforeSigning(proved, wrongRequest, emptyList())
                    }
                    assertTrue(exception.message?.contains("not found in PCZT") == true)
                }

                proved.close()
            }
        }

        @Test
        fun `should handle transaction with no change (exact amount)`() {
            // This test verifies a transaction where input equals output + fee exactly
            val payments = listOf(
                Payment(
                    address = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
                    amount = 99_985_000UL // 1 ZEC - 15k fee
                )
            )

            TransactionRequest(payments).use { request ->
                val inputs = listOf(
                    TransparentInput(
                        pubkey = TEST_PUBLIC_KEY,
                        txid = TEST_TXID,
                        vout = 0,
                        amount = 100_000_000UL, // Exactly 1 ZEC
                        scriptPubKey = TEST_SCRIPT_PUBKEY
                    )
                )

                val pczt = proposeTransaction(inputs, request)
                val proved = proveTransaction(pczt)

                // No change - transaction uses exact input amount minus fee
                verifyBeforeSigning(proved, request, emptyList())

                proved.close()
            }
        }
    }

    @Nested
    inner class SigningUtilityTests {

        @Test
        fun `should derive correct public key from private key`() {
            val pubkey = getPublicKey(TEST_PRIVATE_KEY)
            assertEquals(33, pubkey.size)
            // Verify derived pubkey can be used for signature verification
            val message = sha256("test key derivation".toByteArray())
            val signature = signMessage(TEST_PRIVATE_KEY, message)
            assertTrue(verifySignature(pubkey, message, signature))
        }

        @Test
        fun `should sign and verify message`() {
            val message = sha256("test message".toByteArray())

            val signature = signMessage(TEST_PRIVATE_KEY, message)
            assertEquals(64, signature.size)

            val valid = verifySignature(TEST_PUBLIC_KEY, message, signature)
            assertTrue(valid)
        }

        @Test
        fun `should reject invalid private key length for signing`() {
            val message = sha256("test message".toByteArray())

            assertThrows<IllegalArgumentException> {
                signMessage(ByteArray(16), message) // Should be 32
            }
        }

        @Test
        fun `should reject invalid message length for signing`() {
            assertThrows<IllegalArgumentException> {
                signMessage(TEST_PRIVATE_KEY, ByteArray(16)) // Should be 32
            }
        }
    }
}
