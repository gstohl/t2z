/**
 * Example 5: Transparent to Shielded Transaction (T→Z)
 *
 * Demonstrates the core t2z workflow - sending from transparent to shielded:
 * - Use a transparent UTXO as input
 * - Send to a unified address with Orchard receiver
 * - The library creates Orchard proofs automatically
 *
 * Note: This demonstrates creating shielded outputs. Since Zebra has no wallet,
 * we cannot verify receipt, but the transaction is validated and confirmed.
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
  calculateFee,
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
  console.log('  EXAMPLE 5: TRANSPARENT TO SHIELDED (T→Z)');
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
    console.log(`  Destination (shielded): ${SHIELDED_ADDRESS.slice(0, 30)}...`);
    console.log('  Note: This is an Orchard address (u1... prefix = mainnet)\n');

    // Get UTXOs
    console.log('Fetching mature coinbase UTXOs...');
    const utxos = await getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 1);

    if (utxos.length === 0) {
      throw new Error('No UTXOs available. Run setup first.');
    }

    const input = utxos[0];
    console.log(`Using UTXO: ${zatoshiToZec(input.amount)} ZEC\n`);

    // Create shielded payment (use 50% of UTXO for payment)
    // Calculate fee: 1 input, 1 transparent change, 1 orchard output
    const fee = calculateFee(1, 1, 1);
    const paymentAmount = input.amount / 2n;

    const payments: Payment[] = [
      {
        address: SHIELDED_ADDRESS,
        amount: paymentAmount.toString(),
      },
    ];

    console.log('Creating TransactionRequest for shielded output...');
    const request = new TransactionRequest(payments);

    // Mainnet is the default, just set target height
    request.setTargetHeight(2_500_000);
    console.log('   Using mainnet parameters\n');

    printWorkflowSummary(
      'TRANSACTION SUMMARY - T→Z SHIELDED',
      [input],
      payments.map((p) => ({ address: p.address, amount: p.amount })),
      fee
    );

    console.log('KEY DIFFERENCE from T→T:');
    console.log('   - Payment address is a unified address (u1...)');
    console.log('   - Library creates Orchard actions with zero-knowledge proofs');
    console.log('   - Funds become private after this transaction\n');

    // Workflow
    console.log('1. Proposing transaction...');
    const pczt = proposeTransaction([input], request);
    console.log('   PCZT created with Orchard output\n');

    console.log('2. Proving transaction (generating Orchard ZK proofs)...');
    console.log('   This may take a few seconds...');
    const proved = proveTransaction(pczt);
    console.log('   Orchard proofs generated!\n');

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
    console.log('   Note: T→Z transactions are larger due to Orchard proofs\n');

    console.log('8. Broadcasting transaction...');
    const txid = await client.sendRawTransaction(txHex);
    printBroadcastResult(txid, txHex);

    // Mark UTXO as spent for subsequent examples
    await markUtxosSpent([input]);

    // Wait for confirmation
    console.log('Waiting for confirmation...');
    const currentHeight = (await client.getBlockchainInfo()).blocks;
    await client.waitForBlocks(currentHeight + 1, 60000);
    console.log('   Confirmed!\n');

    console.log(`SUCCESS! TXID: ${txid}`);
    console.log(`   Shielded ${zatoshiToZec(paymentAmount)} ZEC to Orchard\n`);

    // Cleanup
    request.free();
  } catch (error: any) {
    printError('EXAMPLE 5 FAILED', error);
    process.exit(1);
  }
}

main();
