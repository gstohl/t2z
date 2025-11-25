# t2z Go Bindings

Go bindings for t2z using CGO.

## Installation

```bash
# Build Rust library first
cd ../rust && cargo build --release

# Run Go tests
cd ../go && go test -v
```

## Usage

```go
import t2z "github.com/gstohl/t2z/go"

// Create request
request, _ := t2z.NewTransactionRequest([]t2z.Payment{{
    Address: "tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma",
    Amount:  100000,
}})
defer request.Free()

// Create PCZT
pczt, _ := t2z.ProposeTransaction(inputs, request)
proved, _ := t2z.ProveTransaction(pczt)

// Sign
sighash, _ := t2z.GetSighash(proved, 0)
signed, _ := t2z.AppendSignature(proved, 0, signature)

// Finalize
txBytes, _ := t2z.FinalizeAndExtract(signed)
```

## API

| Function | Description |
|----------|-------------|
| `ProposeTransaction` | Create PCZT from inputs |
| `ProveTransaction` | Add Orchard proofs |
| `GetSighash` | Get signature hash |
| `AppendSignature` | Add signature |
| `Combine` | Merge PCZTs |
| `FinalizeAndExtract` | Extract tx bytes |
| `Parse` / `Serialize` | Serialization |
| `VerifyBeforeSigning` | Verification |

## License

MIT
