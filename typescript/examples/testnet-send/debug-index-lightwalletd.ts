#!/usr/bin/env node
/**
 * Real Testnet Example: Send ZEC from Transparent to Shielded
 * Using lightwalletd gRPC protocol (compatible with Zashi's RPC endpoint)
 *
 * This version works with public lightwalletd servers like testnet.zec.rocks
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

// Get __dirname equivalent in ESM
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Load .env file
config();

// ============================================================================
// Types
// ============================================================================

interface Utxo {
  txid: Buffer;
  vout: number;
  script: Buffer;
  valueZat: bigint;
  height: bigint;
}

// ============================================================================
// Protobuf Decoder (manual, since proto-loader isn't working properly)
// ============================================================================

function decodeVarint(buffer: Buffer, offset: number): { value: bigint; newOffset: number } {
  let result = 0n;
  let shift = 0n;
  let byte: number;
  let currentOffset = offset;

  do {
    if (currentOffset >= buffer.length) {
      throw new Error('Unexpected end of buffer while decoding varint');
    }
    byte = buffer[currentOffset++];
    result |= BigInt(byte & 0x7f) << shift;
    shift += 7n;
  } while (byte & 0x80);

  return { value: result, newOffset: currentOffset };
}

function decodeGetAddressUtxosReply(buffer: Buffer): {
  txid: Buffer;
  vout: number;
  script: Buffer;
  valueZat: bigint;
  height: bigint;
} {
  let offset = 0;
  let txid = Buffer.alloc(0);
  let vout = 0;
  let script = Buffer.alloc(0);
  let valueZat = 0n;
  let height = 0n;

  while (offset < buffer.length) {
    // Read field tag
    const tag = buffer[offset++];
    const fieldNumber = tag >> 3;
    const wireType = tag & 0x7;

    if (fieldNumber === 1 && wireType === 2) {
      // txid (bytes)
      const length = buffer[offset++];
      txid = buffer.slice(offset, offset + length);
      offset += length;
    } else if (fieldNumber === 2 && wireType === 0) {
      // index (int32 - varint)
      const decoded = decodeVarint(buffer, offset);
      vout = Number(decoded.value);
      offset = decoded.newOffset;
    } else if (fieldNumber === 3 && wireType === 2) {
      // script (bytes)
      const length = buffer[offset++];
      script = buffer.slice(offset, offset + length);
      offset += length;
    } else if (fieldNumber === 4 && wireType === 0) {
      // valueZat (int64 - varint)
      const decoded = decodeVarint(buffer, offset);
      valueZat = decoded.value;
      offset = decoded.newOffset;
    } else if (fieldNumber === 5 && wireType === 0) {
      // height (uint64 - varint)
      const decoded = decodeVarint(buffer, offset);
      height = decoded.value;
      offset = decoded.newOffset;
    } else if (wireType === 2) {
      // Unknown length-delimited field, skip it
      const length = buffer[offset++];
      offset += length;
    } else if (wireType === 0) {
      // Unknown varint field, skip it
      const decoded = decodeVarint(buffer, offset);
      offset = decoded.newOffset;
    } else {
      throw new Error(`Unknown wire type ${wireType} for field ${fieldNumber}`);
    }
  }

  return { txid, vout, script, valueZat, height };
}

// ============================================================================
// LightwalletD gRPC Client
// ============================================================================

class LightwalletdClient {
  private client: any;

  constructor(serverUrl: string) {
    // Load proto file
    const PROTO_PATH = join(__dirname, 'lightwalletd.proto');
    const packageDefinition = protoLoader.loadSync(PROTO_PATH, {
      keepCase: true,
      longs: String,
      enums: String,
      defaults: true,
      oneofs: true,
    });

    const protoDescriptor = grpc.loadPackageDefinition(packageDefinition) as any;
    const CompactTxStreamer = protoDescriptor.cash.z.wallet.sdk.rpc.CompactTxStreamer;

    // Create gRPC client (no TLS for now, will add if needed)
    const credentials = serverUrl.startsWith('https')
      ? grpc.credentials.createSsl()
      : grpc.credentials.createInsecure();

    // Remove protocol from URL for gRPC
    const grpcUrl = serverUrl.replace(/^https?:\/\//, '').replace(/\/$/, '');

    this.client = new CompactTxStreamer(grpcUrl, credentials);
  }

  async getLatestBlock(): Promise<{ height: bigint; hash: Buffer }> {
    return new Promise((resolve, reject) => {
      this.client.GetLatestBlock({}, (error: Error | null, response: any) => {
        if (error) {
          reject(error);
        } else {
          resolve({
            height: BigInt(response.height),
            hash: Buffer.from(response.hash),
          });
        }
      });
    });
  }

  async getLightdInfo(): Promise<any> {
    return new Promise((resolve, reject) => {
      this.client.GetLightdInfo({}, (error: Error | null, response: any) => {
        if (error) {
          reject(error);
        } else {
          resolve(response);
        }
      });
    });
  }

  async getAddressUtxos(address: string, startHeight: number = 0): Promise<Utxo[]> {
    return new Promise((resolve, reject) => {
      const utxos: Utxo[] = [];

      const call = this.client.GetAddressUtxos({
        addresses: [address],
        startHeight: startHeight,
        maxEntries: 1000,
      });

      call.on('data', (response: any) => {
        // The response comes as a protobuf-encoded buffer, need to manually decode
        const data = response.txid || response; // Sometimes it's nested, sometimes not

        if (!data || !Buffer.isBuffer(data)) {
          console.error('   ⚠️  Unexpected response format:', response);
          return;
        }

        // Manual protobuf decoding
        const decoded = decodeGetAddressUtxosReply(data);

        // Debug logging (comment out for production)
        // console.log('\n   [DEBUG] Decoded UTXO:');
        // console.log('   txid:', decoded.txid.toString('hex'));
        // console.log('   vout:', decoded.vout);
        // console.log('   script:', decoded.script.toString('hex'));
        // console.log('   valueZat:', decoded.valueZat.toString());
        // console.log('   height:', decoded.height.toString());

        utxos.push({
          txid: decoded.txid,
          vout: decoded.vout,
          script: decoded.script,
          valueZat: decoded.valueZat,
          height: decoded.height,
        });
      });

      call.on('end', () => {
        resolve(utxos);
      });

      call.on('error', (error: Error) => {
        reject(error);
      });
    });
  }

  async sendTransaction(txData: Buffer): Promise<{ errorCode: number; errorMessage: string }> {
    return new Promise((resolve, reject) => {
      this.client.SendTransaction(
        {
          data: txData,
          height: 0,
        },
        (error: Error | null, response: any) => {
          if (error) {
            reject(error);
          } else {
            resolve({
              errorCode: response.errorCode,
              errorMessage: response.errorMessage,
            });
          }
        }
      );
    });
  }
}

// ============================================================================
// Crypto Utilities
// ============================================================================

function hash160(buffer: Buffer): Buffer {
  const sha256Hash = createHash('sha256').update(buffer).digest();
  return createHash('ripemd160').update(sha256Hash).digest();
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
  // Convert Signature object to compact 64-byte format (r + s)
  return Buffer.from(sig.toCompactRawBytes());
}

// ============================================================================
// Main Function
// ============================================================================

async function main() {
  console.log('='.repeat(80));
  console.log('🌿  T2Z Testnet Example: Transparent → Shielded (lightwalletd)');
  console.log('='.repeat(80));

  // -------------------------------------------------------------------------
  // 1. Load Configuration
  // -------------------------------------------------------------------------
  console.log('\n📋 Loading configuration from .env...');

  const privateKeyHex = process.env.PRIVATE_KEY;
  const shieldedAddress = process.env.SHIELDED_ADDRESS;
  const lightwalletdUrl = process.env.LIGHTWALLETD_URL || process.env.RPC_URL;
  const transparentAddress = process.env.TRANSPARENT_ADDRESS;
  const amountZec = parseFloat(process.env.AMOUNT || '0.001');

  if (!privateKeyHex || privateKeyHex === 'your_private_key_here') {
    console.error('\n❌ Error: PRIVATE_KEY not set in .env file');
    process.exit(1);
  }

  if (!shieldedAddress || shieldedAddress.includes('1234567890')) {
    console.error('\n❌ Error: SHIELDED_ADDRESS not set in .env file');
    process.exit(1);
  }

  if (!lightwalletdUrl || lightwalletdUrl.includes('user:pass')) {
    console.error('\n❌ Error: LIGHTWALLETD_URL not set in .env file');
    console.error('   Example: LIGHTWALLETD_URL=https://testnet.zec.rocks:443');
    process.exit(1);
  }

  if (!transparentAddress) {
    console.error('\n❌ Error: TRANSPARENT_ADDRESS not set in .env file');
    process.exit(1);
  }

  const privateKey = Buffer.from(privateKeyHex, 'hex');
  if (privateKey.length !== 32) {
    throw new Error('PRIVATE_KEY must be 64 hex characters (32 bytes)');
  }

  console.log('   ✅ Configuration loaded');
  console.log(`   📧 Recipient: ${shieldedAddress}`);
  console.log(`   💰 Amount: ${amountZec} ZEC`);

  // -------------------------------------------------------------------------
  // 2. Derive Public Key
  // -------------------------------------------------------------------------
  console.log('\n🔑 Deriving keys...');

  const publicKey = Buffer.from(secp256k1.getPublicKey(privateKey, true));
  const pubkeyHash = hash160(publicKey);
  const scriptPubKey = createP2pkhScript(pubkeyHash);

  console.log(`   Public Key: ${publicKey.toString('hex')}`);
  console.log(`   PubKey Hash: ${pubkeyHash.toString('hex')}`);
  console.log(`   Address: ${transparentAddress}`);

  // -------------------------------------------------------------------------
  // 3. Connect to lightwalletd
  // -------------------------------------------------------------------------
  console.log('\n🌐 Connecting to lightwalletd server...');
  console.log(`   Server: ${lightwalletdUrl}`);

  const client = new LightwalletdClient(lightwalletdUrl);

  let blockHeight: bigint;
  try {
    const info = await client.getLightdInfo();
    blockHeight = BigInt(info.blockHeight);
    console.log(`   ✅ Connected!`);
    console.log(`   Chain: ${info.chainName}`);
    console.log(`   Block height: ${blockHeight}`);
    console.log(`   Vendor: ${info.vendor} ${info.version}`);
  } catch (error: any) {
    console.error(`\n❌ Failed to connect: ${error.message}`);
    console.error('\n💡 Common issues:');
    console.error('   - Wrong URL format (should be host:port, no http://)');
    console.error('   - Server is down');
    console.error('   - Network/firewall blocking gRPC');
    console.error('\n   Try: https://testnet.zec.rocks:443');
    process.exit(1);
  }

  // -------------------------------------------------------------------------
  // 4. Query UTXOs
  // -------------------------------------------------------------------------
  console.log('\n💰 Fetching UTXOs...');

  let utxos: Utxo[];
  try {
    utxos = await client.getAddressUtxos(transparentAddress);
    console.log(`   Found ${utxos.length} UTXO(s)`);

    if (utxos.length > 0) {
      console.log('\n   UTXOs:');
      utxos.forEach((utxo, i) => {
        // txid is already in internal byte order from lightwalletd, reverse for display
        const txidHex = Buffer.from(utxo.txid).reverse().toString('hex');
        const amount = Number(utxo.valueZat) / 100_000_000;
        console.log(`   ${i + 1}. ${txidHex}:${utxo.vout}`);
        console.log(`      Amount: ${amount.toFixed(8)} ZEC (${utxo.valueZat} zatoshis)`);
        console.log(`      Height: ${utxo.height}`);
      });
    }
  } catch (error: any) {
    console.error(`\n❌ Failed to fetch UTXOs: ${error.message}`);
    process.exit(1);
  }

  if (utxos.length === 0) {
    console.error('\n❌ No UTXOs found!');
    console.error('   Fund your address at: https://faucet.testnet.z.cash/');
    console.error(`   Your address: ${transparentAddress}`);
    process.exit(1);
  }

  // -------------------------------------------------------------------------
  // 5. Calculate Amounts
  // -------------------------------------------------------------------------
  const totalAvailable = utxos.reduce((sum, u) => sum + u.valueZat, 0n);
  const amountSat = BigInt(Math.floor(amountZec * 100_000_000));
  const feeSat = 15_000n;
  const totalNeededSat = amountSat + feeSat;

  console.log('\n📊 Transaction Summary:');
  console.log(`   Available: ${(Number(totalAvailable) / 100_000_000).toFixed(8)} ZEC`);
  console.log(`   Sending: ${amountZec.toFixed(8)} ZEC`);
  console.log(`   Fee: ${(Number(feeSat) / 100_000_000).toFixed(8)} ZEC`);
  console.log(`   Total: ${(Number(totalNeededSat) / 100_000_000).toFixed(8)} ZEC`);

  if (totalAvailable < totalNeededSat) {
    console.error(`\n❌ Insufficient funds!`);
    process.exit(1);
  }

  // -------------------------------------------------------------------------
  // 6. Build Transaction
  // -------------------------------------------------------------------------
  console.log('\n🔨 Building transaction...');

  const inputs: TransparentInput[] = utxos.map((utxo) => ({
    pubkey: publicKey,
    txid: Buffer.from(utxo.txid), // lightwalletd returns in internal byte order
    vout: utxo.vout,
    amount: utxo.valueZat.toString(),
    scriptPubKey: utxo.script, // Use the actual script from the UTXO
  }));

  const payments: Payment[] = [
    {
      address: shieldedAddress,
      amount: amountSat.toString(),
    },
  ];

  console.log(`   Creating transaction request...`);
  // Use current block height for proper consensus branch ID and expiry calculation
  const request = TransactionRequest.withTargetHeight(payments, Number(blockHeight));

  console.log(`   Proposing transaction...`);
  let pczt = proposeTransaction(inputs, request);

  console.log(`   Adding Orchard proofs (this may take a moment)...`);
  pczt = proveTransaction(pczt);

  // -------------------------------------------------------------------------
  // 7. Sign Transaction
  // -------------------------------------------------------------------------
  console.log(`\n✍️  Signing transaction...`);

  for (let i = 0; i < inputs.length; i++) {
    console.log(`   Signing input ${i + 1}/${inputs.length}...`);
    const sighash = getSighash(pczt, i);
    const signature = await signSighash(privateKey, sighash);
    pczt = appendSignature(pczt, i, signature);
  }

  console.log(`   ✅ All inputs signed`);

  // -------------------------------------------------------------------------
  // 8. Finalize and Extract
  // -------------------------------------------------------------------------
  console.log('\n📦 Finalizing transaction...');
  const txBytes = finalizeAndExtract(pczt);
  const txHex = txBytes.toString('hex');

  console.log(`   Transaction size: ${txBytes.length} bytes`);

  // -------------------------------------------------------------------------
  // 9. Broadcast
  // -------------------------------------------------------------------------
  console.log('\n📡 Broadcasting transaction...');

  try {
    const response = await client.sendTransaction(txBytes);

    if (response.errorCode === 0) {
      // Success! Calculate txid
      const txid = createHash('sha256')
        .update(createHash('sha256').update(txBytes).digest())
        .digest()
        .reverse()
        .toString('hex');

      console.log(`   ✅ Transaction broadcast successful!`);
      console.log('\n' + '='.repeat(80));
      console.log('🎉 SUCCESS! Transaction sent!');
      console.log('='.repeat(80));
      console.log(`\n   TXID: ${txid}`);
      console.log(`\n   View on explorer:`);
      console.log(`   https://testnet.zcashblockexplorer.com/transactions/${txid}`);
    } else {
      console.error(`\n❌ Broadcast failed: ${response.errorMessage}`);
      console.error(`   Error code: ${response.errorCode}`);
      console.error(`\n   Transaction hex (for debugging):`);
      console.error(`   ${txHex}`);
      process.exit(1);
    }
  } catch (error: any) {
    console.error(`\n❌ Failed to broadcast: ${error.message}`);
    process.exit(1);
  }

  // Cleanup
  request.free();
}

// ============================================================================
// Entry Point
// ============================================================================

main().catch((error) => {
  console.error('\n💥 Fatal Error:', error.message);
  if (error.stack) {
    console.error('\nStack trace:');
    console.error(error.stack);
  }
  process.exit(1);
});
