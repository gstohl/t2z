/**
 * Cryptographic signing utilities using secp256k1
 */

import * as secp256k1 from '@noble/secp256k1';

/**
 * Sign a 32-byte message hash with secp256k1
 * Returns a 64-byte signature (r || s) without recovery ID
 *
 * @param privateKey 32-byte private key
 * @param messageHash 32-byte message hash (e.g., sighash)
 * @returns 64-byte signature (r || s)
 */
export async function signMessage(
  privateKey: Buffer,
  messageHash: Buffer
): Promise<Buffer> {
  if (privateKey.length !== 32) {
    throw new Error(`Invalid private key length: expected 32, got ${privateKey.length}`);
  }
  if (messageHash.length !== 32) {
    throw new Error(`Invalid message hash length: expected 32, got ${messageHash.length}`);
  }

  // Sign with secp256k1 (returns Signature object)
  const signature = await secp256k1.signAsync(messageHash, privateKey);

  // Convert to compact format (r || s, 64 bytes)
  // toCompactRawBytes() returns Uint8Array of [r (32 bytes) || s (32 bytes)]
  const compactSig = signature.toCompactRawBytes();

  return Buffer.from(compactSig);
}

/**
 * Verify a secp256k1 signature
 *
 * @param publicKey 33-byte compressed public key
 * @param messageHash 32-byte message hash
 * @param signature 64-byte signature (r || s)
 * @returns true if signature is valid
 */
export async function verifySignature(
  publicKey: Buffer,
  messageHash: Buffer,
  signature: Buffer
): Promise<boolean> {
  if (publicKey.length !== 33) {
    throw new Error(`Invalid public key length: expected 33, got ${publicKey.length}`);
  }
  if (messageHash.length !== 32) {
    throw new Error(`Invalid message hash length: expected 32, got ${messageHash.length}`);
  }
  if (signature.length !== 64) {
    throw new Error(`Invalid signature length: expected 64, got ${signature.length}`);
  }

  try {
    // Parse compact signature (r || s)
    const sig = secp256k1.Signature.fromCompact(signature);

    // Verify signature
    return secp256k1.verify(sig, messageHash, publicKey);
  } catch (error) {
    return false;
  }
}

/**
 * Derive compressed public key from private key
 *
 * @param privateKey 32-byte private key
 * @returns 33-byte compressed public key
 */
export function getPublicKey(privateKey: Buffer): Buffer {
  if (privateKey.length !== 32) {
    throw new Error(`Invalid private key length: expected 32, got ${privateKey.length}`);
  }

  const publicKey = secp256k1.getPublicKey(privateKey, true); // true = compressed
  return Buffer.from(publicKey);
}
