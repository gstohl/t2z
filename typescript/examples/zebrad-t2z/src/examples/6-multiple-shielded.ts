/**
 * Example 6: Multiple Shielded Outputs (T→Z×2)
 *
 * Demonstrates sending to multiple shielded recipients:
 * - Use a transparent UTXO as input
 * - Send to two different unified addresses with Orchard receivers
 * - Shows how the library handles multiple Orchard actions
 *
 * Note: We use the same Orchard address twice with different amounts
 * to demonstrate multiple shielded outputs.
 */

import { ZebraClient } from '../zebra-client.js';
import { TEST_KEYPAIR, signDER } from '../keys.js';
import {
  printWorkflowSummary,
  printBroadcastResult,
  printError,
  zatoshiToZec,
  getMatureCoinbaseUtxos,
  markUtxosSpent,
} from '../utils.js';
import {
  TransactionRequest,
  proposeTransaction,
  proveTransaction,
  getSighash,
  appendSignature,
  finalizeAndExtract,
  verifyBeforeSigning,
  Payment,
} from 't2z';

// Deterministic mainnet unified addresses with Orchard receivers
// Generated from SpendingKey::from_bytes([42u8; 32]) and [43u8; 32]
const SHIELDED_ADDRESS_1 = 'u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz';
// Using same address for simplicity - in real usage these would be different recipients
const SHIELDED_ADDRESS_2 = 'u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz';

interface TestData {
  transparent: {
    address: string;
    publicKey: string;
    privateKey: string;
    wif: string;
  };
}

async function main() {
  console.log('\n' + '='.repeat(70));
  console.log('  EXAMPLE 6: MULTIPLE SHIELDED OUTPUTS (T→Z×2)');
  console.log('='.repeat(70) + '\n');

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

    console.log('Configuration:');
    console.log(`  Source (transparent): ${testData.transparent.address}`);
    console.log(`  Recipient 1 (shielded): ${SHIELDED_ADDRESS_1.slice(0, 25)}...`);
    console.log(`  Recipient 2 (shielded): ${SHIELDED_ADDRESS_2.slice(0, 25)}...`);
    console.log('  Note: Both are Orchard addresses (u1... prefix)\n');

    // Get UTXOs
    console.log('Fetching mature coinbase UTXOs...');
    const utxos = await getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 1);

    if (utxos.length === 0) {
      throw new Error('No UTXOs available. Run setup first.');
    }

    const input = utxos[0];
    console.log(`Using UTXO: ${zatoshiToZec(input.amount)} ZEC\n`);

    // Create two shielded payments
    // Split the available funds (minus fee) between two recipients
    // ZIP-317 fee for 2 Orchard outputs + transparent change
    // With 2 Orchard outputs, there's a dummy output for padding to even,
    // plus potential change handling adds complexity
    const fee = 40_000n; // Higher fee for multiple Orchard actions
    const availableForPayments = input.amount - fee;
    const payment1Amount = availableForPayments / 3n; // ~33%
    const payment2Amount = availableForPayments / 3n; // ~33%
    // Remaining ~33% goes to change

    const payments: Payment[] = [
      {
        address: SHIELDED_ADDRESS_1,
        amount: payment1Amount.toString(),
      },
      {
        address: SHIELDED_ADDRESS_2,
        amount: payment2Amount.toString(),
      },
    ];

    console.log('Creating TransactionRequest with 2 shielded outputs...');
    const request = new TransactionRequest(payments);

    // Mainnet is the default, just set target height
    request.setTargetHeight(2_500_000);
    console.log('   Using mainnet parameters\n');

    printWorkflowSummary(
      'TRANSACTION SUMMARY - MULTIPLE SHIELDED',
      [input],
      payments.map((p) => ({ address: p.address, amount: p.amount })),
      fee
    );

    console.log('WHAT THIS DEMONSTRATES:');
    console.log('   - Single transparent input');
    console.log('   - Two Orchard outputs (shielded recipients)');
    console.log('   - Library creates multiple Orchard actions');
    console.log('   - Each recipient receives private funds\n');

    // Workflow
    console.log('1. Proposing transaction...');
    const pczt = proposeTransaction([input], request);
    console.log('   PCZT created with multiple Orchard outputs\n');

    console.log('2. Proving transaction (generating Orchard ZK proofs)...');
    console.log('   This takes longer with multiple outputs...');
    const startTime = Date.now();
    const proved = proveTransaction(pczt);
    const proofTime = ((Date.now() - startTime) / 1000).toFixed(1);
    console.log(`   Orchard proofs generated! (${proofTime}s)\n`);

    console.log('3. Verifying PCZT...');
    verifyBeforeSigning(proved, request, []);
    console.log('   Verification passed\n');

    console.log('4. Getting sighash...');
    const sighash = getSighash(proved, 0);
    console.log(`   Sighash: ${sighash.toString('hex').slice(0, 32)}...\n`);

    console.log('5. Signing transaction (client-side)...');
    const signature = signDER(sighash, TEST_KEYPAIR);
    console.log(`   Signature: ${signature.toString('hex').slice(0, 32)}...\n`);

    console.log('6. Appending signature...');
    const signed = appendSignature(proved, 0, signature);
    console.log('   Signature appended\n');

    console.log('7. Finalizing transaction...');
    const txBytes = finalizeAndExtract(signed);
    const txHex = txBytes.toString('hex');
    console.log(`   Transaction finalized (${txBytes.length} bytes)`);
    console.log('   Multiple Orchard outputs = larger transaction\n');

    console.log('8. Broadcasting transaction...');
    const txid = await client.sendRawTransaction(txHex);
    printBroadcastResult(txid, txHex);

    // Mark UTXO as spent for subsequent examples
    await markUtxosSpent([input]);

    // Wait for confirmation
    console.log('Waiting for confirmation (internal miner)...');
    const currentHeight = (await client.getBlockchainInfo()).blocks;
    await client.waitForBlocks(currentHeight + 1, 60000);
    console.log('   Transaction confirmed!\n');

    console.log('='.repeat(70));
    console.log('  MULTIPLE SHIELDED OUTPUTS - SUCCESS');
    console.log('='.repeat(70));
    console.log(`\nTXID: ${txid}`);
    console.log(`\nTransaction breakdown:`);
    console.log(`  - Transparent input: ${zatoshiToZec(input.amount)} ZEC`);
    console.log(`  - Shielded output 1: ${zatoshiToZec(payment1Amount)} ZEC`);
    console.log(`  - Shielded output 2: ${zatoshiToZec(payment2Amount)} ZEC`);
    console.log(`  - Change: ~${zatoshiToZec(input.amount - payment1Amount - payment2Amount - fee)} ZEC`);
    console.log(`  - Fee: ${zatoshiToZec(fee)} ZEC\n`);

    console.log('Privacy achieved:');
    console.log('   - Both outputs are in the Orchard shielded pool');
    console.log('   - Amounts are hidden from public view');
    console.log('   - Only recipients can see their incoming funds\n');

    console.log('EXAMPLE 6 COMPLETED SUCCESSFULLY!\n');

    // Cleanup
    request.free();
  } catch (error: any) {
    printError('EXAMPLE 6 FAILED', error);
    process.exit(1);
  }
}

main();
