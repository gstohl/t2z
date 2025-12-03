/**
 * Device B - Offline Signer (Hardware Wallet Simulation)
 *
 * This script simulates the OFFLINE device (hardware wallet) that:
 * 1. Holds the private key securely
 * 2. Receives a sighash from Device A
 * 3. Signs it and returns the signature
 *
 * This device NEVER connects to the network!
 * In a real hardware wallet, this would be an air-gapped device.
 */
import * as fs from 'fs';
import * as path from 'path';
import * as readline from 'readline';
import * as secp256k1 from '@noble/secp256k1';

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

function prompt(rl: readline.Interface, question: string): Promise<string> {
  return new Promise(resolve => rl.question(question, resolve));
}

async function main() {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });

  const env = loadEnv();
  const privateKey = Buffer.from(env.PRIVATE_KEY, 'hex');
  const address = env.ADDRESS;

  console.log('\n' + '='.repeat(60));
  console.log('  DEVICE B - OFFLINE SIGNER (Hardware Wallet Simulation)');
  console.log('='.repeat(60));
  console.log('\nThis device holds the private key and signs transactions.');
  console.log('In production, this would be an air-gapped hardware wallet.\n');
  console.log(`Wallet address: ${address}\n`);

  // Get sighash from user
  console.log('Paste the sighash from Device A:\n');
  const sighashHex = await prompt(rl, 'SIGHASH: ');

  if (!sighashHex.trim() || sighashHex.trim().length !== 64) {
    console.log('\nInvalid sighash (expected 32 bytes / 64 hex chars). Exiting.');
    rl.close();
    process.exit(1);
  }

  const sighash = Buffer.from(sighashHex.trim(), 'hex');

  console.log('\nSigning...');

  // Sign the sighash
  const signature = await secp256k1.signAsync(sighash, privateKey);
  const signatureHex = Buffer.from(signature.toCompactRawBytes()).toString('hex');

  console.log('\n' + '='.repeat(60));
  console.log('  SIGNATURE READY');
  console.log('='.repeat(60));
  console.log('\nCopy this signature back to Device A:\n');
  console.log(`SIGNATURE: ${signatureHex}`);
  console.log('\n' + '='.repeat(60));
  console.log('\nThe private key stayed on this device!');

  rl.close();
}

main().catch(err => {
  console.error('\nError:', err.message);
  process.exit(1);
});
