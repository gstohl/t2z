# Kotlin Bindings for PCZT Library

Kotlin bindings for the PCZT library using JNA (Java Native Access).

## Status

🚧 **Under Development** - Not yet implemented

## Planned Implementation

Using JNA for simpler interface compared to JNI:

```kotlin
package com.zcash.pczt

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

interface PcztLibrary : Library {
    companion object {
        val INSTANCE = Native.load("pczt_lib", PcztLibrary::class.java) as PcztLibrary
    }

    fun pczt_transaction_request_new(
        payments: Pointer,
        numPayments: Int,
        requestOut: Pointer
    ): Int

    fun pczt_propose_transaction(
        inputs: Pointer,
        numInputs: Int,
        request: Pointer,
        pcztOut: Pointer
    ): Int

    // ... other functions
}

data class Payment(
    val address: String,
    val amount: ULong,
    val memo: String? = null,
    val label: String? = null,
    val message: String? = null
)

class Pczt private constructor(private val handle: Pointer) : AutoCloseable {

    fun addProofs(): Pczt {
        // Implementation
    }

    fun getSighash(inputIndex: Int): ByteArray {
        // Implementation
    }

    fun appendSignature(inputIndex: Int, signature: ByteArray): Pczt {
        // Implementation
    }

    fun finalize(): ByteArray {
        // Implementation
    }

    override fun close() {
        PcztLibrary.INSTANCE.pczt_free(handle)
    }

    companion object {
        fun propose(inputs: List<TransparentInput>, request: TransactionRequest): Pczt {
            // Implementation
        }
    }
}
```

## Installation (Future)

### Gradle

```kotlin
dependencies {
    implementation("com.zcash:pczt-lib:0.1.0")
}
```

### Maven

```xml
<dependency>
    <groupId>com.zcash</groupId>
    <artifactId>pczt-lib</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Usage Example (Future)

```kotlin
import com.zcash.pczt.*

fun main() {
    // Create payment
    val payment = Payment(
        address = "u1unified_address",
        amount = 100000UL,
        memo = "Payment to Alice"
    )

    // Create request
    val request = TransactionRequest(listOf(payment))

    // Create and process PCZT
    Pczt.propose(inputs, request).use { pczt ->
        val proved = pczt.addProofs()

        val sighash = proved.getSighash(0)
        val signature = signSighash(sighash)

        val signed = proved.appendSignature(0, signature)
        val txBytes = signed.finalize()

        // Use txBytes...
    }
}
```

## TODO

- [ ] Set up Kotlin/JVM project
- [ ] Implement JNA bindings
- [ ] Create Kotlin wrapper classes
- [ ] Add proper resource management (use/AutoCloseable)
- [ ] Error handling with Kotlin Result
- [ ] Write tests
- [ ] Add KDoc documentation
- [ ] Publish to Maven Central
