# Go Bindings for t2z Library

Go bindings for the t2z (Transparent to Zcash) library using CGO.

**t2z** enables transparent Zcash wallets (including hardware wallets) to send shielded Orchard outputs using PCZT (Partially Constructed Zcash Transactions) as defined in [ZIP 374](https://zips.z.cash/zip-0374).

**Repository:** https://github.com/gstohl/t2z (private)

## Status

All API functions implemented and tested

## Features

- **Complete API Coverage**: All required functions implemented
  - `ProposeTransaction` / `ProposeTransactionWithChange`
  - `ProveTransaction`
  - `VerifyBeforeSigning`
  - `GetSighash`
  - `AppendSignature`
  - `FinalizeAndExtract`
  - `Parse` / `Serialize`

- **Native Performance**: CGO bindings to Rust core library
- **Type Safety**: Full Go type definitions
- **Memory Safe**: Explicit ownership semantics documented
- **Hardware Wallet Support**: External signing via `GetSighash` + `AppendSignature`

## Installation

### Prerequisites

This is a **private repository**. Configure Git authentication:

```bash
# Option 1: SSH (recommended)
ssh -T git@github.com

# Option 2: HTTPS with token
git config --global credential.helper store
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

# Run tests
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

- **[basic/](examples/basic/)** - Simple transparent->transparent transaction
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
            Vout:         0,               // Output index
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

## API Reference

### Types

#### `Payment`

```go
type Payment struct {
    // Address can be a transparent address (starts with 't')
    // or a unified address with Orchard receiver (starts with 'u')
    Address string

    // Amount in zatoshis (1 ZEC = 100,000,000 zatoshis)
    Amount uint64

    // Optional memo for shielded outputs (max 512 bytes)
    Memo string

    // Optional label for the recipient
    Label string

    // Optional message
    Message string
}
```

#### `TransparentInput`

```go
type TransparentInput struct {
    // Pubkey is the compressed secp256k1 public key (33 bytes)
    Pubkey []byte

    // TxID is the transaction ID of the UTXO being spent (32 bytes)
    TxID [32]byte

    // Vout is the output index in the previous transaction
    Vout uint32

    // Amount in zatoshis
    Amount uint64

    // ScriptPubKey is the script of the UTXO being spent
    ScriptPubKey []byte
}
```

#### `TransparentOutput`

```go
type TransparentOutput struct {
    // ScriptPubKey is the P2PKH script of the output (raw bytes, no CompactSize prefix)
    ScriptPubKey []byte

    // Value in zatoshis
    Value uint64
}
```

#### `TransactionRequest`

```go
type TransactionRequest struct {
    Payments []Payment
}

// Create a new transaction request
func NewTransactionRequest(payments []Payment) (*TransactionRequest, error)

// Create with specific target block height
func NewTransactionRequestWithTargetHeight(payments []Payment, targetHeight uint32) (*TransactionRequest, error)

// Set the target block height for consensus branch ID selection
func (r *TransactionRequest) SetTargetHeight(height uint32) error

// Free the transaction request
func (r *TransactionRequest) Free()
```

#### `PCZT`

```go
type PCZT struct {
    // opaque handle
}

// Free the PCZT handle
// Note: Most operations consume the PCZT
func (p *PCZT) Free()
```

### Functions

#### `ProposeTransaction()`

Create a PCZT from transparent inputs and transaction request.

```go
func ProposeTransaction(inputs []TransparentInput, request *TransactionRequest) (*PCZT, error)
```

#### `ProposeTransactionWithChange()`

Create a PCZT with explicit change handling.

```go
func ProposeTransactionWithChange(inputs []TransparentInput, request *TransactionRequest, changeAddress string) (*PCZT, error)
```

#### `ProveTransaction()`

Add Orchard proofs to the PCZT. **Consumes the input PCZT**.

```go
func ProveTransaction(pczt *PCZT) (*PCZT, error)
```

#### `VerifyBeforeSigning()`

Verify the PCZT before signing. Does NOT consume the PCZT.

```go
func VerifyBeforeSigning(pczt *PCZT, request *TransactionRequest, expectedChange []TransparentOutput) error
```

#### `GetSighash()`

Get signature hash for a transparent input. Does NOT consume the PCZT.

```go
func GetSighash(pczt *PCZT, inputIndex uint) ([32]byte, error)
```

#### `AppendSignature()`

Append an external signature to the PCZT. **Consumes the input PCZT**.

```go
func AppendSignature(pczt *PCZT, inputIndex uint, signature [64]byte) (*PCZT, error)
```

#### `FinalizeAndExtract()`

Finalize the PCZT and extract transaction bytes. **Consumes the input PCZT**.

```go
func FinalizeAndExtract(pczt *PCZT) ([]byte, error)
```

#### `Serialize()`

Serialize PCZT to bytes. Does NOT consume the PCZT.

```go
func Serialize(pczt *PCZT) ([]byte, error)
```

#### `Parse()`

Parse PCZT from bytes.

```go
func Parse(pcztBytes []byte) (*PCZT, error)
```

## Memory Management

**Important:** Most PCZT functions **consume** their input handles:

- `ProveTransaction(pczt)` - consumes `pczt`
- `AppendSignature(pczt, ...)` - consumes `pczt`
- `FinalizeAndExtract(pczt)` - consumes `pczt`

**Non-consuming functions:**

- `GetSighash(pczt, ...)` - does NOT consume
- `Serialize(pczt)` - does NOT consume
- `VerifyBeforeSigning(pczt, ...)` - does NOT consume

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

## Error Handling

All functions return standard Go errors on failure:

```go
pczt, err := t2z.ProposeTransaction(inputs, request)
if err != nil {
    log.Fatalf("Failed to propose transaction: %v", err)
}
```

Error codes include:

- `ErrorProposal`: Invalid transaction request
- `ErrorProver`: Failed to generate proofs
- `ErrorVerification`: Verification failed
- `ErrorSighash`: Failed to compute sighash
- `ErrorSignature`: Signature verification failed
- `ErrorFinalization`: Failed to finalize transaction
- `ErrorParse`: Invalid PCZT format

## Performance

- **Native Speed**: C-level performance via Rust FFI
- **Zero-Copy**: Efficient buffer handling where possible
- **Lazy Loading**: Proving keys loaded on first use
- **Minimal Overhead**: CGO bindings add negligible latency

## Security

- **Memory Safety**: Rust's ownership system prevents memory bugs
- **Type Safety**: Go's type system catches errors at compile time
- **Input Validation**: All inputs validated at FFI boundary
- **No Unsafe Code**: Minimal unsafe blocks, all audited

## Troubleshooting

### Build Errors

If you encounter build errors:

```bash
# Ensure Rust library is built
cd ../rust && cargo build --release

# Clean and rebuild Go
cd ../go
go clean -cache
go build
```

### Runtime Errors

**"Invalid signature"**: Ensure you're using secp256k1, not P-256 or other curves. Use `github.com/decred/dcrd/dcrec/secp256k1/v4` for signing.

**"Invalid script"**: Ensure P2PKH script includes OP_PUSHDATA1 (0x19) prefix (26 bytes total).

## Dependencies

```go
require (
    github.com/decred/dcrd/dcrec/secp256k1/v4 v4.0.1
)
```

## Contributing

See [CONTRIBUTING.md](../CONTRIBUTING.md) for guidelines.

## License

MIT License - See [LICENSE](../LICENSE) for details.

## Links

- [GitHub Repository](https://github.com/gstohl/t2z)
- [ZIP 374: PCZT Specification](https://zips.z.cash/zip-0374)
- [Zcash Documentation](https://zcash.readthedocs.io/)
