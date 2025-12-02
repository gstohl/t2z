# Zebra Testnet Infrastructure

This directory contains Docker infrastructure for running a Zebra testnet node.

## Quick Start

```bash
# Build and start Zebra (first build takes ~15-20 minutes to compile)
docker-compose up -d

# Watch sync progress
docker-compose logs -f

# Check sync status
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"getblockchaininfo","params":[],"id":1}' \
  http://localhost:18232 | jq

# Stop (keeps blockchain data)
docker-compose down

# Stop and delete blockchain data
docker-compose down -v
```

## Initial Sync

Testnet sync takes several hours depending on your connection. You can monitor progress via:

```bash
# Watch logs for sync progress
docker-compose logs -f

# Or check block height
curl -s -X POST -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"getblockchaininfo","params":[],"id":1}' \
  http://localhost:18232 | jq '.result.blocks'
```

## Ports

- **18232**: RPC port (JSON-RPC interface)
- **18233**: P2P port (testnet peer connections)

## Getting Testnet ZEC

To test transactions, you'll need testnet ZEC:

1. **Testnet Faucet**: https://faucet.zecpages.com/
2. Generate a testnet address using your wallet
3. Request testnet ZEC from the faucet

## Storage

Testnet blockchain data is stored in a Docker volume (`zebra-testnet-data`). The full testnet chain requires ~30-50 GB of storage.

## Differences from Regtest

| Feature | Regtest | Testnet |
|---------|---------|---------|
| Network | Isolated local | Public testnet |
| Mining | Internal auto-miner | Real network |
| Coins | Generated locally | Faucet required |
| Sync time | Instant | Several hours |
| Use case | Development/testing | Integration testing |
