# t2z - Transparent to Shielded

Multi-language library for sending transparent Zcash to shielded Orchard outputs using PCZT ([ZIP 374](https://zips.z.cash/zip-0374)).

## Installation

### TypeScript/Node.js

```bash
npm install @gstohl/t2z
```

### Go

```bash
go get github.com/gstohl/t2z-go
```

### Kotlin

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.gstohl:t2z-kotlin:0.2.1")
}
```

### Java

```groovy
// build.gradle
dependencies {
    implementation 'io.github.gstohl:t2z-java:0.2.1'
}
```

Native libraries are bundled for: macOS (arm64/x64), Linux (x64/arm64), Windows (x64/arm64).

## Quick Example

```typescript
import { TransactionRequest, proposeTransaction, proveTransaction,
         getSighash, appendSignature, finalizeAndExtract, signMessage } from '@gstohl/t2z';

// Create payment to shielded address
const request = new TransactionRequest([{
  address: 'u1...',       // unified address
  amount: '100000',       // 0.001 ZEC in zatoshis
}]);

// Build transaction from transparent UTXOs
const pczt = proposeTransaction([{
  pubkey: pubkeyBuffer,       // 33 bytes compressed
  txid: txidBuffer,           // 32 bytes
  vout: 0,
  amount: '100000000',        // 1 ZEC in zatoshis
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
| `calculate_fee` | Calculate ZIP-317 fee for given inputs/outputs |

## Use Cases

- **Hardware wallets**: Sign transparent inputs externally via `get_sighash` + `append_signature`
- **Privacy upgrade**: Convert transparent holdings to shielded Orchard
- **Multi-party**: Parallel signing with `combine`

## Fees (ZIP-317)

Use `calculate_fee(numInputs, numOutputs)` to get the exact fee before building transactions.

- Transparent only: 10,000 zatoshis
- Transparent to Shielded: 15,000 zatoshis

## Package Registries

| Language | Package | Registry |
|----------|---------|----------|
| TypeScript | `@gstohl/t2z` | [npm](https://www.npmjs.com/package/@gstohl/t2z) |
| Go | `github.com/gstohl/t2z-go` | [GitHub](https://github.com/gstohl/t2z-go) |
| Kotlin | `io.github.gstohl:t2z-kotlin` | [Maven Central](https://central.sonatype.com/artifact/io.github.gstohl/t2z-kotlin) |
| Java | `io.github.gstohl:t2z-java` | [Maven Central](https://central.sonatype.com/artifact/io.github.gstohl/t2z-java) |

## Development

### Requirements

| Tool | Version | Required For |
|------|---------|--------------|
| **Node.js** | 18+ | Build scripts, TypeScript bindings |
| **Rust** | 1.75+ | Core library compilation |
| **Go** | 1.21+ | Go bindings |
| **Java JDK** | 17+ | Java/Kotlin bindings |

### Structure

```
t2z/
├── core/
│   └── rust/           # Core library with C FFI
├── bindings/
│   ├── go/             # Go bindings (CGO)
│   ├── typescript/     # TypeScript bindings (koffi)
│   ├── java/           # Java bindings (JNA)
│   └── kotlin/         # Kotlin bindings (JNA)
└── infra/
    ├── zebrad-regtest/ # Local regtest node (auto-mining)
    ├── zebrad-testnet/ # Testnet node
    ├── zebrad-mainnet/ # Mainnet node
    └── linux-build/    # Cross-compilation for Linux
```

### Scripts

```bash
# Build native library and copy to all bindings
node scripts/build-dev.js

# Run all tests (Rust + all bindings)
node scripts/test-all.js

# Clean all build artifacts
node scripts/clean.js

# Run regtest examples (requires Docker)
node scripts/run-regtest-examples.js           # All languages
node scripts/run-regtest-examples.js --ts      # TypeScript only
node scripts/run-regtest-examples.js --go      # Go only
node scripts/run-regtest-examples.js --kotlin  # Kotlin only
node scripts/run-regtest-examples.js --java    # Java only
```

### Regtest Examples

The `run-regtest-examples.js` script runs end-to-end transaction examples against a local Zebra regtest node:

1. Starts zebrad regtest container (or reuses existing)
2. Waits for coinbase maturity (120+ blocks)
3. Runs setup + all 9 examples for each language
4. Leaves container running for faster subsequent runs

Examples included:
- **1-5**: Basic transparent and shielded transactions
- **6-7**: Multiple outputs and mixed transactions
- **8**: Combine workflow (parallel multi-party signing)
- **9**: Offline signing (hardware wallet simulation)

### Manual Build

```bash
cd core/rust && cargo build --release
```

Outputs in `core/rust/target/release/`:
- `libt2z.dylib` (macOS) / `libt2z.so` (Linux) / `t2z.dll` (Windows)

### Releasing

Releases are automated via GitHub Actions. To create a new release:

```bash
git tag v0.2.1
git push origin v0.2.1
```

The release workflow will:
1. Build native libraries for all 6 platforms (macOS/Linux/Windows × x64/arm64)
2. Create a GitHub Release with all binaries attached
3. Publish TypeScript package to npm
4. Publish Java and Kotlin packages to Maven Central
5. Push Go bindings to the [t2z-go](https://github.com/gstohl/t2z-go) repository with matching tag

Required repository secrets:
- `NPM_TOKEN` - npm access token for `@gstohl/t2z`
- `OSSRH_USERNAME`, `OSSRH_PASSWORD` - Maven Central credentials
- `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE` - GPG signing for Maven
- `MULTI_REPO_TOKEN` - GitHub PAT for pushing to t2z-go repo

## License

MIT
