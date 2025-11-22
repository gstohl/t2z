# t2z - Transparent to Shielded

Multi-language library for sending transparent Zcash to shielded (Orchard) outputs using PCZT (ZIP 374).

## Project Structure

```
t2z/
├── rust/              # Core Rust library with C FFI bindings
│   ├── src/
│   │   ├── lib.rs     # Core Rust implementation
│   │   ├── ffi.rs     # C FFI bindings
│   │   ├── types.rs   # Type definitions
│   │   └── error.rs   # Error types
│   ├── include/       # Generated C headers
│   │   └── t2z.h
│   └── examples/      # C usage examples
├── typescript/        # TypeScript/Node.js bindings (TODO)
├── kotlin/           # Kotlin/JVM bindings (TODO)
├── java/             # Java bindings (TODO)
├── go/               # Go bindings (TODO)
└── REQUIREMENTS.md   # Requirements specification
```

## Features

- **Rust Core**: High-performance with memory safety
- **C FFI**: Stable ABI for language bindings
- **Auto-generated Headers**: C/C++ via cbindgen
- **Multi-language**: TypeScript, Go, Kotlin, Java

## API

1. **propose_transaction** - Create PCZT from transparent inputs and payment request
2. **prove_transaction** - Add Orchard proofs
3. **verify_before_signing** - Optional pre-signing verification
4. **get_sighash** - Get signature hash for inputs
5. **append_signature** - Add signatures to PCZT
6. **combine** - Combine multiple PCZTs
7. **finalize_and_extract** - Finalize and extract transaction bytes
8. **parse_pczt** / **serialize_pczt** - Serialize/deserialize PCZT

## Build

```bash
cd rust
cargo build --release
```

Output: `rust/target/release/`
- `libt2z.dylib` (macOS)
- `libt2z.so` (Linux)
- `t2z.dll` (Windows)
- `libt2z.a` (static)

Header: `rust/include/t2z.h`

## Language Bindings

- **TypeScript**: node-ffi or napi-rs (TODO)
- **Go**: cgo (TODO)
- **Kotlin**: JNI/JNA (TODO)
- **Java**: JNI (TODO)

## Usage

```c
#include "t2z.h"

// Create payments
CPayment payments[] = {
    {
        .address = "u1unified_address",
        .amount = 100000,
        .memo = NULL,
        .label = NULL,
        .message = NULL
    }
};

// Create transaction request
TransactionRequestHandle* request = NULL;
ResultCode result = t2z_transaction_request_new(payments, 1, &request);

// Propose transaction
PcztHandle* pczt = NULL;
result = t2z_propose_transaction(inputs, num_inputs, request, &pczt);

// Add proofs
PcztHandle* proved_pczt = NULL;
result = t2z_prove_transaction(pczt, &proved_pczt);

// Get sighash and sign
uint8_t sighash[32];
result = t2z_get_sighash(proved_pczt, 0, &sighash);

// ... sign the sighash ...

// Append signature
PcztHandle* signed_pczt = NULL;
result = t2z_append_signature(proved_pczt, 0, signature, &signed_pczt);

// Finalize and extract
uint8_t* tx_bytes = NULL;
size_t tx_bytes_len = 0;
result = t2z_finalize_and_extract(signed_pczt, &tx_bytes, &tx_bytes_len);

// Cleanup
t2z_free_bytes(tx_bytes, tx_bytes_len);
t2z_transaction_request_free(request);
```

## Status

### Completed

- [x] Project structure
- [x] API skeleton
- [x] C FFI bindings
- [x] Header generation
- [x] Error handling
- [x] Build system

### In Progress

- [ ] Creator role
- [ ] Constructor role
- [ ] IO Finalizer
- [ ] Prover integration
- [ ] ZIP 244 sighash
- [ ] Signature verification
- [ ] PCZT combination
- [ ] Transaction finalization

### Planned

- [ ] Language bindings
- [ ] Test suite
- [ ] Documentation
- [ ] Examples

## Dependencies

- `pczt` - PCZT implementation
- `zcash_primitives` - Core primitives
- `zcash_proofs` - Proof generation
- `orchard` - Orchard protocol
- `zip321` - Payment requests

## License

MIT or Apache-2.0

## References

- [ZIP 374: PCZT Specification](https://zips.z.cash/zip-0374)
- [ZIP 321: Payment Request URIs](https://zips.z.cash/zip-0321)
- [ZIP 244: Transaction Signature Validation](https://zips.z.cash/zip-0244)
- [librustzcash](https://github.com/zcash/librustzcash)
