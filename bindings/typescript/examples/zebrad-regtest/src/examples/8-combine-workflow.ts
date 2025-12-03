/**
 * Example 8: Combine Workflow (Parallel Signing)
 *
 * Demonstrates the `combine` function for multi-party signing workflows:
 * - Create a transaction with multiple inputs
 * - Serialize the PCZT and create copies for parallel signing
 * - Each "signer" signs their input independently
 * - Combine the partially-signed PCZTs into one
 * - Finalize and broadcast
 *
 * Use case: Multiple parties each control different UTXOs and need to
 * co-sign a transaction without sharing private keys.
 */

import { ZebraClient } from '../zebra-client.js';
import { TEST_KEYPAIR, signDER, generateKeypair, ZcashKeypair } from '../keys.js';
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
  combine,
  serializePczt,
  parsePczt,
  calculateFee,
  Payment,
  TransparentInput,
} from 't2z';

async function main() {
  console.log('\n' + '='.repeat(70));
  console.log('  EXAMPLE 8: COMBINE WORKFLOW (Parallel Signing)');
  console.log('='.repeat(70) + '\n');

  console.log('This example demonstrates the combine() function for parallel signing.');
  console.log('In a real scenario, each signer would be on a different device.\n');

  const client = new ZebraClient();

  try {
    // For this example, we use the same keypair for all inputs (simulated)
    // In production, each input would have a different keypair/owner
    const keypair = TEST_KEYPAIR;

    console.log('Configuration:');
    console.log(`  Address: ${keypair.address}`);

    // Fetch mature coinbase UTXOs
    console.log('\nFetching mature coinbase UTXOs...');
    const utxos = await getMatureCoinbaseUtxos(client, keypair, 8);

    if (utxos.length < 6) {
      throw new Error('Need at least 6 mature UTXOs. Run setup and wait for maturity.');
    }

    // Use 3 inputs to demonstrate combine with multiple signers
    const inputs = utxos.slice(0, 3);
    const totalInput = inputs.reduce((sum, u) => sum + u.amount, 0n);
    console.log(`  Selected ${inputs.length} UTXOs totaling: ${zatoshiToZec(totalInput)} ZEC\n`);

    // Send back to ourselves
    const destAddress = keypair.address;
    const fee = calculateFee(inputs.length, 2, 0);
    const paymentAmount = totalInput / 2n;

    const payments: Payment[] = [
      {
        address: destAddress,
        amount: paymentAmount.toString(),
      },
    ];

    console.log('Creating TransactionRequest...');
    const request = new TransactionRequest(payments);

    const info = await client.getBlockchainInfo();
    console.log(`  Current block height: ${info.blocks}`);

    const targetHeight = 2_500_000;
    request.setTargetHeight(targetHeight);
    console.log(`  Target height set to ${targetHeight}\n`);

    printWorkflowSummary(
      'TRANSACTION SUMMARY',
      inputs,
      payments.map((p) => ({ address: p.address, amount: p.amount })),
      fee
    );

    // === COMBINE WORKFLOW ===

    console.log('--- PARALLEL SIGNING WORKFLOW ---\n');

    // Step 1: Create and prove the PCZT
    console.log('1. Creating and proving PCZT...');
    const pczt = proposeTransaction(inputs, request);
    const proved = proveTransaction(pczt);
    console.log('   PCZT created and proved\n');

    // Step 2: Serialize the proved PCZT (this is what you'd send to each signer)
    console.log('2. Serializing PCZT for distribution to signers...');
    const pcztBytes = serializePczt(proved);
    console.log(`   Serialized PCZT: ${pcztBytes.length} bytes\n`);

    // Step 3: Simulate parallel signing by different parties
    // In reality, each party would receive the serialized PCZT,
    // sign their input(s), and return the partially-signed PCZT
    console.log('3. Simulating parallel signing by 3 different parties...\n');

    // Signer A signs input 0
    console.log('   Signer A: Signing input 0...');
    const pcztA = parsePczt(pcztBytes);
    const sighashA = getSighash(pcztA, 0);
    const signatureA = signDER(sighashA, keypair);
    const signedA = appendSignature(pcztA, 0, signatureA);
    const bytesA = serializePczt(signedA);
    console.log('   Signer A: Done (signed input 0)\n');

    // Signer B signs input 1
    console.log('   Signer B: Signing input 1...');
    const pcztB = parsePczt(pcztBytes);
    const sighashB = getSighash(pcztB, 1);
    const signatureB = signDER(sighashB, keypair);
    const signedB = appendSignature(pcztB, 1, signatureB);
    const bytesB = serializePczt(signedB);
    console.log('   Signer B: Done (signed input 1)\n');

    // Signer C signs input 2
    console.log('   Signer C: Signing input 2...');
    const pcztC = parsePczt(pcztBytes);
    const sighashC = getSighash(pcztC, 2);
    const signatureC = signDER(sighashC, keypair);
    const signedC = appendSignature(pcztC, 2, signatureC);
    const bytesC = serializePczt(signedC);
    console.log('   Signer C: Done (signed input 2)\n');

    // Step 4: Combine all partially-signed PCZTs
    console.log('4. Combining partially-signed PCZTs...');
    const combinedPcztA = parsePczt(bytesA);
    const combinedPcztB = parsePczt(bytesB);
    const combinedPcztC = parsePczt(bytesC);

    // combine() takes all partially-signed PCZTs and merges their signatures
    const fullySignedPczt = combine([combinedPcztA, combinedPcztB, combinedPcztC]);
    console.log('   All signatures combined into single PCZT\n');

    // Step 5: Finalize and extract
    console.log('5. Finalizing transaction...');
    const txBytes = finalizeAndExtract(fullySignedPczt);
    const txHex = txBytes.toString('hex');
    console.log(`   Transaction finalized (${txBytes.length} bytes)\n`);

    // Step 6: Broadcast
    console.log('6. Broadcasting transaction to network...');
    const txid = await client.sendRawTransaction(txHex);
    printBroadcastResult(txid, txHex);

    // Mark UTXOs as spent
    await markUtxosSpent(inputs);

    // Wait for confirmation
    console.log('Waiting for confirmation...');
    const currentHeight = (await client.getBlockchainInfo()).blocks;
    await client.waitForBlocks(currentHeight + 1, 60000);
    console.log('   Confirmed!\n');

    console.log(`SUCCESS! TXID: ${txid}`);
    console.log('\nThe combine() function merged signatures from 3 independent signers.\n');

    // Cleanup
    request.free();
  } catch (error: any) {
    printError('EXAMPLE 8 FAILED', error);
    process.exit(1);
  }
}

main();
