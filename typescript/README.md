# TypeScript/Node.js Bindings for PCZT Library

TypeScript bindings for the PCZT library, enabling Node.js applications to create Zcash transactions with shielded outputs.

## Status

🚧 **Under Development** - Not yet implemented

## Planned Implementation

### Option 1: Using node-ffi-napi

```typescript
import ffi from 'ffi-napi';
import ref from 'ref-napi';

const pczt = ffi.Library('../rust/target/release/libpczt_lib', {
  'pczt_transaction_request_new': ['int', ['pointer', 'size_t', 'pointer']],
  'pczt_propose_transaction': ['int', ['pointer', 'size_t', 'pointer', 'pointer']],
  // ... other functions
});
```

### Option 2: Using napi-rs

Create native Node.js addon with safer bindings.

## Installation (Future)

```bash
npm install @zcash/pczt-lib
```

## Usage Example (Future)

```typescript
import { TransactionRequest, Payment, createPczt } from '@zcash/pczt-lib';

// Create payment request
const payment = new Payment({
  address: 'u1unified_address',
  amount: 100000n,
  memo: 'Payment to Alice'
});

const request = new TransactionRequest([payment]);

// Create PCZT
const pczt = await createPczt(inputs, request);

// Add proofs
const proved = await pczt.addProofs();

// Sign and finalize
const sighash = await proved.getSighash(0);
const signature = await mySigningFunction(sighash);
const signed = await proved.appendSignature(0, signature);

// Extract transaction
const txBytes = await signed.finalize();
```

## TODO

- [ ] Choose FFI approach (node-ffi-napi vs napi-rs)
- [ ] Create TypeScript type definitions
- [ ] Implement wrapper classes
- [ ] Add error handling
- [ ] Write tests
- [ ] Add documentation
- [ ] Publish to npm
