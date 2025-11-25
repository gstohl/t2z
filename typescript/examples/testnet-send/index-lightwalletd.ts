#!/usr/bin/env node
/**
 * T2Z Testnet Send Example (Compressed)
 * Send ZEC from transparent to shielded address using lightwalletd
 */

import { config } from 'dotenv';
import { createHash } from 'crypto';
import * as grpc from '@grpc/grpc-js';
import * as protoLoader from '@grpc/proto-loader';
import * as secp256k1 from '@noble/secp256k1';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import {
  TransactionRequest,
  TransparentInput,
  Payment,
  proposeTransaction,
  proveTransaction,
  getSighash,
  appendSignature,
  finalizeAndExtract,
} from 't2z';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
config();

interface Utxo {
  txid: Buffer;
  vout: number;
  script: Buffer;
  valueZat: bigint;
  height: bigint;
}

// ============================================================================
// Protobuf Decoder
// ============================================================================

function decodeVarint(buffer: Buffer, offset: number): { value: bigint; newOffset: number } {
  let result = 0n;
  let shift = 0n;
  let byte: number;
  let currentOffset = offset;

  do {
    if (currentOffset >= buffer.length) {
      throw new Error('Unexpected end of buffer');
    }
    byte = buffer[currentOffset++];
    result |= BigInt(byte & 0x7f) << shift;
    shift += 7n;
  } while (byte & 0x80);

  return { value: result, newOffset: currentOffset };
}

function decodeGetAddressUtxosReply(buffer: Buffer): Utxo {
  let offset = 0;
  let txid = Buffer.alloc(0);
  let vout = 0;
  let script = Buffer.alloc(0);
  let valueZat = 0n;
  let height = 0n;

  while (offset < buffer.length) {
    const tag = buffer[offset++];
    const fieldNumber = tag >> 3;
    const wireType = tag & 0x7;

    if (fieldNumber === 1 && wireType === 2) {
      const length = buffer[offset++];
      txid = buffer.slice(offset, offset + length);
      offset += length;
    } else if (fieldNumber === 2 && wireType === 0) {
      const decoded = decodeVarint(buffer, offset);
      vout = Number(decoded.value);
      offset = decoded.newOffset;
    } else if (fieldNumber === 3 && wireType === 2) {
      const length = buffer[offset++];
      script = buffer.slice(offset, offset + length);
      offset += length;
    } else if (fieldNumber === 4 && wireType === 0) {
      const decoded = decodeVarint(buffer, offset);
      valueZat = decoded.value;
      offset = decoded.newOffset;
    } else if (fieldNumber === 5 && wireType === 0) {
      const decoded = decodeVarint(buffer, offset);
      height = decoded.value;
      offset = decoded.newOffset;
    } else {
      // Skip unknown field
      if (wireType === 0) {
        decodeVarint(buffer, offset);
        offset = decodeVarint(buffer, offset).newOffset;
      } else if (wireType === 2) {
        const length = buffer[offset++];
        offset += length;
      }
    }
  }

  return { txid, vout, script, valueZat, height };
}

// ============================================================================
// Lightwalletd Client
// ============================================================================

class LightwalletdClient {
  private client: any;

  constructor(serverUrl: string) {
    const protoPath = join(__dirname, 'lightwalletd.proto');
    const packageDefinition = protoLoader.loadSync(protoPath, {
      keepCase: true,
      longs: String,
      enums: String,
      defaults: true,
      oneofs: true,
    });

    const proto: any = grpc.loadPackageDefinition(packageDefinition);
    const url = serverUrl.replace(/^https?:\/\//, '');

    this.client = new proto.cash.z.wallet.sdk.rpc.CompactTxStreamer(
      url,
      serverUrl.startsWith('https')
        ? grpc.credentials.createSsl()
        : grpc.credentials.createInsecure()
    );
  }

  async getBlockHeight(): Promise<bigint> {
    return new Promise((resolve, reject) => {
      this.client.GetLatestBlock({}, (error: Error | null, response: any) => {
        if (error) reject(error);
        else resolve(BigInt(response.height));
      });
    });
  }

  async getAddressUtxos(address: string): Promise<Utxo[]> {
    return new Promise((resolve, reject) => {
      const utxos: Utxo[] = [];
      const call = this.client.GetAddressUtxos({
        addresses: [address],
        startHeight: 0,
        maxEntries: 1000,
      });

      call.on('data', (response: any) => {
        const data = response.txid || response;
        if (Buffer.isBuffer(data)) {
          utxos.push(decodeGetAddressUtxosReply(data));
        }
      });

      call.on('end', () => resolve(utxos));
      call.on('error', reject);
    });
  }

  async sendTransaction(txBytes: Buffer): Promise<string> {
    return new Promise((resolve, reject) => {
      this.client.SendTransaction(
        { data: txBytes, height: 0 },
        (error: Error | null, response: any) => {
          if (error) {
            reject(error);
          } else if (response.errorCode !== 0) {
            reject(new Error(`Broadcast failed: ${response.errorMessage} (code ${response.errorCode})`));
          } else {
            const txid = createHash('sha256')
              .update(createHash('sha256').update(txBytes).digest())
              .digest();
            resolve(txid.reverse().toString('hex'));
          }
        }
      );
    });
  }
}

// ============================================================================
// Helper Functions
// ============================================================================

function hash160(data: Buffer): Buffer {
  return createHash('ripemd160')
    .update(createHash('sha256').update(data).digest())
    .digest();
}

function createP2pkhScript(pubkeyHash: Buffer): Buffer {
  return Buffer.concat([
    Buffer.from([0x76, 0xa9, 0x14]),
    pubkeyHash,
    Buffer.from([0x88, 0xac]),
  ]);
}

async function signSighash(privateKey: Buffer, sighash: Buffer): Promise<Buffer> {
  const sig = await secp256k1.signAsync(sighash, privateKey, { lowS: true });
  return Buffer.from(sig.toCompactRawBytes());
}

// ============================================================================
// Main
// ============================================================================

async function main() {
  console.log('T2Z Testnet Send: Transparent → Shielded\n');

  // Load config
  const privateKeyHex = process.env.PRIVATE_KEY;
  const transparentAddress = process.env.TRANSPARENT_ADDRESS;
  const shieldedAddress = process.env.SHIELDED_ADDRESS;
  const lightwalletdUrl = process.env.LIGHTWALLETD_URL || 'https://testnet.zec.rocks:443';
  const amountZec = parseFloat(process.env.AMOUNT || '0.001');

  if (!privateKeyHex || !transparentAddress || !shieldedAddress) {
    throw new Error('Missing required environment variables');
  }

  const privateKey = Buffer.from(privateKeyHex, 'hex');
  const publicKey = Buffer.from(secp256k1.getPublicKey(privateKey, true));
  const pubkeyHash = hash160(publicKey);
  const scriptPubKey = createP2pkhScript(pubkeyHash);

  console.log(`From: ${transparentAddress}`);
  console.log(`To: ${shieldedAddress.substring(0, 20)}...`);
  console.log(`Amount: ${amountZec} ZEC\n`);

  // Connect to lightwalletd
  console.log('→ Connecting to lightwalletd...');
  const client = new LightwalletdClient(lightwalletdUrl);
  const blockHeight = await client.getBlockHeight();
  console.log(`✓ Connected (block ${blockHeight})\n`);

  // Fetch UTXOs
  console.log('→ Fetching UTXOs...');
  const utxos = await client.getAddressUtxos(transparentAddress);

  if (utxos.length === 0) {
    throw new Error('No UTXOs found');
  }

  const totalAvailable = utxos.reduce((sum, utxo) => sum + utxo.valueZat, 0n);
  console.log(`✓ Found ${utxos.length} UTXO(s), ${Number(totalAvailable) / 1e8} ZEC available\n`);

  // Build transaction
  console.log('→ Building transaction...');
  const amountSat = BigInt(Math.floor(amountZec * 1e8));
  const feeZat = 15000n; // ZIP-317 fee for transparent→shielded

  const inputs: TransparentInput[] = utxos.map((utxo) => ({
    pubkey: publicKey,
    txid: Buffer.from(utxo.txid),
    vout: utxo.vout,
    amount: utxo.valueZat.toString(),
    scriptPubKey: utxo.script,
  }));

  const payments: Payment[] = [{
    address: shieldedAddress,
    amount: amountSat.toString(),
  }];

  const request = TransactionRequest.withTargetHeight(payments, Number(blockHeight));
  let pczt = proposeTransaction(inputs, request);

  // Add proofs
  console.log('→ Adding Orchard proofs...');
  pczt = proveTransaction(pczt);

  // Sign inputs
  console.log('→ Signing...');
  for (let i = 0; i < inputs.length; i++) {
    const sighash = getSighash(pczt, i);
    const signature = await signSighash(privateKey, sighash);
    pczt = appendSignature(pczt, i, signature);
  }

  // Finalize
  const txBytes = finalizeAndExtract(pczt);
  console.log(`✓ Transaction ready (${txBytes.length} bytes)\n`);

  // Broadcast
  console.log('→ Broadcasting...');
  const txid = await client.sendTransaction(txBytes);

  console.log('\n✓ SUCCESS!\n');
  console.log(`TXID: ${txid}`);
  console.log(`Explorer: https://testnet.zcashblockexplorer.com/transactions/${txid}`);
}

main().catch((error) => {
  console.error('\n✗ Error:', error.message);
  process.exit(1);
});
