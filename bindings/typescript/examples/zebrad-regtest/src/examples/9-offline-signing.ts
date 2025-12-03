/**
 * Example 9: Offline Signing (Hardware Wallet / Air-Gapped Device Simulation)
 *
 * Demonstrates the serialize/parse workflow for offline signing:
 * - Online device: Creates PCZT, serializes it, outputs sighash
 * - Offline device: Signs the sighash (never sees full transaction)
 * - Online device: Parses PCZT, appends signature, finalizes
 *
 * Use case: Hardware wallets, air-gapped signing devices, or any scenario
 * where the signing key never touches an internet-connected device.
 */

import { ZebraClient } from '../zebra-client.js';
import { TEST_KEYPAIR, signDER, bytesToHex } from '../keys.js';
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
  serializePczt,
  parsePczt,
  calculateFee,
  Payment,
  TransparentInput,
} from 't2z';

async function main() {
  console.log('\n' + '='.repeat(70));
  console.log('  EXAMPLE 9: OFFLINE SIGNING (Hardware Wallet Simulation)');
  console.log('='.repeat(70) + '\n');

  console.log('This example demonstrates the serialize/parse workflow for offline signing.');
  console.log('The private key NEVER touches the online device!\n');

  const client = new ZebraClient();

  try {
    const keypair = TEST_KEYPAIR;

    console.log('Configuration:');
    console.log(`  Public key (online):  ${bytesToHex(keypair.publicKey).slice(0, 32)}...`);
    console.log(`  Private key (OFFLINE): ${bytesToHex(keypair.privateKey).slice(0, 16)}...`);
    console.log(`  Address: ${keypair.address}`);

    // Fetch mature coinbase UTXOs
    console.log('\nFetching mature coinbase UTXOs...');
    const utxos = await getMatureCoinbaseUtxos(client, keypair, 5);

    if (utxos.length < 1) {
      throw new Error('Need at least 1 mature UTXO. Run setup and wait for maturity.');
    }

    const input = utxos[0];
    const totalInput = input.amount;
    console.log(`  Selected UTXO: ${zatoshiToZec(totalInput)} ZEC\n`);

    // Send back to ourselves
    const destAddress = keypair.address;
    const fee = calculateFee(1, 2, 0);
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
      [input],
      payments.map((p) => ({ address: p.address, amount: p.amount })),
      fee
    );

    // ============================================================
    // ONLINE DEVICE: Build transaction, extract sighash
    // ============================================================
    console.log('='.repeat(70));
    console.log('  ONLINE DEVICE - Transaction Builder');
    console.log('='.repeat(70) + '\n');

    console.log('1. Proposing transaction...');
    const pczt = proposeTransaction([input], request);
    console.log('   PCZT created');

    console.log('\n2. Proving transaction...');
    const proved = proveTransaction(pczt);
    console.log('   Proofs generated');

    console.log('\n3. Serializing PCZT for storage...');
    const pcztBytes = serializePczt(proved);
    console.log(`   PCZT serialized: ${pcztBytes.length} bytes`);

    console.log('\n4. Getting sighash for offline signing...');
    const sighash = getSighash(proved, 0);
    const sighashHex = sighash.toString('hex');
    console.log(`   Sighash: ${sighashHex}`);

    console.log('\n   >>> Transfer this sighash to the OFFLINE device <<<\n');

    // ============================================================
    // OFFLINE DEVICE: Sign the sighash (air-gapped)
    // ============================================================
    console.log('='.repeat(70));
    console.log('  OFFLINE DEVICE - Air-Gapped Signer');
    console.log('='.repeat(70) + '\n');

    console.log('1. Receiving sighash...');
    console.log(`   Sighash: ${sighashHex}`);

    console.log('\n2. Signing with private key (NEVER leaves this device)...');
    const signature = signDER(sighash, keypair);
    const signatureHex = signature.toString('hex');
    console.log(`   Signature: ${signatureHex}`);

    console.log('\n   >>> Transfer this signature back to the ONLINE device <<<\n');

    // ============================================================
    // ONLINE DEVICE: Append signature and finalize
    // ============================================================
    console.log('='.repeat(70));
    console.log('  ONLINE DEVICE - Finalization');
    console.log('='.repeat(70) + '\n');

    console.log('1. Parsing stored PCZT...');
    const loadedPczt = parsePczt(pcztBytes);
    console.log('   PCZT restored from bytes');

    console.log('\n2. Receiving signature from offline device...');
    console.log(`   Signature: ${signatureHex.slice(0, 32)}...`);

    console.log('\n3. Appending signature to PCZT...');
    const signed = appendSignature(loadedPczt, 0, signature);
    console.log('   Signature appended');

    console.log('\n4. Finalizing transaction...');
    const txBytes = finalizeAndExtract(signed);
    const txHex = txBytes.toString('hex');
    console.log(`   Transaction finalized (${txBytes.length} bytes)`);

    console.log('\n5. Broadcasting transaction to network...');
    const txid = await client.sendRawTransaction(txHex);
    printBroadcastResult(txid, txHex);

    // Mark UTXOs as spent
    await markUtxosSpent([input]);

    // Wait for confirmation
    console.log('Waiting for confirmation...');
    const currentHeight = (await client.getBlockchainInfo()).blocks;
    await client.waitForBlocks(currentHeight + 1, 60000);
    console.log('   Confirmed!\n');

    console.log('='.repeat(70));
    console.log('  SUCCESS!');
    console.log('='.repeat(70));
    console.log(`\nTXID: ${txid}`);
    console.log('\nKey security properties:');
    console.log('  - Private key NEVER touched the online device');
    console.log('  - PCZT can be serialized/parsed for transport');
    console.log('  - Sighash is safe to transfer (reveals no private data)\n');

    // Cleanup
    request.free();
  } catch (error: any) {
    printError('EXAMPLE 9 FAILED', error);
    process.exit(1);
  }
}

main();
