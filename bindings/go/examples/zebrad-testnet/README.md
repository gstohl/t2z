# Testnet Demo - Mixed T→T+Z

Compact example sending to both transparent and shielded addresses on testnet.

## Prerequisites

1. Zebra testnet node running and synced (`infra/zebrad-testnet/`)
2. Testnet ZEC from [faucet](https://faucet.zecpages.com/)
3. Rust library built (`core/rust/`)

## Usage

```bash
PRIVATE_KEY=<hex> go run main.go
```

The demo automatically:
1. Derives your testnet address from the private key
2. Fetches UTXOs from your local Zebra node
3. Creates a mixed T→T+Z transaction
4. Broadcasts it

## Example

```bash
PRIVATE_KEY=e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35 go run main.go
```

Output:
```
Address: tmXyz...
Fetching UTXOs... ✓ Found 1 UTXO(s)

Input:       0.00100000 ZEC
Transparent: 0.00038000 ZEC → tmXyz...
Shielded:    0.00038000 ZEC → u1eq7...
Fee:         0.00015000 ZEC

Proposing... ✓
Proving... ✓
Signing... ✓
Finalizing... ✓
Broadcasting... ✓

TXID: abc123...
```

## Getting Testnet ZEC

1. Run the demo once to see your address
2. Go to [faucet.zecpages.com](https://faucet.zecpages.com/)
3. Request testnet ZEC to your address
4. Wait for confirmation, then run the demo again
