# t2z Kotlin Bindings

Kotlin bindings using JNA to wrap the Rust core library.

**Status**: Not yet implemented.

## Planned Usage

```kotlin
import com.zcash.t2z.*

// Create payment request
val payments = listOf(
    Payment(address = "utest1...", amount = 100000UL)
)
val request = TransactionRequest(payments)

// Build and sign transaction
Pczt.propose(inputs, request).use { pczt ->
    val proved = pczt.prove()
    val sighash = proved.getSighash(0)
    val signature = sign(sighash)
    val signed = proved.appendSignature(0, signature)
    val txBytes = signed.finalize()
}
```

## License

MIT
