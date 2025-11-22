# PCZT Library - Multi-Language Implementation

A comprehensive library for enabling transparent Zcash users to send shielded (Orchard) outputs using the PCZT (Partially Constructed Zcash Transaction) API as defined in ZIP 374.

## Project Structure

```
pczt-lib/
├── rust/              # Core Rust library with C FFI bindings
│   ├── src/
│   │   ├── lib.rs     # Core Rust implementation
│   │   ├── ffi.rs     # C FFI bindings
│   │   ├── types.rs   # Type definitions
│   │   └── error.rs   # Error types
│   ├── include/       # Generated C headers
│   │   └── pczt_lib.h
│   └── examples/      # C usage examples
├── typescript/        # TypeScript/Node.js bindings (TODO)
├── kotlin/           # Kotlin/JVM bindings (TODO)
├── java/             # Java bindings (TODO)
├── go/               # Go bindings (TODO)
└── REQ.md            # Original requirements specification
```

## Features

- **Core Rust Library**: High-performance implementation with memory safety guarantees
- **C FFI Interface**: Stable ABI for cross-language compatibility
- **Auto-generated Headers**: C/C++ headers generated via cbindgen
- **Multiple Language Support**: Bindings for TypeScript, Go, Kotlin, and Java

## API Overview

The library implements the complete PCZT workflow:

1. **propose_transaction** - Creates PCZT from transparent inputs and payment request
2. **prove_transaction** - Adds Orchard proofs (must use Rust prover)
3. **verify_before_signing** - Optional pre-signing verification
4. **get_sighash** - Obtains signature hash for inputs
5. **append_signature** - Adds signatures to PCZT
6. **combine** - Combines multiple PCZTs
7. **finalize_and_extract** - Finalizes and extracts transaction bytes
8. **parse_pczt** / **serialize_pczt** - Serialization for transmission

## Getting Started

### Building the Rust Library

```bash
cd rust
cargo build --release
```

The built libraries will be in `rust/target/release/`:
- `libpczt_lib.dylib` (macOS)
- `libpczt_lib.so` (Linux)
- `pczt_lib.dll` (Windows)
- `libpczt_lib.a` (static library)

### C Header File

The C header is automatically generated during build at `rust/include/pczt_lib.h`.

## Language Bindings

### TypeScript (Node.js)

Coming soon. Will use node-ffi or napi-rs to interface with the C library.

**Location**: `typescript/`

### Go

Coming soon. Will use cgo to interface with the C library.

**Location**: `go/`

### Kotlin

Coming soon. Will use JNI or JNA to interface with the C library.

**Location**: `kotlin/`

### Java

Coming soon. Will use JNI to interface with the C library.

**Location**: `java/`

## Usage Example (C)

```c
#include "pczt_lib.h"

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
ResultCode result = pczt_transaction_request_new(payments, 1, &request);

// Propose transaction
PcztHandle* pczt = NULL;
result = pczt_propose_transaction(inputs, num_inputs, request, &pczt);

// Add proofs
PcztHandle* proved_pczt = NULL;
result = pczt_prove_transaction(pczt, &proved_pczt);

// Get sighash and sign
uint8_t sighash[32];
result = pczt_get_sighash(proved_pczt, 0, &sighash);

// ... sign the sighash ...

// Append signature
PcztHandle* signed_pczt = NULL;
result = pczt_append_signature(proved_pczt, 0, signature, &signed_pczt);

// Finalize and extract
uint8_t* tx_bytes = NULL;
size_t tx_bytes_len = 0;
result = pczt_finalize_and_extract(signed_pczt, &tx_bytes, &tx_bytes_len);

// Cleanup
pczt_free_bytes(tx_bytes, tx_bytes_len);
pczt_transaction_request_free(request);
```

## Development Status

### Completed ✅

- [x] Rust project structure
- [x] Core library API skeleton
- [x] C FFI bindings
- [x] C header generation
- [x] Error handling framework
- [x] Type definitions
- [x] Build system setup

### In Progress 🚧

- [ ] Creator role implementation
- [ ] Constructor role implementation
- [ ] IO Finalizer implementation
- [ ] Prover role integration
- [ ] ZIP 244 signature hashing
- [ ] Signature verification
- [ ] PCZT combination logic
- [ ] Transaction finalization

### Planned 📋

- [ ] TypeScript/Node.js bindings
- [ ] Go bindings
- [ ] Kotlin bindings
- [ ] Java bindings
- [ ] Comprehensive test suite
- [ ] Documentation
- [ ] Examples for each language

## Dependencies

### Rust

- `pczt` - PCZT implementation from librustzcash
- `zcash_primitives` - Core Zcash primitives
- `zcash_proofs` - Proof generation
- `orchard` - Orchard protocol implementation
- `zip321` - Payment request parsing

## Contributing

Contributions are welcome! Areas that need work:

1. Complete the TODO sections in the Rust implementation
2. Implement language-specific bindings
3. Add comprehensive tests
4. Improve documentation
5. Add usage examples

## License

MIT or Apache-2.0

## References

- [ZIP 374: PCZT Specification](https://zips.z.cash/zip-0374)
- [ZIP 321: Payment Request URIs](https://zips.z.cash/zip-0321)
- [ZIP 244: Transaction Signature Validation](https://zips.z.cash/zip-0244)
- [librustzcash](https://github.com/zcash/librustzcash)
