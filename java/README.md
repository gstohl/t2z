# t2z Java Bindings

Java bindings using [JNA](https://github.com/java-native-access/jna) to wrap the Rust core library.

## Installation

```bash
# Build Rust library first
cd ../rust && cargo build --release

# Build and test Java
cd ../java
./gradlew build
./gradlew test
```

## Usage

```java
import com.zcash.t2z.*;
import java.util.List;

// 1. Create payment request
try (TransactionRequest request = new TransactionRequest(List.of(
    new Payment("utest1...", 100_000L)  // unified or transparent address
))) {

    // 2. Create PCZT from transparent UTXOs
    PCZT pczt = T2z.proposeTransaction(List.of(
        new TransparentInput(
            pubkeyBytes,       // 33 bytes compressed
            txidBytes,         // 32 bytes
            0,
            100_000_000L,      // 1 ZEC in zatoshis
            scriptBytes
        )
    ), request);

    // 3. Add Orchard proofs
    PCZT proved = T2z.proveTransaction(pczt);

    // 4. Sign transparent inputs
    byte[] sighash = T2z.getSighash(proved, 0);
    byte[] signature = Signing.signMessage(privateKey, sighash);
    PCZT signed = T2z.appendSignature(proved, 0, signature);

    // 5. Finalize and broadcast
    byte[] txBytes = T2z.finalizeAndExtract(signed);
    // submit txBytes to zcashd/lightwalletd
}
```

## API

See [root README](../README.md) for full API documentation.

| Method | Description |
|--------|-------------|
| `new TransactionRequest(payments)` | Create payment request |
| `T2z.proposeTransaction(inputs, request)` | Create PCZT from inputs |
| `T2z.proveTransaction(pczt)` | Add Orchard proofs |
| `T2z.verifyBeforeSigning(pczt, request, change)` | Verify PCZT integrity |
| `T2z.getSighash(pczt, index)` | Get 32-byte signature hash |
| `T2z.appendSignature(pczt, index, sig)` | Add 64-byte signature |
| `T2z.combine(pczts)` | Merge multiple PCZTs |
| `T2z.finalizeAndExtract(pczt)` | Extract transaction bytes |
| `T2z.parse(bytes)` / `T2z.serialize(pczt)` | PCZT serialization |
| `Signing.signMessage(privKey, hash)` | secp256k1 signing utility |

## Types

```java
// Payment request
public class Payment {
    String address;    // transparent (tm...) or unified (utest1...)
    long amount;       // zatoshis
    String memo;       // optional, for shielded outputs
    String label;      // optional
    String message;    // optional
}

// Transparent input
public class TransparentInput {
    byte[] pubkey;       // 33 bytes compressed secp256k1
    byte[] txid;         // 32 bytes
    int vout;
    long amount;         // zatoshis
    byte[] scriptPubKey; // P2PKH script
}

// Transparent output (for verification)
public class TransparentOutput {
    byte[] scriptPubKey;
    long value;          // zatoshis
}
```

## Memory Management

**Automatic cleanup**: All handles implement `Closeable` and are automatically freed via `Cleaner`. Use try-with-resources for scoped cleanup:

```java
try (TransactionRequest request = new TransactionRequest(payments)) {
    // request is automatically closed when block exits
}
```

**Consuming methods** transfer ownership (input PCZT becomes invalid):
- `proveTransaction`, `appendSignature`, `finalizeAndExtract`, `combine`

**Non-consuming** (read-only):
- `getSighash`, `serialize`, `verifyBeforeSigning`

## Builder Pattern

Use builders for complex objects:

```java
Payment payment = Payment.builder()
    .address("tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma")
    .amount(100_000L)
    .memo("Test payment")
    .build();

TransparentInput input = TransparentInput.builder()
    .pubkey(pubkeyBytes)
    .txid(txidBytes)
    .vout(0)
    .amount(100_000_000L)
    .scriptPubKey(scriptBytes)
    .build();
```

## Dependencies

- **JNA 5.14+** - Java Native Access for FFI
- **Bouncy Castle** - secp256k1 signing (bcprov-jdk18on)

## Gradle

```groovy
dependencies {
    implementation 'com.zcash:t2z:0.1.0'
}
```

## Requirements

- JDK 17+
- Native `libt2z` library in library path

Set library path:
```java
System.setProperty("jna.library.path", "/path/to/rust/target/release");
```

Or via environment:
```bash
export JNA_LIBRARY_PATH=/path/to/rust/target/release
./gradlew test
```

## License

MIT
