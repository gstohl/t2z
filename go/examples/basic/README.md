# Basic t2z Example

This example demonstrates the basic workflow for creating a transparent-to-transparent Zcash transaction using the t2z library.

## What This Example Does

1. Creates a payment request with a recipient address and amount
2. Prepares transparent inputs (UTXOs) to spend
3. Proposes a PCZT (Partially Constructed Zcash Transaction)
4. Adds Orchard proofs (required by the protocol)
5. Gets the signature hash for each input
6. Signs the transaction using secp256k1
7. Appends the signatures to the PCZT
8. Finalizes and extracts the raw transaction bytes

## Running the Example

```bash
# From the go/ directory
cd examples/basic

# Build and run
go run main.go
```

## Expected Output

```
=== Basic t2z (Transparent to Zcash) Example ===

1. Creating payment request...
   ✓ Created request with 1 payment(s)

2. Preparing transparent inputs...
   Public key: 031b84c5567b126440995d3ed5aaba0565d71e1834604819ff9c17f5e9d5dd078f
   ✓ Prepared 1 input(s) with total: 1.0 ZEC

3. Proposing transaction (creating PCZT)...
   ✓ PCZT created successfully

...

✓ Example completed successfully!
```

## Notes

- This example uses **test keys** (private key = `[1,1,1,...]`) - **DO NOT USE IN PRODUCTION**
- The transaction created is valid but uses fake UTXOs
- In production, you would:
  - Get UTXOs from your wallet or blockchain query
  - Use real private keys (preferably from hardware wallet)
  - Broadcast the transaction to the Zcash network

## Next Steps

- See [../hardware_wallet/](../hardware_wallet/) for external signing with hardware wallets
- Read the [main documentation](../../README.md) for more details
