/**
 * Example 2: Multiple Output Transaction
 *
 * Demonstrates sending to multiple Orchard addresses in a single transaction:
 * - Select transparent UTXOs
 * - Create multiple shielded payments
 * - Show how change is handled
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
  console.log('  EXAMPLE 2: MULTIPLE OUTPUT TRANSACTION');
  console.log('█'.repeat(70) + '\n');

  const client = new ZcashdClient();

  try {
    // Load test addresses
    const fs = await import('fs/promises');
    const addresses = JSON.parse(await fs.readFile('test-addresses.json', 'utf-8'));

    // Create additional Orchard accounts for this example
    console.log('🛡️  Creating additional Orchard accounts...');
    const account2 = await client.createAccount();
    const account3 = await client.createAccount();

    console.log('📋 Configuration:');
    console.log(`  Source: ${addresses.transparent}`);
    console.log(`  Destination 1: ${addresses.unified}`);
    console.log(`  Destination 2: ${account2.address}`);
    console.log(`  Destination 3: ${account3.address}\n`);

    // Get UTXOs
    console.log('🔍 Fetching UTXOs...');
    const utxos = await client.listUnspent(1, 9999999, [addresses.transparent]);

    if (utxos.length === 0) {
      throw new Error('No UTXOs available. Run setup.ts first.');
    }

    // Use first UTXO
    const utxo = utxos[0];
    console.log(`✅ Selected UTXO: ${zatoshiToZec(BigInt(Math.floor(utxo.amount * 100_000_000)))} ZEC\n`);

    const input = await utxoToTransparentInput(client, utxo);

    // Create multiple payments
    const payments: Payment[] = [
      {
        address: addresses.unified,
        amount: (100_000_000n).toString(), // 1 ZEC
      },
      {
        address: account2.address,
        amount: (50_000_000n).toString(), // 0.5 ZEC
      },
      {
        address: account3.address,
        amount: (25_000_000n).toString(), // 0.25 ZEC
      },
    ];

    console.log('📝 Creating TransactionRequest with 3 payments...');
    const request = new TransactionRequest(payments);

    const fee = 15_000n;
    printWorkflowSummary(
      '📊 TRANSACTION SUMMARY - THREE RECIPIENTS',
      [input],
      payments.map((p) => ({ address: p.address, amount: p.amount })),
      fee
    );

    // Workflow
    console.log('1️⃣  Proposing transaction...');
    const pczt = proposeTransaction([input], request);
    console.log('   ✅ PCZT created with 3 Orchard outputs\n');

    console.log('2️⃣  Proving transaction...');
    const proved = proveTransaction(pczt);
    console.log('   ✅ Generated 3 Orchard proofs\n');

    console.log('3️⃣  Verifying PCZT...');
    verifyBeforeSigning(proved, request, []);
    console.log('   ✅ Verified: all 3 payments present\n');

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

    // Get transaction details
    const tx = await client.getRawTransaction(txid, true);
    console.log('📄 Transaction Analysis:');
    console.log(`   Total sent to shielded addresses: 1.75 ZEC`);
    console.log(`   Fee: ${zatoshiToZec(fee)} ZEC`);
    console.log(`   Change (auto-shielded): ${zatoshiToZec(input.amount - 175_000_000n - fee)} ZEC`);
    console.log(`   Transparent inputs: ${tx.vin.length}`);
    console.log(`   Orchard outputs: 4 (3 payments + 1 change)\n`);

    console.log('✅ EXAMPLE 2 COMPLETED SUCCESSFULLY!\n');

    request.free();
  } catch (error: any) {
    printError('EXAMPLE 2 FAILED', error);
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
