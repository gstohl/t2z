/*
 * t2z - Transparent to Zcash Library
 *
 * This header provides FFI bindings for the t2z library, enabling transparent
 * Zcash users to send shielded outputs using PCZT (Partially Constructed Zcash Transaction).
 *
 * Generated automatically by cbindgen.
 */


/* Generated with cbindgen:0.26.0 */

#include <stdarg.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>

#ifdef __cplusplus
namespace t2z {
#endif // __cplusplus

/**
 * Result code for FFI functions
 */
typedef enum ResultCode {
  SUCCESS = 0,
  ERROR_NULL_POINTER = 1,
  ERROR_INVALID_UTF8 = 2,
  ERROR_BUFFER_TOO_SMALL = 3,
  ERROR_PROPOSAL = 10,
  ERROR_PROVER = 11,
  ERROR_VERIFICATION = 12,
  ERROR_SIGHASH = 13,
  ERROR_SIGNATURE = 14,
  ERROR_COMBINE = 15,
  ERROR_FINALIZATION = 16,
  ERROR_PARSE = 17,
  ERROR_NOT_IMPLEMENTED = 99,
} ResultCode;

/**
 * C-compatible payment structure
 */
typedef struct CPayment {
  const char *address;
  uint64_t amount;
  const char *memo;
  const char *label;
  const char *message;
} CPayment;

/**
 * Opaque handle to a TransactionRequest object
 */
typedef struct TransactionRequestHandle {
  uint8_t _private[0];
} TransactionRequestHandle;

/**
 * Opaque handle to a PCZT object
 */
typedef struct PcztHandle {
  uint8_t _private[0];
} PcztHandle;

/**
 * C-compatible transaction output
 */
typedef struct CTransparentOutput {
  const unsigned char *script_pub_key;
  uintptr_t script_pub_key_len;
  uint64_t value;
} CTransparentOutput;

#ifdef __cplusplus
extern "C" {
#endif // __cplusplus

/**
 * Gets the last error message
 */

enum ResultCode pczt_get_last_error(char *aBuffer,
                                    uintptr_t aBufferLen)
;

/**
 * Creates a new transaction request
 */

enum ResultCode pczt_transaction_request_new(const struct CPayment *aPayments,
                                             uintptr_t aNumPayments,
                                             struct TransactionRequestHandle **aRequestOut)
;

/**
 * Frees a transaction request
 */

void pczt_transaction_request_free(struct TransactionRequestHandle *aRequest)
;

/**
 * Sets the target height for a transaction request
 */

enum ResultCode pczt_transaction_request_set_target_height(struct TransactionRequestHandle *aRequest,
                                                           uint32_t aTargetHeight)
;

/**
 * Sets whether to use mainnet parameters for consensus branch ID
 *
 * By default, the library uses testnet parameters. Set this to true
 * for mainnet or for regtest networks that use mainnet-like branch IDs.
 */

enum ResultCode pczt_transaction_request_set_use_mainnet(struct TransactionRequestHandle *aRequest,
                                                         bool aUseMainnet)
;

/**
 * Proposes a new transaction using serialized input bytes
 */

enum ResultCode pczt_propose_transaction(const uint8_t *aInputsBytes,
                                         uintptr_t aInputsBytesLen,
                                         const struct TransactionRequestHandle *aRequest,
                                         const char *aChangeAddress,
                                         struct PcztHandle **aPcztOut)
;

/**
 * Adds proofs to a PCZT
 */

enum ResultCode pczt_prove_transaction(struct PcztHandle *aPczt,
                                       struct PcztHandle **aPcztOut)
;

/**
 * Verifies the PCZT before signing
 */

enum ResultCode pczt_verify_before_signing(const struct PcztHandle *aPczt,
                                           const struct TransactionRequestHandle *aRequest,
                                           const struct CTransparentOutput *aExpectedChange,
                                           uintptr_t aExpectedChangeLen)
;

/**
 * Gets the signature hash for an input
 */

enum ResultCode pczt_get_sighash(const struct PcztHandle *aPczt,
                                 uintptr_t aInputIndex,
                                 uint8_t (*aSighashOut)[32])
;

/**
 * Appends a signature to the PCZT
 */

enum ResultCode pczt_append_signature(struct PcztHandle *aPczt,
                                      uintptr_t aInputIndex,
                                      const uint8_t (*aSignature)[64],
                                      struct PcztHandle **aPcztOut)
;

/**
 * Finalizes and extracts the transaction
 */

enum ResultCode pczt_finalize_and_extract(struct PcztHandle *aPczt,
                                          uint8_t **aTxBytesOut,
                                          uintptr_t *aTxBytesLenOut)
;

/**
 * Parses a PCZT from bytes
 */

enum ResultCode pczt_parse(const uint8_t *aPcztBytes,
                           uintptr_t aPcztBytesLen,
                           struct PcztHandle **aPcztOut)
;

/**
 * Serializes a PCZT to bytes
 */

enum ResultCode pczt_serialize(const struct PcztHandle *aPczt,
                               uint8_t **aBytesOut,
                               uintptr_t *aBytesLenOut)
;

/**
 * Combines multiple PCZTs into one
 *
 * This is useful for parallel signing workflows where different parts of the transaction
 * are processed independently and need to be merged.
 *
 * # Arguments
 * * `pczts` - Array of PCZT handles to combine
 * * `num_pczts` - Number of PCZTs in the array
 * * `pczt_out` - Output pointer for the combined PCZT handle
 *
 * # Returns
 * * `ResultCode::Success` on success
 * * `ResultCode::ErrorCombine` if combination fails
 */

enum ResultCode pczt_combine(struct PcztHandle *const *aPczts,
                             uintptr_t aNumPczts,
                             struct PcztHandle **aPcztOut)
;

/**
 * Frees a PCZT handle
 */

void pczt_free(struct PcztHandle *aPczt)
;

/**
 * Frees a byte buffer allocated by the library
 */

void pczt_free_bytes(uint8_t *aBytes,
                     uintptr_t aLen)
;

#ifdef __cplusplus
} // extern "C"
#endif // __cplusplus

#ifdef __cplusplus
} // namespace t2z
#endif // __cplusplus
