# t2z TypeScript Bindings

TypeScript/Node.js bindings using [koffi](https://koffi.dev/) FFI to wrap the Rust core library.

## Installation

```bash
# Build Rust library first
cd ../rust && cargo build --release

# Install and build TypeScript
cd ../typescript
npm install
npm run build
npm test
```

## Usage

```typescript
import {
  TransactionRequest,
  proposeTransaction,
  proveTransaction,
  getSighash,
  appendSignature,
  finalizeAndExtract,
  signMessage,
} from 't2z';

// 1. Create payment request
const request = new TransactionRequest([{
  address: 'utest1...',  // unified or transparent address
  amount: '100000',      // 0.001 ZEC (string for BigInt)
}]);

// 2. Create PCZT from transparent UTXOs
const pczt = proposeTransaction([{
  pubkey: pubkeyBuffer,       // 33 bytes compressed
  txid: txidBuffer,           // 32 bytes
  vout: 0,
  amount: '100000000',        // 1 ZEC (string for BigInt)
  scriptPubKey: scriptBuffer,
}], request);

// 3. Add Orchard proofs
const proved = proveTransaction(pczt);

// 4. Sign transparent inputs
const sighash = getSighash(proved, 0);
const signature = await signMessage(privateKey, sighash);
const signed = appendSignature(proved, 0, signature);

// 5. Finalize and broadcast
const txBytes = finalizeAndExtract(signed);
// submit txBytes to zcashd/lightwalletd

request.free();
```

## API

| Function | Description |
|----------|-------------|
| `new TransactionRequest(payments)` | Create payment request |
| `proposeTransaction(inputs, request)` | Create PCZT from inputs |
| `proveTransaction(pczt)` | Add Orchard proofs |
| `verifyBeforeSigning(pczt, request, change)` | Verify PCZT integrity |
| `getSighash(pczt, index)` | Get 32-byte signature hash |
| `appendSignature(pczt, index, sig)` | Add 64-byte signature |
| `combine(pczts)` | Merge multiple PCZTs |
| `finalizeAndExtract(pczt)` | Extract transaction bytes |
| `parse(bytes)` / `serialize(pczt)` | PCZT serialization |
| `signMessage(privKey, hash)` | secp256k1 signing utility |

## Types

```typescript
interface Payment {
  address: string;    // transparent (tm...) or unified (utest1...)
  amount: string;     // zatoshis as string (BigInt compatibility)
  memo?: string;      // optional, for shielded outputs
}

interface TransparentInput {
  pubkey: Buffer;       // 33 bytes compressed secp256k1
  txid: Buffer;         // 32 bytes
  vout: number;
  amount: string;       // zatoshis as string
  scriptPubKey: Buffer; // P2PKH script
}
```

## Memory

Most functions **consume** the input PCZT (ownership transfer):
- `proveTransaction`, `appendSignature`, `finalizeAndExtract` - consume input

These do **not** consume:
- `getSighash`, `serialize`, `verifyBeforeSigning` - read-only

Call `request.free()` when done with TransactionRequest.

## License

MIT
