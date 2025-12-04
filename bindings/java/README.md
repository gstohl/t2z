# t2z-java

Java bindings for t2z - enabling transparent Zcash wallets to send shielded Orchard outputs via PCZT ([ZIP 374](https://zips.z.cash/zip-0374)).

## Installation

```groovy
// build.gradle
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.gstohl:t2z-java:0.2.0'
}
```

Native libraries are bundled for: macOS (arm64/x64), Linux (x64/arm64), Windows (x64/arm64).

## Usage

```java
import com.zcash.t2z.*;
import java.util.List;

// 1. Create payment request
try (TransactionRequest request = new TransactionRequest(List.of(
    new Payment("u1...", 100_000L)  // unified or transparent address
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
    // submit txBytes to Zcash network
}
```

## API

See the [main repo](https://github.com/gstohl/t2z) for full documentation.

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
| `T2z.parsePczt(bytes)` / `T2z.serializePczt(pczt)` | PCZT serialization |
| `Signing.signMessage(privKey, hash)` | secp256k1 signing utility |
| `Signing.getPublicKey(privKey)` | Derive compressed public key |
| `T2z.calculateFee(inputs, outputs)` | Calculate ZIP-317 fee |

## Types

```java
public class Payment {
    String address;    // transparent (t1...) or unified (u1...)
    long amount;       // zatoshis
    String memo;       // optional, for shielded outputs
}

public class TransparentInput {
    byte[] pubkey;       // 33 bytes compressed secp256k1
    byte[] txid;         // 32 bytes
    int vout;
    long amount;         // zatoshis
    byte[] scriptPubKey; // P2PKH script
}
```

## Examples

See [examples/](examples/) for complete working examples:

- **zebrad-regtest/** - Local regtest network examples (1-9)
- **zebrad-mainnet/** - Mainnet examples with hardware wallet flow

## Memory

**Automatic cleanup**: All handles implement `Closeable` and are automatically freed via `Cleaner`. Use try-with-resources for scoped cleanup:

```java
try (TransactionRequest request = new TransactionRequest(payments)) {
    // request is automatically closed when block exits
}
```

## License

MIT
