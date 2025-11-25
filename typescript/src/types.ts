/**
 * Result codes from the native library
 */
export enum ResultCode {
  Success = 0,
  ProposalError = 1,
  ProverError = 2,
  SignerError = 3,
  SigningError = 4,
  ExtractorError = 5,
  IoError = 6,
  ParseError = 7,
  VerificationError = 8,
  InvalidIndex = 9,
  InvalidSignature = 10,
  CombineError = 11,
}

/**
 * Payment request with address, amount, and optional metadata
 */
export interface Payment {
  /** Zcash address (unified, transparent, or shielded) */
  address: string;

  /** Amount in zatoshis (1 ZEC = 100,000,000 zatoshis) */
  amount: bigint;

  /** Optional memo for shielded outputs (max 512 bytes) */
  memo?: string;

  /** Optional label for the payment */
  label?: string;

  /** Optional message */
  message?: string;
}

/**
 * Transparent UTXO input to spend
 */
export interface TransparentInput {
  /** Compressed public key (33 bytes) */
  pubkey: Buffer;

  /** Transaction ID (32 bytes) */
  txid: Buffer;

  /** Output index */
  vout: number;

  /** Amount in zatoshis */
  amount: bigint;

  /** P2PKH script pubkey (raw bytes, no CompactSize prefix) */
  scriptPubKey: Buffer;
}

/**
 * Transparent transaction output (for change verification)
 */
export interface TransparentOutput {
  /** P2PKH script pubkey (raw bytes, no CompactSize prefix) */
  scriptPubKey: Buffer;

  /** Amount in zatoshis */
  value: bigint;
}

/**
 * Transaction request containing multiple payments
 */
export class TransactionRequest {
  private handle: unknown;

  /**
   * Create a new transaction request from payment array
   * @param payments Array of payment requests
   */
  constructor(payments: Payment[]);

  /**
   * Free the native resources
   * Call this when done with the transaction request
   */
  free(): void;
}

/**
 * Partially Constructed Zcash Transaction (PCZT)
 * Represents a transaction being constructed through multiple stages
 */
export class PCZT {
  private handle: unknown;

  /**
   * Do not construct directly - use ProposeTransaction instead
   */
  private constructor();

  /**
   * Free the native resources
   * Note: Most operations consume the PCZT, so free() may not be needed
   */
  free(): void;
}

/**
 * Create a PCZT from transparent inputs and transaction request
 * @param inputs Array of transparent UTXOs to spend
 * @param request Transaction request with payment destinations
 * @returns New PCZT ready for proving
 */
export function proposeTransaction(
  inputs: TransparentInput[],
  request: TransactionRequest
): PCZT;

/**
 * Create a PCZT with explicit change handling
 * @param inputs Array of transparent UTXOs to spend
 * @param request Transaction request with payment destinations
 * @param changeAddress Address to receive change (transparent or shielded)
 * @returns New PCZT ready for proving
 */
export function proposeTransactionWithChange(
  inputs: TransparentInput[],
  request: TransactionRequest,
  changeAddress: string
): PCZT;

/**
 * Add Orchard proofs to the PCZT
 * Consumes the input PCZT and returns a new proved PCZT
 * @param pczt PCZT to add proofs to (will be consumed)
 * @returns New PCZT with proofs added
 */
export function proveTransaction(pczt: PCZT): PCZT;

/**
 * Verify the PCZT before signing
 * Does NOT consume the PCZT
 *
 * This verifies:
 * - Payment outputs match the transaction request
 * - Change outputs match the expected change
 * - Fees are reasonable
 *
 * @param pczt PCZT to verify
 * @param request Original transaction request
 * @param expectedChange Expected change outputs (empty array if no change expected)
 * @throws Error if verification fails
 */
export function verifyBeforeSigning(
  pczt: PCZT,
  request: TransactionRequest,
  expectedChange: TransparentOutput[]
): void;

/**
 * Get signature hash for a transparent input
 * Does NOT consume the PCZT (can be called multiple times)
 * @param pczt PCZT to get sighash from
 * @param index Index of transparent input to sign
 * @returns 32-byte signature hash
 */
export function getSighash(pczt: PCZT, index: number): Buffer;

/**
 * Append an external signature to the PCZT
 * Consumes the input PCZT and returns a new signed PCZT
 * Use this for hardware wallet signatures
 * @param pczt PCZT to append signature to (will be consumed)
 * @param index Index of transparent input being signed
 * @param signature 64-byte secp256k1 signature (r || s)
 * @returns New PCZT with signature appended
 */
export function appendSignature(
  pczt: PCZT,
  index: number,
  signature: Buffer
): PCZT;

/**
 * Combine multiple PCZTs into one
 * Useful for parallel signing workflows
 * Consumes all input PCZTs
 * @param pczts Array of PCZTs to combine (will be consumed)
 * @returns New combined PCZT
 */
export function combine(pczts: PCZT[]): PCZT;

/**
 * Finalize the PCZT and extract transaction bytes
 * Consumes the input PCZT
 * @param pczt PCZT to finalize (will be consumed)
 * @returns Raw transaction bytes ready for broadcast
 */
export function finalizeAndExtract(pczt: PCZT): Buffer;

/**
 * Serialize PCZT to bytes
 * Does NOT consume the PCZT
 * @param pczt PCZT to serialize
 * @returns Serialized PCZT bytes
 */
export function serialize(pczt: PCZT): Buffer;

/**
 * Parse PCZT from bytes
 * @param bytes Serialized PCZT bytes
 * @returns Parsed PCZT object
 */
export function parse(bytes: Buffer): PCZT;
