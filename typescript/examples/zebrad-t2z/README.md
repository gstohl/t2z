# zebrad-t2z Examples

Comprehensive examples demonstrating the **t2z (Transparent to Zcash)** library using **Zebra** regtest in Docker.

> **🚀 TL;DR**: Everything runs in Docker! See [QUICKSTART.md](./QUICKSTART.md) for the fast path.

## Overview

This example suite shows the full capabilities of t2z for building transparent-to-shielded Zcash transactions using PCZT (Partially Constructed Zcash Transactions) as defined in ZIP 374.

### What You'll Learn

- ✅ How to use **Zebra** (modern, future-proof Zcash node)
- ✅ Use **@mayaprotocol/zcash-ts** for wallet & RPC operations
- ✅ Convert transparent UTXOs to `TransparentInput` format
- ✅ Create and broadcast shielded Zcash transactions
- ✅ Handle single and multiple outputs
- ✅ Mix transparent and shielded recipients
- ✅ Detect PCZT malleation attacks using `verifyBeforeSigning`
- ✅ Complete production-ready transaction workflows
- ✅ **Run everything in Docker** (no platform compatibility issues!)

## Architecture

```
┌─────────────────────────────────────────────────────┐
│  Docker Compose (linux/amd64)                       │
│                                                     │
│  ┌──────────────┐         ┌────────────────────┐  │
│  │   Zebra      │◄───RPC──┤  Examples          │  │
│  │   (regtest)  │         │  (Node.js 20)      │  │
│  │              │         │                    │  │
│  │  • Port 18232│         │  • @mayaprotocol   │  │
│  │  • No wallet │         │    /zcash-ts       │  │
│  │  • Pure node │         │  • t2z library     │  │
│  └──────────────┘         │  • tsx runner      │  │
│                           └────────────────────┘  │
│                                                     │
└─────────────────────────────────────────────────────┘
         │
         ↓
  npm run example:1  ← Run from host, executes in container
```

**Key Benefits**:
- 🐳 Everything isolated in Docker
- 🔒 No platform compatibility issues
- 🚀 Zebra (future-proof!)
- 💎 @mayaprotocol/zcash-ts (wallet + RPC)

## Prerequisites

- **Docker** & **Docker Compose**
- That's it! No Node.js needed on host machine.

## Quick Start

### 1. Start Everything

```bash
# Build and start Zebra + Examples containers
npm run docker:up

# Wait ~30 seconds for Zebra to initialize
# Check logs:
npm run docker:logs:zebra
```

### 2. Setup Test Environment

```bash
# Initialize regtest blockchain and create test addresses
# (Runs inside Docker container)
npm run setup
```

This will:
- Wait for Zebra to be ready
- Mine blocks for coinbase maturity
- Generate test keypairs
- Create test addresses
- Save to `test-addresses.json`

### 3. Run Examples

All examples run **inside Docker** (linux/amd64):

```bash
# Run all examples
npm run all

# Or run individually:
npm run example:1  # Single output (transparent → shielded)
npm run example:2  # Multiple outputs (3 shielded recipients)
npm run example:3  # Mixed outputs (transparent + shielded)
npm run example:4  # Attack scenario (security demonstration)
```

### 4. Development

```bash
# Get a shell inside examples container
npm run docker:shell

# Then run commands directly:
tsx src/examples/1-single-output.ts
```

### 5. Cleanup

```bash
# Stop and remove all containers
npm run docker:down
```

## Examples Breakdown

### Example 1: Single Output Transaction

**File**: `src/examples/1-single-output.ts`

Demonstrates the basic t2z workflow:

```typescript
// 1. Get transparent UTXO
const utxos = await client.listUnspent();
const input = await utxoToTransparentInput(client, utxos[0]);

// 2. Create payment request
const payments = [{
  address: 'u1...',  // Unified address
  amount: '50000000',  // 0.5 ZEC
}];
const request = new TransactionRequest(payments);

// 3. Propose → Prove → Verify → Sign → Finalize → Broadcast
const pczt = proposeTransaction([input], request);
const proved = proveTransaction(pczt);
verifyBeforeSigning(proved, request, []);
const sighash = getSighash(proved, 0);
const signature = await signMessage(privKey, sighash);
const signed = appendSignature(proved, 0, signature);
const txBytes = finalizeAndExtract(signed);
const txid = await client.sendRawTransaction(txBytes.toString('hex'));
```

**Output**:
- 1 transparent input
- 1 shielded output (payment)
- 1 shielded output (auto-generated change)

### Example 2: Multiple Outputs

**File**: `src/examples/2-multiple-outputs.ts`

Send to 3 different shielded addresses in one transaction:

```typescript
const payments = [
  { address: 'u1addr1...', amount: '100000000' },  // 1 ZEC
  { address: 'u1addr2...', amount: '50000000' },   // 0.5 ZEC
  { address: 'u1addr3...', amount: '25000000' },   // 0.25 ZEC
];
```

**Features**:
- Efficient batching (single transaction fee)
- All recipients get shielded outputs
- Change automatically shielded

### Example 3: Mixed Outputs

**File**: `src/examples/3-mixed-outputs.ts`

Combine transparent AND shielded outputs:

```typescript
const payments = [
  { address: 'tmAddr...', amount: '200000000' },  // 2 ZEC transparent
  { address: 'u1addr...', amount: '300000000' },  // 3 ZEC shielded
];
```

**Use Cases**:
- Pay a merchant (transparent) and save (shielded) in one tx
- Hybrid privacy - some public, some private payments
- Flexibility for different recipient requirements

### Example 4: Attack Scenario

**File**: `src/examples/4-attack-scenario.ts`

**⚠️ SECURITY DEMONSTRATION**

Shows why `verifyBeforeSigning()` is critical:

**Attack 1**: Wrong Amount
```typescript
// User wants to send 1 ZEC
const legitimate = [{ address: 'u1...', amount: '100000000' }];

// Attacker modifies PCZT to send 5 ZEC
const malicious = [{ address: 'u1...', amount: '500000000' }];

// Verification catches the attack!
verifyBeforeSigning(pczt, maliciousRequest, []); // ❌ THROWS ERROR
```

**Attack 2**: Wrong Recipient
```typescript
// Attacker replaces recipient address
const malicious = [{ address: 'attackerAddr', amount: '100000000' }];
verifyBeforeSigning(pczt, maliciousRequest, []); // ❌ THROWS ERROR
```

**Attack 3**: Missing Payment
```typescript
// Attacker removes payment (keeps all as change)
const malicious = [];
verifyBeforeSigning(pczt, maliciousRequest, []); // ❌ THROWS ERROR
```

**Key Lesson**: ALWAYS verify PCZTs before signing, especially:
- Hardware wallet scenarios
- Multi-sig workflows
- Any untrusted PCZT source

## Project Structure

```
zcashd-t2z/
├── docker-compose.yml          # zcashd container configuration
├── zcashd.conf                 # zcashd regtest settings
├── package.json                # Dependencies and scripts
├── tsconfig.json               # TypeScript configuration
├── README.md                   # This file
│
├── src/
│   ├── zcashd-client.ts       # RPC client for zcashd
│   ├── utils.ts               # Helper functions
│   ├── setup.ts               # Environment initialization
│   │
│   └── examples/
│       ├── 1-single-output.ts    # Basic single payment
│       ├── 2-multiple-outputs.ts  # Batch payments
│       ├── 3-mixed-outputs.ts     # Transparent + shielded
│       └── 4-attack-scenario.ts   # Security demonstration
│
└── test-addresses.json        # Generated by setup.ts
```

## API Reference

### ZcashdClient

RPC client for interacting with zcashd:

```typescript
const client = new ZcashdClient('localhost', 18232, 'user', 'pass');

// Blockchain operations
await client.getBlockchainInfo();
await client.generate(numBlocks);

// Address management
await client.getNewAddress();
await client.validateAddress(address);
await client.dumpPrivKey(address);

// UTXO management
await client.listUnspent(minconf, maxconf, addresses);

// Transaction operations
await client.sendRawTransaction(hexString);
await client.getRawTransaction(txid, verbose);
await client.decodeRawTransaction(hexString);

// Orchard/Unified addresses
await client.createAccount();
await client.getUnifiedAddress(account);
```

### Utility Functions

```typescript
// Convert UTXO to t2z input format
const input = await utxoToTransparentInput(client, utxo);

// Get compressed public key
const pubkey = await getCompressedPubKey(client, address);

// Amount conversions
const zatoshis = zecToZatoshi(1.5);  // 150000000n
const zec = zatoshiToZec(150000000n);  // "1.50000000"

// Display helpers
printWorkflowSummary(title, inputs, outputs, fee);
printBroadcastResult(txid, txHex);
printError(title, error);
```

## Transaction Flow

```
┌─────────────────┐
│  1. Propose     │  Create PCZT from inputs + payment request
└────────┬────────┘
         │
┌────────▼────────┐
│  2. Prove       │  Add Orchard proofs (zk-SNARKs)
└────────┬────────┘
         │
┌────────▼────────┐
│  3. Verify      │  ⚠️ CRITICAL: Check for malleation
└────────┬────────┘
         │
┌────────▼────────┐
│  4. Sighash     │  Get signature hash for each input
└────────┬────────┘
         │
┌────────▼────────┐
│  5. Sign        │  Create secp256k1 signature
└────────┬────────┘
         │
┌────────▼────────┐
│  6. Append      │  Add signature to PCZT
└────────┬────────┘
         │
┌────────▼────────┐
│  7. Finalize    │  Extract final transaction bytes
└────────┬────────┘
         │
┌────────▼────────┐
│  8. Broadcast   │  Send to Zcash network
└─────────────────┘
```

## Docker Management

```bash
# Start containers
npm run docker:up

# Stop containers
npm run docker:down

# View logs
npm run docker:logs

# Execute zcash-cli commands
npm run docker:cli -- getblockchaininfo
npm run docker:cli -- listunspent
```

## Fees

Transaction fees follow ZIP 317:

- **Transparent-only**: 10,000 zatoshis (0.0001 ZEC)
- **Transparent→Shielded**: 15,000 zatoshis (0.00015 ZEC)

Fees are automatically calculated by `proposeTransaction()`.

## Security Considerations

### ⚠️ CRITICAL: Always Verify Before Signing

```typescript
// ❌ DANGEROUS - Don't do this!
const signed = appendSignature(pczt, 0, signature);

// ✅ SAFE - Always verify first!
verifyBeforeSigning(pczt, request, expectedChange);
const signed = appendSignature(pczt, 0, signature);
```

### Private Key Management

These examples use zcashd's key management for simplicity. In production:

- Use hardware wallets (Ledger, Trezor)
- Secure key storage (HSM, encrypted storage)
- Never expose private keys in logs
- Use separate signing devices

### PCZT Trust

Only sign PCZTs from trusted sources:

- ✅ PCZTs you created yourself
- ✅ PCZTs from verified co-signers
- ✅ PCZTs from hardware wallets
- ❌ PCZTs from untrusted third parties (without verification!)

## Troubleshooting

### Docker Issues

```bash
# Check if zcashd is running
docker ps

# Check logs for errors
npm run docker:logs

# Restart containers
npm run docker:down && npm run docker:up
```

### RPC Connection Errors

```
Error: connect ECONNREFUSED 127.0.0.1:18232
```

**Solution**: Wait for zcashd to fully start (~30 seconds)

### No UTXOs Available

```
Error: No UTXOs available
```

**Solution**: Run `npm run setup` to initialize the environment

### Signature Verification Failed

```
Error: Invalid signature length
```

**Solution**: Ensure you're using the correct private key format (32 bytes)

## Advanced Usage

### Custom Change Address

```typescript
// Specify explicit change address (transparent or shielded)
const pczt = proposeTransactionWithChange(
  inputs,
  request,
  'tmChangeAddr...'
);
```

### Multiple Input Signing

```typescript
// Sign each input individually
for (let i = 0; i < inputs.length; i++) {
  const sighash = getSighash(pczt, i);
  const signature = await signMessage(privKeys[i], sighash);
  pczt = appendSignature(pczt, i, signature);
}
```

### Serialization / Deserialization

```typescript
// Serialize PCZT for storage or transport
const bytes = serialize(pczt);

// Later: deserialize
const pczt = parse(bytes);
```

## Performance

Typical transaction timings on modern hardware:

- Propose: <10ms
- Prove: ~2-3 seconds (Orchard proof generation)
- Sign: <5ms
- Finalize: <10ms
- Broadcast: <100ms

**Total**: ~2-3 seconds (dominated by proof generation)

## Resources

- [ZIP 374: PCZT Specification](https://zips.z.cash/zip-0374)
- [ZIP 317: Fee Estimation](https://zips.z.cash/zip-0317)
- [Zcash Documentation](https://zcash.readthedocs.io/)
- [t2z Library Documentation](../../README.md)

## License

MIT

## Contributing

Found a bug or want to add more examples? PRs welcome!

---

**Built with ❤️ for the Zcash community**
