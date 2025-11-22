# Java Bindings for PCZT Library

Java bindings for the PCZT library using JNI (Java Native Interface).

## Status

🚧 **Under Development** - Not yet implemented

## Planned Implementation

### Option 1: Using JNA (Simpler)

```java
package com.zcash.pczt;

import com.sun.jna.*;

public interface PcztLibrary extends Library {
    PcztLibrary INSTANCE = Native.load("pczt_lib", PcztLibrary.class);

    int pczt_transaction_request_new(
        Pointer payments,
        int numPayments,
        PointerByReference requestOut
    );

    int pczt_propose_transaction(
        Pointer inputs,
        int numInputs,
        Pointer request,
        PointerByReference pcztOut
    );

    // ... other functions
}

public class Payment {
    private final String address;
    private final long amount;
    private final String memo;
    private final String label;
    private final String message;

    public Payment(String address, long amount) {
        this(address, amount, null, null, null);
    }

    // ... constructors and getters
}

public class Pczt implements AutoCloseable {
    private Pointer handle;

    public Pczt addProofs() throws PcztException {
        // Implementation
    }

    public byte[] getSighash(int inputIndex) throws PcztException {
        // Implementation
    }

    public Pczt appendSignature(int inputIndex, byte[] signature) throws PcztException {
        // Implementation
    }

    public byte[] finalize() throws PcztException {
        // Implementation
    }

    @Override
    public void close() {
        if (handle != null) {
            PcztLibrary.INSTANCE.pczt_free(handle);
            handle = null;
        }
    }

    public static Pczt propose(
        List<TransparentInput> inputs,
        TransactionRequest request
    ) throws PcztException {
        // Implementation
    }
}
```

### Option 2: Using JNI (More Control)

Native implementation with better performance but more complex setup.

## Installation (Future)

### Gradle

```groovy
dependencies {
    implementation 'com.zcash:pczt-lib:0.1.0'
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

```java
import com.zcash.pczt.*;

public class Example {
    public static void main(String[] args) {
        // Create payment
        Payment payment = new Payment(
            "u1unified_address",
            100000L,
            "Payment to Alice"
        );

        // Create request
        List<Payment> payments = Arrays.asList(payment);
        TransactionRequest request = new TransactionRequest(payments);

        try (Pczt pczt = Pczt.propose(inputs, request)) {
            // Add proofs
            Pczt proved = pczt.addProofs();

            // Get sighash
            byte[] sighash = proved.getSighash(0);

            // Sign
            byte[] signature = signSighash(sighash);

            // Append signature
            Pczt signed = proved.appendSignature(0, signature);

            // Finalize
            byte[] txBytes = signed.finalize();

            // Use txBytes...
        } catch (PcztException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static byte[] signSighash(byte[] sighash) {
        // Signing implementation
        return new byte[64];
    }
}
```

## TODO

- [ ] Choose JNA vs JNI approach
- [ ] Create Java wrapper classes
- [ ] Implement proper exception handling
- [ ] Add try-with-resources support
- [ ] Write unit tests
- [ ] Add Javadoc documentation
- [ ] Set up Maven/Gradle build
- [ ] Publish to Maven Central
