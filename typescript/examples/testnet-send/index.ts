#!/usr/bin/env node
/**
 * Real Testnet Example: Send ZEC from Transparent to Shielded
 *
 * This example demonstrates sending ZEC on Zcash testnet from a
 * transparent address to a shielded (Orchard) address.
 *
 * Setup:
 * 1. Copy .env.example to .env
 * 2. Fill in your PRIVATE_KEY, SHIELDED_ADDRESS, and RPC_URL
 * 3. Run: npm start
 */

import { config } from 'dotenv';
import { createHash } from 'crypto';
import * as secp256k1 from '@noble/secp256k1';
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

// Load .env file
config();

// ============================================================================
// Types
// ============================================================================

interface Utxo {
  txid: string;
  vout: number;
  address: string;
  scriptPubKey: string;
  amount: number; // in ZEC
  confirmations: number;
}

// ============================================================================
// RPC Client
// ============================================================================

class ZcashRpc {
  constructor(private url: string) {}

  async call<T>(method: string, params: any[] = []): Promise<T> {
    let response: Response;

    try {
      response = await fetch(this.url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          jsonrpc: '1.0',
          id: Date.now(),
          method,
          params,
        }),
      });
    } catch (error: any) {
      // Network-level errors (connection refused, DNS, etc.)
      if (error.code === 'ECONNREFUSED') {
        throw new Error(
          `Connection refused to ${this.url.replace(/\/\/.*:.*@/, '//***:***@')}.\n` +
          `   The Zcash node is not running or not accepting connections on this port.`
        );
      }
      if (error.code === 'ENOTFOUND') {
        throw new Error(
          `Cannot resolve hostname in ${this.url.replace(/\/\/.*:.*@/, '//***:***@')}.\n` +
          `   Check the hostname/IP address in your RPC_URL.`
        );
      }
      if (error.code === 'ETIMEDOUT') {
        throw new Error(
          `Connection timeout to ${this.url.replace(/\/\/.*:.*@/, '//***:***@')}.\n` +
          `   The node might be down, or a firewall is blocking the connection.`
        );
      }
      throw new Error(`Network error: ${error.message}\n   Code: ${error.code || 'unknown'}`);
    }

    if (!response.ok) {
      const text = await response.text();
      if (response.status === 401) {
        throw new Error(
          `HTTP 401 Unauthorized.\n` +
          `   Your RPC credentials are incorrect.\n` +
          `   Check rpcuser and rpcpassword in ~/.zcash/zcash.conf`
        );
      }
      if (response.status === 403) {
        throw new Error(
          `HTTP 403 Forbidden.\n` +
          `   Your IP address is not allowed.\n` +
          `   Add to zcash.conf: rpcallowip=<your_ip>`
        );
      }
      throw new Error(
        `HTTP ${response.status}: ${response.statusText}\n` +
        `   Response: ${text.substring(0, 200)}`
      );
    }

    const json: any = await response.json();

    if (json.error) {
      throw new Error(`RPC Error: ${json.error.message} (code: ${json.error.code || 'unknown'})`);
    }

    return json.result;
  }

  async getUtxos(address: string, minConf = 1): Promise<Utxo[]> {
    return this.call<Utxo[]>('listunspent', [minConf, 9999999, [address]]);
  }

  async sendRawTransaction(hex: string): Promise<string> {
    return this.call<string>('sendrawtransaction', [hex]);
  }

  async getBlockCount(): Promise<number> {
    return this.call<number>('getblockcount');
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
  // OP_DUP OP_HASH160 <20 bytes> OP_EQUALVERIFY OP_CHECKSIG
  return Buffer.concat([
    Buffer.from([0x76, 0xa9, 0x14]),
    pubkeyHash,
    Buffer.from([0x88, 0xac]),
  ]);
}

function getPublicKeyHash(privateKey: Buffer): Buffer {
  const publicKey = Buffer.from(secp256k1.getPublicKey(privateKey, true));
  return hash160(publicKey);
}

async function signSighash(privateKey: Buffer, sighash: Buffer): Promise<Buffer> {
  const sig = await secp256k1.signAsync(sighash, privateKey, {
    lowS: true, // Bitcoin/Zcash compatibility
  });
  return Buffer.from(sig);
}

// ============================================================================
// Main Function
// ============================================================================

async function main() {
  console.log('='.repeat(80));
  console.log('🌿  T2Z Testnet Example: Transparent → Shielded Transaction');
  console.log('='.repeat(80));

  // -------------------------------------------------------------------------
  // 1. Load Configuration
  // -------------------------------------------------------------------------
  console.log('\n📋 Loading configuration from .env...');

  const privateKeyHex = process.env.PRIVATE_KEY;
  const shieldedAddress = process.env.SHIELDED_ADDRESS;
  const rpcUrl = process.env.RPC_URL;
  const amountZec = parseFloat(process.env.AMOUNT || '0.001');

  if (!privateKeyHex || privateKeyHex === 'your_private_key_here') {
    console.error('\n❌ Error: PRIVATE_KEY not set in .env file');
    console.error('   Copy .env.example to .env and fill in your private key');
    process.exit(1);
  }

  if (!shieldedAddress || shieldedAddress.includes('1234567890')) {
    console.error('\n❌ Error: SHIELDED_ADDRESS not set in .env file');
    console.error('   Copy .env.example to .env and fill in the recipient address');
    process.exit(1);
  }

  if (!rpcUrl || rpcUrl.includes('user:pass')) {
    console.error('\n❌ Error: RPC_URL not set in .env file');
    console.error('   Copy .env.example to .env and fill in your RPC endpoint');
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
  // 2. Derive Public Key and Address Info
  // -------------------------------------------------------------------------
  console.log('\n🔑 Deriving keys...');

  const publicKey = Buffer.from(secp256k1.getPublicKey(privateKey, true));
  const pubkeyHash = hash160(publicKey);
  const scriptPubKey = createP2pkhScript(pubkeyHash);

  console.log(`   Public Key: ${publicKey.toString('hex')}`);
  console.log(`   PubKey Hash: ${pubkeyHash.toString('hex')}`);
  console.log(`   Script: ${scriptPubKey.toString('hex')}`);

  // -------------------------------------------------------------------------
  // 3. Connect to RPC and Check Status
  // -------------------------------------------------------------------------
  console.log('\n🌐 Connecting to Zcash node...');
  console.log(`   Attempting connection to: ${rpcUrl.replace(/\/\/.*:.*@/, '//***:***@')}`);

  const rpc = new ZcashRpc(rpcUrl);

  let blockHeight: number;
  try {
    blockHeight = await rpc.getBlockCount();
    console.log(`   ✅ Connected! Block height: ${blockHeight}`);
  } catch (error: any) {
    console.error('\n' + '='.repeat(80));
    console.error('❌ Failed to Connect to Zcash Node');
    console.error('='.repeat(80));
    console.error(`\nError: ${error.message}`);

    if (error.code) {
      console.error(`Error Code: ${error.code}`);
    }

    console.error('\n📋 Common Issues:');
    console.error('');
    console.error('1. ❌ Node Not Running');
    console.error('   → Start your Zcash node: zcashd -testnet');
    console.error('   → Or use Docker: docker run -d zcash/zcashd');
    console.error('');
    console.error('2. ❌ Wrong RPC URL');
    console.error('   → Check your .env file: RPC_URL');
    console.error('   → Format: http://username:password@host:port');
    console.error('   → Default testnet port: 18232');
    console.error('   → Default mainnet port: 8232');
    console.error('');
    console.error('3. ❌ Wrong Credentials');
    console.error('   → Check ~/.zcash/zcash.conf for:');
    console.error('     rpcuser=your_username');
    console.error('     rpcpassword=your_password');
    console.error('');
    console.error('4. ❌ RPC Not Enabled');
    console.error('   → Add to ~/.zcash/zcash.conf:');
    console.error('     server=1');
    console.error('     rpcallowip=127.0.0.1');
    console.error('     rpcbind=127.0.0.1');
    console.error('');
    console.error('5. ❌ Firewall/Network Issue');
    console.error('   → Check if port is accessible: nc -zv 127.0.0.1 18232');
    console.error('   → Check iptables/firewall rules');
    console.error('');
    console.error('6. ❌ Node Still Syncing');
    console.error('   → Wait for node to finish initial sync');
    console.error('   → Check status: zcash-cli getinfo');
    console.error('');

    console.error('🔧 Debug Steps:');
    console.error('');
    console.error('1. Check if zcashd is running:');
    console.error('   ps aux | grep zcashd');
    console.error('');
    console.error('2. Test RPC manually:');
    console.error('   curl --user user:pass --data-binary \'{"jsonrpc":"1.0","id":"test","method":"getblockcount","params":[]}\' -H \'content-type: text/plain;\' http://127.0.0.1:18232/');
    console.error('');
    console.error('3. Check zcash.conf:');
    console.error('   cat ~/.zcash/zcash.conf');
    console.error('');
    console.error('4. Check node logs:');
    console.error('   tail -f ~/.zcash/testnet3/debug.log');
    console.error('');

    if (error.cause) {
      console.error('📝 Technical Details:');
      console.error(error.cause);
      console.error('');
    }

    console.error('='.repeat(80));
    process.exit(1);
  }

  // -------------------------------------------------------------------------
  // 4. Query UTXOs
  // -------------------------------------------------------------------------
  console.log('\n💰 Fetching UTXOs...');
  console.log('   Note: You need to provide the transparent address that matches your private key');
  console.log('   The RPC will return UTXOs if you\'re running a full node with txindex');

  // For this example, we need the user to tell us which UTXOs to spend
  // In a real wallet, you would track your own addresses
  const transparentAddress = process.env.TRANSPARENT_ADDRESS;

  if (!transparentAddress) {
    console.error('\n❌ Error: TRANSPARENT_ADDRESS not set in .env file');
    console.error('   Please add your transparent testnet address');
    console.error('   Example: TRANSPARENT_ADDRESS=tmXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX');
    process.exit(1);
  }

  let utxos: Utxo[];
  try {
    utxos = await rpc.getUtxos(transparentAddress, 1);
    console.log(`   Found ${utxos.length} confirmed UTXO(s)`);

    if (utxos.length > 0) {
      console.log('\n   UTXOs:');
      utxos.forEach((utxo, i) => {
        console.log(`   ${i + 1}. ${utxo.txid}:${utxo.vout}`);
        console.log(`      Amount: ${utxo.amount} ZEC (${utxo.confirmations} confirmations)`);
      });
    }
  } catch (error: any) {
    console.error(`\n❌ Failed to fetch UTXOs: ${error.message}`);
    process.exit(1);
  }

  if (utxos.length === 0) {
    console.error('\n❌ No confirmed UTXOs found!');
    console.error('   Fund your address at: https://faucet.testnet.z.cash/');
    console.error(`   Your address: ${transparentAddress}`);
    process.exit(1);
  }

  // -------------------------------------------------------------------------
  // 5. Select UTXOs and Calculate Amounts
  // -------------------------------------------------------------------------
  const totalAvailable = utxos.reduce((sum, u) => sum + u.amount, 0);
  const amountSat = BigInt(Math.floor(amountZec * 100_000_000));
  const feeSat = 15_000n; // ZIP-317 fee for transparent→shielded
  const totalNeededSat = amountSat + feeSat;
  const totalNeededZec = Number(totalNeededSat) / 100_000_000;

  console.log('\n📊 Transaction Summary:');
  console.log(`   Available: ${totalAvailable.toFixed(8)} ZEC`);
  console.log(`   Sending: ${amountZec.toFixed(8)} ZEC`);
  console.log(`   Fee: ${Number(feeSat) / 100_000_000} ZEC`);
  console.log(`   Total: ${totalNeededZec.toFixed(8)} ZEC`);

  if (totalAvailable < totalNeededZec) {
    console.error(`\n❌ Insufficient funds!`);
    console.error(`   Need: ${totalNeededZec.toFixed(8)} ZEC`);
    console.error(`   Have: ${totalAvailable.toFixed(8)} ZEC`);
    process.exit(1);
  }

  // -------------------------------------------------------------------------
  // 6. Build Transaction
  // -------------------------------------------------------------------------
  console.log('\n🔨 Building transaction...');

  // Convert UTXOs to inputs
  const inputs: TransparentInput[] = utxos.map((utxo) => ({
    pubkey: publicKey,
    txid: Buffer.from(utxo.txid, 'hex').reverse(), // Reverse byte order
    vout: utxo.vout,
    amount: Math.floor(utxo.amount * 100_000_000).toString(),
    scriptPubKey,
  }));

  const payments: Payment[] = [
    {
      address: shieldedAddress,
      amount: amountSat.toString(),
    },
  ];

  console.log(`   Creating transaction request...`);
  const request = new TransactionRequest(payments);

  console.log(`   Proposing transaction with ${inputs.length} input(s)...`);
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
  console.log(`   Transaction hex: ${txHex.substring(0, 80)}...`);

  // -------------------------------------------------------------------------
  // 9. Broadcast
  // -------------------------------------------------------------------------
  console.log('\n📡 Broadcasting transaction...');

  let txid: string;
  try {
    txid = await rpc.sendRawTransaction(txHex);
    console.log(`   ✅ Transaction broadcast successful!`);
  } catch (error: any) {
    console.error(`\n❌ Failed to broadcast: ${error.message}`);
    console.error('\n   Full transaction hex (for debugging):');
    console.error(`   ${txHex}`);
    throw error;
  }

  // -------------------------------------------------------------------------
  // 10. Success!
  // -------------------------------------------------------------------------
  console.log('\n' + '='.repeat(80));
  console.log('🎉 SUCCESS! Transaction sent!');
  console.log('='.repeat(80));
  console.log(`\n   TXID: ${txid}`);
  console.log(`\n   View on explorer:`);
  console.log(`   https://testnet.zcashblockexplorer.com/transactions/${txid}`);
  console.log('');

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
