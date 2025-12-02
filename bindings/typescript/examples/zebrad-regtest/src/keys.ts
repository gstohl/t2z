/**
 * Zcash Transparent Key Management
 *
 * Client-side key generation and signing for Zcash transparent addresses
 * using bitcoinjs-lib + ecpair. Compatible with Zebra (no wallet required).
 */

import * as bitcoin from 'bitcoinjs-lib';
import ECPairFactory, { ECPairAPI, ECPairInterface } from 'ecpair';
import * as ecc from 'tiny-secp256k1';
import bs58check from 'bs58check';

// Initialize ECPair with secp256k1 implementation
// Handle both ESM and CJS module resolution
const ECPairMod = (ECPairFactory as any).default || ECPairFactory;
const ECPair: ECPairAPI = ECPairMod(ecc);

// Zcash uses 2-byte version prefixes (not compatible with bitcoinjs-lib Network type)
// Testnet/Regtest P2PKH: 0x1D25 -> 'tm' prefix
// Mainnet P2PKH: 0x1CB8 -> 't1' prefix
const ZCASH_TESTNET_P2PKH = Buffer.from([0x1d, 0x25]);
const ZCASH_MAINNET_P2PKH = Buffer.from([0x1c, 0xb8]);

// WIF version bytes
const WIF_TESTNET = 0xef;
const WIF_MAINNET = 0x80;

export type NetworkType = 'mainnet' | 'testnet' | 'regtest';

export interface ZcashKeypair {
  privateKey: Buffer;
  publicKey: Buffer;
  address: string;
  wif: string;
  ecpair: ECPairInterface;
}

/**
 * Generate a new random Zcash transparent keypair
 */
export function generateKeypair(network: NetworkType = 'regtest'): ZcashKeypair {
  const ecpair = ECPair.makeRandom({ compressed: true });
  return keypairFromECPair(ecpair, network);
}

/**
 * Create a keypair from an existing private key buffer
 */
export function keypairFromPrivateKey(
  privateKey: Buffer,
  network: NetworkType = 'regtest'
): ZcashKeypair {
  const ecpair = ECPair.fromPrivateKey(privateKey, { compressed: true });
  return keypairFromECPair(ecpair, network);
}

/**
 * Import a keypair from WIF (Wallet Import Format)
 */
export function keypairFromWIF(wif: string, network: NetworkType = 'regtest'): ZcashKeypair {
  // Decode WIF manually since we're not using bitcoinjs network
  const decoded = bs58check.decode(wif);
  const version = decoded[0];

  // Validate version byte
  const expectedVersion = network === 'mainnet' ? WIF_MAINNET : WIF_TESTNET;
  if (version !== expectedVersion) {
    throw new Error(`Invalid WIF version byte: ${version}, expected ${expectedVersion}`);
  }

  // Extract private key (skip version byte, potentially skip compression flag)
  let privateKey: Buffer;
  if (decoded.length === 34 && decoded[33] === 0x01) {
    // Compressed
    privateKey = Buffer.from(decoded.slice(1, 33));
  } else if (decoded.length === 33) {
    // Uncompressed
    privateKey = Buffer.from(decoded.slice(1, 33));
  } else {
    throw new Error(`Invalid WIF length: ${decoded.length}`);
  }

  return keypairFromPrivateKey(privateKey, network);
}

/**
 * Create ZcashKeypair from ECPair
 */
function keypairFromECPair(
  ecpair: ECPairInterface,
  network: NetworkType
): ZcashKeypair {
  const publicKey = Buffer.from(ecpair.publicKey);
  const privateKey = Buffer.from(ecpair.privateKey!);

  // Create Zcash address (P2PKH)
  const address = pubkeyToAddress(publicKey, network);
  const wif = privateKeyToWIF(privateKey, network);

  return {
    privateKey,
    publicKey,
    address,
    wif,
    ecpair,
  };
}

/**
 * Convert private key to WIF format
 */
export function privateKeyToWIF(privateKey: Buffer, network: NetworkType = 'regtest'): string {
  const version = network === 'mainnet' ? WIF_MAINNET : WIF_TESTNET;
  // Add version byte and compression flag (0x01 for compressed)
  const payload = Buffer.concat([
    Buffer.from([version]),
    privateKey,
    Buffer.from([0x01]),
  ]);
  return bs58check.encode(payload);
}

/**
 * Convert public key to Zcash transparent address
 * Uses 2-byte version prefix for Zcash addresses
 */
export function pubkeyToAddress(publicKey: Buffer, network: NetworkType = 'regtest'): string {
  // Hash160 the public key
  const hash = bitcoin.crypto.hash160(publicKey);

  // Zcash uses 2-byte version prefix
  const versionBytes = network === 'mainnet' ? ZCASH_MAINNET_P2PKH : ZCASH_TESTNET_P2PKH;

  // Concatenate version + hash
  const payload = Buffer.concat([versionBytes, hash]);

  // Base58check encode
  return bs58check.encode(payload);
}

/**
 * Sign a message hash (sighash) with the keypair
 * Returns DER-encoded signature with SIGHASH_ALL appended
 */
export function sign(messageHash: Buffer, keypair: ZcashKeypair): Buffer {
  const signature = keypair.ecpair.sign(messageHash, { lowS: true });
  // Return DER encoded signature with SIGHASH_ALL
  return Buffer.from(bitcoin.script.signature.encode(signature, bitcoin.Transaction.SIGHASH_ALL));
}

/**
 * Sign and return 64-byte compact signature (r || s format)
 * t2z library expects compact format, not DER
 */
export function signDER(messageHash: Buffer, keypair: ZcashKeypair): Buffer {
  const signature = keypair.ecpair.sign(messageHash, { lowS: true });
  // ecpair.sign() returns 64-byte compact signature directly
  return Buffer.from(signature);
}

/**
 * Verify a signature
 */
export function verify(messageHash: Buffer, signature: Buffer, publicKey: Buffer): boolean {
  return ecc.verify(messageHash, publicKey, signature);
}

/**
 * Get public key hash (hash160 of public key)
 */
export function pubkeyHash(publicKey: Buffer): Buffer {
  return bitcoin.crypto.hash160(publicKey);
}

/**
 * Double SHA256
 */
export function doubleSha256(data: Buffer): Buffer {
  return bitcoin.crypto.hash256(data);
}

/**
 * Convert hex string to Buffer
 */
export function hexToBytes(hex: string): Buffer {
  return Buffer.from(hex, 'hex');
}

/**
 * Convert Buffer to hex string
 */
export function bytesToHex(bytes: Buffer | Uint8Array): string {
  return Buffer.from(bytes).toString('hex');
}

// Pre-generated test keypair for regtest (deterministic for reproducibility)
// WARNING: Only use this for testing! Never use in production.
const TEST_PRIVKEY_HEX = 'e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35';

export const TEST_KEYPAIR = keypairFromPrivateKey(hexToBytes(TEST_PRIVKEY_HEX), 'regtest');

// Alias for backward compatibility
export const generateNewKeypair = generateKeypair;

// Export ECPair factory for advanced usage
export { ECPair, ecc };
