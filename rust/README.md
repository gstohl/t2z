# t2z Rust Core

Rust implementation of t2z with C FFI bindings.

## Build

```bash
cargo build --release
```

## Test

```bash
cargo test
```

## API

All functions use `pczt_` prefix. See `include/t2z.h` for C API.

| Function | Description |
|----------|-------------|
| `pczt_propose_transaction_v2` | Create PCZT from serialized inputs |
| `pczt_prove_transaction` | Add Orchard proofs |
| `pczt_get_sighash` | Get signature hash |
| `pczt_append_signature` | Add signature |
| `pczt_combine` | Merge PCZTs |
| `pczt_finalize_and_extract` | Extract tx bytes |
| `pczt_parse` / `pczt_serialize` | Serialization |
| `pczt_verify_before_signing` | Verification |

## Memory

- `pczt_free()` - Free PCZT handle
- `pczt_free_bytes()` - Free byte buffers
- `pczt_transaction_request_free()` - Free request

## License

MIT
