/**
 * @module t2z
 *
 * TypeScript/Node.js bindings for the t2z (Transparent to Zcash) library.
 *
 * This library enables transparent Zcash wallets (including hardware wallets)
 * to send shielded Orchard outputs using PCZT (Partially Constructed Zcash Transactions)
 * as defined in ZIP 374.
 *
 * @example
 * ```typescript
 * import {
 *   Payment,
 *   TransactionRequest,
 *   proposeTransaction,
 *   proveTransaction,
 *   getSighash,
 *   appendSignature,
 *   finalizeAndExtract,
 *   signMessage
 * } from 't2z';
 *
 * // Create payment request
 * const payments: Payment[] = [{
 *   address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
 *   amount: '100000', // 0.001 ZEC in zatoshis
 * }];
 *
 * const request = new TransactionRequest(payments);
 *
 * // Add transparent inputs
 * const inputs = [{
 *   pubkey: Buffer.from('031b84c5567b126440995d3ed5aaba0565d71e1834604819ff9c17f5e9d5dd078f', 'hex'),
 *   txid: Buffer.alloc(32, 0),
 *   vout: 0,
 *   amount: '100000000', // 1 ZEC in zatoshis
 *   scriptPubKey: Buffer.from('76a91479b000887626b294a914501a4cd226b58b23598388ac', 'hex'),
 * }];
 *
 * // Create and process PCZT
 * const pczt = proposeTransaction(inputs, request);
 * const proved = proveTransaction(pczt);
 * const sighash = getSighash(proved, 0);
 *
 * // Sign with your key (secp256k1)
 * const signature = signMessage(privateKey, sighash);
 *
 * // Finalize
 * const signed = appendSignature(proved, 0, signature);
 * const txBytes = finalizeAndExtract(signed);
 *
 * // Broadcast txBytes to Zcash network
 * ```
 */

// Export all FFI bindings from lib.ts
export {
  ResultCode,
  T2zError,
  Payment,
  TransparentInput,
  TransparentOutput,
  TransactionRequest,
  PCZT,
  proposeTransaction,
  proposeTransactionWithChange,
  proveTransaction,
  verifyBeforeSigning,
  getSighash,
  appendSignature,
  combine,
  finalizeAndExtract,
  serializePczt,
  parsePczt,
  calculateFee,
} from './lib';

// Re-export signing utilities
export { signMessage, verifySignature, getPublicKey } from './utils/signing';
