/**
 * Zcash Wallet Generator
 * Generates a new wallet with BIP39 mnemonic and saves to .env file
 */
import * as fs from 'fs';
import * as path from 'path';
import * as bip39 from 'bip39';
import * as secp256k1 from '@noble/secp256k1';
import { sha256 } from '@noble/hashes/sha256';
import { ripemd160 } from '@noble/hashes/ripemd160';

const ENV_PATH = path.join(import.meta.dirname, '..', '.env');

function pubkeyToMainnetAddress(pubkey: Uint8Array): string {
  const pkh = ripemd160(sha256(pubkey));
  const data = Buffer.concat([Buffer.from([0x1c, 0xb8]), Buffer.from(pkh)]); // mainnet prefix
  const check = sha256(sha256(data)).slice(0, 4);
  return base58Encode(Buffer.concat([data, Buffer.from(check)]));
}

function base58Encode(data: Buffer): string {
  const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
  let num = BigInt('0x' + data.toString('hex'));
  let result = '';
  while (num > 0n) { result = alphabet[Number(num % 58n)] + result; num /= 58n; }
  for (const b of data) { if (b !== 0) break; result = '1' + result; }
  return result;
}

async function main() {
  // Check if .env already exists
  if (fs.existsSync(ENV_PATH)) {
    console.log('Wallet already exists at .env');
    console.log('Delete .env first if you want to generate a new wallet.\n');

    const env = fs.readFileSync(ENV_PATH, 'utf-8');
    const addressMatch = env.match(/ADDRESS=(.+)/);
    if (addressMatch) {
      console.log(`Current address: ${addressMatch[1]}`);
    }
    return;
  }

  // Generate 24-word mnemonic (256 bits of entropy)
  const mnemonic = bip39.generateMnemonic(256);

  // Derive seed from mnemonic
  const seed = await bip39.mnemonicToSeed(mnemonic);

  // Use first 32 bytes of seed as private key (simplified derivation)
  const privateKey = seed.slice(0, 32);
  const publicKey = secp256k1.getPublicKey(privateKey, true);
  const address = pubkeyToMainnetAddress(publicKey);

  // Build .env content
  const envContent = `# Zcash Mainnet Wallet
# Generated: ${new Date().toISOString()}
# WARNING: Keep this file secret! Never commit to git.

MNEMONIC="${mnemonic}"
PRIVATE_KEY=${Buffer.from(privateKey).toString('hex')}
PUBLIC_KEY=${Buffer.from(publicKey).toString('hex')}
ADDRESS=${address}

# Zebra RPC (mainnet default port)
ZEBRA_HOST=localhost
ZEBRA_PORT=8232
`;

  fs.writeFileSync(ENV_PATH, envContent, { mode: 0o600 });

  console.log('New wallet generated!\n');
  console.log(`Mnemonic (24 words):\n${mnemonic}\n`);
  console.log(`Address: ${address}`);
  console.log(`\nSaved to: ${ENV_PATH}`);
  console.log('\nIMPORTANT: Back up your mnemonic phrase securely!');
}

main().catch(console.error);
