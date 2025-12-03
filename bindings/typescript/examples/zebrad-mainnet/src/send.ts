/**
 * Interactive Zcash Send Script
 * Reads wallet from .env, shows balance, prompts for recipients, sends transaction
 */
import * as fs from 'fs';
import * as path from 'path';
import * as readline from 'readline';
import * as secp256k1 from '@noble/secp256k1';
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
} from 't2z';

// Load .env
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
interface Recipient { address: string; amount: bigint; memo?: string; }

// Reverse hex string (for txid endianness conversion)
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
  const privateKey = Buffer.from(env.PRIVATE_KEY, 'hex');
  const publicKey = Buffer.from(secp256k1.getPublicKey(privateKey, true));
  const address = env.ADDRESS;

  console.log('\n=== t2z Mainnet Send ===\n');
  console.log(`Your address: ${address}\n`);

  // Fetch UTXOs and show balance
  process.stdout.write('Fetching balance... ');
  const utxos = await getUTXOs(address);
  const totalSats = utxos.reduce((sum, u) => sum + u.satoshis, 0);
  const totalZec = totalSats / 1e8;
  console.log('done\n');

  if (utxos.length === 0) {
    console.log('No UTXOs found. Send ZEC to this address first.');
    process.exit(0);
  }

  console.log(`Balance: ${totalZec.toFixed(8)} ZEC (${utxos.length} UTXO${utxos.length > 1 ? 's' : ''})\n`);

  // Interactive recipient input
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  const recipients: Recipient[] = [];

  console.log('Enter recipients (shielded addresses starting with "u" recommended)');
  console.log('Press Enter with empty address to finish.\n');

  while (true) {
    const addr = await prompt(rl, `Recipient ${recipients.length + 1} address: `);
    if (!addr.trim()) break;

    const amountStr = await prompt(rl, `Amount in ZEC: `);
    const amountZec = parseFloat(amountStr);
    if (isNaN(amountZec) || amountZec <= 0) {
      console.log('Invalid amount, skipping.\n');
      continue;
    }

    const amountSats = BigInt(Math.round(amountZec * 1e8));

    // Ask for memo if shielded address
    let memo: string | undefined;
    if (!addr.startsWith('t')) {
      const memoInput = await prompt(rl, `Memo (optional, press Enter to skip): `);
      if (memoInput.trim()) {
        memo = memoInput.trim();
      }
    }

    recipients.push({ address: addr.trim(), amount: amountSats, memo });
    const memoInfo = memo ? ` [memo: "${memo.slice(0, 20)}${memo.length > 20 ? '...' : ''}"]` : '';
    console.log(`Added: ${amountZec.toFixed(8)} ZEC → ${addr.slice(0, 30)}...${memoInfo}\n`);
  }

  rl.close();

  if (recipients.length === 0) {
    console.log('\nNo recipients entered. Exiting.');
    process.exit(0);
  }

  // Calculate fee
  const numTransparent = recipients.filter(r => r.address.startsWith('t')).length;
  const numShielded = recipients.filter(r => !r.address.startsWith('t')).length;
  const fee = calculateFee(utxos.length, numTransparent + 1, numShielded); // +1 for potential change

  const totalSend = recipients.reduce((sum, r) => sum + r.amount, 0n);
  const totalNeeded = totalSend + fee;

  console.log('\n--- Transaction Summary ---');
  for (const r of recipients) {
    const memoInfo = r.memo ? ` [memo]` : '';
    console.log(`  ${(Number(r.amount) / 1e8).toFixed(8)} ZEC → ${r.address.slice(0, 40)}...${memoInfo}`);
  }
  console.log(`  Fee: ${(Number(fee) / 1e8).toFixed(8)} ZEC`);
  console.log(`  Total: ${(Number(totalNeeded) / 1e8).toFixed(8)} ZEC`);

  if (totalNeeded > BigInt(totalSats)) {
    console.log(`\nInsufficient balance! Need ${(Number(totalNeeded) / 1e8).toFixed(8)} ZEC but have ${totalZec.toFixed(8)} ZEC`);
    process.exit(1);
  }

  // Build inputs
  const pkh = ripemd160(sha256(publicKey));
  const script = Buffer.concat([Buffer.from([0x76, 0xa9, 0x14]), Buffer.from(pkh), Buffer.from([0x88, 0xac])]);

  const inputs: TransparentInput[] = [];
  let inputTotal = 0n;
  for (const utxo of utxos) {
    // txid needs to be in little-endian (reversed from RPC display format)
    inputs.push({
      pubkey: publicKey,
      txid: Buffer.from(reverseHex(utxo.txid), 'hex'),
      vout: utxo.outputIndex,
      amount: BigInt(utxo.satoshis),
      scriptPubKey: script,
    });
    inputTotal += BigInt(utxo.satoshis);
    if (inputTotal >= totalNeeded) break;
  }

  // Build outputs
  const outputs = recipients.map(r => {
    const output: { address: string; amount: string; memo?: string } = {
      address: r.address,
      amount: r.amount.toString(),
    };
    if (r.memo) output.memo = r.memo;
    return output;
  });

  // Get current block height
  const blockHeight = await getBlockHeight();

  // Create transaction request
  const request = new TransactionRequest(outputs);
  request.setTargetHeight(blockHeight + 10);

  console.log('\nBuilding transaction...');

  process.stdout.write('  Proposing... ');
  const pczt = proposeTransaction(inputs, request);
  console.log('done');

  process.stdout.write('  Proving... ');
  const proved = proveTransaction(pczt);
  console.log('done');

  process.stdout.write('  Signing... ');
  let signed = proved;
  for (let i = 0; i < inputs.length; i++) {
    const sighash = getSighash(signed, i);
    const sig = await secp256k1.signAsync(sighash, privateKey);
    signed = appendSignature(signed, i, Buffer.from(sig.toCompactRawBytes()));
  }
  console.log('done');

  process.stdout.write('  Finalizing... ');
  const txBytes = finalizeAndExtract(signed);
  console.log('done');

  process.stdout.write('  Broadcasting... ');
  const txid = await rpc('sendrawtransaction', [txBytes.toString('hex')]);
  console.log('done');

  console.log(`\nTransaction sent!`);
  console.log(`TXID: ${txid}`);
  console.log(`\nView on explorer: https://zcashblockexplorer.com/transactions/${txid}`);

  request.free();
}

main().catch(err => {
  console.error('\nError:', err.message);
  process.exit(1);
});
