# t2z-kotlin

Kotlin/JVM bindings for t2z - enabling transparent Zcash wallets to send shielded Orchard outputs via PCZT ([ZIP 374](https://zips.z.cash/zip-0374)).

## Installation

```kotlin
// build.gradle.kts
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.gstohl:t2z-kotlin:0.2.0")
}
```

Native libraries are bundled for: macOS (arm64/x64), Linux (x64/arm64), Windows (x64/arm64).

## Usage

```kotlin
import com.zcash.t2z.*

// 1. Create payment request
TransactionRequest(listOf(
    Payment(
        address = "u1...",      // unified or transparent address
        amount = 100_000UL      // 0.001 ZEC in zatoshis
    )
)).use { request ->

    // 2. Create PCZT from transparent UTXOs
    val pczt = proposeTransaction(listOf(
        TransparentInput(
            pubkey = pubkeyBytes,       // 33 bytes compressed
            txid = txidBytes,           // 32 bytes
            vout = 0,
            amount = 100_000_000UL,     // 1 ZEC in zatoshis
            scriptPubKey = scriptBytes
        )
    ), request)

    // 3. Add Orchard proofs
    val proved = proveTransaction(pczt)

    // 4. Sign transparent inputs
    val sighash = getSighash(proved, 0)
    val signature = signMessage(privateKey, sighash)
    val signed = appendSignature(proved, 0, signature)

    // 5. Finalize and broadcast
    val txBytes = finalizeAndExtract(signed)
    // submit txBytes to Zcash network
}
```

## API

See the [main repo](https://github.com/gstohl/t2z) for full documentation.

| Function | Description |
|----------|-------------|
| `TransactionRequest(payments)` | Create payment request |
| `proposeTransaction(inputs, request)` | Create PCZT from inputs |
| `proveTransaction(pczt)` | Add Orchard proofs |
| `verifyBeforeSigning(pczt, request, change)` | Verify PCZT integrity |
| `getSighash(pczt, index)` | Get 32-byte signature hash |
| `appendSignature(pczt, index, sig)` | Add 64-byte signature |
| `combine(pczts)` | Merge multiple PCZTs |
| `finalizeAndExtract(pczt)` | Extract transaction bytes |
| `parsePczt(bytes)` / `serializePczt(pczt)` | PCZT serialization |
| `signMessage(privKey, hash)` | secp256k1 signing utility |
| `getPublicKey(privKey)` | Derive compressed public key |
| `calculateFee(inputs, outputs)` | Calculate ZIP-317 fee |

## Types

```kotlin
data class Payment(
    val address: String,        // transparent (t1...) or unified (u1...)
    val amount: ULong,          // zatoshis
    val memo: String? = null    // optional, for shielded outputs
)

data class TransparentInput(
    val pubkey: ByteArray,      // 33 bytes compressed secp256k1
    val txid: ByteArray,        // 32 bytes
    val vout: Int,
    val amount: ULong,          // zatoshis
    val scriptPubKey: ByteArray // P2PKH script
)
```

## Examples

See [examples/](examples/) for complete working examples:

- **zebrad-regtest/** - Local regtest network examples (1-9)
- **zebrad-mainnet/** - Mainnet examples with hardware wallet flow

## Memory

**Automatic cleanup**: All handles implement `Closeable` and are automatically freed via `Cleaner`. Use Kotlin's `use` extension for scoped cleanup:

```kotlin
TransactionRequest(payments).use { request ->
    // request is automatically closed when block exits
}
```

## License

MIT
