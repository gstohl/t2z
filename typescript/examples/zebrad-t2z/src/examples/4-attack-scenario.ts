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

import { ZcashdClient } from '../zcashd-client.js';
import { utxoToTransparentInput, printError, zatoshiToZec } from '../utils.js';
import {
  TransactionRequest,
  proposeTransaction,
  proveTransaction,
  verifyBeforeSigning,
  Payment,
} from 't2z';

async function main() {
  console.log('\n' + '█'.repeat(70));
  console.log('  EXAMPLE 4: ATTACK SCENARIO - PCZT Malleation Detection');
  console.log('█'.repeat(70) + '\n');

  console.log('⚠️  WARNING: This demonstrates a security feature!');
  console.log('   This example shows why you MUST verify PCZTs before signing.\n');

  const client = new ZcashdClient();

  try {
    // Load test addresses
    const fs = await import('fs/promises');
    const addresses = JSON.parse(await fs.readFile('test-addresses.json', 'utf-8'));

    // Create attacker's address
    console.log('🎭 Creating "attacker" address...');
    const attackerAddr = (await client.createAccount()).address;

    console.log('📋 Scenario Setup:');
    console.log(`  Victim's address: ${addresses.transparent}`);
    console.log(`  Legitimate recipient: ${addresses.unified}`);
    console.log(`  Attacker's address: ${attackerAddr}\n`);

    // Get UTXOs
    const utxos = await client.listUnspent(1, 9999999, [addresses.transparent]);
    if (utxos.length === 0) {
      throw new Error('No UTXOs available. Run setup.ts first.');
    }

    const utxo = utxos[0];
    const input = await utxoToTransparentInput(client, utxo);

    console.log('═'.repeat(70));
    console.log('  SCENARIO 1: Legitimate Transaction');
    console.log('═'.repeat(70) + '\n');

    // Legitimate payment
    const legitimatePayments: Payment[] = [
      {
        address: addresses.unified,
        amount: (100_000_000n).toString(), // 1 ZEC
      },
    ];

    console.log('✅ User creates legitimate payment:');
    console.log(`   Send 1 ZEC → ${addresses.unified.slice(0, 30)}...\n`);

    const legitimateRequest = new TransactionRequest(legitimatePayments);

    console.log('1️⃣  Proposing legitimate transaction...');
    const pczt = proposeTransaction([input], legitimateRequest);
    console.log('   ✅ PCZT created\n');

    console.log('2️⃣  Proving transaction...');
    const proved = proveTransaction(pczt);
    console.log('   ✅ Proofs generated\n');

    console.log('3️⃣  Verifying PCZT (BEFORE signing)...');
    try {
      verifyBeforeSigning(proved, legitimateRequest, []);
      console.log('   ✅ VERIFICATION PASSED - Safe to sign!\n');
    } catch (error: any) {
      console.log(`   ❌ VERIFICATION FAILED: ${error.message}\n`);
    }

    console.log('═'.repeat(70));
    console.log('  SCENARIO 2: Attack - Wrong Payment Amount');
    console.log('═'.repeat(70) + '\n');

    // Attacker tries to modify the payment amount
    console.log('🎭 ATTACK: Attacker intercepts PCZT and creates different request');
    console.log('   Attacker changes payment to 5 ZEC (instead of 1 ZEC)\n');

    const attackedRequest1: Payment[] = [
      {
        address: addresses.unified,
        amount: (500_000_000n).toString(), // 5 ZEC - WRONG!
      },
    ];

    const maliciousRequest1 = new TransactionRequest(attackedRequest1);

    console.log('🛡️  User verifies PCZT before signing...');
    try {
      verifyBeforeSigning(proved, maliciousRequest1, []);
      console.log('   ❌ DANGER: Verification passed (should not happen!)\n');
    } catch (error: any) {
      console.log('   ✅ ATTACK DETECTED! Verification failed:');
      console.log(`   Error: ${error.message}\n`);
      console.log('   🎉 Transaction NOT signed - funds are SAFE!\n');
    }

    console.log('═'.repeat(70));
    console.log('  SCENARIO 3: Attack - Wrong Recipient');
    console.log('═'.repeat(70) + '\n');

    console.log('🎭 ATTACK: Attacker replaces recipient with their own address\n');

    const attackedRequest2: Payment[] = [
      {
        address: attackerAddr, // Attacker's address!
        amount: (100_000_000n).toString(),
      },
    ];

    const maliciousRequest2 = new TransactionRequest(attackedRequest2);

    console.log('🛡️  User verifies PCZT before signing...');
    try {
      verifyBeforeSigning(proved, maliciousRequest2, []);
      console.log('   ❌ DANGER: Verification passed (should not happen!)\n');
    } catch (error: any) {
      console.log('   ✅ ATTACK DETECTED! Verification failed:');
      console.log(`   Error: ${error.message}\n`);
      console.log('   🎉 Transaction NOT signed - funds are SAFE!\n');
    }

    console.log('═'.repeat(70));
    console.log('  SCENARIO 4: Attack - Missing Payment');
    console.log('═'.repeat(70) + '\n');

    console.log('🎭 ATTACK: Attacker removes payment entirely (keeps all as change)\n');

    const attackedRequest3: Payment[] = []; // Empty!
    const maliciousRequest3 = new TransactionRequest(attackedRequest3);

    console.log('🛡️  User verifies PCZT before signing...');
    try {
      verifyBeforeSigning(proved, maliciousRequest3, []);
      console.log('   ❌ DANGER: Verification passed (should not happen!)\n');
    } catch (error: any) {
      console.log('   ✅ ATTACK DETECTED! Verification failed:');
      console.log(`   Error: ${error.message}\n`);
      console.log('   🎉 Transaction NOT signed - funds are SAFE!\n');
    }

    console.log('═'.repeat(70));
    console.log('  SECURITY LESSONS');
    console.log('═'.repeat(70) + '\n');

    console.log('✅ KEY TAKEAWAYS:\n');
    console.log('1. ALWAYS call verifyBeforeSigning() before signing a PCZT');
    console.log('2. Never sign a PCZT from an untrusted source without verification');
    console.log('3. The verification checks:');
    console.log('   - Payment outputs match your transaction request');
    console.log('   - Change outputs match expected values');
    console.log('   - Fees are reasonable');
    console.log('4. Hardware wallets should implement this verification');
    console.log('5. In multi-party workflows, each signer should verify\n');

    console.log('💡 REAL-WORLD SCENARIOS WHERE THIS MATTERS:\n');
    console.log('- Hardware wallet signing (PCZT comes from computer)');
    console.log('- Multi-sig wallets (PCZT passed between co-signers)');
    console.log('- Payment processors (PCZT from merchant/customer)');
    console.log('- Any scenario where PCZT crosses trust boundaries\n');

    console.log('✅ EXAMPLE 4 COMPLETED - Security Features Demonstrated!\n');

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
