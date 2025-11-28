# t2z Rust Core

Rust implementation with C FFI bindings for cross-language support.

## Build

```bash
cargo build --release
cargo test
```

## Output

- `target/release/libt2z.{dylib,so,dll}` - Shared library
- `target/release/libt2z.a` - Static library
- `include/t2z.h` - C header (auto-generated)

## C API

All functions return `ResultCode` (0 = success). Use `pczt_get_last_error()` for error details.

### Transaction Flow

```c
#include "t2z.h"

// 1. Create payment request
CPayment payments[] = {{ .address = "utest1...", .amount = 100000 }};
TransactionRequestHandle* request;
pczt_transaction_request_new(payments, 1, &request);

// 2. Propose transaction (serialized inputs)
PcztHandle* pczt;
pczt_propose_transaction_v2(input_bytes, input_len, request, NULL, &pczt);

// 3. Add proofs
PcztHandle* proved;
pczt_prove_transaction(pczt, &proved);

// 4. Sign each input
uint8_t sighash[32];
pczt_get_sighash(proved, 0, &sighash);
// ... sign sighash with secp256k1 ...
PcztHandle* signed_pczt;
pczt_append_signature(proved, 0, signature, &signed_pczt);

// 5. Finalize
uint8_t* tx_bytes;
size_t tx_len;
pczt_finalize_and_extract(signed_pczt, &tx_bytes, &tx_len);

// Cleanup
pczt_free_bytes(tx_bytes, tx_len);
pczt_transaction_request_free(request);
```

### Functions

See [root README](../README.md) for API overview.

| Function | Description |
|----------|-------------|
| `pczt_transaction_request_new` | Create payment request |
| `pczt_propose_transaction_v2` | Create PCZT from serialized inputs |
| `pczt_prove_transaction` | Add Orchard proofs |
| `pczt_verify_before_signing` | Verify PCZT integrity |
| `pczt_get_sighash` | Get signature hash for input |
| `pczt_append_signature` | Add 64-byte signature |
| `pczt_combine` | Merge multiple PCZTs |
| `pczt_finalize_and_extract` | Extract transaction bytes |
| `pczt_parse` / `pczt_serialize` | Serialization |

### Memory Management

| Function | Description |
|----------|-------------|
| `pczt_free` | Free PCZT handle |
| `pczt_free_bytes` | Free byte buffers |
| `pczt_transaction_request_free` | Free request handle |
| `pczt_get_last_error` | Get error message |

## License

MIT
