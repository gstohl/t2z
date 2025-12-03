/**
 * Device A - Online Device (Hardware Wallet Simulation)
 *
 * This script simulates the ONLINE device that:
 * 1. Builds the transaction (PCZT)
 * 2. Generates proofs
 * 3. Outputs sighash for offline signing
 * 4. Waits for signature from Device B
 * 5. Finalizes and broadcasts
 *
 * The private key NEVER touches this device!
 */
import * as fs from 'fs';
import * as path from 'path';
import * as readline from 'readline';
import { sha256 } from '@noble/hashes/sha256';
import { ripemd160 } from '@noble/hashes/ripemd160';
import {
  TransactionRequest,
  proposeTransaction,
  proveTransaction,
  getSighash,
  appendSignature,
  finalizeAndExtract,
  calculateFee,
  TransparentInput,
  serializePczt,
  parsePczt,
} from 't2z';

const ENV_PATH = path.join(import.meta.dirname, '..', '.env');

function loadEnv(): Record<string, string> {
  if (!fs.existsSync(ENV_PATH)) {
    console.error('No .env file found. Run: npm run generate-wallet');
    process.exit(1);
  }
  const env: Record<string, string> = {};
  for (const line of fs.readFileSync(ENV_PATH, 'utf-8').split('\n')) {
    const match = line.match(/^([A-Z_]+)=["']?(.+?)["']?$/);
    if (match) env[match[1]] = match[2];
  }
  return env;
}

const env = loadEnv();
const ZEBRA_RPC = `http://${env.ZEBRA_HOST || 'localhost'}:${env.ZEBRA_PORT || '8232'}`;

interface UTXO { txid: string; outputIndex: number; satoshis: number; height: number; }

function reverseHex(hex: string): string {
  return Buffer.from(hex, 'hex').reverse().toString('hex');
}

async function rpc(method: string, params: any[] = []): Promise<any> {
  const resp = await fetch(ZEBRA_RPC, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ jsonrpc: '2.0', method, params, id: 1 }),
  });
  const json = await resp.json() as any;
  if (json.error) throw new Error(json.error.message);
  return json.result;
}

async function getUTXOs(address: string): Promise<UTXO[]> {
  return rpc('getaddressutxos', [{ addresses: [address] }]);
}

async function getBlockHeight(): Promise<number> {
  const info = await rpc('getblockchaininfo');
  return info.blocks;
}

function prompt(rl: readline.Interface, question: string): Promise<string> {
  return new Promise(resolve => rl.question(question, resolve));
}

async function main() {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });

  // We only need PUBLIC_KEY and ADDRESS - private key stays on Device B!
  const publicKey = Buffer.from(env.PUBLIC_KEY, 'hex');
  const address = env.ADDRESS;

  console.log('\n' + '='.repeat(60));
  console.log('  DEVICE A - ONLINE DEVICE (Hardware Wallet Simulation)');
  console.log('='.repeat(60));
  console.log('\nThis device builds transactions but NEVER sees the private key!\n');
  console.log(`Your address: ${address}\n`);

  // Fetch UTXOs
  process.stdout.write('Fetching balance... ');
  const utxos = await getUTXOs(address);
  const totalSats = utxos.reduce((sum, u) => sum + u.satoshis, 0);
  const totalZec = totalSats / 1e8;
  console.log('done\n');

  if (utxos.length === 0) {
    console.log('No UTXOs found. Send ZEC to this address first.');
    rl.close();
    process.exit(0);
  }

  console.log(`Balance: ${totalZec.toFixed(8)} ZEC (${utxos.length} UTXO${utxos.length > 1 ? 's' : ''})\n`);

  // Get recipient
  console.log('Enter recipient (shielded address starting with "u" recommended):\n');
  const recipientAddr = await prompt(rl, 'Recipient address: ');
  if (!recipientAddr.trim()) {
    console.log('No address entered. Exiting.');
    rl.close();
    process.exit(0);
  }

  const amountStr = await prompt(rl, 'Amount in ZEC: ');
  const amountZec = parseFloat(amountStr);
  if (isNaN(amountZec) || amountZec <= 0) {
    console.log('Invalid amount. Exiting.');
    rl.close();
    process.exit(1);
  }

  const amountSats = BigInt(Math.round(amountZec * 1e8));

  // Optional memo for shielded
  let memo: string | undefined;
  if (!recipientAddr.startsWith('t')) {
    const memoInput = await prompt(rl, 'Memo (optional, press Enter to skip): ');
    if (memoInput.trim()) memo = memoInput.trim();
  }

  // Calculate fee
  const isShielded = !recipientAddr.startsWith('t');
  const fee = calculateFee(1, isShielded ? 1 : 2, isShielded ? 1 : 0);
  const totalNeeded = amountSats + fee;

  if (totalNeeded > BigInt(totalSats)) {
    console.log(`\nInsufficient balance! Need ${(Number(totalNeeded) / 1e8).toFixed(8)} ZEC`);
    rl.close();
    process.exit(1);
  }

  console.log('\n--- Transaction Summary ---');
  console.log(`  To: ${recipientAddr.slice(0, 40)}...`);
  console.log(`  Amount: ${amountZec.toFixed(8)} ZEC`);
  if (memo) console.log(`  Memo: "${memo}"`);
  console.log(`  Fee: ${(Number(fee) / 1e8).toFixed(8)} ZEC`);
  console.log('');

  // Build input
  const pkh = ripemd160(sha256(publicKey));
  const script = Buffer.concat([Buffer.from([0x76, 0xa9, 0x14]), Buffer.from(pkh), Buffer.from([0x88, 0xac])]);
  const utxo = utxos[0];

  const input: TransparentInput = {
    pubkey: publicKey,
    txid: Buffer.from(reverseHex(utxo.txid), 'hex'),
    vout: utxo.outputIndex,
    amount: BigInt(utxo.satoshis),
    scriptPubKey: script,
  };

  // Build output
  const output: { address: string; amount: string; memo?: string } = {
    address: recipientAddr.trim(),
    amount: amountSats.toString(),
  };
  if (memo) output.memo = memo;

  const blockHeight = await getBlockHeight();

  // Create and prove transaction
  console.log('Building transaction...');
  process.stdout.write('  Proposing... ');
  const request = new TransactionRequest([output]);
  request.setTargetHeight(blockHeight + 10);
  const pczt = proposeTransaction([input], request);
  console.log('done');

  process.stdout.write('  Proving (this may take a few seconds)... ');
  const proved = proveTransaction(pczt);
  console.log('done');

  // Get sighash for input 0
  const sighash = getSighash(proved, 0);
  const sighashHex = sighash.toString('hex');

  // Serialize PCZT for later
  const psztHex = serializePczt(proved).toString('hex');

  console.log('\n' + '='.repeat(60));
  console.log('  SIGHASH READY FOR OFFLINE SIGNING');
  console.log('='.repeat(60));
  console.log('\nCopy this sighash to Device B:\n');
  console.log(`SIGHASH: ${sighashHex}`);
  console.log('\n' + '='.repeat(60));

  // Save PCZT to temp file for later
  const tempFile = path.join(import.meta.dirname, '..', '.pczt-temp');
  fs.writeFileSync(tempFile, psztHex);

  // Wait for signature
  console.log('\nRun Device B with the sighash, then paste the signature here.\n');
  const signatureHex = await prompt(rl, 'Paste signature from Device B: ');

  if (!signatureHex.trim() || signatureHex.trim().length !== 128) {
    console.log('Invalid signature (expected 64 bytes / 128 hex chars). Exiting.');
    rl.close();
    process.exit(1);
  }

  const signature = Buffer.from(signatureHex.trim(), 'hex');

  // Load PCZT back and append signature
  console.log('\nFinalizing transaction...');
  const loadedPczt = parsePczt(Buffer.from(fs.readFileSync(tempFile, 'utf-8'), 'hex'));
  const signed = appendSignature(loadedPczt, 0, signature);

  process.stdout.write('  Extracting... ');
  const txBytes = finalizeAndExtract(signed);
  console.log('done');

  process.stdout.write('  Broadcasting... ');
  const txid = await rpc('sendrawtransaction', [txBytes.toString('hex')]);
  console.log('done');

  // Cleanup temp file
  fs.unlinkSync(tempFile);

  console.log('\n' + '='.repeat(60));
  console.log('  TRANSACTION BROADCAST SUCCESSFUL!');
  console.log('='.repeat(60));
  console.log(`\nTXID: ${txid}`);
  console.log('\nThe private key NEVER touched this device!');

  request.free();
  rl.close();
}

main().catch(err => {
  console.error('\nError:', err.message);
  process.exit(1);
});
