/**
 * Utility functions for t2z examples
 */

import * as bitcoin from 'bitcoinjs-lib';
import * as fs from 'fs/promises';
import * as path from 'path';
import { fileURLToPath } from 'url';
import { ZebraClient, UTXO } from './zebra-client.js';
import { ZcashKeypair, signDER, bytesToHex } from './keys.js';
import { TransparentInput } from 't2z';

// Spent UTXO tracking file location
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SPENT_UTXOS_FILE = path.join(__dirname, '..', 'data', 'spent-utxos.json');

/**
 * Load spent UTXOs from file
 */
async function loadSpentUtxos(): Promise<Set<string>> {
  try {
    const data = await fs.readFile(SPENT_UTXOS_FILE, 'utf-8');
    const arr = JSON.parse(data);
    return new Set(arr);
  } catch {
    return new Set();
  }
}

/**
 * Save spent UTXOs to file
 */
async function saveSpentUtxos(spent: Set<string>): Promise<void> {
  await fs.writeFile(SPENT_UTXOS_FILE, JSON.stringify([...spent], null, 2));
}

/**
 * Mark UTXOs as spent (call after successful broadcast)
 */
export async function markUtxosSpent(inputs: TransparentInput[]): Promise<void> {
  const spent = await loadSpentUtxos();
  for (const input of inputs) {
    const key = `${input.txid.toString('hex')}:${input.vout}`;
    spent.add(key);
  }
  await saveSpentUtxos(spent);
}

/**
 * Clear spent UTXOs tracking (call on setup)
 */
export async function clearSpentUtxos(): Promise<void> {
  await saveSpentUtxos(new Set());
}

/**
 * Convert zatoshis to ZEC
 */
export function zatoshiToZec(zatoshi: bigint): string {
  return (Number(zatoshi) / 100_000_000).toFixed(8);
}

/**
 * Convert ZEC to zatoshis
 */
export function zecToZatoshi(zec: number): bigint {
  return BigInt(Math.floor(zec * 100_000_000));
}

/**
 * Reverse a hex string (for txid endianness)
 */
export function reverseHex(hex: string): string {
  return Buffer.from(hex, 'hex').reverse().toString('hex');
}

/**
 * Sign a sighash with a keypair
 * Returns DER-encoded signature suitable for t2z append_signature
 */
export function signSighash(sighash: Buffer, keypair: ZcashKeypair): Buffer {
  return signDER(sighash, keypair);
}

/**
 * Convert UTXO from RPC to TransparentInput format
 */
export function utxoToTransparentInput(
  utxo: UTXO,
  keypair: ZcashKeypair
): TransparentInput {
  // txid needs to be in little-endian (reversed)
  const txid = Buffer.from(reverseHex(utxo.txid), 'hex');

  // Get script pubkey from UTXO or derive from pubkey
  let scriptPubKey: Buffer;
  if (utxo.scriptPubKey) {
    scriptPubKey = Buffer.from(utxo.scriptPubKey, 'hex');
  } else {
    // Derive P2PKH script: OP_DUP OP_HASH160 <pubkeyhash> OP_EQUALVERIFY OP_CHECKSIG
    const pubkeyHash = bitcoin.crypto.hash160(keypair.publicKey);
    scriptPubKey = Buffer.concat([
      Buffer.from([0x76, 0xa9, 0x14]), // OP_DUP OP_HASH160 PUSH20
      pubkeyHash,
      Buffer.from([0x88, 0xac]), // OP_EQUALVERIFY OP_CHECKSIG
    ]);
  }

  return {
    pubkey: keypair.publicKey,
    txid,
    vout: utxo.vout,
    amount: BigInt(utxo.amount), // amount in zatoshis
    scriptPubKey,
  };
}

/**
 * Parse a Zcash transaction hex to extract outputs
 * Note: This is a simplified parser for coinbase transactions
 */
function parseTxOutputs(txHex: string): Array<{ value: bigint; scriptPubKey: Buffer }> {
  const tx = Buffer.from(txHex, 'hex');
  let offset = 0;

  // Skip header (4 bytes version + 4 bytes version group id)
  offset += 8;

  // Read vin count (varint)
  const vinCount = tx[offset];
  offset += 1;

  // Skip all inputs (for coinbase there's 1 input with fixed structure)
  for (let i = 0; i < vinCount; i++) {
    offset += 32; // prev txid
    offset += 4; // prev vout
    const scriptLen = tx[offset];
    offset += 1 + scriptLen; // script length + script
    offset += 4; // sequence
  }

  // Read vout count (varint)
  const voutCount = tx[offset];
  offset += 1;

  const outputs: Array<{ value: bigint; scriptPubKey: Buffer }> = [];

  for (let i = 0; i < voutCount; i++) {
    // Read value (8 bytes, little-endian)
    const value = tx.readBigUInt64LE(offset);
    offset += 8;

    // Read script length (varint) and script
    const scriptLen = tx[offset];
    offset += 1;
    const scriptPubKey = tx.slice(offset, offset + scriptLen);
    offset += scriptLen;

    outputs.push({ value, scriptPubKey: Buffer.from(scriptPubKey) });
  }

  return outputs;
}

/**
 * Compute txid from raw transaction hex
 * txid = double SHA256 of tx bytes (reversed for display)
 */
function computeTxid(txHex: string): string {
  const tx = Buffer.from(txHex, 'hex');
  const hash = bitcoin.crypto.hash256(tx);
  return hash.reverse().toString('hex');
}

/**
 * Create a TransparentInput from coinbase transaction
 * (Coinbase outputs have a specific structure)
 */
export async function getCoinbaseUtxo(
  client: ZebraClient,
  blockHeight: number,
  keypair: ZcashKeypair
): Promise<TransparentInput | null> {
  try {
    const blockHash = await client.getBlockHash(blockHeight);
    const block = await client.getBlock(blockHash, 2); // verbosity 2 for tx data

    // Handle both Zebra (raw format) and zcashd (decoded format)
    let coinbaseTx: any;
    if (Array.isArray(block.tx)) {
      coinbaseTx = block.tx[0];
    } else {
      return null;
    }

    // Check if we have zcashd format (has vout array) or Zebra format (has hex)
    if (coinbaseTx.vout) {
      // zcashd-style decoded transaction
      for (let i = 0; i < coinbaseTx.vout.length; i++) {
        const vout = coinbaseTx.vout[i];
        if (
          vout.scriptPubKey?.addresses?.includes(keypair.address) ||
          vout.scriptPubKey?.address === keypair.address
        ) {
          const txid = Buffer.from(reverseHex(coinbaseTx.txid), 'hex');
          const scriptPubKey = Buffer.from(vout.scriptPubKey.hex, 'hex');
          const amount = zecToZatoshi(vout.value);

          return {
            pubkey: keypair.publicKey,
            txid,
            vout: i,
            amount,
            scriptPubKey,
          };
        }
      }
    } else if (coinbaseTx.hex) {
      // Zebra-style raw transaction hex
      const outputs = parseTxOutputs(coinbaseTx.hex);
      const expectedPubkeyHash = bitcoin.crypto.hash160(keypair.publicKey);

      for (let i = 0; i < outputs.length; i++) {
        const output = outputs[i];
        // Check if this is a P2PKH output matching our pubkey
        // P2PKH: OP_DUP OP_HASH160 <20 bytes> OP_EQUALVERIFY OP_CHECKSIG
        // Hex:   76    a9       14 <pubkeyhash>  88           ac
        if (output.scriptPubKey.length === 25 &&
            output.scriptPubKey[0] === 0x76 &&
            output.scriptPubKey[1] === 0xa9 &&
            output.scriptPubKey[2] === 0x14 &&
            output.scriptPubKey[23] === 0x88 &&
            output.scriptPubKey[24] === 0xac) {
          const pubkeyHashInScript = output.scriptPubKey.slice(3, 23);
          if (pubkeyHashInScript.equals(expectedPubkeyHash)) {
            const txid = Buffer.from(reverseHex(computeTxid(coinbaseTx.hex)), 'hex');
            return {
              pubkey: keypair.publicKey,
              txid,
              vout: i,
              amount: output.value,
              scriptPubKey: output.scriptPubKey,
            };
          }
        }
      }
    }

    return null;
  } catch (e) {
    console.error(`Error getting coinbase UTXO at height ${blockHeight}:`, e);
    return null;
  }
}

/**
 * Get mature coinbase UTXOs (100+ confirmations)
 * Filters out UTXOs that have been marked as spent in previous examples.
 * @param startFromRecent If true, scan from most recent mature blocks (default: true)
 */
export async function getMatureCoinbaseUtxos(
  client: ZebraClient,
  keypair: ZcashKeypair,
  maxCount: number = 10,
  startFromRecent: boolean = true
): Promise<TransparentInput[]> {
  const info = await client.getBlockchainInfo();
  const currentHeight = info.blocks;
  const matureHeight = currentHeight - 100;

  // Load spent UTXOs to filter them out
  const spentUtxos = await loadSpentUtxos();

  const utxos: TransparentInput[] = [];

  if (startFromRecent) {
    // Scan from most recent mature blocks backwards to get fresh UTXOs
    for (let height = matureHeight; height >= 1 && utxos.length < maxCount; height--) {
      const utxo = await getCoinbaseUtxo(client, height, keypair);
      if (utxo) {
        const key = `${utxo.txid.toString('hex')}:${utxo.vout}`;
        if (!spentUtxos.has(key)) {
          utxos.push(utxo);
        }
      }
    }
  } else {
    // Start from block 1 (block 0 is genesis with no coinbase output)
    for (let height = 1; height <= matureHeight && utxos.length < maxCount; height++) {
      const utxo = await getCoinbaseUtxo(client, height, keypair);
      if (utxo) {
        const key = `${utxo.txid.toString('hex')}:${utxo.vout}`;
        if (!spentUtxos.has(key)) {
          utxos.push(utxo);
        }
      }
    }
  }

  return utxos;
}

/**
 * Format transaction for display
 */
export function formatTransaction(tx: any): string {
  let output = `\nTransaction Details:\n`;
  output += `  TXID: ${tx.txid}\n`;
  output += `  Version: ${tx.version}\n`;
  output += `  Locktime: ${tx.locktime}\n`;

  output += `\n  Inputs (${tx.vin.length}):\n`;
  tx.vin.forEach((input: any, i: number) => {
    if (input.coinbase) {
      output += `    [${i}] Coinbase\n`;
    } else {
      output += `    [${i}] ${input.txid}:${input.vout}\n`;
      if (input.scriptSig) {
        output += `        ScriptSig: ${input.scriptSig.hex.slice(0, 40)}...\n`;
      }
    }
  });

  output += `\n  Outputs (${tx.vout.length}):\n`;
  tx.vout.forEach((output_item: any, i: number) => {
    output += `    [${i}] ${output_item.value} ZEC\n`;
    if (output_item.scriptPubKey) {
      output += `        Type: ${output_item.scriptPubKey.type}\n`;
      if (output_item.scriptPubKey.addresses) {
        output += `        Address: ${output_item.scriptPubKey.addresses[0]}\n`;
      }
    }
  });

  if (tx.vShieldedOutput && tx.vShieldedOutput.length > 0) {
    output += `\n  Shielded Outputs: ${tx.vShieldedOutput.length}\n`;
  }

  return output;
}

/**
 * Pretty print transaction broadcast result
 */
export function printBroadcastResult(txid: string, txHex?: string): void {
  console.log('\n' + '='.repeat(70));
  console.log('TRANSACTION BROADCAST SUCCESSFUL');
  console.log('='.repeat(70));
  console.log(`\nTXID: ${txid}`);
  if (txHex) {
    console.log(`\nRaw Transaction (${txHex.length / 2} bytes):`);
    console.log(txHex.slice(0, 100) + '...');
  }
  console.log('\n' + '='.repeat(70) + '\n');
}

/**
 * Pretty print error
 */
export function printError(title: string, error: Error): void {
  console.log('\n' + '='.repeat(70));
  console.log(`ERROR: ${title}`);
  console.log('='.repeat(70));
  console.log(`\nError: ${error.message}`);
  if (error.stack) {
    console.log(`\nStack trace:\n${error.stack}`);
  }
  console.log('\n' + '='.repeat(70) + '\n');
}

/**
 * Create a summary of the transaction workflow
 */
export function printWorkflowSummary(
  title: string,
  inputs: TransparentInput[],
  outputs: Array<{ address: string; amount: string }>,
  fee: bigint
): void {
  console.log('\n' + '='.repeat(70));
  console.log(title);
  console.log('='.repeat(70));

  const totalInput = inputs.reduce((sum, input) => sum + input.amount, 0n);
  const totalOutput = outputs.reduce((sum, output) => sum + BigInt(output.amount), 0n);

  console.log(`\nInputs: ${inputs.length}`);
  inputs.forEach((input, i) => {
    console.log(`  [${i}] ${zatoshiToZec(input.amount)} ZEC`);
  });
  console.log(`  Total: ${zatoshiToZec(totalInput)} ZEC`);

  console.log(`\nOutputs: ${outputs.length}`);
  outputs.forEach((output, i) => {
    console.log(
      `  [${i}] ${output.address.slice(0, 20)}... -> ${zatoshiToZec(BigInt(output.amount))} ZEC`
    );
  });
  console.log(`  Total: ${zatoshiToZec(totalOutput)} ZEC`);

  console.log(`\nFee: ${zatoshiToZec(fee)} ZEC`);
  console.log('='.repeat(70) + '\n');
}

// Re-export commonly used functions
export { bytesToHex };
