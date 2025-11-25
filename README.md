# t2z - Transparent to Shielded

Multi-language library for sending transparent Zcash to shielded Orchard outputs using PCZT ([ZIP 374](https://zips.z.cash/zip-0374)).

## Structure

```
t2z/
├── rust/        # Core library with C FFI
├── go/          # Go bindings (CGO)
├── typescript/  # TypeScript bindings (koffi)
├── kotlin/      # Kotlin bindings (planned)
└── java/        # Java bindings (planned)
```

## Build

```bash
cd rust && cargo build --release
```

Outputs in `rust/target/release/`:
- `libt2z.dylib` (macOS) / `libt2z.so` (Linux) / `t2z.dll` (Windows)
- `libt2z.a` (static)

Header: `rust/include/t2z.h`

## Quick Example (TypeScript)

```typescript
import { TransactionRequest, proposeTransaction, proveTransaction,
         getSighash, appendSignature, finalizeAndExtract, signMessage } from 't2z';

// Create payment to shielded address
const request = new TransactionRequest([{
  address: 'utest1...', // unified address
  amount: '100000',     // 0.001 ZEC
}]);

// Build transaction from transparent UTXOs
const pczt = proposeTransaction([{
  pubkey: pubkeyBuffer,       // 33 bytes
  txid: txidBuffer,           // 32 bytes
  vout: 0,
  amount: '100000000',        // 1 ZEC
  scriptPubKey: scriptBuffer,
}], request);

// Prove, sign, finalize
const proved = proveTransaction(pczt);
const sighash = getSighash(proved, 0);
const signature = await signMessage(privateKey, sighash);
const signed = appendSignature(proved, 0, signature);
const txBytes = finalizeAndExtract(signed);

// Broadcast txBytes to network
```

## API

| Function | Description |
|----------|-------------|
| `propose_transaction` | Create PCZT from transparent inputs and payment request |
| `prove_transaction` | Add Orchard zero-knowledge proofs |
| `verify_before_signing` | Verify PCZT matches expected payments (security) |
| `get_sighash` | Get signature hash for transparent input |
| `append_signature` | Add secp256k1 signature (64 bytes) |
| `combine` | Merge multiple PCZTs (parallel signing) |
| `finalize_and_extract` | Extract final transaction bytes |
| `parse` / `serialize` | PCZT serialization for storage/transport |

## Use Cases

- **Hardware wallets**: Sign transparent inputs externally via `get_sighash` + `append_signature`
- **Privacy upgrade**: Convert transparent holdings to shielded Orchard
- **Multi-party**: Parallel signing with `combine`

## Fees (ZIP-317)

- Transparent only: 10,000 zatoshis
- Transparent → Shielded: 15,000 zatoshis

## License

MIT
