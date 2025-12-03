# t2z - Transparent to Shielded

Multi-language library for sending transparent Zcash to shielded Orchard outputs using PCZT ([ZIP 374](https://zips.z.cash/zip-0374)).

## Structure

```
t2z/
├── core/
│   └── rust/        # Core library with C FFI
├── bindings/
│   ├── go/          # Go bindings (CGO)
│   ├── typescript/  # TypeScript bindings (koffi)
│   ├── java/        # Java bindings (JNA)
│   └── kotlin/      # Kotlin bindings (JNA)
└── infra/           # Docker infrastructure for regtest
```

## Build

### Development Build (Recommended)

Use the cross-platform Node.js build script to build the Rust library and copy it to all bindings:

```bash
node scripts/build-dev.js
```

This will:
1. Build the Rust library in release mode
2. Detect your platform (macOS/Linux/Windows, x64/arm64)
3. Copy the native library to all binding directories

### Manual Build

```bash
cd core/rust && cargo build --release
```

Outputs in `core/rust/target/release/`:
- `libt2z.dylib` (macOS) / `libt2z.so` (Linux) / `t2z.dll` (Windows)
- `libt2z.a` (static)

Header: `core/rust/include/t2z.h`

### Library Locations

After running `build-dev.js`, libraries are placed at:

| Language | Location |
|----------|----------|
| TypeScript | `bindings/typescript/lib/<platform>/` |
| Go | `bindings/go/lib/<platform>/` |
| Kotlin | `bindings/kotlin/src/main/resources/<jna-platform>/` |
| Java | `bindings/java/src/main/resources/<jna-platform>/` |

Platform mappings:
- `darwin-arm64` / `darwin-aarch64` (macOS Apple Silicon)
- `darwin-x64` / `darwin-x86-64` (macOS Intel)
- `linux-x64` / `linux-x86-64` (Linux x64)
- `linux-arm64` / `linux-aarch64` (Linux ARM)
- `windows-x64` / `win32-x86-64` (Windows x64)

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
| `parse_pczt` / `serialize_pczt` | PCZT serialization for storage/transport |

## Use Cases

- **Hardware wallets**: Sign transparent inputs externally via `get_sighash` + `append_signature`
- **Privacy upgrade**: Convert transparent holdings to shielded Orchard
- **Multi-party**: Parallel signing with `combine`

## Fees (ZIP-317)

- Transparent only: 10,000 zatoshis
- Transparent → Shielded: 15,000 zatoshis

## Release Workflow (Proposal)

A GitHub Actions workflow is provided in `.github/workflows/release.yml` for automated releases.

### What It Does

```
┌─────────────────────────────────────────────────────────────┐
│  Trigger: git tag v1.0.0                                    │
├─────────────────────────────────────────────────────────────┤
│  1. Build native libraries (parallel)                       │
│     ├── macos-14    → libt2z-darwin-arm64.dylib             │
│     ├── macos-13    → libt2z-darwin-x64.dylib               │
│     ├── ubuntu      → libt2z-linux-x64.so                   │
│     ├── ubuntu-arm  → libt2z-linux-arm64.so                 │
│     └── windows     → t2z-windows-x64.dll                   │
│                                                             │
│  2. Create GitHub Release with all binaries                 │
│                                                             │
│  3. Publish packages                                        │
│     ├── npm         → @gstohl/t2z                           │
│     ├── Maven       → com.gstohl:t2z-kotlin                 │
│     ├── Maven       → com.gstohl:t2z-java                   │
│     └── Go repo     → github.com/gstohl/t2z-go              │
└─────────────────────────────────────────────────────────────┘
```

### Prerequisites

1. **Create language-specific repos** (for Go modules):
   - `gstohl/t2z-go`

2. **Configure secrets**:
   - `NPM_TOKEN` - npm publish token
   - `OSSRH_USERNAME` / `OSSRH_PASSWORD` - Maven Central credentials
   - `MULTI_REPO_TOKEN` - GitHub PAT with repo scope (for Go repo push)

3. **Enable the workflow**: Edit `.github/workflows/release.yml` and change the trigger from `workflow_dispatch` to:
   ```yaml
   on:
     push:
       tags: ['v*']
   ```

### Manual Trigger

The workflow can also be triggered manually via GitHub Actions UI for testing (dry run mode).

## License

MIT
