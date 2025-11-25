# t2z Java Bindings

Java bindings using JNA to wrap the Rust core library.

**Status**: Not yet implemented.

## Planned Usage

```java
import com.zcash.t2z.*;

// Create payment request
List<Payment> payments = List.of(
    new Payment("utest1...", 100000L)
);
TransactionRequest request = new TransactionRequest(payments);

// Build and sign transaction
try (Pczt pczt = Pczt.propose(inputs, request)) {
    Pczt proved = pczt.prove();
    byte[] sighash = proved.getSighash(0);
    byte[] signature = sign(sighash);
    Pczt signed = proved.appendSignature(0, signature);
    byte[] txBytes = signed.finalize();
}
```

## License

MIT
