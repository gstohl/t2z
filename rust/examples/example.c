/*
 * Example C program demonstrating PCZT library usage
 *
 * Compile with:
 *   gcc -o example example.c -L../target/release -lt2z -I../include
 *
 * Run with:
 *   LD_LIBRARY_PATH=../target/release ./example
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../include/t2z.h"

void print_error(const char* operation) {
    char error_msg[512];
    ResultCode result = pczt_get_last_error(error_msg, sizeof(error_msg));
    if (result == Success) {
        fprintf(stderr, "Error during %s: %s\n", operation, error_msg);
    } else {
        fprintf(stderr, "Error during %s (failed to get error message)\n", operation);
    }
}

int main() {
    printf("PCZT Library Example\n");
    printf("====================\n\n");

    // Step 1: Create a payment request
    printf("Step 1: Creating payment request...\n");

    CPayment payments[] = {
        {
            .address = "u1unified_address_example_1234567890abcdef",
            .amount = 100000,  // 0.001 ZEC in zatoshis
            .memo = "Payment to Alice",
            .label = "Alice",
            .message = "Thanks for the coffee!"
        },
        {
            .address = "t1transparent_address_example",
            .amount = 50000,   // 0.0005 ZEC
            .memo = NULL,
            .label = "Bob",
            .message = NULL
        }
    };

    TransactionRequestHandle* request = NULL;
    ResultCode result = pczt_transaction_request_new(payments, 2, &request);

    if (result != Success) {
        print_error("transaction request creation");
        return 1;
    }
    printf("  ✓ Transaction request created\n\n");

    // Step 2: Create a PCZT from inputs
    printf("Step 2: Proposing transaction...\n");

    // In a real implementation, you would provide actual transparent inputs here
    CTransparentInput inputs[] = {
        // Example input structure (would need real data)
        {
            .prevout_hash = {0},  // 32-byte transaction hash
            .prevout_index = 0,
            .script_pub_key = NULL,
            .script_pub_key_len = 0,
            .value = 200000  // Input value
        }
    };

    PcztHandle* pczt = NULL;
    result = pczt_propose_transaction(inputs, 1, request, &pczt);

    if (result != Success) {
        print_error("transaction proposal");
        pczt_transaction_request_free(request);
        return 1;
    }
    printf("  ✓ Transaction proposed\n\n");

    // Step 3: Add proofs (for shielded outputs)
    printf("Step 3: Adding Orchard proofs...\n");

    PcztHandle* proved_pczt = NULL;
    result = pczt_prove_transaction(pczt, &proved_pczt);

    if (result != Success) {
        print_error("proof generation");
        pczt_free(pczt);
        pczt_transaction_request_free(request);
        return 1;
    }
    printf("  ✓ Proofs added\n\n");

    // Step 4: Get signature hashes and sign
    printf("Step 4: Signing transaction...\n");

    unsigned char sighash[32];
    result = pczt_get_sighash(proved_pczt, 0, (unsigned char(*)[32])sighash);

    if (result != Success) {
        print_error("sighash calculation");
        pczt_free(proved_pczt);
        pczt_transaction_request_free(request);
        return 1;
    }

    printf("  Sighash for input 0: ");
    for (int i = 0; i < 32; i++) {
        printf("%02x", sighash[i]);
    }
    printf("\n");

    // In a real implementation, you would sign the sighash here
    unsigned char signature[64] = {0};  // Placeholder signature

    PcztHandle* signed_pczt = NULL;
    result = pczt_append_signature(proved_pczt, 0, (unsigned char(*)[64])signature, &signed_pczt);

    if (result != Success) {
        print_error("signature append");
        pczt_free(proved_pczt);
        pczt_transaction_request_free(request);
        return 1;
    }
    printf("  ✓ Signature added\n\n");

    // Step 5: Finalize and extract transaction
    printf("Step 5: Finalizing transaction...\n");

    unsigned char* tx_bytes = NULL;
    size_t tx_bytes_len = 0;
    result = pczt_finalize_and_extract(signed_pczt, &tx_bytes, &tx_bytes_len);

    if (result != Success) {
        print_error("finalization");
        pczt_transaction_request_free(request);
        return 1;
    }

    printf("  ✓ Transaction finalized\n");
    printf("  Transaction size: %zu bytes\n\n", tx_bytes_len);

    // Step 6: Demonstrate serialization
    printf("Step 6: Testing serialization...\n");

    // Serialize the original PCZT
    unsigned char* serialized = NULL;
    size_t serialized_len = 0;
    result = pczt_serialize(proved_pczt, &serialized, &serialized_len);

    if (result != Success) {
        print_error("serialization");
        pczt_free_bytes(tx_bytes, tx_bytes_len);
        pczt_transaction_request_free(request);
        return 1;
    }
    printf("  Serialized PCZT size: %zu bytes\n", serialized_len);

    // Parse it back
    PcztHandle* parsed_pczt = NULL;
    result = pczt_parse(serialized, serialized_len, &parsed_pczt);

    if (result != Success) {
        print_error("parsing");
        pczt_free_bytes(serialized, serialized_len);
        pczt_free_bytes(tx_bytes, tx_bytes_len);
        pczt_transaction_request_free(request);
        return 1;
    }
    printf("  ✓ PCZT serialization/parsing successful\n\n");

    // Cleanup
    printf("Cleaning up resources...\n");
    pczt_free(parsed_pczt);
    pczt_free_bytes(serialized, serialized_len);
    pczt_free_bytes(tx_bytes, tx_bytes_len);
    pczt_transaction_request_free(request);
    printf("  ✓ All resources freed\n\n");

    printf("Example completed successfully!\n");
    return 0;
}
