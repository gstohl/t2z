# t2z - Transparent to Shielded

Multi-language library for sending transparent Zcash to shielded Orchard outputs using PCZT ([ZIP 374](https://zips.z.cash/zip-0374)).

## Structure

```
t2z/
├── rust/        # Core library with C FFI
├── go/          # Go bindings (CGO)
├── typescript/  # TypeScript bindings (koffi)
├── kotlin/      # Kotlin bindings (planned)
└── java/        # Java bindings (planned)
```

## Build

```bash
cd rust && cargo build --release
```

Outputs in `rust/target/release/`:
- `libt2z.dylib` / `libt2z.so` / `t2z.dll`
- `libt2z.a` (static)

Header: `rust/include/t2z.h`

## API

| Function | Description |
|----------|-------------|
| `propose_transaction` | Create PCZT from inputs |
| `prove_transaction` | Add Orchard proofs |
| `get_sighash` | Get signature hash for input |
| `append_signature` | Add signature to PCZT |
| `combine` | Merge multiple PCZTs |
| `finalize_and_extract` | Extract transaction bytes |
| `parse` / `serialize` | PCZT serialization |
| `verify_before_signing` | Pre-signing verification |

## License

MIT
