# Zebra Regtest Infrastructure

This directory contains Docker infrastructure for running Zebra in regtest mode with internal miner enabled.

## Quick Start

```bash
# Build and start Zebra (first build takes ~15-20 minutes to compile)
docker-compose up -d

# Watch logs (look for "successfully mined a new block" messages)
docker-compose logs -f

# Check block height
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"getblockchaininfo","params":[],"id":1}' \
  http://localhost:18232

# Stop and clean up
docker-compose down -v
```

## Configuration

The miner address is set to `tmEUfekwCArJoFTMEL2kFwQyrsDMCNX5ZFf` which corresponds to the test keypair in the TypeScript examples. This allows the examples to spend coinbase outputs.

## Ports

- **18232**: RPC port (JSON-RPC interface)
- **18344**: P2P port (not used in regtest)

## Internal Miner

Zebra's internal miner automatically mines blocks every ~30 seconds once started. After 101 blocks, coinbase outputs become mature and spendable.

## Building from Source

The Dockerfile builds Zebra from source with the `internal-miner` feature flag:

```bash
cargo build --release -p zebrad --features "internal-miner"
```

This feature is required for regtest auto-mining and is not included in the official release binaries.
