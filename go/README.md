# Go Bindings for PCZT Library

Go bindings for the PCZT library using cgo.

## Status

🚧 **Under Development** - Not yet implemented

## Planned Implementation

```go
package pczt

/*
#cgo CFLAGS: -I../rust/include
#cgo LDFLAGS: -L../rust/target/release -lpczt_lib
#include "pczt_lib.h"
*/
import "C"
import "unsafe"

type Pczt struct {
    handle *C.PcztHandle
}

type Payment struct {
    Address string
    Amount  uint64
    Memo    *string
    Label   *string
    Message *string
}

func NewTransactionRequest(payments []Payment) (*TransactionRequest, error) {
    // Implementation
}

func ProposePczt(inputs []TransparentInput, request *TransactionRequest) (*Pczt, error) {
    // Implementation
}

// ... other functions
```

## Installation (Future)

```bash
go get github.com/zcash/pczt-lib/go
```

## Usage Example (Future)

```go
package main

import (
    "github.com/zcash/pczt-lib/go"
)

func main() {
    // Create payment
    payment := pczt.Payment{
        Address: "u1unified_address",
        Amount:  100000,
    }

    // Create request
    request, err := pczt.NewTransactionRequest([]pczt.Payment{payment})
    if err != nil {
        panic(err)
    }
    defer request.Free()

    // Create PCZT
    p, err := pczt.ProposePczt(inputs, request)
    if err != nil {
        panic(err)
    }
    defer p.Free()

    // Add proofs
    proved, err := p.AddProofs()
    if err != nil {
        panic(err)
    }
    defer proved.Free()

    // Get sighash and sign
    sighash, err := proved.GetSighash(0)
    if err != nil {
        panic(err)
    }

    signature := signSighash(sighash)

    // Append signature
    signed, err := proved.AppendSignature(0, signature)
    if err != nil {
        panic(err)
    }
    defer signed.Free()

    // Finalize
    txBytes, err := signed.Finalize()
    if err != nil {
        panic(err)
    }

    // Use txBytes...
}
```

## TODO

- [ ] Create cgo bindings
- [ ] Implement Go wrapper types
- [ ] Add proper memory management
- [ ] Error handling
- [ ] Write tests
- [ ] Add documentation
- [ ] Publish module
