# Go Bindings for t2z Library

Go bindings for the t2z (Transparent to Zcash) library using CGO.

**t2z** enables transparent Zcash wallets (including hardware wallets) to send shielded Orchard outputs using PCZT (Partially Constructed Zcash Transactions) as defined in [ZIP 374](https://zips.z.cash/zip-0374).

**Repository:** https://github.com/gstohl/t2z (private)

## Status

✅ **Complete** - All 8 API functions implemented and tested (9/9 tests passing)

## Features

- **Complete API Coverage**: All 8 required functions implemented
  - `ProposeTransaction` / `ProposeTransactionWithChange`
  - `ProveTransaction`
  - `GetSighash`
  - `AppendSignature`
  - `FinalizeAndExtract`
  - `Parse` / `Serialize`
  - `Combine`
  - `VerifyBeforeSigning`

- **Proper secp256k1 Support**: Uses `decred/secp256k1` for Bitcoin/Zcash-compatible signing
- **Memory-Safe CGO Bindings**: Explicit ownership semantics documented
- **Complete Test Coverage**: 9 tests covering all workflows
- **Hardware Wallet Support**: External signing via `GetSighash` + `AppendSignature`

## Installation

### Prerequisites

Since this is a **private repository**, you need to configure Git authentication:

```bash
# Option 1: SSH (recommended)
# Ensure your SSH key is added to GitHub
ssh -T git@github.com

# Option 2: HTTPS with token
# Set up Git credential helper with personal access token
git config --global credential.helper store

# Configure Go to use private repo
export GOPRIVATE=github.com/gstohl/t2z
# Add to your ~/.bashrc or ~/.zshrc to persist
```

### Building

```bash
# Clone the repository
git clone git@github.com:gstohl/t2z.git
cd t2z

# Build the Rust library first (required for CGO)
cd rust
cargo build --release

# Install Go dependencies
cd ../go
go mod download

# Run tests to verify
go test -v
```

### Using in Your Project

Add to your `go.mod`:

```go
require (
    github.com/gstohl/t2z/go v0.1.0
)
```

Then configure private repo access:

```bash
# Tell Go this is a private repo
go env -w GOPRIVATE=github.com/gstohl/t2z

# Fetch the module
go get github.com/gstohl/t2z/go@main
```

## Quick Start

See the [examples/](examples/) directory for complete working examples:

- **[basic/](examples/basic/)** - Simple transparent→transparent transaction
- **[hardware_wallet/](examples/hardware_wallet/)** - External signing workflow with hardware wallets

### Basic Usage

```go
package main

import (
    t2z "github.com/gstohl/t2z/go"
    "github.com/decred/dcrd/dcrec/secp256k1/v4"
    "github.com/decred/dcrd/dcrec/secp256k1/v4/ecdsa"
)

func main() {
    // 1. Create payment request
    payments := []t2z.Payment{
        {
            Address: "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
            Amount:  100_000, // 0.001 ZEC in zatoshis
        },
    }

    request, _ := t2z.NewTransactionRequest(payments)
    defer request.Free()

    // 2. Add transparent UTXOs to spend
    inputs := []t2z.TransparentInput{
        {
            Pubkey:       pubkeyBytes,     // Your 33-byte compressed pubkey
            TxID:         txidBytes,       // 32-byte transaction ID
            Vout:         0,                // Output index
            Amount:       100_000_000,     // 1 ZEC in zatoshis
            ScriptPubKey: scriptPubKey,    // P2PKH script from address
        },
    }

    // 3. Create PCZT (Partially Constructed Zcash Transaction)
    pczt, _ := t2z.ProposeTransaction(inputs, request)

    // 4. Add Orchard proofs (for shielded outputs)
    proved, _ := t2z.ProveTransaction(pczt)

    // 5. Get signature hash
    sighash, _ := t2z.GetSighash(proved, 0)

    // 6. Sign with secp256k1
    privKey := secp256k1.PrivKeyFromBytes(privateKeyBytes)
    compactSig := ecdsa.SignCompact(privKey, sighash[:], true)
    var signature [64]byte
    copy(signature[:], compactSig[1:]) // Skip recovery ID

    // 7. Append signature
    signed, _ := t2z.AppendSignature(proved, 0, signature)

    // 8. Finalize and extract transaction bytes
    txBytes, _ := t2z.FinalizeAndExtract(signed)

    // 9. Broadcast txBytes to Zcash network
    // ... submit to zcashd or lightwalletd
}
```

## Memory Management

**Important:** Most PCZT functions **consume** their input handles:
- ✅ `ProveTransaction(pczt)` - consumes `pczt`
- ✅ `AppendSignature(pczt, ...)` - consumes `pczt`
- ✅ `FinalizeAndExtract(pczt)` - consumes `pczt`

**Non-consuming functions:**
- ✅ `GetSighash(pczt, ...)` - does NOT consume
- ✅ `Serialize(pczt)` - does NOT consume

Only call `Free()` on PCZT objects that won't be consumed by another function.

## Examples

Complete working examples are available in the [examples/](examples/) directory:

- **[basic/](examples/basic/)** - Simple end-to-end transparent transaction workflow
- **[hardware_wallet/](examples/hardware_wallet/)** - Hardware wallet integration with PCZT serialization

Run an example:
```bash
cd examples/basic
go run main.go
```

### Documentation Examples

The library also includes runnable documentation examples that appear in `go doc`:

```bash
# View all examples
go doc -all

# View specific function examples
go doc NewTransactionRequest
go doc ProposeTransaction
```

## Testing

```bash
# Run all tests
go test -v

# Run tests with coverage
go test -cover

# Run examples as tests
go test -v -run Example
```

## Dependencies

```go
require (
    github.com/btcsuite/btcd/btcec/v2 v2.3.6
    github.com/decred/dcrd/dcrec/secp256k1/v4 v4.0.1
)
```
