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

import { ZebraClient } from '../zebra-client.js';
import { TEST_KEYPAIR, generateNewKeypair } from '../keys.js';
import {
  printError,
  zatoshiToZec,
  zecToZatoshi,
  getMatureCoinbaseUtxos,
} from '../utils.js';
import {
  TransactionRequest,
  proposeTransaction,
  proveTransaction,
  verifyBeforeSigning,
  Payment,
} from 't2z';

interface TestData {
  transparent: {
    address: string;
    publicKey: string;
    privateKey: string;
    wif: string;
  };
  utxos: Array<{
    txid: string;
    vout: number;
    amount: number;
    scriptPubKey: string;
  }>;
}

async function main() {
  console.log('\n' + '='.repeat(70));
  console.log('  EXAMPLE 4: ATTACK SCENARIO - PCZT Malleation Detection');
  console.log('='.repeat(70) + '\n');

  console.log('WARNING: This demonstrates a security feature!');
  console.log('   This example shows why you MUST verify PCZTs before signing.\n');

  const client = new ZebraClient();

  try {
    // Load test data from setup
    const fs = await import('fs/promises');
    const path = await import('path');
    const { fileURLToPath } = await import('url');
    const __dirname = path.dirname(fileURLToPath(import.meta.url));
    const dataFile = path.join(__dirname, '..', '..', 'data', 'test-addresses.json');
    const testData: TestData = JSON.parse(
      await fs.readFile(dataFile, 'utf-8')
    );

    // Generate an "attacker's" address
    console.log('Creating "attacker" address...');
    const attackerKeypair = generateNewKeypair();

    console.log('Scenario Setup:');
    console.log(`  Victim's address: ${testData.transparent.address}`);
    console.log(`  Legitimate recipient: ${testData.transparent.address}`);
    console.log(`  Attacker's address: ${attackerKeypair.address}\n`);

    // Get UTXOs
    console.log('Fetching mature coinbase UTXOs...');
    const utxos = await getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 1);

    if (utxos.length === 0) {
      throw new Error('No UTXOs available. Run setup first.');
    }

    const input = utxos[0];
    console.log(`Using UTXO: ${zatoshiToZec(input.amount)} ZEC\n`);

    console.log('='.repeat(70));
    console.log('  SCENARIO 1: Legitimate Transaction');
    console.log('='.repeat(70) + '\n');

    // Legitimate payment (use 50% of UTXO value for fee room)
    const paymentAmount = input.amount / 2n;
    const legitimatePayments: Payment[] = [
      {
        address: testData.transparent.address,
        amount: paymentAmount.toString(),
      },
    ];

    console.log('User creates legitimate payment:');
    console.log(`   Send ${zatoshiToZec(paymentAmount)} ZEC -> ${testData.transparent.address.slice(0, 30)}...\n`);

    const legitimateRequest = new TransactionRequest(legitimatePayments);

    // Configure for Zebra regtest (uses mainnet branch IDs)
    legitimateRequest.setUseMainnet(true);
    legitimateRequest.setTargetHeight(2_500_000);

    console.log('1. Proposing legitimate transaction...');
    const pczt = proposeTransaction([input], legitimateRequest);
    console.log('   PCZT created\n');

    console.log('2. Proving transaction...');
    const proved = proveTransaction(pczt);
    console.log('   Proofs generated\n');

    console.log('3. Verifying PCZT (BEFORE signing)...');
    try {
      verifyBeforeSigning(proved, legitimateRequest, []);
      console.log('   VERIFICATION PASSED - Safe to sign!\n');
    } catch (error: any) {
      console.log(`   VERIFICATION FAILED: ${error.message}\n`);
    }

    console.log('='.repeat(70));
    console.log('  SCENARIO 2: Attack - Wrong Payment Amount');
    console.log('='.repeat(70) + '\n');

    // Attacker tries to modify the payment amount
    console.log('ATTACK: Attacker intercepts PCZT and creates different request');
    const wrongAmount = paymentAmount * 2n; // Double the payment amount - WRONG!
    console.log(`   Attacker claims payment is ${zatoshiToZec(wrongAmount)} ZEC (instead of ${zatoshiToZec(paymentAmount)} ZEC)\n`);

    const attackedRequest1: Payment[] = [
      {
        address: testData.transparent.address,
        amount: wrongAmount.toString(), // WRONG amount!
      },
    ];

    const maliciousRequest1 = new TransactionRequest(attackedRequest1);

    console.log('User verifies PCZT before signing...');
    try {
      verifyBeforeSigning(proved, maliciousRequest1, []);
      console.log('   DANGER: Verification passed (should not happen!)\n');
    } catch (error: any) {
      console.log('   ATTACK DETECTED! Verification failed:');
      console.log(`   Error: ${error.message}\n`);
      console.log('   Transaction NOT signed - funds are SAFE!\n');
    }

    console.log('='.repeat(70));
    console.log('  SCENARIO 3: Attack - Wrong Recipient');
    console.log('='.repeat(70) + '\n');

    console.log('ATTACK: Attacker replaces recipient with their own address\n');

    const attackedRequest2: Payment[] = [
      {
        address: attackerKeypair.address, // Attacker's address!
        amount: paymentAmount.toString(),
      },
    ];

    const maliciousRequest2 = new TransactionRequest(attackedRequest2);

    console.log('User verifies PCZT before signing...');
    try {
      verifyBeforeSigning(proved, maliciousRequest2, []);
      console.log('   DANGER: Verification passed (should not happen!)\n');
    } catch (error: any) {
      console.log('   ATTACK DETECTED! Verification failed:');
      console.log(`   Error: ${error.message}\n`);
      console.log('   Transaction NOT signed - funds are SAFE!\n');
    }

    console.log('='.repeat(70));
    console.log('  SCENARIO 4: Attack - Different Payment Amount (Lower)');
    console.log('='.repeat(70) + '\n');

    console.log('ATTACK: Attacker claims user only wants to send half the amount\n');
    const lowerAmount = paymentAmount / 2n;

    const attackedRequest3: Payment[] = [
      {
        address: testData.transparent.address,
        amount: lowerAmount.toString(),
      },
    ];
    const maliciousRequest3 = new TransactionRequest(attackedRequest3);

    console.log('User verifies PCZT before signing...');
    try {
      verifyBeforeSigning(proved, maliciousRequest3, []);
      console.log('   DANGER: Verification passed (should not happen!)\n');
    } catch (error: any) {
      console.log('   ATTACK DETECTED! Verification failed:');
      console.log(`   Error: ${error.message}\n`);
      console.log('   Transaction NOT signed - funds are SAFE!\n');
    }

    console.log('='.repeat(70));
    console.log('  SECURITY LESSONS');
    console.log('='.repeat(70) + '\n');

    console.log('KEY TAKEAWAYS:\n');
    console.log('1. ALWAYS call verifyBeforeSigning() before signing a PCZT');
    console.log('2. Never sign a PCZT from an untrusted source without verification');
    console.log('3. The verification checks:');
    console.log('   - Payment outputs match your transaction request');
    console.log('   - Change outputs match expected values');
    console.log('   - Fees are reasonable');
    console.log('4. Hardware wallets should implement this verification');
    console.log('5. In multi-party workflows, each signer should verify\n');

    console.log('REAL-WORLD SCENARIOS WHERE THIS MATTERS:\n');
    console.log('- Hardware wallet signing (PCZT comes from computer)');
    console.log('- Multi-sig wallets (PCZT passed between co-signers)');
    console.log('- Payment processors (PCZT from merchant/customer)');
    console.log('- Any scenario where PCZT crosses trust boundaries\n');

    console.log('EXAMPLE 4 COMPLETED - Security Features Demonstrated!\n');

    // Cleanup
    legitimateRequest.free();
    maliciousRequest1.free();
    maliciousRequest2.free();
    maliciousRequest3.free();
  } catch (error: any) {
    printError('EXAMPLE 4 FAILED', error);
    process.exit(1);
  }
}

main();
