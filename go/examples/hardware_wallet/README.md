# Hardware Wallet Integration Example

This example demonstrates how to integrate t2z with hardware wallets using PCZT serialization for secure external signing.

## Overview

Hardware wallets require special handling because private keys never leave the secure device. The PCZT (Partially Constructed Zcash Transaction) format enables this by allowing:

1. **Transaction creation** on a coordinator (hot wallet/PC)
2. **Serialization** of the unsigned PCZT
3. **Transmission** to the hardware wallet (USB/Bluetooth/QR)
4. **Signing** within the secure hardware wallet
5. **Return** of the signed PCZT to coordinator
6. **Finalization** and broadcast by coordinator

## Workflow Diagram

```
┌─────────────┐                           ┌──────────────────┐
│ Coordinator │                           │ Hardware Wallet  │
│   (PC)      │                           │  (Secure Device) │
└─────────────┘                           └──────────────────┘
      │                                            │
      │ 1. Create unsigned PCZT                   │
      ├──────────────────────────────────────────►│
      │    (Serialize & transmit)                 │
      │                                            │
      │                                  2. Parse PCZT
      │                                  3. Get sighash
      │                                  4. Display to user
      │                                  5. Sign with secure key
      │                                  6. Serialize signed PCZT
      │                                            │
      │ 7. Return signed PCZT                     │
      │◄──────────────────────────────────────────┤
      │                                            │
      │ 8. Parse & finalize                       │
      │ 9. Broadcast to network                   │
      │                                            │
```

## Running the Example

```bash
# From the go/ directory
cd examples/hardware_wallet

# Build and run
go run main.go
```

## Expected Output

```
=== Hardware Wallet Integration Example ===

📱 COORDINATOR: Creating payment request...
   ✓ Payment to: tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma
   ✓ Amount: 50000 zatoshis (0.0005 ZEC)

...

🔐 HARDWARE WALLET: [Display on screen]
   ┌─────────────────────────────────┐
   │ Sign Transaction?               │
   │ Sighash: 20ed64a8...            │
   │ [Confirm] [Reject]              │
   └─────────────────────────────────┘
👤 USER: [Presses CONFIRM button]

...

✅ SUCCESS: Hardware wallet signing complete!
```

## Key Functions Used

### Non-Consuming Functions (Safe for Hardware Wallet)
These functions can be used multiple times without consuming the PCZT:

- **`GetSighash(pczt, inputIndex)`** - Get signature hash for signing
- **`Serialize(pczt)`** - Serialize PCZT for transmission

### Consuming Functions (Use Once)
These functions consume the input PCZT:

- **`ProveTransaction(pczt)`** - Add Orchard proofs
- **`AppendSignature(pczt, index, sig)`** - Add signature
- **`FinalizeAndExtract(pczt)`** - Extract final transaction

## Integration Tips

### For Hardware Wallet Manufacturers

1. **Parse incoming PCZT**: Use `Parse(bytes)` to load the PCZT
2. **Extract sighashes**: Use `GetSighash(pczt, i)` for each input
3. **Display on screen**: Show sighash/amount for user verification
4. **Sign securely**: Use your device's secure signing infrastructure
5. **Append signatures**: Use `AppendSignature(pczt, i, sig)` for each input
6. **Return serialized**: Use `Serialize(pczt)` to send back to coordinator

### For Wallet Software

1. **Create unsigned PCZT**: Use standard transaction creation flow
2. **Serialize for device**: Use `Serialize(pczt)`
3. **Transmit securely**:
   - USB: Direct connection
   - Bluetooth: Encrypted channel
   - QR codes: For air-gapped devices
4. **Parse response**: Use `Parse(bytes)` on returned signed PCZT
5. **Finalize & broadcast**: Use `FinalizeAndExtract(pczt)`

## Security Considerations

✅ **Private keys never leave hardware wallet**
✅ **User confirms transaction on trusted display**
✅ **PCZT format is well-defined and auditable**
✅ **Supports multi-sig and partial signing**

⚠️ **Verify sighash** - Always display sighash to user for verification
⚠️ **Validate amounts** - Check input/output amounts match expectations
⚠️ **Secure transmission** - Use encrypted channels or air-gapped QR codes

## Real-World Usage

Popular hardware wallets supporting PCZT-like formats:
- Ledger (custom formats)
- Trezor (PSBT for Bitcoin)
- Keystone (QR-based air-gapped signing)

For Zcash specifically, this library enables hardware wallet support for:
- ✅ Transparent inputs (P2PKH)
- ✅ Orchard shielded outputs
- ✅ Hardware wallet signing workflows
- ✅ Multi-party signing via `Combine()`

## Next Steps

- See [../basic/](../basic/) for simpler single-wallet example
- Read [ZIP 374](https://zips.z.cash/zip-0374) for PCZT specification
- Check [../../README.md](../../README.md) for full API documentation
