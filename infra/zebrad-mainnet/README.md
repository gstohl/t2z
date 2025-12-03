# Zebra Mainnet Node

Production Zcash mainnet node for t2z demos.

## Quick Start

```bash
docker-compose up -d
```

## Ports

- **8232**: JSON-RPC (for t2z demos)
- **8233**: P2P network

## Sync Time

Mainnet has many peers and syncs much faster than testnet:
- Initial sync: ~12-24 hours (checkpoint sync enabled)
- Disk usage: ~300GB when fully synced

## Check Status

```bash
# Sync progress
curl -s --data-binary '{"jsonrpc":"2.0","method":"getblockchaininfo","params":[],"id":1}' \
  -H 'Content-Type: application/json' http://localhost:8232

# Peer count
curl -s --data-binary '{"jsonrpc":"2.0","method":"getpeerinfo","params":[],"id":1}' \
  -H 'Content-Type: application/json' http://localhost:8232
```

## Running Demos

Once synced, run mainnet demos:

```bash
# Go
cd bindings/go/examples/zebrad-mainnet
PRIVATE_KEY=<hex> go run main.go

# TypeScript
cd bindings/typescript/examples/zebrad-mainnet
PRIVATE_KEY=<hex> npx ts-node src/demo.ts
```

## Stop

```bash
docker-compose down
```
