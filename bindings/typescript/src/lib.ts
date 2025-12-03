/**
 * t2z TypeScript bindings using koffi FFI
 *
 * This module provides bindings to the t2z Rust library using koffi,
 * a pure JavaScript FFI library. It wraps the same libt2z shared library
 * used by the Go bindings.
 */

import koffi from 'koffi';
import path from 'path';
import os from 'os';

// Platform directory mapping
const PLATFORM_DIRS: Record<string, string> = {
  'darwin-arm64': 'darwin-arm64',
  'darwin-x64': 'darwin-x64',
  'linux-x64': 'linux-x64',
  'linux-arm64': 'linux-arm64',
  'win32-x64': 'windows-x64',
};

// Determine the correct library path based on platform
function getLibraryPath(): string {
  const platform = os.platform();
  const arch = os.arch();
  const key = `${platform}-${arch}`;

  const platformDir = PLATFORM_DIRS[key];
  if (!platformDir) {
    throw new Error(`Unsupported platform: ${key}`);
  }

  // Library is in lib/<platform>/ relative to bindings/typescript/dist folder
  const basePath = path.join(__dirname, '..', 'lib', platformDir);

  if (platform === 'darwin') {
    return path.join(basePath, 'libt2z.dylib');
  } else if (platform === 'linux') {
    return path.join(basePath, 'libt2z.so');
  } else if (platform === 'win32') {
    return path.join(basePath, 't2z.dll');
  }

  throw new Error(`Unsupported platform: ${platform}-${arch}`);
}

// Load the native library
const lib = koffi.load(getLibraryPath());

// Result codes from FFI
export enum ResultCode {
  Success = 0,
  ErrorNullPointer = 1,
  ErrorInvalidUtf8 = 2,
  ErrorBufferTooSmall = 3,
  ErrorProposal = 10,
  ErrorProver = 11,
  ErrorVerification = 12,
  ErrorSighash = 13,
  ErrorSignature = 14,
  ErrorCombine = 15,
  ErrorFinalization = 16,
  ErrorParse = 17,
  ErrorNotImplemented = 99,
}

/**
 * Error class for t2z operations.
 *
 * Includes the ResultCode for programmatic error handling.
 *
 * @example
 * ```typescript
 * try {
 *   const proved = proveTransaction(pczt);
 * } catch (e) {
 *   if (e instanceof T2zError && e.code === ResultCode.ErrorProver) {
 *     console.error('Proof generation failed:', e.message);
 *   }
 * }
 * ```
 */
export class T2zError extends Error {
  /** The result code from the native library */
  public readonly code: ResultCode;

  constructor(message: string, code: ResultCode) {
    super(message);
    this.name = 'T2zError';
    this.code = code;
    // Maintains proper stack trace in V8 environments
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, T2zError);
    }
  }
}

// Define opaque pointer type for handles
const VoidPtr = koffi.pointer('void');

// Define C struct types
const CPayment = koffi.struct('CPayment', {
  address: 'const char*',
  amount: 'uint64_t',
  memo: 'const char*',
  label: 'const char*',
  message: 'const char*',
});

const CTransparentOutput = koffi.struct('CTransparentOutput', {
  script_pub_key: 'const uint8_t*',
  script_pub_key_len: 'size_t',
  value: 'uint64_t',
});

// Define FFI functions with proper _out parameters
const pczt_get_last_error = lib.func('uint32_t pczt_get_last_error(_Out_ char* buffer, size_t buffer_len)');

const pczt_transaction_request_new = lib.func(
  'uint32_t pczt_transaction_request_new(const CPayment* payments, size_t num_payments, _Out_ void** request_out)'
);

const pczt_transaction_request_free = lib.func('void pczt_transaction_request_free(void* request)');

const pczt_transaction_request_set_target_height = lib.func(
  'uint32_t pczt_transaction_request_set_target_height(void* request, uint32_t target_height)'
);

const pczt_transaction_request_set_use_mainnet = lib.func(
  'uint32_t pczt_transaction_request_set_use_mainnet(void* request, bool use_mainnet)'
);

const pczt_propose_transaction = lib.func(
  'uint32_t pczt_propose_transaction(const uint8_t* inputs_bytes, size_t inputs_bytes_len, const void* request, const char* change_address, _Out_ void** pczt_out)'
);

const pczt_prove_transaction = lib.func('uint32_t pczt_prove_transaction(void* pczt, _Out_ void** pczt_out)');

const pczt_verify_before_signing = lib.func(
  'uint32_t pczt_verify_before_signing(const void* pczt, const void* request, const CTransparentOutput* expected_change, size_t expected_change_len)'
);

const pczt_get_sighash = lib.func(
  'uint32_t pczt_get_sighash(const void* pczt, size_t input_index, _Out_ uint8_t* sighash_out)'
);

const pczt_append_signature = lib.func(
  'uint32_t pczt_append_signature(void* pczt, size_t input_index, const uint8_t* signature, _Out_ void** pczt_out)'
);

const pczt_combine = lib.func('uint32_t pczt_combine(void** pczts, size_t num_pczts, _Out_ void** pczt_out)');

const pczt_finalize_and_extract = lib.func(
  'uint32_t pczt_finalize_and_extract(void* pczt, _Out_ void** tx_bytes_out, _Out_ size_t* tx_bytes_len_out)'
);

const pczt_parse = lib.func(
  'uint32_t pczt_parse(const uint8_t* pczt_bytes, size_t pczt_bytes_len, _Out_ void** pczt_out)'
);

const pczt_serialize = lib.func(
  'uint32_t pczt_serialize(const void* pczt, _Out_ void** bytes_out, _Out_ size_t* bytes_len_out)'
);

const pczt_free = lib.func('void pczt_free(void* pczt)');

const pczt_free_bytes = lib.func('void pczt_free_bytes(void* bytes, size_t len)');

const pczt_calculate_fee = lib.func(
  'uint64_t pczt_calculate_fee(size_t num_transparent_inputs, size_t num_transparent_outputs, size_t num_orchard_outputs)'
);

// Helper: Get last error message
function getLastError(): string {
  const buffer = Buffer.alloc(512);
  pczt_get_last_error(buffer, buffer.length);
  const nullIndex = buffer.indexOf(0);
  return buffer.slice(0, nullIndex > 0 ? nullIndex : buffer.length).toString('utf8');
}

// Helper: Check result code and throw on error
function checkResult(code: number, operation: string): void {
  if (code !== ResultCode.Success) {
    const errorMsg = getLastError();
    throw new T2zError(
      `${operation} failed: ${errorMsg || `error code ${code}`}`,
      code as ResultCode
    );
  }
}

/**
 * Payment request with address, amount, and optional metadata
 */
export interface Payment {
  address: string;
  amount: string; // BigInt as string for FFI compatibility
  memo?: string;
  label?: string;
  message?: string;
}

/**
 * Transparent UTXO input to spend
 */
export interface TransparentInput {
  pubkey: Buffer; // 33 bytes compressed
  txid: Buffer; // 32 bytes
  vout: number;
  amount: string; // BigInt as string
  scriptPubKey: Buffer;
}

/**
 * Transparent transaction output (for change verification)
 */
export interface TransparentOutput {
  scriptPubKey: Buffer;
  value: string; // BigInt as string
}

// FinalizationRegistry for automatic cleanup when objects are garbage collected
const requestRegistry = new FinalizationRegistry((handle: any) => {
  if (handle) {
    pczt_transaction_request_free(handle);
  }
});

const pcztRegistry = new FinalizationRegistry((handle: any) => {
  if (handle) {
    pczt_free(handle);
  }
});

/**
 * Transaction request containing multiple payments
 */
export class TransactionRequest {
  private handle: any;
  private freed = false;

  constructor(payments: Payment[]) {
    // Build C payment array with proper structure
    const cPayments: any[] = [];
    for (const p of payments) {
      cPayments.push({
        address: p.address,
        amount: BigInt(p.amount),
        memo: p.memo ?? null,
        label: p.label ?? null,
        message: p.message ?? null,
      });
    }

    const handleOut: any[] = [null];
    const code = pczt_transaction_request_new(
      cPayments.length > 0 ? cPayments : null,
      cPayments.length,
      handleOut
    );
    checkResult(code, 'Create transaction request');
    this.handle = handleOut[0];

    // Register for automatic cleanup on GC
    requestRegistry.register(this, this.handle, this);
  }

  /**
   * Set the target block height for consensus branch ID selection
   */
  setTargetHeight(height: number): void {
    if (this.freed) throw new Error('TransactionRequest already freed');
    const code = pczt_transaction_request_set_target_height(this.handle, height);
    checkResult(code, 'Set target height');
  }

  /**
   * Set whether to use mainnet parameters for consensus branch ID
   *
   * By default, the library uses mainnet parameters. Set this to false for testnet.
   * Regtest networks (like Zebra's regtest) typically use mainnet-like branch IDs,
   * so keep the default (true) for regtest.
   */
  setUseMainnet(useMainnet: boolean): void {
    if (this.freed) throw new Error('TransactionRequest already freed');
    const code = pczt_transaction_request_set_use_mainnet(this.handle, useMainnet);
    checkResult(code, 'Set use mainnet');
  }

  /**
   * Explicitly free native resources (optional - GC will handle automatically)
   */
  free(): void {
    if (!this.freed && this.handle) {
      requestRegistry.unregister(this);
      pczt_transaction_request_free(this.handle);
      this.handle = null;
      this.freed = true;
    }
  }

  /** @internal */
  getHandle(): any {
    if (this.freed) throw new Error('TransactionRequest already freed');
    return this.handle;
  }
}

/**
 * Partially Constructed Zcash Transaction (PCZT)
 */
export class PCZT {
  private handle: any;
  private freed = false;

  /** @internal */
  constructor(handle: any) {
    this.handle = handle;
    // Register for automatic cleanup on GC
    pcztRegistry.register(this, this.handle, this);
  }

  /**
   * Explicitly free native resources (optional - GC will handle automatically)
   */
  free(): void {
    if (!this.freed && this.handle) {
      pcztRegistry.unregister(this);
      pczt_free(this.handle);
      this.handle = null;
      this.freed = true;
    }
  }

  /** @internal */
  getHandle(): any {
    if (this.freed) throw new Error('PCZT already freed');
    return this.handle;
  }

  /** @internal */
  takeHandle(): any {
    if (this.freed) throw new Error('PCZT already freed');
    pcztRegistry.unregister(this); // Ownership transferred, don't auto-free
    const h = this.handle;
    this.handle = null;
    this.freed = true;
    return h;
  }
}

/**
 * Serialize transparent inputs to binary format expected by Rust
 */
function serializeTransparentInputs(inputs: TransparentInput[]): Buffer {
  const chunks: Buffer[] = [];

  // Number of inputs (u16 LE)
  const numInputs = Buffer.alloc(2);
  numInputs.writeUInt16LE(inputs.length, 0);
  chunks.push(numInputs);

  for (const input of inputs) {
    // Validate lengths
    if (input.pubkey.length !== 33) {
      throw new Error(`Invalid pubkey length: expected 33, got ${input.pubkey.length}`);
    }
    if (input.txid.length !== 32) {
      throw new Error(`Invalid txid length: expected 32, got ${input.txid.length}`);
    }

    // Pubkey (33 bytes)
    chunks.push(input.pubkey);

    // TXID (32 bytes)
    chunks.push(input.txid);

    // Vout (u32 LE)
    const vout = Buffer.alloc(4);
    vout.writeUInt32LE(input.vout, 0);
    chunks.push(vout);

    // Amount (u64 LE)
    const amount = Buffer.alloc(8);
    amount.writeBigUInt64LE(BigInt(input.amount), 0);
    chunks.push(amount);

    // Script length (u16 LE)
    const scriptLen = Buffer.alloc(2);
    scriptLen.writeUInt16LE(input.scriptPubKey.length, 0);
    chunks.push(scriptLen);

    // Script pubkey
    chunks.push(input.scriptPubKey);
  }

  return Buffer.concat(chunks);
}

/**
 * Create a PCZT from transparent inputs and transaction request
 */
export function proposeTransaction(inputs: TransparentInput[], request: TransactionRequest): PCZT {
  const inputBytes = serializeTransparentInputs(inputs);
  const handleOut: any[] = [null];

  const code = pczt_propose_transaction(
    inputBytes,
    inputBytes.length,
    request.getHandle(),
    null, // No change address
    handleOut
  );
  checkResult(code, 'Propose transaction');

  return new PCZT(handleOut[0]);
}

/**
 * Create a PCZT with explicit change handling
 */
export function proposeTransactionWithChange(
  inputs: TransparentInput[],
  request: TransactionRequest,
  changeAddress: string
): PCZT {
  const inputBytes = serializeTransparentInputs(inputs);
  const handleOut: any[] = [null];

  const code = pczt_propose_transaction(
    inputBytes,
    inputBytes.length,
    request.getHandle(),
    changeAddress,
    handleOut
  );
  checkResult(code, 'Propose transaction with change');

  return new PCZT(handleOut[0]);
}

/**
 * Add Orchard proofs to the PCZT.
 *
 * **IMPORTANT:** This function ALWAYS consumes the input PCZT, even on error.
 * On error, the input PCZT is invalidated and cannot be reused.
 * If you need to retry on failure, call `serialize()` before this function
 * to create a backup that can be restored with `parse()`.
 */
export function proveTransaction(pczt: PCZT): PCZT {
  const handleOut: any[] = [null];
  const code = pczt_prove_transaction(pczt.takeHandle(), handleOut);
  checkResult(code, 'Prove transaction');
  return new PCZT(handleOut[0]);
}

/**
 * Verify the PCZT before signing
 */
export function verifyBeforeSigning(
  pczt: PCZT,
  request: TransactionRequest,
  expectedChange: TransparentOutput[]
): void {
  // Build C transparent output array
  const cOutputs: any[] = [];
  for (const o of expectedChange) {
    cOutputs.push({
      script_pub_key: o.scriptPubKey,
      script_pub_key_len: o.scriptPubKey.length,
      value: BigInt(o.value),
    });
  }

  const code = pczt_verify_before_signing(
    pczt.getHandle(),
    request.getHandle(),
    cOutputs.length > 0 ? cOutputs : null,
    cOutputs.length
  );
  checkResult(code, 'Verify before signing');
}

/**
 * Get signature hash for a transparent input
 */
export function getSighash(pczt: PCZT, index: number): Buffer {
  const sighash = Buffer.alloc(32);
  const code = pczt_get_sighash(pczt.getHandle(), index, sighash);
  checkResult(code, 'Get sighash');
  return sighash;
}

/**
 * Append an external signature to the PCZT.
 *
 * **IMPORTANT:** This function ALWAYS consumes the input PCZT, even on error.
 * On error, the input PCZT is invalidated and cannot be reused.
 * If you need to retry on failure, call `serialize()` before this function
 * to create a backup that can be restored with `parse()`.
 */
export function appendSignature(pczt: PCZT, index: number, signature: Buffer): PCZT {
  if (signature.length !== 64) {
    throw new Error(`Invalid signature length: expected 64, got ${signature.length}`);
  }

  const handleOut: any[] = [null];
  const code = pczt_append_signature(pczt.takeHandle(), index, signature, handleOut);
  checkResult(code, 'Append signature');
  return new PCZT(handleOut[0]);
}

/**
 * Combine multiple PCZTs into one.
 *
 * **IMPORTANT:** This function ALWAYS consumes ALL input PCZTs, even on error.
 * On error, all input PCZTs are invalidated and cannot be reused.
 * If you need to retry on failure, call `serialize()` on each PCZT before
 * this function to create backups that can be restored with `parse()`.
 */
export function combine(pczts: PCZT[]): PCZT {
  if (pczts.length === 0) {
    throw new Error('At least one PCZT is required');
  }

  // Extract handles and transfer ownership
  const handles = pczts.map((p) => p.takeHandle());
  const handleOut: any[] = [null];

  const code = pczt_combine(handles, handles.length, handleOut);
  checkResult(code, 'Combine PCZTs');

  return new PCZT(handleOut[0]);
}

/**
 * Finalize the PCZT and extract transaction bytes.
 *
 * **IMPORTANT:** This function ALWAYS consumes the input PCZT, even on error.
 * On error, the input PCZT is invalidated and cannot be reused.
 * If you need to retry on failure, call `serialize()` before this function
 * to create a backup that can be restored with `parse()`.
 */
export function finalizeAndExtract(pczt: PCZT): Buffer {
  const bytesOut: any[] = [null];
  const lenOut: number[] = [0];

  const code = pczt_finalize_and_extract(pczt.takeHandle(), bytesOut, lenOut);
  checkResult(code, 'Finalize and extract');

  // Copy bytes and free native memory
  const len = lenOut[0];
  const ptr = bytesOut[0];
  const result = Buffer.from(koffi.decode(ptr, 'uint8_t', len));
  pczt_free_bytes(ptr, len);

  return result;
}

/**
 * Serialize PCZT to bytes
 */
export function serialize(pczt: PCZT): Buffer {
  const bytesOut: any[] = [null];
  const lenOut: number[] = [0];

  const code = pczt_serialize(pczt.getHandle(), bytesOut, lenOut);
  checkResult(code, 'Serialize PCZT');

  // Copy bytes and free native memory
  const len = lenOut[0];
  const ptr = bytesOut[0];
  const result = Buffer.from(koffi.decode(ptr, 'uint8_t', len));
  pczt_free_bytes(ptr, len);

  return result;
}

/**
 * Parse PCZT from bytes
 */
export function parse(bytes: Buffer): PCZT {
  const handleOut: any[] = [null];
  const code = pczt_parse(bytes, bytes.length, handleOut);
  checkResult(code, 'Parse PCZT');
  return new PCZT(handleOut[0]);
}

/**
 * Calculate the ZIP-317 transaction fee.
 *
 * This is a pure function that computes the fee based on transaction shape.
 * Use this to calculate fees before building a transaction, e.g., for "send max"
 * functionality where you need to know the fee to calculate the maximum sendable amount.
 *
 * @param numTransparentInputs - Number of transparent UTXOs to spend
 * @param numTransparentOutputs - Number of transparent outputs (including change if any)
 * @param numOrchardOutputs - Number of Orchard (shielded) outputs
 * @returns The fee in zatoshis as a bigint
 *
 * @example
 * ```typescript
 * // Transparent-only: 1 input, 2 outputs (1 payment + 1 change)
 * const fee = calculateFee(1, 2, 0); // Returns 10000n
 *
 * // Shielded: 1 input, 1 change, 1 orchard output
 * const fee = calculateFee(1, 1, 1); // Returns 15000n
 *
 * // Calculate max sendable amount
 * const totalInput = 100000000n; // 1 ZEC in zatoshis
 * const fee = calculateFee(1, 2, 0);
 * const maxSend = totalInput - fee; // 99990000n zatoshis
 * ```
 *
 * @see {@link https://zips.z.cash/zip-0317 | ZIP-317}
 */
export function calculateFee(
  numTransparentInputs: number,
  numTransparentOutputs: number,
  numOrchardOutputs: number
): bigint {
  return BigInt(pczt_calculate_fee(numTransparentInputs, numTransparentOutputs, numOrchardOutputs));
}
