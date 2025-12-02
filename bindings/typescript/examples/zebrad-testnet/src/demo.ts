/**
 * Testnet Demo: Mixed Transparent + Shielded (T→T+Z)
 * Usage: PRIVATE_KEY=<hex> npx tsx src/demo.ts
 */
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

const ZEBRA_RPC = 'http://localhost:18232';
const SHIELDED = 'u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz';

async function main() {
  const privKeyHex = process.env.PRIVATE_KEY;
  if (!privKeyHex) {
    console.log('t2z Testnet Demo - Mixed T→T+Z Transaction\n');
    console.log('Usage: PRIVATE_KEY=<hex> npx tsx src/demo.ts');
    console.log('\nThe demo will automatically fetch UTXOs from your Zebra node.');
    process.exit(1);
  }

  const privKey = Buffer.from(privKeyHex, 'hex');
  const pubkey = Buffer.from(secp256k1.getPublicKey(privKey, true));
  const address = pubkeyToTestnetAddress(pubkey);

  console.log(`Address: ${address}`);
  process.stdout.write('Fetching UTXOs... ');

  const utxos = await getUTXOs(address);
  if (utxos.length === 0) {
    console.log('✗ No UTXOs. Get testnet ZEC from faucet.zecpages.com');
    process.exit(1);
  }
  console.log(`✓ Found ${utxos.length} UTXO(s)\n`);

  const utxo = utxos[0];
  const txid = Buffer.from(utxo.txid, 'hex');
  const pkh = ripemd160(sha256(pubkey));
  const script = Buffer.concat([Buffer.from([0x76, 0xa9, 0x14]), pkh, Buffer.from([0x88, 0xac])]);

  const input: TransparentInput = { pubkey, txid, vout: utxo.outputIndex, amount: BigInt(utxo.satoshis), scriptPubKey: script };

  const fee = calculateFee(1, 2, 1);
  const available = BigInt(utxo.satoshis) - fee;
  const tAmt = available * 40n / 100n;
  const zAmt = available * 40n / 100n;

  console.log(`Input:       ${(Number(utxo.satoshis) / 1e8).toFixed(8)} ZEC`);
  console.log(`Transparent: ${(Number(tAmt) / 1e8).toFixed(8)} ZEC → ${address.slice(0, 20)}...`);
  console.log(`Shielded:    ${(Number(zAmt) / 1e8).toFixed(8)} ZEC → ${SHIELDED.slice(0, 20)}...`);
  console.log(`Fee:         ${(Number(fee) / 1e8).toFixed(8)} ZEC\n`);

  const request = new TransactionRequest([
    { address, amount: tAmt.toString() },
    { address: SHIELDED, amount: zAmt.toString() },
  ]);
  request.setUseMainnet(false);
  request.setTargetHeight(3_000_000);

  process.stdout.write('Proposing... ');
  const pczt = proposeTransaction([input], request);
  console.log('✓');

  process.stdout.write('Proving... ');
  const proved = proveTransaction(pczt);
  console.log('✓');

  process.stdout.write('Signing... ');
  const sighash = getSighash(proved, 0);
  const sig = await secp256k1.signAsync(sighash, privKey);
  const signed = appendSignature(proved, 0, Buffer.from(sig.toCompactRawBytes()));
  console.log('✓');

  process.stdout.write('Finalizing... ');
  const txBytes = finalizeAndExtract(signed);
  console.log('✓');

  process.stdout.write('Broadcasting... ');
  const txidResult = await broadcast(txBytes.toString('hex'));
  console.log('✓');
  console.log(`\nTXID: ${txidResult}`);

  request.free();
}

interface UTXO { txid: string; outputIndex: number; satoshis: number; }

async function getUTXOs(address: string): Promise<UTXO[]> {
  const resp = await fetch(ZEBRA_RPC, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ jsonrpc: '2.0', method: 'getaddressutxos', params: [{ addresses: [address] }], id: 1 }),
  });
  const json = await resp.json() as any;
  if (json.error) throw new Error(json.error.message);
  return json.result;
}

function pubkeyToTestnetAddress(pubkey: Buffer): string {
  const pkh = ripemd160(sha256(pubkey));
  const data = Buffer.concat([Buffer.from([0x1d, 0x25]), pkh]);
  const check = sha256(sha256(data)).slice(0, 4);
  return base58Encode(Buffer.concat([data, check]));
}

function base58Encode(data: Buffer): string {
  const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
  let num = BigInt('0x' + data.toString('hex'));
  let result = '';
  while (num > 0n) { result = alphabet[Number(num % 58n)] + result; num /= 58n; }
  for (const b of data) { if (b !== 0) break; result = '1' + result; }
  return result;
}

async function broadcast(txHex: string): Promise<string> {
  const resp = await fetch(ZEBRA_RPC, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ jsonrpc: '2.0', method: 'sendrawtransaction', params: [txHex], id: 1 }),
  });
  const json = await resp.json() as any;
  if (json.error) throw new Error(json.error.message);
  return json.result;
}

main().catch(console.error);
