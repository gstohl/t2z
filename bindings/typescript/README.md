# @gstohl/t2z

TypeScript/Node.js bindings for t2z - enabling transparent Zcash wallets to send shielded Orchard outputs via PCZT ([ZIP 374](https://zips.z.cash/zip-0374)).

## Installation

```bash
npm install @gstohl/t2z
```

Native libraries are bundled for: macOS (arm64/x64), Linux (x64/arm64), Windows (x64).

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
} from '@gstohl/t2z';

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
```

## API

See the [main repo](https://github.com/gstohl/t2z) for full documentation.

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
| `parsePczt(bytes)` / `serializePczt(pczt)` | PCZT serialization |
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

**Automatic cleanup**: All handles are automatically freed by the garbage collector via `FinalizationRegistry`. No manual cleanup required.

Consuming functions transfer ownership (input PCZT becomes invalid):
- `proveTransaction`, `appendSignature`, `finalizeAndExtract`, `combine`

Non-consuming (read-only):
- `getSighash`, `serialize`, `verifyBeforeSigning`

## Considerations

**Sync API**: All functions are synchronous and will block the event loop during FFI calls. For most operations this is negligible (microseconds), but `proveTransaction` can take longer on first call when loading proving keys.

For server applications requiring non-blocking I/O, consider wrapping calls in a worker thread:

```typescript
import { Worker } from 'worker_threads';
// Run heavy operations like proveTransaction in a worker
```

A built-in async API using worker threads may be added in a future release.

## License

MIT
