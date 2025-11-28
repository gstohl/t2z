# Java Zebrad Examples

Java examples demonstrating t2z library usage with Zebra regtest.

## Quick Start

**Requirements:**
- Java 17+
- Docker and docker-compose
- Rust toolchain (for building native library)

**Important:** Reset docker before running examples to ensure fresh coinbase UTXOs:

```bash
# From repository root
cd infra/zebrad
docker-compose down -v && docker-compose up -d

# Run setup from typescript examples (waits for block 101)
cd ../../typescript/examples/zebrad-t2z
npm install && npm run setup

# Copy test data to Java examples
cp data/test-addresses.json ../../../java/examples/zebrad-t2z/data/

# Run Java examples
cd ../../../java/examples/zebrad-t2z
./gradlew example1
```

## Prerequisites

1. **Zebra regtest running** (via docker-compose in `infra/zebrad/`)
2. **Java library built** (run `./gradlew build` in `java/`)
3. **Rust library built** (run `cargo build --release` in `rust/`)

## Running Examples

```bash
cd java/examples/zebrad-t2z

# Build
./gradlew build

# Run individual examples
./gradlew example1  # Single transparent output (T→T)
./gradlew example2  # Multiple transparent outputs (T→T×2)
./gradlew example3  # UTXO consolidation (2 inputs → 1 output)
./gradlew example5  # Single shielded output (T→Z)
./gradlew example6  # Multiple shielded outputs (T→Z×2)
./gradlew example7  # Mixed transparent + shielded (T→T+Z)

# Run all examples
./gradlew all
```

## Examples Overview

| Example | Description |
|---------|-------------|
| 1 | Single output transparent transaction |
| 2 | Multiple recipients (2 transparent outputs) |
| 3 | UTXO consolidation (multiple inputs → single output) |
| 5 | Transparent to shielded (Orchard) |
| 6 | Multiple shielded recipients |
| 7 | Mixed transparent + shielded outputs |

## Project Structure

```
java/examples/zebrad-t2z/
├── src/main/java/com/zcash/t2z/examples/
│   ├── Keys.java              # Key management utilities
│   ├── ZebraClient.java       # Zebra RPC client
│   ├── Utils.java             # Helper utilities
│   ├── Example1SingleOutput.java
│   ├── Example2MultipleOutputs.java
│   ├── Example3MultipleInputs.java
│   ├── Example5ShieldedOutput.java
│   ├── Example6MultipleShielded.java
│   └── Example7MixedOutputs.java
├── data/                      # Runtime data (gitignored)
├── build.gradle
└── settings.gradle
```

## Dependencies

- `com.zcash:t2z` - Local t2z Java library
- `net.java.dev.jna:jna` - JNA for native library loading
- `org.bouncycastle:bcprov-jdk18on` - Crypto operations
- `org.bitcoinj:bitcoinj-core` - Base58 encoding
- `com.google.code.gson:gson` - JSON parsing
