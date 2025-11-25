/**
 * Example 1: Single Output Transaction
 *
 * Demonstrates the basic t2z workflow:
 * - Select a transparent UTXO
 * - Create a payment to a single Orchard (shielded) address
 * - Propose, prove, sign, and broadcast the transaction
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
  console.log('  EXAMPLE 1: SINGLE OUTPUT TRANSACTION (Transparent → Orchard)');
  console.log('█'.repeat(70) + '\n');

  const client = new ZcashdClient();

  try {
    // Load test addresses
    const fs = await import('fs/promises');
    const addresses = JSON.parse(await fs.readFile('test-addresses.json', 'utf-8'));

    console.log('📋 Configuration:');
    console.log(`  Source (transparent): ${addresses.transparent}`);
    console.log(`  Destination (unified): ${addresses.unified}\n`);

    // Get UTXOs for the transparent address
    console.log('🔍 Fetching UTXOs...');
    const utxos = await client.listUnspent(1, 9999999, [addresses.transparent]);

    if (utxos.length === 0) {
      throw new Error('No UTXOs available. Run setup.ts first.');
    }

    console.log(`Found ${utxos.length} UTXO(s)`);
    utxos.forEach((utxo, i) => {
      console.log(`  [${i}] ${utxo.txid.slice(0, 16)}...  ${utxo.amount} ZEC`);
    });

    // Use the first UTXO
    const utxo = utxos[0];
    console.log(`\n✅ Selected UTXO: ${zatoshiToZec(BigInt(Math.floor(utxo.amount * 100_000_000)))} ZEC\n`);

    // Convert UTXO to TransparentInput
    console.log('🔄 Converting UTXO to TransparentInput...');
    const input = await utxoToTransparentInput(client, utxo);

    // Create payment - send 0.5 ZEC to unified address
    const paymentAmount = 50_000_000n; // 0.5 ZEC in zatoshis
    const payments: Payment[] = [
      {
        address: addresses.unified,
        amount: paymentAmount.toString(),
      },
    ];

    console.log('📝 Creating TransactionRequest...');
    const request = new TransactionRequest(payments);

    // Print workflow summary
    const fee = 15_000n; // ZIP 317 fee for transparent->shielded
    printWorkflowSummary(
      '📊 TRANSACTION SUMMARY',
      [input],
      payments.map((p) => ({ address: p.address, amount: p.amount })),
      fee
    );

    // Step 1: Propose transaction
    console.log('1️⃣  Proposing transaction...');
    const pczt = proposeTransaction([input], request);
    console.log('   ✅ PCZT created\n');

    // Step 2: Prove transaction (add Orchard proofs)
    console.log('2️⃣  Proving transaction (generating Orchard proofs)...');
    const proved = proveTransaction(pczt);
    console.log('   ✅ Proofs generated\n');

    // Step 3: Verify before signing
    console.log('3️⃣  Verifying PCZT before signing...');
    verifyBeforeSigning(proved, request, []);
    console.log('   ✅ Verification passed\n');

    // Step 4: Get sighash for the transparent input
    console.log('4️⃣  Getting sighash for input 0...');
    const sighash = getSighash(proved, 0);
    console.log(`   Sighash: ${sighash.toString('hex').slice(0, 32)}...\n`);

    // Step 5: Sign the sighash
    console.log('5️⃣  Signing transaction...');
    // Get private key from zcashd
    const privKeyWIF = await client.dumpPrivKey(addresses.transparent);

    // For this example, we'll use t2z's signMessage function
    // In production, you'd use hardware wallet or secure key management
    const privKeyBuffer = decodePrivateKey(privKeyWIF);
    const signature = await signMessage(privKeyBuffer, sighash);
    console.log(`   Signature: ${signature.toString('hex').slice(0, 32)}...\n`);

    // Step 6: Append signature to PCZT
    console.log('6️⃣  Appending signature to PCZT...');
    const signed = appendSignature(proved, 0, signature);
    console.log('   ✅ Signature appended\n');

    // Step 7: Finalize and extract transaction bytes
    console.log('7️⃣  Finalizing transaction...');
    const txBytes = finalizeAndExtract(signed);
    const txHex = txBytes.toString('hex');
    console.log(`   ✅ Transaction finalized (${txBytes.length} bytes)\n`);

    // Step 8: Broadcast transaction
    console.log('8️⃣  Broadcasting transaction to network...');
    const txid = await client.sendRawTransaction(txHex);
    printBroadcastResult(txid, txHex);

    // Mine a block to confirm
    console.log('⛏️  Mining confirmation block...');
    await client.generate(1);
    console.log('   ✅ Transaction confirmed\n');

    // Get transaction details
    const tx = await client.getRawTransaction(txid, true);
    console.log('📄 Transaction Details:');
    console.log(`   TXID: ${tx.txid}`);
    console.log(`   Inputs: ${tx.vin.length}`);
    console.log(`   Outputs: ${tx.vout.length}`);
    console.log(`   Confirmations: ${tx.confirmations || 0}\n`);

    console.log('✅ EXAMPLE 1 COMPLETED SUCCESSFULLY!\n');

    // Cleanup
    request.free();
  } catch (error: any) {
    printError('EXAMPLE 1 FAILED', error);
    process.exit(1);
  }
}

/**
 * Decode WIF private key to raw 32 bytes
 */
function decodePrivateKey(wif: string): Buffer {
  // Simple WIF decoder for regtest
  // In production, use a proper library like 'wif' from npm
  const bs58 = require_bs58();
  const decoded = bs58.decode(wif);

  // Remove version byte (first) and checksum (last 4 bytes)
  // Also handle compressed flag if present
  if (decoded.length === 38) {
    // Compressed key (33 bytes + 1 compression flag + 4 checksum)
    return decoded.slice(1, 33);
  } else if (decoded.length === 37) {
    // Uncompressed key (32 bytes + 4 checksum)
    return decoded.slice(1, 33);
  }

  throw new Error('Invalid WIF format');
}

/**
 * Simple base58 implementation
 */
function require_bs58() {
  const ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';

  return {
    decode(str: string): Buffer {
      const bytes: number[] = [];
      let num = BigInt(0);

      for (let i = 0; i < str.length; i++) {
        const c = str[i];
        const p = ALPHABET.indexOf(c);
        if (p === -1) throw new Error(`Invalid base58 character: ${c}`);
        num = num * BigInt(58) + BigInt(p);
      }

      // Convert to hex and then to bytes
      let hex = num.toString(16);
      if (hex.length % 2) hex = '0' + hex;

      for (let i = 0; i < hex.length; i += 2) {
        bytes.push(parseInt(hex.slice(i, i + 2), 16));
      }

      // Handle leading zeros
      for (let i = 0; i < str.length && str[i] === '1'; i++) {
        bytes.unshift(0);
      }

      return Buffer.from(bytes);
    },
  };
}

main();
