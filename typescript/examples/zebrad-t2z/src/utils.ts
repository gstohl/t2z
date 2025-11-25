/**
 * Utility functions for t2z examples
 */

import { createHash, createECDH } from 'crypto';
import { ZcashdClient, UTXO } from './zcashd-client.js';
import { TransparentInput } from 't2z';

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
 * Get compressed public key from zcashd address
 * This extracts the pubkey from the address using zcashd's validateaddress RPC
 */
export async function getCompressedPubKey(
  client: ZcashdClient,
  address: string
): Promise<Buffer> {
  const addrInfo = await client.validateAddress(address);

  if (!addrInfo.pubkey) {
    throw new Error(`No pubkey available for address ${address}`);
  }

  const pubkeyHex = addrInfo.pubkey;

  // If it's already compressed (33 bytes = 66 hex chars), return it
  if (pubkeyHex.length === 66) {
    return Buffer.from(pubkeyHex, 'hex');
  }

  // If it's uncompressed (65 bytes), compress it
  if (pubkeyHex.length === 130) {
    const uncompressed = Buffer.from(pubkeyHex, 'hex');
    const x = uncompressed.slice(1, 33);
    const y = uncompressed.slice(33, 65);

    // Determine prefix: 02 if y is even, 03 if y is odd
    const prefix = y[y.length - 1] % 2 === 0 ? 0x02 : 0x03;

    return Buffer.concat([Buffer.from([prefix]), x]);
  }

  throw new Error(`Unexpected pubkey format: ${pubkeyHex}`);
}

/**
 * Sign a message using zcashd's signmessage
 * Note: This is for demo purposes. In production, use proper secp256k1 signing
 */
export async function signWithZcashd(
  client: ZcashdClient,
  address: string,
  message: Buffer
): Promise<Buffer> {
  // For zcashd, we need to use signrawtransaction or external signing
  // This is a placeholder - in real scenarios, we'd use the private key directly
  throw new Error('Signing via zcashd requires using private keys directly');
}

/**
 * Get private key from zcashd and sign message using secp256k1
 */
export async function getPrivateKeyAndSign(
  client: ZcashdClient,
  address: string,
  sighash: Buffer
): Promise<Buffer> {
  const privKeyWIF = await client.dumpPrivKey(address);

  // Decode WIF private key
  const privKeyBytes = decodeWIF(privKeyWIF);

  // Sign using secp256k1 (we'll use native crypto for this example)
  const signature = signSecp256k1(privKeyBytes, sighash);

  return signature;
}

/**
 * Decode WIF (Wallet Import Format) private key
 */
function decodeWIF(wif: string): Buffer {
  // Simple base58 decode (for regtest, keys start with 'c')
  // In production, use a proper base58 library
  const decoded = Buffer.from(bs58Decode(wif));

  // Remove checksum (last 4 bytes) and version byte (first byte)
  const privKey = decoded.slice(1, 33);

  return privKey;
}

/**
 * Simple base58 decode
 * Note: This is a simplified version. In production, use a proper library like 'bs58'
 */
function bs58Decode(str: string): number[] {
  const ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
  const ALPHABET_MAP: { [key: string]: number } = {};
  for (let i = 0; i < ALPHABET.length; i++) {
    ALPHABET_MAP[ALPHABET[i]] = i;
  }

  let result = BigInt(0);
  for (let i = 0; i < str.length; i++) {
    result = result * BigInt(58) + BigInt(ALPHABET_MAP[str[i]]);
  }

  const hex = result.toString(16);
  const bytes: number[] = [];
  for (let i = 0; i < hex.length; i += 2) {
    bytes.push(parseInt(hex.slice(i, i + 2), 16));
  }

  return bytes;
}

/**
 * Sign message with secp256k1 private key
 * Returns 64-byte signature (r || s)
 */
function signSecp256k1(privKey: Buffer, message: Buffer): Buffer {
  // For this example, we'll use Node's crypto with ECDH
  // In production, use a proper secp256k1 library like 'secp256k1' npm package
  const ecdh = createECDH('secp256k1');
  ecdh.setPrivateKey(privKey);

  // This is a simplified version - real signing is more complex
  // For proper implementation, you'd use a library like 'secp256k1' or '@noble/secp256k1'
  throw new Error(
    'For proper secp256k1 signing, use the signMessage function from t2z library or a dedicated secp256k1 library'
  );
}

/**
 * Convert UTXO to TransparentInput
 */
export async function utxoToTransparentInput(
  client: ZcashdClient,
  utxo: UTXO
): Promise<TransparentInput> {
  // Get compressed pubkey
  const pubkey = await getCompressedPubKey(client, utxo.address);

  // Get script pubkey from the UTXO
  const scriptPubKey = Buffer.from(utxo.scriptPubKey, 'hex');

  // txid needs to be in little-endian (reversed)
  const txid = Buffer.from(reverseHex(utxo.txid), 'hex');

  // Convert amount from BTC to zatoshis
  const amount = zecToZatoshi(utxo.amount);

  return {
    pubkey,
    txid,
    vout: utxo.vout,
    amount,
    scriptPubKey,
  };
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
  console.log('✅ TRANSACTION BROADCAST SUCCESSFUL');
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
  console.log(`❌ ${title}`);
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

  console.log(`\n📥 Inputs: ${inputs.length}`);
  inputs.forEach((input, i) => {
    console.log(`  [${i}] ${zatoshiToZec(input.amount)} ZEC`);
  });
  console.log(`  Total: ${zatoshiToZec(totalInput)} ZEC`);

  console.log(`\n📤 Outputs: ${outputs.length}`);
  outputs.forEach((output, i) => {
    console.log(`  [${i}] ${output.address.slice(0, 20)}... → ${zatoshiToZec(BigInt(output.amount))} ZEC`);
  });
  console.log(`  Total: ${zatoshiToZec(totalOutput)} ZEC`);

  console.log(`\n💰 Fee: ${zatoshiToZec(fee)} ZEC`);
  console.log('='.repeat(70) + '\n');
}
