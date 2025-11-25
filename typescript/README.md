# TypeScript/Node.js Bindings for t2z Library

TypeScript/Node.js bindings for the t2z (Transparent to Zcash) library using napi-rs.

**t2z** enables transparent Zcash wallets (including hardware wallets) to send shielded Orchard outputs using PCZT (Partially Constructed Zcash Transactions) as defined in [ZIP 374](https://zips.z.cash/zip-0374).

**Repository:** https://github.com/gstohl/t2z (private)

## Status

✅ **Complete** - All 8 API functions implemented

## Features

- **Complete API Coverage**: All 8 required functions implemented
  - `proposeTransaction` / `proposeTransactionWithChange`
  - `proveTransaction`
  - `getSighash`
  - `appendSignature`
  - `finalizeAndExtract`
  - `parse` / `serialize`
  - `combine`
  - `verifyBeforeSigning`

- **Native Performance**: Built with napi-rs for optimal performance
- **Type Safety**: Full TypeScript type definitions
- **Memory Safe**: Automatic garbage collection with NAPI
- **Hardware Wallet Support**: External signing via `getSighash` + `appendSignature`

## Installation

### Prerequisites

This is a **private repository**. Configure Git authentication:

```bash
# Option 1: SSH (recommended)
ssh -T git@github.com

# Option 2: HTTPS with token
git config --global credential.helper store
```

### Building

```bash
# Clone the repository
git clone git@github.com:gstohl/t2z.git
cd t2z/typescript

# Install dependencies
npm install

# Build the native module
npm run build

# Run tests
npm test
```

### Using in Your Project

Add to your `package.json`:

```json
{
  "dependencies": {
    "t2z": "^0.1.0"
  }
}
```

Or install directly:

```bash
npm install t2z
```

## Quick Start

See the [examples/](examples/) directory for complete working examples:

- **[basic/](examples/basic/)** - Simple transparent→transparent transaction
- **[hardware-wallet/](examples/hardware-wallet/)** - External signing workflow with hardware wallets

### Basic Usage

```typescript
import {
  Payment,
  TransparentInput,
  TransactionRequest,
  proposeTransaction,
  proveTransaction,
  getSighash,
  appendSignature,
  finalizeAndExtract,
  signMessage,
} from 't2z';

// 1. Create payment request
const payments: Payment[] = [
  {
    address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
    amount: 100_000n, // 0.001 ZEC in zatoshis
  },
];

const request = new TransactionRequest(payments);

// 2. Add transparent UTXOs to spend
const inputs: TransparentInput[] = [
  {
    pubkey: pubkeyBytes,     // Your 33-byte compressed pubkey
    txid: txidBytes,         // 32-byte transaction ID
    vout: 0,                 // Output index
    amount: 100_000_000n,    // 1 ZEC in zatoshis
    scriptPubKey: scriptPubKey, // P2PKH script from address
  },
];

// 3. Create PCZT (Partially Constructed Zcash Transaction)
const pczt = proposeTransaction(inputs, request);

// 4. Add Orchard proofs (for shielded outputs)
const proved = proveTransaction(pczt);

// 5. Get signature hash
const sighash = getSighash(proved, 0);

// 6. Sign with secp256k1
const signature = await signMessage(privateKey, sighash);

// 7. Append signature
const signed = appendSignature(proved, 0, signature);

// 8. Finalize and extract transaction bytes
const txBytes = finalizeAndExtract(signed);

// 9. Broadcast txBytes to Zcash network
// ... submit to zcashd or lightwalletd
```

## API Reference

### Types

#### `Payment`

```typescript
interface Payment {
  address: string;      // Zcash address (unified, transparent, or shielded)
  amount: bigint;       // Amount in zatoshis
  memo?: string;        // Optional memo for shielded outputs (max 512 bytes)
  label?: string;       // Optional label
  message?: string;     // Optional message
}
```

#### `TransparentInput`

```typescript
interface TransparentInput {
  pubkey: Buffer;       // Compressed public key (33 bytes)
  txid: Buffer;         // Transaction ID (32 bytes)
  vout: number;         // Output index
  amount: bigint;       // Amount in zatoshis
  scriptPubKey: Buffer; // P2PKH script pubkey
}
```

### Classes

#### `TransactionRequest`

```typescript
class TransactionRequest {
  constructor(payments: Payment[]);
  free(): void;
}
```

#### `PCZT`

```typescript
class PCZT {
  free(): void;
}
```

### Functions

#### `proposeTransaction()`

Create a PCZT from transparent inputs and transaction request.

```typescript
function proposeTransaction(
  inputs: TransparentInput[],
  request: TransactionRequest
): PCZT;
```

#### `proposeTransactionWithChange()`

Create a PCZT with explicit change handling.

```typescript
function proposeTransactionWithChange(
  inputs: TransparentInput[],
  request: TransactionRequest,
  changeAddress: string
): PCZT;
```

#### `proveTransaction()`

Add Orchard proofs to the PCZT. **Consumes the input PCZT**.

```typescript
function proveTransaction(pczt: PCZT): PCZT;
```

#### `verifyBeforeSigning()`

Verify the PCZT before signing. Does NOT consume the PCZT.

```typescript
function verifyBeforeSigning(pczt: PCZT): void;
```

#### `getSighash()`

Get signature hash for a transparent input. Does NOT consume the PCZT.

```typescript
function getSighash(pczt: PCZT, index: number): Buffer;
```

#### `appendSignature()`

Append an external signature to the PCZT. **Consumes the input PCZT**.

```typescript
function appendSignature(
  pczt: PCZT,
  index: number,
  signature: Buffer // 64-byte secp256k1 signature (r || s)
): PCZT;
```

#### `combine()`

Combine multiple PCZTs into one. **Consumes all input PCZTs**.

```typescript
function combine(pczts: PCZT[]): PCZT;
```

#### `finalizeAndExtract()`

Finalize the PCZT and extract transaction bytes. **Consumes the input PCZT**.

```typescript
function finalizeAndExtract(pczt: PCZT): Buffer;
```

#### `serialize()`

Serialize PCZT to bytes. Does NOT consume the PCZT.

```typescript
function serialize(pczt: PCZT): Buffer;
```

#### `parse()`

Parse PCZT from bytes.

```typescript
function parse(bytes: Buffer): PCZT;
```

## Memory Management

**Important:** Most PCZT functions **consume** their input:

- ✅ `proveTransaction(pczt)` - consumes `pczt`
- ✅ `appendSignature(pczt, ...)` - consumes `pczt`
- ✅ `finalizeAndExtract(pczt)` - consumes `pczt`

**Non-consuming functions:**

- ✅ `getSighash(pczt, ...)` - does NOT consume
- ✅ `serialize(pczt)` - does NOT consume
- ✅ `verifyBeforeSigning(pczt)` - does NOT consume

The NAPI runtime handles garbage collection, but you can call `free()` explicitly when done with objects that won't be consumed.

## Examples

Complete working examples are available in the [examples/](examples/) directory:

- **[basic/](examples/basic/)** - Simple end-to-end transparent transaction workflow
- **[hardware-wallet/](examples/hardware-wallet/)** - Hardware wallet integration with PCZT serialization

Run an example:

```bash
cd examples/basic
npm install
npm start
```

## Testing

```bash
# Run all tests
npm test

# Run tests in watch mode
npm run test:watch

# Run tests with coverage
npm run test:coverage
```

## Building

```bash
# Build native module (release)
npm run build

# Build native module (debug)
npm run build:debug

# Generate artifacts for publishing
npm run artifacts
```

## Platform Support

Prebuilt binaries are available for:

- macOS (x86_64, ARM64)
- Linux (x86_64, ARM64)
- Windows (x86_64) - coming soon

## Dependencies

Built with:

- **napi-rs**: Native Node.js addon framework
- **TypeScript**: Type definitions and compilation
- **vitest**: Fast unit testing
- **@noble/secp256k1**: Pure TypeScript secp256k1 implementation for signing

## Error Handling

All functions throw standard JavaScript errors on failure:

```typescript
try {
  const pczt = proposeTransaction(inputs, request);
} catch (error) {
  console.error('Failed to propose transaction:', error.message);
}
```

Error codes:

- `ProposalError`: Invalid transaction request
- `ProverError`: Failed to generate proofs
- `SignerError`: Signature verification failed
- `InvalidIndex`: Input index out of bounds
- `ParseError`: Invalid PCZT format

## Performance

- **Native Speed**: C-level performance via Rust FFI
- **Zero-Copy**: Efficient buffer handling
- **Lazy Loading**: Proving keys loaded on first use
- **Minimal Overhead**: NAPI bindings add negligible latency

## Security

- **Memory Safety**: Rust's ownership system prevents memory bugs
- **Type Safety**: TypeScript catches errors at compile time
- **Input Validation**: All inputs validated at FFI boundary
- **No Unsafe Code**: Minimal unsafe blocks, all audited

## Troubleshooting

### Build Errors

If you encounter build errors:

```bash
# Clean and rebuild
rm -rf node_modules dist
npm install
npm run build
```

### Runtime Errors

**"Cannot find module"**: Ensure native module is built:

```bash
npm run build
```

**"Invalid signature"**: Ensure you're using secp256k1, not P-256 or other curves. Use the provided `signMessage` utility from `t2z`.

**"Invalid script"**: Ensure P2PKH script includes OP_PUSHDATA1 (0x19) prefix (26 bytes total).

**"Cannot find module @noble/secp256k1"**: Install the secp256k1 dependency:
```bash
npm install @noble/secp256k1
```

## Contributing

See [CONTRIBUTING.md](../CONTRIBUTING.md) for guidelines.

## License

MIT License - See [LICENSE](../LICENSE) for details.

## Links

- [GitHub Repository](https://github.com/gstohl/t2z)
- [ZIP 374: PCZT Specification](https://zips.z.cash/zip-0374)
- [Zcash Documentation](https://zcash.readthedocs.io/)
- [napi-rs Documentation](https://napi.rs/)
