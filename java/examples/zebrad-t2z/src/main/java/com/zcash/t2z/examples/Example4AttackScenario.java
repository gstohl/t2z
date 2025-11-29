/**
 * Example 4: Attack Scenario - PCZT Malleation Detection
 *
 * Demonstrates how verify_before_signing catches malicious modifications:
 * - Create a legitimate transaction request
 * - Simulate an attacker modifying the PCZT
 * - Show verification catching the attack
 *
 * This is a DEMO showing why verification is critical!
 */
package com.zcash.t2z.examples;

import com.zcash.t2z.*;

import java.util.Collections;
import java.util.List;

import static com.zcash.t2z.examples.Keys.*;
import static com.zcash.t2z.examples.Utils.*;

public class Example4AttackScenario {
    // Victim's intended recipient (same as sender for demo)
    private static final String VICTIM_ADDRESS = "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma";
    // Attacker's address (different address)
    private static final String ATTACKER_ADDRESS = "tmBsTi2xWTjUdEXnuTceL7fecEQKeWi4vxA";

    public static void main(String[] args) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("  EXAMPLE 4: ATTACK SCENARIO - PCZT Malleation Detection");
        System.out.println("=".repeat(70));
        System.out.println();

        System.out.println("WARNING: This demonstrates a security feature!");
        System.out.println("   This example shows why you MUST verify PCZTs before signing.");
        System.out.println();

        try {
            // Create mock UTXO (no network needed for this demo)
            byte[] txid = new byte[32];
            System.arraycopy("example4_attack_scenario_txid00".getBytes(), 0, txid, 0, 31);

            byte[] pubkey = TEST_KEYPAIR.publicKey;
            byte[] scriptPubKey = createP2PKHScript(pubkey);

            long inputAmount = 100_000_000L; // 1 ZEC

            List<TransparentInput> inputs = Collections.singletonList(
                    new TransparentInput(pubkey, txid, 0, inputAmount, scriptPubKey)
            );

            System.out.println("Scenario Setup:");
            System.out.println("  Victim's address: " + VICTIM_ADDRESS);
            System.out.println("  Legitimate recipient: " + VICTIM_ADDRESS);
            System.out.println("  Attacker's address: " + ATTACKER_ADDRESS);
            System.out.println();
            System.out.println("Using UTXO: " + zatoshiToZec(inputAmount) + " ZEC\n");

            // =================================================================
            // SCENARIO 1: Legitimate Transaction
            // =================================================================
            System.out.println("=".repeat(70));
            System.out.println("  SCENARIO 1: Legitimate Transaction");
            System.out.println("=".repeat(70));
            System.out.println();

            long paymentAmount = inputAmount / 2; // 50%

            List<Payment> legitimatePayments = Collections.singletonList(
                    new Payment(VICTIM_ADDRESS, paymentAmount)
            );

            System.out.println("User creates legitimate payment:");
            System.out.println("   Send " + zatoshiToZec(paymentAmount) + " ZEC -> " +
                    VICTIM_ADDRESS.substring(0, 30) + "...\n");

            try (TransactionRequest legitimateRequest = new TransactionRequest(legitimatePayments)) {
                // Mainnet is the default, just set target height
                legitimateRequest.setTargetHeight(2_500_000);

                System.out.println("1. Proposing legitimate transaction...");
                PCZT pczt = T2z.proposeTransaction(inputs, legitimateRequest);
                System.out.println("   PCZT created\n");

                System.out.println("2. Proving transaction...");
                PCZT proved = T2z.proveTransaction(pczt);
                System.out.println("   Proofs generated\n");

                System.out.println("3. Verifying PCZT (BEFORE signing)...");
                try {
                    T2z.verifyBeforeSigning(proved, legitimateRequest, Collections.emptyList());
                    System.out.println("   VERIFICATION PASSED - Safe to sign!\n");
                } catch (T2zException e) {
                    System.out.println("   Verification returned: " + e.getMessage() + "\n");
                }

                // =================================================================
                // SCENARIO 2: Attack - Wrong Payment Amount
                // =================================================================
                System.out.println("=".repeat(70));
                System.out.println("  SCENARIO 2: Attack - Wrong Payment Amount");
                System.out.println("=".repeat(70));
                System.out.println();

                System.out.println("ATTACK: Attacker intercepts PCZT and creates different request");
                long wrongAmount = paymentAmount * 2;
                System.out.println("   Attacker claims payment is " + zatoshiToZec(wrongAmount) +
                        " ZEC (instead of " + zatoshiToZec(paymentAmount) + " ZEC)\n");

                List<Payment> attackedPayments1 = Collections.singletonList(
                        new Payment(VICTIM_ADDRESS, wrongAmount)
                );

                try (TransactionRequest maliciousRequest1 = new TransactionRequest(attackedPayments1)) {
                    System.out.println("User verifies PCZT before signing...");
                    try {
                        T2z.verifyBeforeSigning(proved, maliciousRequest1, Collections.emptyList());
                        System.out.println("   DANGER: Verification passed (should not happen!)\n");
                    } catch (T2zException e) {
                        System.out.println("   ATTACK DETECTED! Verification failed:");
                        System.out.println("   Error: " + e.getMessage() + "\n");
                        System.out.println("   Transaction NOT signed - funds are SAFE!\n");
                    }
                }

                // =================================================================
                // SCENARIO 3: Attack - Wrong Recipient
                // =================================================================
                System.out.println("=".repeat(70));
                System.out.println("  SCENARIO 3: Attack - Wrong Recipient");
                System.out.println("=".repeat(70));
                System.out.println();

                System.out.println("ATTACK: Attacker replaces recipient with their own address\n");

                List<Payment> attackedPayments2 = Collections.singletonList(
                        new Payment(ATTACKER_ADDRESS, paymentAmount)
                );

                try (TransactionRequest maliciousRequest2 = new TransactionRequest(attackedPayments2)) {
                    System.out.println("User verifies PCZT before signing...");
                    try {
                        T2z.verifyBeforeSigning(proved, maliciousRequest2, Collections.emptyList());
                        System.out.println("   DANGER: Verification passed (should not happen!)\n");
                    } catch (T2zException e) {
                        System.out.println("   ATTACK DETECTED! Verification failed:");
                        System.out.println("   Error: " + e.getMessage() + "\n");
                        System.out.println("   Transaction NOT signed - funds are SAFE!\n");
                    }
                }

                // =================================================================
                // SCENARIO 4: Attack - Lower Amount
                // =================================================================
                System.out.println("=".repeat(70));
                System.out.println("  SCENARIO 4: Attack - Different Payment Amount (Lower)");
                System.out.println("=".repeat(70));
                System.out.println();

                System.out.println("ATTACK: Attacker claims user only wants to send half the amount\n");
                long lowerAmount = paymentAmount / 2;

                List<Payment> attackedPayments3 = Collections.singletonList(
                        new Payment(VICTIM_ADDRESS, lowerAmount)
                );

                try (TransactionRequest maliciousRequest3 = new TransactionRequest(attackedPayments3)) {
                    System.out.println("User verifies PCZT before signing...");
                    try {
                        T2z.verifyBeforeSigning(proved, maliciousRequest3, Collections.emptyList());
                        System.out.println("   DANGER: Verification passed (should not happen!)\n");
                    } catch (T2zException e) {
                        System.out.println("   ATTACK DETECTED! Verification failed:");
                        System.out.println("   Error: " + e.getMessage() + "\n");
                        System.out.println("   Transaction NOT signed - funds are SAFE!\n");
                    }
                }

                // Clean up the proved PCZT
                proved.close();
            }

            // =================================================================
            // Security Lessons
            // =================================================================
            System.out.println("=".repeat(70));
            System.out.println("  SECURITY LESSONS");
            System.out.println("=".repeat(70));
            System.out.println();

            System.out.println("KEY TAKEAWAYS:");
            System.out.println();
            System.out.println("1. ALWAYS call verifyBeforeSigning() before signing a PCZT");
            System.out.println("2. Never sign a PCZT from an untrusted source without verification");
            System.out.println("3. The verification checks:");
            System.out.println("   - Payment outputs match your transaction request");
            System.out.println("   - Change outputs match expected values");
            System.out.println("   - Fees are reasonable");
            System.out.println("4. Hardware wallets should implement this verification");
            System.out.println("5. In multi-party workflows, each signer should verify");
            System.out.println();

            System.out.println("REAL-WORLD SCENARIOS WHERE THIS MATTERS:");
            System.out.println();
            System.out.println("- Hardware wallet signing (PCZT comes from computer)");
            System.out.println("- Multi-sig wallets (PCZT passed between co-signers)");
            System.out.println("- Payment processors (PCZT from merchant/customer)");
            System.out.println("- Any scenario where PCZT crosses trust boundaries");
            System.out.println();

            System.out.println("EXAMPLE 4 COMPLETED - Security Features Demonstrated!");
            System.out.println();

        } catch (Exception e) {
            printError("EXAMPLE 4 FAILED", e);
            System.exit(1);
        }
    }

    /**
     * Create P2PKH script from public key.
     * Format: OP_DUP OP_HASH160 <20-byte hash> OP_EQUALVERIFY OP_CHECKSIG
     */
    private static byte[] createP2PKHScript(byte[] pubkey) {
        byte[] pubkeyHash = hash160(pubkey);
        byte[] script = new byte[25];
        script[0] = 0x76;       // OP_DUP
        script[1] = (byte) 0xa9; // OP_HASH160
        script[2] = 0x14;       // Push 20 bytes
        System.arraycopy(pubkeyHash, 0, script, 3, 20);
        script[23] = (byte) 0x88; // OP_EQUALVERIFY
        script[24] = (byte) 0xac; // OP_CHECKSIG
        return script;
    }
}
