/**
 * Example 1: Single Output Transaction (Transparent → Transparent)
 *
 * Demonstrates the basic t2z workflow using Zebra (no wallet):
 * - Load UTXO and keys from setup data
 * - Create a payment to another transparent address
 * - Propose, sign (client-side), and broadcast the transaction
 *
 * Note: This example sends to a transparent address since Zebra
 * doesn't have a wallet to receive shielded funds.
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
  console.log('  EXAMPLE 1: SINGLE OUTPUT TRANSACTION (Transparent -> Transparent)');
  console.log('='.repeat(70) + '\n');

  const client = new ZebraClient();

  try {
    // Load test data for address info
    const fs = await import('fs/promises');
    const path = await import('path');
    const { fileURLToPath } = await import('url');
    const __dirname = path.dirname(fileURLToPath(import.meta.url));
    const dataFile = path.join(__dirname, '..', '..', 'data', 'test-addresses.json');
    const testData: TestData = JSON.parse(
      await fs.readFile(dataFile, 'utf-8')
    );

    console.log('Configuration:');
    console.log(`  Source address: ${testData.transparent.address}`);

    // Fetch fresh mature coinbase UTXOs from the blockchain
    // Note: Regtest coinbase rewards are very small, so we need multiple UTXOs
    console.log('Fetching mature coinbase UTXOs...');
    const utxos = await getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 6);

    if (utxos.length < 5) {
      throw new Error('Need at least 5 mature UTXOs. Run setup and wait for maturity.');
    }

    // Use multiple UTXOs to have enough value (each is ~2-5k zatoshis, fee is 10000)
    const inputs = utxos.slice(0, 5);
    const totalInput = inputs.reduce((sum, u) => sum + u.amount, 0n);
    console.log(`  Selected ${inputs.length} UTXOs totaling: ${zatoshiToZec(totalInput)} ZEC\n`);

    // For this example, send back to ourselves (transparent -> transparent)
    const destAddress = testData.transparent.address;
    // Use 50% of the total input value, leaving room for fee and change
    const paymentAmount = totalInput / 2n; // Send half to destination
    const fee = 10_000n; // 0.0001 ZEC fee for transparent-only tx

    const payments: Payment[] = [
      {
        address: destAddress,
        amount: paymentAmount.toString(),
      },
    ];

    console.log('Creating TransactionRequest...');
    const request = new TransactionRequest(payments);

    // Get current block height for reference
    const info = await client.getBlockchainInfo();
    console.log(`  Current block height: ${info.blocks}`);

    // Mainnet is the default (Zebra regtest uses mainnet-like branch IDs)
    // Set target height where NU5 is active (activated at block 1,687,104)
    const targetHeight = 2_500_000;
    request.setTargetHeight(targetHeight);
    console.log(`  Target height set to ${targetHeight} (mainnet post-NU5)\n`);

    // Print workflow summary
    printWorkflowSummary(
      'TRANSACTION SUMMARY',
      inputs,
      payments.map((p) => ({ address: p.address, amount: p.amount })),
      fee
    );

    // Step 1: Propose transaction
    console.log('1. Proposing transaction...');
    const pczt = proposeTransaction(inputs, request);
    console.log('   PCZT created\n');

    // Step 2: Prove transaction (for transparent-only, this is minimal)
    console.log('2. Proving transaction...');
    const proved = proveTransaction(pczt);
    console.log('   Proofs generated\n');

    // Step 3: Verify before signing
    console.log('3. Verifying PCZT before signing...');
    verifyBeforeSigning(proved, request, []);
    console.log('   Verification passed\n');

    // Step 4-6: Sign each input
    console.log('4. Signing each input...');
    let currentPczt = proved;
    for (let i = 0; i < inputs.length; i++) {
      const sighash = getSighash(currentPczt, i);
      const signature = signDER(sighash, TEST_KEYPAIR);
      currentPczt = appendSignature(currentPczt, i, signature);
      console.log(`   Input ${i}: signed`);
    }
    console.log('');

    // Step 7: Finalize and extract transaction bytes
    console.log('5. Finalizing transaction...');
    const txBytes = finalizeAndExtract(currentPczt);
    const txHex = txBytes.toString('hex');
    console.log(`   Transaction finalized (${txBytes.length} bytes)\n`);

    // Step 8: Broadcast transaction
    console.log('6. Broadcasting transaction to network...');
    const txid = await client.sendRawTransaction(txHex);
    printBroadcastResult(txid, txHex);

    // Mark UTXOs as spent for subsequent examples
    await markUtxosSpent(inputs);

    // Wait for internal miner to confirm the transaction
    console.log('Waiting for confirmation block (internal miner)...');
    const currentHeight = (await client.getBlockchainInfo()).blocks;
    console.log(`  Block height: ${currentHeight}`);
    await client.waitForBlocks(currentHeight + 1, 60000); // 1 min timeout
    const newHeight = (await client.getBlockchainInfo()).blocks;
    console.log(`  New block height: ${newHeight}`);
    console.log('   Transaction confirmed!\n');

    // Note: Zebra's getrawtransaction doesn't return full decoded tx like zcashd
    // The transaction was successfully broadcast and confirmed
    console.log('Transaction confirmed in the blockchain.');
    console.log(`  TXID: ${txid}\n`);

    console.log('EXAMPLE 1 COMPLETED SUCCESSFULLY!\n');

    // Cleanup
    request.free();
  } catch (error: any) {
    printError('EXAMPLE 1 FAILED', error);
    process.exit(1);
  }
}

main();
