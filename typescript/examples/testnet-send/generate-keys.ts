#!/usr/bin/env node
/**
 * Generate Private Key and Transparent Address
 *
 * This utility generates a new random private key and shows the
 * corresponding transparent testnet address.
 *
 * Usage: npm run generate-keys
 */

import { randomBytes, createHash } from 'crypto';
import * as secp256k1 from '@noble/secp256k1';

// ============================================================================
// Base58 Encoding
// ============================================================================

const BASE58_ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';

function base58Encode(buffer: Buffer): string {
  let num = BigInt('0x' + buffer.toString('hex'));
  let encoded = '';

  while (num > 0n) {
    const remainder = Number(num % 58n);
    num = num / 58n;
    encoded = BASE58_ALPHABET[remainder] + encoded;
  }

  // Add leading '1's for leading zeros in buffer
  for (let i = 0; i < buffer.length && buffer[i] === 0; i++) {
    encoded = '1' + encoded;
  }

  return encoded;
}

// ============================================================================
// Address Generation
// ============================================================================

function hash160(buffer: Buffer): Buffer {
  const sha256Hash = createHash('sha256').update(buffer).digest();
  return createHash('ripemd160').update(sha256Hash).digest();
}

function generateTransparentAddress(publicKey: Buffer, testnet: boolean = true): string {
  // Hash160 of public key
  const pubkeyHash = hash160(publicKey);

  // Zcash uses 2-byte version prefixes (not 1 like Bitcoin!)
  // Testnet P2PKH: 0x1D25 → "tm" prefix
  // Mainnet P2PKH: 0x1CB8 → "t1" prefix
  const versionBytes = testnet
    ? Buffer.from([0x1D, 0x25])  // testnet
    : Buffer.from([0x1C, 0xB8]); // mainnet

  const versionedHash = Buffer.concat([versionBytes, pubkeyHash]);

  // Double SHA256 for checksum
  const checksum = createHash('sha256')
    .update(createHash('sha256').update(versionedHash).digest())
    .digest()
    .slice(0, 4);

  // Encode as Base58
  return base58Encode(Buffer.concat([versionedHash, checksum]));
}

// ============================================================================
// WIF (Wallet Import Format) Encoding
// ============================================================================

function toWif(privateKey: Buffer, testnet: boolean = true, compressed: boolean = true): string {
  // Version byte: 0xEF for testnet, 0x80 for mainnet
  const versionByte = testnet ? 0xEF : 0x80;

  let payload = Buffer.concat([
    Buffer.from([versionByte]),
    privateKey
  ]);

  // Add compression flag if needed
  if (compressed) {
    payload = Buffer.concat([payload, Buffer.from([0x01])]);
  }

  // Double SHA256 for checksum
  const checksum = createHash('sha256')
    .update(createHash('sha256').update(payload).digest())
    .digest()
    .slice(0, 4);

  return base58Encode(Buffer.concat([payload, checksum]));
}

// ============================================================================
// Main
// ============================================================================

function main() {
  console.log('='.repeat(80));
  console.log('🔐  Generate Zcash Testnet Keys');
  console.log('='.repeat(80));

  // Generate random private key
  const privateKey = randomBytes(32);
  const privateKeyHex = privateKey.toString('hex');

  // Derive public key
  const publicKey = Buffer.from(secp256k1.getPublicKey(privateKey, true));
  const publicKeyHex = publicKey.toString('hex');

  // Generate testnet address
  const testnetAddress = generateTransparentAddress(publicKey, true);

  // Also generate mainnet address (for reference)
  const mainnetAddress = generateTransparentAddress(publicKey, false);

  // WIF format (for import into wallets)
  const wifTestnet = toWif(privateKey, true, true);
  const wifMainnet = toWif(privateKey, false, true);

  // Display results
  console.log('\n✅ Generated New Keys\n');

  console.log('📋 Private Key (Hex):');
  console.log(`   ${privateKeyHex}`);
  console.log('   ⚠️  Keep this secret! Never share or commit to git!\n');

  console.log('📋 Private Key (WIF - Testnet):');
  console.log(`   ${wifTestnet}`);
  console.log('   Use this to import into testnet wallets\n');

  console.log('📋 Public Key (Compressed):');
  console.log(`   ${publicKeyHex}\n`);

  console.log('📧 Transparent Address (TESTNET):');
  console.log(`   ${testnetAddress}`);
  console.log('   Fund this address at: https://faucet.testnet.z.cash/\n');

  console.log('📧 Transparent Address (MAINNET):');
  console.log(`   ${mainnetAddress}`);
  console.log('   ⚠️  DO NOT use for real funds (you have the private key!)\n');

  console.log('='.repeat(80));
  console.log('📝 Next Steps:');
  console.log('='.repeat(80));
  console.log('1. Copy the Private Key (Hex) above');
  console.log('2. Add it to your .env file:');
  console.log(`   PRIVATE_KEY=${privateKeyHex}`);
  console.log('');
  console.log('3. Add the Testnet Address to your .env file:');
  console.log(`   TRANSPARENT_ADDRESS=${testnetAddress}`);
  console.log('');
  console.log('4. Fund the address from the testnet faucet');
  console.log('   https://faucet.testnet.z.cash/');
  console.log('');
  console.log('5. Wait for confirmation (usually 1-2 minutes)');
  console.log('');
  console.log('6. Run the example: npm start');
  console.log('');

  // Also create a sample .env content
  console.log('='.repeat(80));
  console.log('💾 Sample .env Configuration:');
  console.log('='.repeat(80));
  console.log('');
  console.log(`PRIVATE_KEY=${privateKeyHex}`);
  console.log(`TRANSPARENT_ADDRESS=${testnetAddress}`);
  console.log('SHIELDED_ADDRESS=your_recipient_address_here');
  console.log('RPC_URL=http://user:pass@127.0.0.1:18232');
  console.log('AMOUNT=0.001');
  console.log('');

  console.log('='.repeat(80));
  console.log('ℹ️  Address Format Reference:');
  console.log('='.repeat(80));
  console.log('');
  console.log('Testnet Transparent (P2PKH):  tm...');
  console.log('Mainnet Transparent (P2PKH):  t1...');
  console.log('');
  console.log('Testnet Unified Address:      utest1...');
  console.log('Mainnet Unified Address:      u1...');
  console.log('');
  console.log('Testnet Orchard:              utest... (via unified)');
  console.log('Mainnet Orchard:              u1... (via unified)');
  console.log('');
  console.log('⚠️  Security Warning:');
  console.log('   - Never share your private key');
  console.log('   - Never commit .env to git (it\'s in .gitignore)');
  console.log('   - Use testnet only for testing');
  console.log('   - Generate new keys for mainnet if needed');
  console.log('');
}

main();
