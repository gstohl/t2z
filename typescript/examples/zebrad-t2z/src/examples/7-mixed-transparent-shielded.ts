/**
 * Example 7: Mixed Transparent and Shielded Outputs (T→T+Z)
 *
 * Demonstrates sending to both transparent and shielded recipients:
 * - Use a transparent UTXO as input
 * - Send to one transparent address AND one shielded (Orchard) address
 * - Shows how the library handles mixed output types in a single transaction
 *
 * This is a common real-world scenario where you want to pay someone
 * transparently while also shielding some funds.
 */

import { ZebraClient } from '../zebra-client.js';
import { TEST_KEYPAIR, signDER, generateNewKeypair } from '../keys.js';
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

// Deterministic mainnet unified address with Orchard receiver
// Generated from SpendingKey::from_bytes([42u8; 32])
const SHIELDED_ADDRESS = 'u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz';

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
  console.log('  EXAMPLE 7: MIXED TRANSPARENT + SHIELDED OUTPUTS (T→T+Z)');
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

    // Generate a new transparent address for the transparent recipient
    const transparentRecipient = generateNewKeypair();

    console.log('Configuration:');
    console.log(`  Source (transparent): ${testData.transparent.address}`);
    console.log(`  Recipient 1 (transparent): ${transparentRecipient.address}`);
    console.log(`  Recipient 2 (shielded): ${SHIELDED_ADDRESS.slice(0, 30)}...`);
    console.log('  Note: Mixed output types in single transaction\n');

    // Get UTXOs
    console.log('Fetching mature coinbase UTXOs...');
    const utxos = await getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 1);

    if (utxos.length === 0) {
      throw new Error('No UTXOs available. Run setup first.');
    }

    const input = utxos[0];
    console.log(`Using UTXO: ${zatoshiToZec(input.amount)} ZEC\n`);

    // Create mixed payments
    // ZIP-317 fee for 1 transparent + 1 Orchard output + change
    // Mixed transactions need higher fee to account for both output types
    const fee = 30_000n; // Higher fee for mixed outputs
    const availableForPayments = input.amount - fee;

    // Split: 35% to transparent, 35% to shielded, 30% to change
    // Leave more for change to ensure fee estimation has margin
    const transparentPayment = availableForPayments * 35n / 100n;
    const shieldedPayment = availableForPayments * 35n / 100n;

    const payments: Payment[] = [
      {
        address: transparentRecipient.address,
        amount: transparentPayment.toString(),
      },
      {
        address: SHIELDED_ADDRESS,
        amount: shieldedPayment.toString(),
      },
    ];

    console.log('Creating TransactionRequest with mixed outputs...');
    const request = new TransactionRequest(payments);

    // Mainnet is the default, just set target height
    request.setTargetHeight(2_500_000);
    console.log('   Using mainnet parameters\n');

    printWorkflowSummary(
      'TRANSACTION SUMMARY - MIXED T+Z',
      [input],
      payments.map((p) => ({ address: p.address, amount: p.amount })),
      fee
    );

    console.log('WHAT THIS DEMONSTRATES:');
    console.log('   - Single transparent input');
    console.log('   - One transparent output (publicly visible)');
    console.log('   - One Orchard output (shielded/private)');
    console.log('   - Change returned to source address');
    console.log('   - Real-world use case: pay merchant + shield savings\n');

    // Workflow
    console.log('1. Proposing transaction...');
    const pczt = proposeTransaction([input], request);
    console.log('   PCZT created with mixed outputs\n');

    console.log('2. Proving transaction (generating Orchard ZK proofs)...');
    console.log('   This may take a few seconds...');
    const startTime = Date.now();
    const proved = proveTransaction(pczt);
    const proofTime = ((Date.now() - startTime) / 1000).toFixed(1);
    console.log(`   Proofs generated! (${proofTime}s)\n`);

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
    console.log('   Mixed outputs = Orchard proofs + transparent outputs\n');

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
    console.log('  MIXED OUTPUTS TRANSACTION - SUCCESS');
    console.log('='.repeat(70));
    console.log(`\nTXID: ${txid}`);
    console.log(`\nTransaction breakdown:`);
    console.log(`  - Transparent input: ${zatoshiToZec(input.amount)} ZEC`);
    console.log(`  - Transparent output: ${zatoshiToZec(transparentPayment)} ZEC (publicly visible)`);
    console.log(`  - Shielded output: ${zatoshiToZec(shieldedPayment)} ZEC (private)`);
    console.log(`  - Change: ~${zatoshiToZec(input.amount - transparentPayment - shieldedPayment - fee)} ZEC`);
    console.log(`  - Fee: ${zatoshiToZec(fee)} ZEC\n`);

    console.log('Privacy analysis:');
    console.log('   - Transparent recipient: amount and address are PUBLIC');
    console.log('   - Shielded recipient: amount and address are PRIVATE');
    console.log('   - Observer can see: input amount, transparent output');
    console.log('   - Observer cannot see: shielded amount, shielded recipient\n');

    console.log('Use cases for mixed transactions:');
    console.log('   - Pay merchant (transparent) + save remainder (shielded)');
    console.log('   - Exchange withdrawal (transparent) + personal wallet (shielded)');
    console.log('   - Tax-reportable payment + private savings\n');

    console.log('EXAMPLE 7 COMPLETED SUCCESSFULLY!\n');

    // Cleanup
    request.free();
  } catch (error: any) {
    printError('EXAMPLE 7 FAILED', error);
    process.exit(1);
  }
}

main();
