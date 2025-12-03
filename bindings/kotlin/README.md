# t2z Kotlin Bindings

Kotlin/JVM bindings using [JNA](https://github.com/java-native-access/jna) to wrap the Rust core library.

## Installation

```bash
# Build Rust library first
cd ../../core/rust && cargo build --release

# Build and test Kotlin
cd ../../bindings/kotlin
./gradlew build
./gradlew test
```

## Usage

```kotlin
import com.zcash.t2z.*

// 1. Create payment request
TransactionRequest(listOf(
    Payment(
        address = "utest1...",  // unified or transparent address
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
    // submit txBytes to zcashd/lightwalletd
}
```

## API

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

## Types

```kotlin
data class Payment(
    val address: String,    // transparent (tm...) or unified (utest1...)
    val amount: ULong,      // zatoshis
    val memo: String? = null,
    val label: String? = null,
    val message: String? = null
)

data class TransparentInput(
    val pubkey: ByteArray,      // 33 bytes compressed secp256k1
    val txid: ByteArray,        // 32 bytes
    val vout: Int,
    val amount: ULong,          // zatoshis
    val scriptPubKey: ByteArray // P2PKH script
)

data class TransparentOutput(
    val scriptPubKey: ByteArray,
    val value: ULong            // zatoshis
)
```

## Memory Management

**Automatic cleanup**: All handles implement `Closeable` and are automatically freed via `Cleaner`. Use Kotlin's `use` extension for scoped cleanup:

```kotlin
TransactionRequest(payments).use { request ->
    // request is automatically closed when block exits
}
```

**Consuming functions** transfer ownership (input PCZT becomes invalid):
- `proveTransaction`, `appendSignature`, `finalizeAndExtract`, `combine`

**Non-consuming** (read-only):
- `getSighash`, `serialize`, `verifyBeforeSigning`

## Dependencies

- **JNA 5.14+** - Java Native Access for FFI
- **secp256k1-kmp** - Kotlin Multiplatform secp256k1 bindings (ACINQ)

## Gradle

```kotlin
dependencies {
    implementation("com.zcash:t2z:0.1.0")
}
```

## Requirements

- JDK 17+
- Native `libt2z` library in library path

Set library path:
```kotlin
System.setProperty("jna.library.path", "/path/to/core/rust/target/release")
```

Or via environment:
```bash
export JNA_LIBRARY_PATH=/path/to/core/rust/target/release
./gradlew test
```

## License

MIT
