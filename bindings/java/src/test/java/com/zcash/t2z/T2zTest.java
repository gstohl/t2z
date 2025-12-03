package com.zcash.t2z;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for t2z Java bindings.
 * Matching the TypeScript and Kotlin integration tests.
 */
class T2zTest {

    // Test keys matching Go, TypeScript, Kotlin, and Rust tests
    private static final byte[] TEST_PRIVATE_KEY = new byte[32];
    static {
        Arrays.fill(TEST_PRIVATE_KEY, (byte) 1); // [1u8; 32]
    }

    private static final byte[] TEST_PUBLIC_KEY = hexToBytes(
        "031b84c5567b126440995d3ed5aaba0565d71e1834604819ff9c17f5e9d5dd078f"
    );

    // Script pubkey WITHOUT CompactSize prefix (raw 25 bytes for P2PKH)
    private static final byte[] TEST_SCRIPT_PUBKEY = hexToBytes(
        "76a91479b000887626b294a914501a4cd226b58b23598388ac"
    );

    // Generate a realistic-looking txid
    private static final byte[] TEST_TXID = sha256("test transaction for t2z".getBytes());

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    class TransactionRequestTests {

        @Test
        void shouldCreateTransactionRequestWithSinglePayment() {
            List<Payment> payments = List.of(
                new Payment("tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", 100_000L)
            );

            try (TransactionRequest request = new TransactionRequest(payments)) {
                assertNotNull(request);
            }
        }

        @Test
        void shouldCreateTransactionRequestWithMultiplePayments() {
            List<Payment> payments = List.of(
                new Payment("tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", 50_000L),
                new Payment("tmBsTi2xWTjUdEXnuTceL7fecEQKeWzzhan", 30_000L)
            );

            try (TransactionRequest request = new TransactionRequest(payments)) {
                assertNotNull(request);
            }
        }

        @Test
        void shouldCreateTransactionRequestWithMemo() {
            List<Payment> payments = List.of(
                Payment.builder()
                    .address("tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma")
                    .amount(100_000L)
                    .memo("Test payment")
                    .label("Alice")
                    .message("Hello from Java")
                    .build()
            );

            try (TransactionRequest request = new TransactionRequest(payments)) {
                assertNotNull(request);
            }
        }

        @Test
        void shouldRejectEmptyPaymentList() {
            assertThrows(IllegalArgumentException.class, () -> {
                new TransactionRequest(List.of());
            });
        }
    }

    @Nested
    class FullTransparentWorkflowTests {

        @Test
        void shouldCreateSignAndFinalizeTransparentTransaction() {
            // Create payment request
            List<Payment> payments = List.of(
                new Payment("tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", 100_000L)
            );

            try (TransactionRequest request = new TransactionRequest(payments)) {
                // Create transparent input
                List<TransparentInput> inputs = List.of(
                    new TransparentInput(
                        TEST_PUBLIC_KEY,
                        TEST_TXID,
                        0,
                        100_000_000L, // 1 ZEC
                        TEST_SCRIPT_PUBKEY
                    )
                );

                // Propose transaction
                PCZT pczt = T2z.proposeTransaction(inputs, request);
                assertNotNull(pczt);

                // Prove transaction
                PCZT proved = T2z.proveTransaction(pczt);
                assertNotNull(proved);

                // Verify before signing
                assertDoesNotThrow(() -> T2z.verifyBeforeSigning(proved, request, List.of()));

                // Get sighash
                byte[] sighash = T2z.getSighash(proved, 0);
                assertEquals(32, sighash.length);

                // Sign
                byte[] signature = Signing.signMessage(TEST_PRIVATE_KEY, sighash);
                assertEquals(64, signature.length);

                // Append signature
                PCZT signed = T2z.appendSignature(proved, 0, signature);
                assertNotNull(signed);

                // Finalize and extract
                byte[] txBytes = T2z.finalizeAndExtract(signed);
                assertTrue(txBytes.length > 0);
            }
        }

        @Test
        void shouldHandleMultipleInputs() {
            List<Payment> payments = List.of(
                new Payment("tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", 150_000L)
            );

            try (TransactionRequest request = new TransactionRequest(payments)) {
                List<TransparentInput> inputs = List.of(
                    new TransparentInput(
                        TEST_PUBLIC_KEY,
                        TEST_TXID,
                        0,
                        100_000_000L,
                        TEST_SCRIPT_PUBKEY
                    ),
                    new TransparentInput(
                        TEST_PUBLIC_KEY,
                        sha256("second test transaction".getBytes()),
                        1,
                        100_000_000L,
                        TEST_SCRIPT_PUBKEY
                    )
                );

                PCZT pczt = T2z.proposeTransaction(inputs, request);
                PCZT proved = T2z.proveTransaction(pczt);

                // Sign both inputs
                byte[] sighash1 = T2z.getSighash(proved, 0);
                byte[] sig1 = Signing.signMessage(TEST_PRIVATE_KEY, sighash1);
                PCZT signed1 = T2z.appendSignature(proved, 0, sig1);

                byte[] sighash2 = T2z.getSighash(signed1, 1);
                byte[] sig2 = Signing.signMessage(TEST_PRIVATE_KEY, sighash2);
                PCZT signed2 = T2z.appendSignature(signed1, 1, sig2);

                byte[] txBytes = T2z.finalizeAndExtract(signed2);
                assertTrue(txBytes.length > 0);
            }
        }
    }

    @Nested
    class PcztSerializationTests {

        @Test
        void shouldSerializeAndParsePczt() {
            List<Payment> payments = List.of(
                new Payment("tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", 100_000L)
            );

            try (TransactionRequest request = new TransactionRequest(payments)) {
                List<TransparentInput> inputs = List.of(
                    new TransparentInput(
                        TEST_PUBLIC_KEY,
                        TEST_TXID,
                        0,
                        100_000_000L,
                        TEST_SCRIPT_PUBKEY
                    )
                );

                PCZT pczt = T2z.proposeTransaction(inputs, request);
                PCZT proved = T2z.proveTransaction(pczt);

                // Serialize
                byte[] serialized = T2z.serializePczt(proved);
                assertTrue(serialized.length > 0);

                // Parse
                PCZT parsed = T2z.parsePczt(serialized);
                assertNotNull(parsed);

                // Should be able to continue workflow with parsed PCZT
                byte[] sighash = T2z.getSighash(parsed, 0);
                assertEquals(32, sighash.length);

                parsed.close();
                proved.close();
            }
        }
    }

    @Nested
    class ErrorHandlingTests {

        @Test
        void shouldRejectInvalidInputIndexForGetSighash() {
            List<Payment> payments = List.of(
                new Payment("tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", 100_000L)
            );

            try (TransactionRequest request = new TransactionRequest(payments)) {
                List<TransparentInput> inputs = List.of(
                    new TransparentInput(
                        TEST_PUBLIC_KEY,
                        TEST_TXID,
                        0,
                        100_000_000L,
                        TEST_SCRIPT_PUBKEY
                    )
                );

                PCZT pczt = T2z.proposeTransaction(inputs, request);
                PCZT proved = T2z.proveTransaction(pczt);

                // Index 999 doesn't exist
                assertThrows(T2zException.class, () -> T2z.getSighash(proved, 999));

                proved.close();
            }
        }

        @Test
        void shouldRejectInvalidSignatureLength() {
            List<Payment> payments = List.of(
                new Payment("tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", 100_000L)
            );

            try (TransactionRequest request = new TransactionRequest(payments)) {
                List<TransparentInput> inputs = List.of(
                    new TransparentInput(
                        TEST_PUBLIC_KEY,
                        TEST_TXID,
                        0,
                        100_000_000L,
                        TEST_SCRIPT_PUBKEY
                    )
                );

                PCZT pczt = T2z.proposeTransaction(inputs, request);
                PCZT proved = T2z.proveTransaction(pczt);

                // Wrong signature length
                byte[] badSig = new byte[32]; // Should be 64
                assertThrows(IllegalArgumentException.class, () -> T2z.appendSignature(proved, 0, badSig));

                proved.close();
            }
        }

        @Test
        void shouldRejectInvalidPubkeyLength() {
            assertThrows(IllegalArgumentException.class, () -> {
                new TransparentInput(
                    new byte[32], // Should be 33
                    TEST_TXID,
                    0,
                    100_000_000L,
                    TEST_SCRIPT_PUBKEY
                );
            });
        }

        @Test
        void shouldRejectInvalidTxidLength() {
            assertThrows(IllegalArgumentException.class, () -> {
                new TransparentInput(
                    TEST_PUBLIC_KEY,
                    new byte[16], // Should be 32
                    0,
                    100_000_000L,
                    TEST_SCRIPT_PUBKEY
                );
            });
        }
    }

    @Nested
    class VerificationTests {

        @Test
        void shouldPassVerificationForValidTransactionWithoutChange() {
            List<Payment> payments = List.of(
                new Payment("tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", 100_000L)
            );

            try (TransactionRequest request = new TransactionRequest(payments)) {
                List<TransparentInput> inputs = List.of(
                    new TransparentInput(
                        TEST_PUBLIC_KEY,
                        TEST_TXID,
                        0,
                        100_000_000L,
                        TEST_SCRIPT_PUBKEY
                    )
                );

                PCZT pczt = T2z.proposeTransaction(inputs, request);
                PCZT proved = T2z.proveTransaction(pczt);

                // No change expected - empty list
                assertDoesNotThrow(() -> T2z.verifyBeforeSigning(proved, request, List.of()));

                proved.close();
            }
        }

        @Test
        void shouldFailVerificationWhenPaymentAmountIsWrong() {
            // Create transaction with one payment
            List<Payment> payments = List.of(
                new Payment("tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", 100_000L)
            );

            try (TransactionRequest request = new TransactionRequest(payments)) {
                List<TransparentInput> inputs = List.of(
                    new TransparentInput(
                        TEST_PUBLIC_KEY,
                        TEST_TXID,
                        0,
                        100_000_000L,
                        TEST_SCRIPT_PUBKEY
                    )
                );

                PCZT pczt = T2z.proposeTransaction(inputs, request);
                PCZT proved = T2z.proveTransaction(pczt);

                // Now verify against a DIFFERENT payment amount
                List<Payment> wrongPayments = List.of(
                    new Payment("tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma", 50_000L) // Different amount
                );

                try (TransactionRequest wrongRequest = new TransactionRequest(wrongPayments)) {
                    // Should fail because the amounts don't match
                    T2zException exception = assertThrows(T2zException.class, () -> {
                        T2z.verifyBeforeSigning(proved, wrongRequest, List.of());
                    });
                    assertTrue(exception.getMessage().contains("not found in PCZT"));
                }

                proved.close();
            }
        }
    }

    @Nested
    class SigningUtilityTests {

        @Test
        void shouldDeriveCorrectPublicKeyFromPrivateKey() {
            byte[] pubkey = Signing.getPublicKey(TEST_PRIVATE_KEY);
            assertEquals(33, pubkey.length);
            assertArrayEquals(TEST_PUBLIC_KEY, pubkey);
        }

        @Test
        void shouldSignAndVerifyMessage() {
            byte[] message = sha256("test message".getBytes());

            byte[] signature = Signing.signMessage(TEST_PRIVATE_KEY, message);
            assertEquals(64, signature.length);

            boolean valid = Signing.verifySignature(TEST_PUBLIC_KEY, message, signature);
            assertTrue(valid);
        }

        @Test
        void shouldRejectInvalidPrivateKeyLengthForSigning() {
            byte[] message = sha256("test message".getBytes());

            assertThrows(IllegalArgumentException.class, () -> {
                Signing.signMessage(new byte[16], message); // Should be 32
            });
        }

        @Test
        void shouldRejectInvalidMessageLengthForSigning() {
            assertThrows(IllegalArgumentException.class, () -> {
                Signing.signMessage(TEST_PRIVATE_KEY, new byte[16]); // Should be 32
            });
        }
    }
}
