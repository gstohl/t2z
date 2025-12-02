/**
 * Example 3: Multiple Inputs Transaction
 *
 * Demonstrates combining multiple UTXOs in a single transaction:
 * - Use multiple coinbase UTXOs as inputs
 * - Sign each input separately
 * - Consolidate funds into fewer outputs
 *
 * This is useful for UTXO consolidation/cleanup.
 */

import { ZebraClient } from '../zebra-client.js';
import { TEST_KEYPAIR, signDER, bytesToHex } from '../keys.js';
import {
  printWorkflowSummary,
  printBroadcastResult,
  printError,
  zatoshiToZec,
  zecToZatoshi,
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
  TransparentInput,
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
  console.log('  EXAMPLE 3: MULTIPLE INPUTS TRANSACTION (UTXO Consolidation)');
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
    console.log(`  Address: ${testData.transparent.address}\n`);

    // Get multiple UTXOs from the blockchain
    console.log('Fetching mature coinbase UTXOs...');
    const utxos = await getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 3);

    if (utxos.length < 2) {
      throw new Error('Need at least 2 mature UTXOs for this example. Wait for more blocks to be mined.');
    }

    console.log(`Found ${utxos.length} mature UTXO(s):`);
    let totalInput = 0n;
    utxos.forEach((utxo, i) => {
      console.log(`  [${i}] ${zatoshiToZec(utxo.amount)} ZEC`);
      totalInput += utxo.amount;
    });
    console.log(`  Total: ${zatoshiToZec(totalInput)} ZEC\n`);

    // Use first 2 UTXOs as inputs
    const inputs = utxos.slice(0, 2);
    const inputTotal = inputs.reduce((sum, u) => sum + u.amount, 0n);

    console.log(`Using ${inputs.length} inputs totaling ${zatoshiToZec(inputTotal)} ZEC\n`);

    // Create a single output back to ourselves (consolidation)
    // Calculate fee: N inputs, 1 output, 0 orchard
    const fee = calculateFee(inputs.length, 1, 0);
    const outputAmount = inputTotal - fee;

    const payments: Payment[] = [
      {
        address: testData.transparent.address,
        amount: outputAmount.toString(),
      },
    ];

    console.log('Creating TransactionRequest...');
    const request = new TransactionRequest(payments);

    // Mainnet is the default, just set target height
    request.setTargetHeight(2_500_000); // Post-NU5 mainnet height
    console.log('   Using mainnet parameters\n');

    printWorkflowSummary(
      'TRANSACTION SUMMARY - UTXO CONSOLIDATION',
      inputs,
      payments.map((p) => ({ address: p.address, amount: p.amount })),
      fee
    );

    // Workflow
    console.log('1. Proposing transaction with multiple inputs...');
    const pczt = proposeTransaction(inputs, request);
    console.log(`   PCZT created with ${inputs.length} inputs\n`);

    console.log('2. Proving transaction...');
    const proved = proveTransaction(pczt);
    console.log('   Proofs generated\n');

    console.log('3. Verifying PCZT...');
    verifyBeforeSigning(proved, request, []);
    console.log('   Verification passed\n');

    // Sign each input
    console.log('4. Getting sighashes and signing each input...');
    let currentPczt = proved;
    for (let i = 0; i < inputs.length; i++) {
      console.log(`   Input ${i}:`);
      const sighash = getSighash(currentPczt, i);
      console.log(`     Sighash: ${sighash.toString('hex').slice(0, 24)}...`);

      const signature = signDER(sighash, TEST_KEYPAIR);
      console.log(`     Signature: ${signature.toString('hex').slice(0, 24)}...`);

      currentPczt = appendSignature(currentPczt, i, signature);
      console.log(`     Signature appended`);
    }
    console.log();

    console.log('5. Finalizing transaction...');
    const txBytes = finalizeAndExtract(currentPczt);
    const txHex = txBytes.toString('hex');
    console.log(`   Transaction finalized (${txBytes.length} bytes)\n`);

    console.log('6. Broadcasting transaction...');
    const txid = await client.sendRawTransaction(txHex);
    printBroadcastResult(txid, txHex);

    // Mark UTXOs as spent for subsequent examples
    await markUtxosSpent(inputs);

    // Wait for confirmation
    console.log('Waiting for confirmation...');
    const currentHeight = (await client.getBlockchainInfo()).blocks;
    await client.waitForBlocks(currentHeight + 1, 60000);
    console.log('   Confirmed!\n');

    console.log(`SUCCESS! TXID: ${txid}`);
    console.log(`   ${inputs.length} UTXOs consolidated into 1\n`);

    // Cleanup
    request.free();
  } catch (error: any) {
    printError('EXAMPLE 3 FAILED', error);
    process.exit(1);
  }
}

main();
