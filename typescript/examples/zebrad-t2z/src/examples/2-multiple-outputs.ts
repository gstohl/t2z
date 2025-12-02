/**
 * Example 2: Multiple Output Transaction
 *
 * Demonstrates sending to multiple transparent addresses in a single transaction:
 * - Use multiple coinbase UTXOs as inputs
 * - Create multiple outputs
 * - Show how change is handled
 *
 * Note: Using transparent outputs since Zebra has no wallet for shielded.
 */

import { ZebraClient } from '../zebra-client.js';
import { TEST_KEYPAIR, signDER, generateNewKeypair, bytesToHex } from '../keys.js';
import {
  utxoToTransparentInput,
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
  console.log('  EXAMPLE 2: MULTIPLE OUTPUT TRANSACTION');
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

    console.log('Generating additional destination addresses...');
    // Generate two new addresses for receiving (client-side)
    const dest1 = generateNewKeypair();
    const dest2 = generateNewKeypair();

    console.log('Configuration:');
    console.log(`  Source: ${testData.transparent.address}`);
    console.log(`  Destination 1: ${dest1.address}`);
    console.log(`  Destination 2: ${dest2.address}\n`);

    // Get more UTXOs if available from the blockchain
    console.log('Fetching mature coinbase UTXOs...');
    const freshUtxos = await getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 3);

    if (freshUtxos.length === 0) {
      throw new Error('No mature UTXOs available. Run setup first and wait for block maturity.');
    }

    console.log(`Found ${freshUtxos.length} mature UTXO(s)`);
    freshUtxos.forEach((utxo, i) => {
      console.log(`  [${i}] ${zatoshiToZec(utxo.amount)} ZEC`);
    });
    console.log();

    // Use first UTXO
    const input = freshUtxos[0];
    const totalInput = input.amount;

    // Create multiple payments with dynamic amounts based on available UTXO
    // Calculate fee: 1 input, 3 outputs (2 payments + 1 change), 0 orchard
    const fee = calculateFee(1, 3, 0);
    // Split available funds: 30% each for payments, 40% for change
    const availableForPayments = totalInput - fee;
    const payment1Amount = availableForPayments * 3n / 10n; // 30%
    const payment2Amount = availableForPayments * 3n / 10n; // 30%

    const payments: Payment[] = [
      {
        address: dest1.address,
        amount: payment1Amount.toString(),
      },
      {
        address: dest2.address,
        amount: payment2Amount.toString(),
      },
    ];

    console.log('Creating TransactionRequest with 2 payments...');
    const request = new TransactionRequest(payments);

    // Mainnet is the default, just set target height
    request.setTargetHeight(2_500_000); // Post-NU5 mainnet height
    console.log('   Using mainnet parameters\n');

    printWorkflowSummary(
      'TRANSACTION SUMMARY - TWO RECIPIENTS',
      [input],
      payments.map((p) => ({ address: p.address, amount: p.amount })),
      fee
    );

    // Calculate expected change
    const totalPayments = payment1Amount + payment2Amount;
    const expectedChange = totalInput - totalPayments - fee;
    console.log(`Expected change: ${zatoshiToZec(expectedChange)} ZEC\n`);

    // Workflow
    console.log('1. Proposing transaction...');
    const pczt = proposeTransaction([input], request);
    console.log('   PCZT created with 2 outputs + change\n');

    console.log('2. Proving transaction...');
    const proved = proveTransaction(pczt);
    console.log('   Proofs generated\n');

    console.log('3. Verifying PCZT...');
    verifyBeforeSigning(proved, request, []);
    console.log('   Verified: both payments present\n');

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
    console.log(`   Transaction finalized (${txBytes.length} bytes)\n`);

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

    console.log(`SUCCESS! TXID: ${txid}\n`);

    // Cleanup
    request.free();
  } catch (error: any) {
    printError('EXAMPLE 2 FAILED', error);
    process.exit(1);
  }
}

main();
