# PCZT Library - Rust Implementation with C FFI

This is a Rust library that implements the PCZT (Partially Constructed Zcash Transaction) API for enabling transparent Zcash users to send shielded (Orchard) outputs.

## Features

- Complete implementation of the PCZT workflow as specified in ZIP 374
- C FFI bindings for use in TypeScript, Go, Kotlin, Java, and other languages
- Automatic C header generation via cbindgen
- Support for Orchard shielded outputs
- ZIP 321 payment request parsing
- ZIP 244 signature hashing

## Building

### Prerequisites

- Rust 1.70 or later
- Cargo

### Build the library

```bash
# Build in release mode (generates optimized library)
cargo build --release

# The library files will be in target/release/:
# - libpczt_lib.dylib (macOS)
# - libpczt_lib.so (Linux)
# - pczt_lib.dll (Windows)
# - libpczt_lib.a (static library, all platforms)
```

### Generate C headers

The C header file is automatically generated during the build process and placed in `include/pczt_lib.h`.

```bash
# Just build to generate headers
cargo build
```

## Usage

### From Rust

```rust
use pczt_lib::{propose_transaction, prove_transaction, TransactionRequest, Payment};

// Create a payment request
let payments = vec![
    Payment::new("u1...".to_string(), 100000),
];
let request = TransactionRequest::new(payments);

// Propose a transaction
let pczt = propose_transaction(inputs, request)?;

// Add proofs
let pczt = prove_transaction(pczt)?;

// ... sign and finalize
```

### From C

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

// Check for errors
if (result != Success) {
    char error_msg[512];
    pczt_get_last_error(error_msg, sizeof(error_msg));
    fprintf(stderr, "Error: %s\n", error_msg);
    return 1;
}

// ... use the request

// Free resources
pczt_transaction_request_free(request);
```

## Project Structure

```
rust/
├── src/
│   ├── lib.rs          # Core library implementation
│   ├── ffi.rs          # C FFI bindings
│   ├── types.rs        # Type definitions
│   └── error.rs        # Error types
├── include/            # Generated C headers (created during build)
│   └── pczt_lib.h      # C header file
├── examples/           # Usage examples
│   └── example.c       # C usage example
├── Cargo.toml          # Rust package manifest
├── cbindgen.toml       # C header generation config
└── build.rs            # Build script
```

## API Functions

### Transaction Management

- `pczt_propose_transaction()` - Creates a new PCZT from inputs and payment request
- `pczt_prove_transaction()` - Adds Orchard proofs to the PCZT
- `pczt_get_sighash()` - Gets the signature hash for an input
- `pczt_append_signature()` - Adds a signature for an input
- `pczt_finalize_and_extract()` - Finalizes and extracts transaction bytes

### Serialization

- `pczt_parse()` - Parses PCZT from bytes
- `pczt_serialize()` - Serializes PCZT to bytes

### Memory Management

- `pczt_free()` - Frees a PCZT handle
- `pczt_free_bytes()` - Frees byte buffers
- `pczt_transaction_request_free()` - Frees transaction request

### Error Handling

- `pczt_get_last_error()` - Gets the last error message

## Current Status

This is a skeleton implementation with the complete API surface defined. The following components need to be fully implemented:

- [ ] Creator role implementation
- [ ] Constructor role implementation
- [ ] IO Finalizer role implementation
- [ ] Prover role integration with zcash_proofs
- [ ] ZIP 244 signature hashing
- [ ] Signature verification
- [ ] PCZT combination logic
- [ ] Transaction finalization and extraction

## License

MIT or Apache-2.0

## Contributing

Contributions are welcome! Please ensure all FFI functions maintain memory safety and proper error handling.
