# t2z Kotlin Zebra Examples

Kotlin examples demonstrating the **t2z** library with Zebra regtest.

## Prerequisites

> **IMPORTANT**: Zebra regtest must be running before executing any examples!

### 1. Start Zebra (Docker)

From the repository root:

```bash
cd infra/zebrad
docker-compose up -d

# Watch logs for mining progress
docker-compose logs -f
```

### 2. Wait for Coinbase Maturity

The internal miner auto-mines blocks every ~30 seconds. You need **101 blocks** for coinbase maturity (~50 minutes from fresh start, or instant if already running).

### 3. Build Rust Library

```bash
cd rust
cargo build --release
```

### 4. Build Examples

```bash
cd kotlin/examples/zebrad-t2z
./gradlew build
```

## Quick Start

```bash
# 1. Setup (waits for mature blocks, saves keypair)
./gradlew setup

# 2. Run any example
./gradlew example1
```

## Examples

### Transparent Transactions (T→T)

| Example | Command | Description |
|---------|---------|-------------|
| 1 | `./gradlew example1` | Single output - basic transaction |
| 2 | `./gradlew example2` | Multiple outputs - 2 recipients |
| 3 | `./gradlew example3` | Multiple inputs - UTXO consolidation |

### Shielded Transactions (T→Z)

| Example | Command | Description |
|---------|---------|-------------|
| 5 | `./gradlew example5` | Single Orchard output |
| 6 | `./gradlew example6` | Multiple Orchard outputs |
| 7 | `./gradlew example7` | Mixed transparent + shielded outputs |

### Run All

```bash
./gradlew all
```

## Configuration

Examples connect to Zebra at `localhost:18232` by default. Override with:

```bash
ZEBRA_HOST=192.168.1.100 ZEBRA_PORT=18232 ./gradlew example1
```

## Test Keypair

All examples use a pre-configured test keypair:
- **Address**: `tmEUfekwCArJoFTMEL2kFwQyrsDMCNX5ZFf`
- This is set as `miner_address` in Zebra config, receiving all mining rewards
- Examples fetch fresh UTXOs from the blockchain at runtime

## Transaction Flow

```
1. Propose   → Create PCZT from inputs + payment request
2. Prove     → Add ZK proofs (Orchard for shielded)
3. Verify    → Check for malleation (CRITICAL!)
4. Sighash   → Get signature hash for each input
5. Sign      → Create secp256k1 signature
6. Append    → Add signature to PCZT
7. Finalize  → Extract final transaction bytes
8. Broadcast → Send to network
```

## Security Note

**Always verify PCZTs before signing!**

```kotlin
// SAFE
verifyBeforeSigning(pczt, request, emptyList())
val signed = appendSignature(pczt, 0, signature)

// DANGEROUS - never skip verification!
val signed = appendSignature(pczt, 0, signature)
```

## Troubleshooting

### "Connection refused"
Zebra is not running. Start it with:
```bash
cd infra/zebrad && docker-compose up -d
```

### "No mature UTXOs available"
Wait for more blocks (101 needed for coinbase maturity). Check progress:
```bash
cd infra/zebrad && docker-compose logs -f
```

### "Transaction was committed to the best chain"
The UTXO was already spent. Examples fetch fresh UTXOs, so this is rare.
Wait for new blocks to be mined.

## Resources

- [ZIP 374: PCZT Specification](https://zips.z.cash/zip-0374)
- [t2z Library](../../README.md)
- [Zebra Regtest Docs](https://zebra.zfnd.org/user/regtest.html)
