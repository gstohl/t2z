# t2z TypeScript Bindings

TypeScript/Node.js bindings for the t2z library using [koffi](https://koffi.dev/) FFI.

Enables transparent Zcash wallets to send shielded Orchard outputs using PCZT ([ZIP 374](https://zips.z.cash/zip-0374)).

## Installation

```bash
cd typescript
npm install
npm run build
```

Requires the Rust library to be built first:
```bash
cd ../rust
cargo build --release
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
  address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
  amount: '100000', // zatoshis as string
}]);

// 2. Create PCZT from transparent inputs
const pczt = proposeTransaction([{
  pubkey: pubkeyBytes,      // 33 bytes
  txid: txidBytes,          // 32 bytes
  vout: 0,
  amount: '100000000',      // zatoshis as string
  scriptPubKey: scriptBytes,
}], request);

// 3. Add proofs, sign, finalize
const proved = proveTransaction(pczt);
const sighash = getSighash(proved, 0);
const signature = await signMessage(privateKey, sighash);
const signed = appendSignature(proved, 0, signature);
const txBytes = finalizeAndExtract(signed);
```

## API

| Function | Description |
|----------|-------------|
| `proposeTransaction(inputs, request)` | Create PCZT from inputs |
| `proveTransaction(pczt)` | Add Orchard proofs |
| `getSighash(pczt, index)` | Get signature hash for input |
| `appendSignature(pczt, index, sig)` | Append 64-byte signature |
| `combine(pczts)` | Merge multiple PCZTs |
| `finalizeAndExtract(pczt)` | Extract final transaction bytes |
| `serialize(pczt)` / `parse(bytes)` | PCZT serialization |
| `verifyBeforeSigning(pczt, request, change)` | Verify before signing |

## Types

```typescript
interface Payment {
  address: string;
  amount: string;      // BigInt as string
  memo?: string;
}

interface TransparentInput {
  pubkey: Buffer;      // 33 bytes compressed
  txid: Buffer;        // 32 bytes
  vout: number;
  amount: string;      // BigInt as string
  scriptPubKey: Buffer;
}
```

## Testing

```bash
npm test
```

## License

MIT
