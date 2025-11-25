# Zebra + @mayaprotocol/zcash-ts Setup

## ✅ What We're Using

- **Zebra**: Modern, future-proof Zcash node (consensus layer)
- **@mayaprotocol/zcash-ts**: Full-featured Zcash library with wallet management and RPC client built-in

## 🔑 Key Changes from zcashd

### Wallet Functionality
Zebra is **node-only** - no built-in wallet, but `@mayaprotocol/zcash-ts` handles it!

**Before** (zcashd):
```typescript
const addr = await client.getnewaddress();
const privkey = await client.dumpprivkey(addr);
```

**Now** (@mayaprotocol/zcash-ts):
```typescript
import { ZcashRPCClient, /* wallet functions */ } from '@mayaprotocol/zcash-ts';
const client = new ZcashRPCClient({ host, port, network: Network.Regtest });
// Use wallet methods from @mayaprotocol/zcash-ts directly
```

**No custom wrappers needed!** The library handles everything.

### Mining in Regtest

**zcashd**:
```typescript
await client.generate(101);  // ✅ Easy
```

**Zebra**:
- Option 1: Enable `internal_miner` in zebra.toml (requires compile flag)
- Option 2: Use `getblocktemplate` + `submitblock` manually
- Option 3: For examples, we pre-fund addresses

For our examples, we'll use **manual mining** or **pre-funded scenarios**.

### What Still Works

✅ All consensus operations:
- `getblockchaininfo`
- `sendrawtransaction`
- `getrawtransaction`
- `getblock`
- Transaction broadcasting
- Mempool queries

## 📦 Benefits of This Approach

1. **Future-proof**: Zebra is the future of Zcash nodes
2. **Realistic**: Shows proper key management patterns
3. **Educational**: Learn how wallets actually work
4. **Flexible**: Can integrate with hardware wallets easily

## 🔄 Migration Note

If you need full zcashd compatibility (wallet + mining), use zcashd instead:
```bash
# Just change docker-compose.yml back to zcashd
# Everything else works the same!
```

The examples show **production-ready** patterns that work with any node!
