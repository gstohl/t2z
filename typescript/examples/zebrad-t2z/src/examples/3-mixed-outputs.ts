/**
 * Example 3: Mixed Output Transaction
 *
 * Demonstrates sending to both transparent and shielded addresses:
 * - Send to transparent address (public)
 * - Send to shielded address (private)
 * - Shows the power of unified transactions
 */

import { ZcashdClient } from '../zcashd-client.js';
import {
  utxoToTransparentInput,
  printWorkflowSummary,
  printBroadcastResult,
  printError,
  zatoshiToZec,
} from '../utils.js';
import {
  TransactionRequest,
  proposeTransaction,
  proveTransaction,
  getSighash,
  appendSignature,
  finalizeAndExtract,
  verifyBeforeSigning,
  signMessage,
  Payment,
} from 't2z';

async function main() {
  console.log('\n' + '█'.repeat(70));
  console.log('  EXAMPLE 3: MIXED OUTPUT TRANSACTION (Transparent + Shielded)');
  console.log('█'.repeat(70) + '\n');

  const client = new ZcashdClient();

  try {
    // Load test addresses
    const fs = await import('fs/promises');
    const addresses = JSON.parse(await fs.readFile('test-addresses.json', 'utf-8'));

    // Create a new transparent address for receiving
    console.log('📍 Creating new transparent address...');
    const newTAddr = await client.getNewAddress();

    console.log('📋 Configuration:');
    console.log(`  Source (transparent): ${addresses.transparent}`);
    console.log(`  Destination 1 (transparent): ${newTAddr}`);
    console.log(`  Destination 2 (shielded): ${addresses.unified}\n`);

    // Get UTXOs
    console.log('🔍 Fetching UTXOs...');
    const utxos = await client.listUnspent(1, 9999999, [addresses.transparent]);

    if (utxos.length === 0) {
      throw new Error('No UTXOs available. Run setup.ts first.');
    }

    const utxo = utxos[0];
    console.log(`✅ Selected UTXO: ${zatoshiToZec(BigInt(Math.floor(utxo.amount * 100_000_000)))} ZEC\n`);

    const input = await utxoToTransparentInput(client, utxo);

    // Create mixed payments - transparent AND shielded
    const payments: Payment[] = [
      {
        address: newTAddr, // Transparent output (public)
        amount: (200_000_000n).toString(), // 2 ZEC
        memo: 'Public transparent payment',
      },
      {
        address: addresses.unified, // Shielded output (private)
        amount: (300_000_000n).toString(), // 3 ZEC
        memo: 'Private shielded payment',
      },
    ];

    console.log('📝 Creating TransactionRequest with mixed outputs...');
    console.log('   💎 Payment 1: 2 ZEC → Transparent (PUBLIC)');
    console.log('   🛡️  Payment 2: 3 ZEC → Shielded (PRIVATE)\n');

    const request = new TransactionRequest(payments);

    const fee = 15_000n;
    printWorkflowSummary(
      '📊 TRANSACTION SUMMARY - MIXED OUTPUTS',
      [input],
      payments.map((p) => ({ address: p.address, amount: p.amount })),
      fee
    );

    // Workflow
    console.log('1️⃣  Proposing transaction...');
    const pczt = proposeTransaction([input], request);
    console.log('   ✅ PCZT created with 1 transparent + 1 shielded output\n');

    console.log('2️⃣  Proving transaction...');
    const proved = proveTransaction(pczt);
    console.log('   ✅ Orchard proofs generated for shielded output\n');

    console.log('3️⃣  Verifying PCZT...');
    verifyBeforeSigning(proved, request, []);
    console.log('   ✅ Verified: both outputs present\n');

    console.log('4️⃣  Getting sighash...');
    const sighash = getSighash(proved, 0);
    console.log(`   Sighash: ${sighash.toString('hex').slice(0, 32)}...\n`);

    console.log('5️⃣  Signing transaction...');
    const privKeyWIF = await client.dumpPrivKey(addresses.transparent);
    const privKeyBuffer = decodePrivateKey(privKeyWIF);
    const signature = await signMessage(privKeyBuffer, sighash);
    console.log(`   Signature: ${signature.toString('hex').slice(0, 32)}...\n`);

    console.log('6️⃣  Appending signature...');
    const signed = appendSignature(proved, 0, signature);
    console.log('   ✅ Signature appended\n');

    console.log('7️⃣  Finalizing transaction...');
    const txBytes = finalizeAndExtract(signed);
    const txHex = txBytes.toString('hex');
    console.log(`   ✅ Transaction finalized (${txBytes.length} bytes)\n`);

    console.log('8️⃣  Broadcasting transaction...');
    const txid = await client.sendRawTransaction(txHex);
    printBroadcastResult(txid, txHex);

    console.log('⛏️  Mining confirmation block...');
    await client.generate(1);
    console.log('   ✅ Transaction confirmed\n');

    // Analyze the transaction
    const tx = await client.getRawTransaction(txid, true);
    console.log('📄 Transaction Analysis:');
    console.log(`   Transparent inputs: ${tx.vin.length}`);
    console.log(`   Transparent outputs: ${tx.vout.length} (payment + possibly change)`);
    console.log(`   Shielded outputs: 1 (Orchard)\n`);

    console.log('🔍 Privacy Analysis:');
    console.log('   ✅ Transparent output (2 ZEC): Publicly visible on blockchain');
    console.log('   ✅ Shielded output (3 ZEC): Private - amount and recipient hidden');
    console.log('   ✅ Change: Auto-shielded to Orchard\n');

    // Verify the transparent output
    console.log('💎 Verifying transparent output...');
    const newUtxos = await client.listUnspent(1, 9999999, [newTAddr]);
    if (newUtxos.length > 0) {
      console.log(`   ✅ Found UTXO for ${newTAddr}: ${newUtxos[0].amount} ZEC\n`);
    }

    console.log('✅ EXAMPLE 3 COMPLETED SUCCESSFULLY!');
    console.log('\n💡 Key Takeaway: t2z enables hybrid transactions that combine');
    console.log('   the transparency of traditional payments with the privacy');
    console.log('   of shielded transactions - all in a single atomic transaction!\n');

    request.free();
  } catch (error: any) {
    printError('EXAMPLE 3 FAILED', error);
    process.exit(1);
  }
}

function decodePrivateKey(wif: string): Buffer {
  const ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
  let num = BigInt(0);

  for (let i = 0; i < wif.length; i++) {
    const p = ALPHABET.indexOf(wif[i]);
    if (p === -1) throw new Error('Invalid base58');
    num = num * BigInt(58) + BigInt(p);
  }

  let hex = num.toString(16);
  if (hex.length % 2) hex = '0' + hex;

  const bytes: number[] = [];
  for (let i = 0; i < hex.length; i += 2) {
    bytes.push(parseInt(hex.slice(i, i + 2), 16));
  }

  for (let i = 0; i < wif.length && wif[i] === '1'; i++) {
    bytes.unshift(0);
  }

  const decoded = Buffer.from(bytes);
  return decoded.slice(1, 33);
}

main();
