use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_uchar};
use std::ptr;
use std::slice;

use crate::error::*;
use crate::types::*;
use crate::*;

use pczt::Pczt;

/// Result code for FFI functions
#[repr(C)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ResultCode {
    Success = 0,
    ErrorNullPointer = 1,
    ErrorInvalidUtf8 = 2,
    ErrorBufferTooSmall = 3,
    ErrorProposal = 10,
    ErrorProver = 11,
    ErrorVerification = 12,
    ErrorSighash = 13,
    ErrorSignature = 14,
    ErrorCombine = 15,
    ErrorFinalization = 16,
    ErrorParse = 17,
    ErrorNotImplemented = 99,
}

/// Opaque handle to a PCZT object
#[repr(C)]
pub struct PcztHandle {
    _private: [u8; 0],
}

/// Opaque handle to a TransactionRequest object
#[repr(C)]
pub struct TransactionRequestHandle {
    _private: [u8; 0],
}

/// C-compatible payment structure
#[repr(C)]
pub struct CPayment {
    pub address: *const c_char,
    pub amount: u64,
    pub memo: *const c_char,      // nullable
    pub label: *const c_char,     // nullable
    pub message: *const c_char,   // nullable
}

/// C-compatible transaction output
#[repr(C)]
pub struct CTransparentOutput {
    pub script_pub_key: *const c_uchar,
    pub script_pub_key_len: usize,
    pub value: u64,
}

thread_local! {
    static LAST_ERROR: std::cell::RefCell<Option<String>> = std::cell::RefCell::new(None);
}

/// Sets the last error message
fn set_last_error(err: FfiError) {
    LAST_ERROR.with(|e| {
        *e.borrow_mut() = Some(err.to_string());
    });
}

/// Gets the last error message
#[no_mangle]
pub unsafe extern "C" fn pczt_get_last_error(
    buffer: *mut c_char,
    buffer_len: usize,
) -> ResultCode {
    if buffer.is_null() {
        return ResultCode::ErrorNullPointer;
    }

    LAST_ERROR.with(|e| {
        if let Some(ref err_msg) = *e.borrow() {
            let c_str = match CString::new(err_msg.as_str()) {
                Ok(s) => s,
                Err(_) => return ResultCode::ErrorInvalidUtf8,
            };

            let bytes = c_str.as_bytes_with_nul();
            if bytes.len() > buffer_len {
                return ResultCode::ErrorBufferTooSmall;
            }

            ptr::copy_nonoverlapping(bytes.as_ptr() as *const c_char, buffer, bytes.len());
            ResultCode::Success
        } else {
            // No error set
            *buffer = 0; // Empty string
            ResultCode::Success
        }
    })
}

/// Creates a new transaction request
#[no_mangle]
pub unsafe extern "C" fn pczt_transaction_request_new(
    payments: *const CPayment,
    num_payments: usize,
    request_out: *mut *mut TransactionRequestHandle,
) -> ResultCode {
    if payments.is_null() || request_out.is_null() {
        set_last_error(FfiError::NullPointer);
        return ResultCode::ErrorNullPointer;
    }

    let payments_slice = slice::from_raw_parts(payments, num_payments);
    let mut rust_payments = Vec::new();

    for c_payment in payments_slice {
        if c_payment.address.is_null() {
            set_last_error(FfiError::NullPointer);
            return ResultCode::ErrorNullPointer;
        }

        let address = match CStr::from_ptr(c_payment.address).to_str() {
            Ok(s) => s.to_string(),
            Err(_) => {
                set_last_error(FfiError::InvalidUtf8);
                return ResultCode::ErrorInvalidUtf8;
            }
        };

        let mut payment = Payment::new(address, c_payment.amount);

        if !c_payment.memo.is_null() {
            if let Ok(memo) = CStr::from_ptr(c_payment.memo).to_str() {
                payment = payment.with_memo(memo.to_string());
            }
        }

        if !c_payment.label.is_null() {
            if let Ok(label) = CStr::from_ptr(c_payment.label).to_str() {
                payment = payment.with_label(label.to_string());
            }
        }

        if !c_payment.message.is_null() {
            if let Ok(message) = CStr::from_ptr(c_payment.message).to_str() {
                payment = payment.with_message(message.to_string());
            }
        }

        rust_payments.push(payment);
    }

    let request = Box::new(TransactionRequest::new(rust_payments));
    *request_out = Box::into_raw(request) as *mut TransactionRequestHandle;

    ResultCode::Success
}

/// Frees a transaction request
#[no_mangle]
pub unsafe extern "C" fn pczt_transaction_request_free(request: *mut TransactionRequestHandle) {
    if !request.is_null() {
        drop(Box::from_raw(request as *mut TransactionRequest));
    }
}

/// Sets the target height for a transaction request
#[no_mangle]
pub unsafe extern "C" fn pczt_transaction_request_set_target_height(
    request: *mut TransactionRequestHandle,
    target_height: u32,
) -> ResultCode {
    if request.is_null() {
        set_last_error(FfiError::NullPointer);
        return ResultCode::ErrorNullPointer;
    }

    let tx_request = &mut *(request as *mut TransactionRequest);
    tx_request.target_height = Some(target_height);
    ResultCode::Success
}

/// Sets whether to use mainnet parameters for consensus branch ID
///
/// By default, the library uses mainnet parameters. Set this to false for testnet.
/// Regtest networks (like Zebra's regtest) typically use mainnet-like branch IDs,
/// so keep the default (true) for regtest.
#[no_mangle]
pub unsafe extern "C" fn pczt_transaction_request_set_use_mainnet(
    request: *mut TransactionRequestHandle,
    use_mainnet: bool,
) -> ResultCode {
    if request.is_null() {
        set_last_error(FfiError::NullPointer);
        return ResultCode::ErrorNullPointer;
    }

    let tx_request = &mut *(request as *mut TransactionRequest);
    tx_request.use_mainnet = use_mainnet;
    ResultCode::Success
}

/// Proposes a new transaction using serialized input bytes
#[no_mangle]
pub unsafe extern "C" fn pczt_propose_transaction(
    inputs_bytes: *const u8,
    inputs_bytes_len: usize,
    request: *const TransactionRequestHandle,
    change_address: *const c_char,  // nullable
    pczt_out: *mut *mut PcztHandle,
) -> ResultCode {
    if inputs_bytes.is_null() || request.is_null() || pczt_out.is_null() {
        set_last_error(FfiError::NullPointer);
        return ResultCode::ErrorNullPointer;
    }

    let inputs_slice = slice::from_raw_parts(inputs_bytes, inputs_bytes_len);
    let tx_request = &*(request as *const TransactionRequest);

    // Parse optional change address
    let change_addr = if change_address.is_null() {
        None
    } else {
        match CStr::from_ptr(change_address).to_str() {
            Ok(s) => Some(s.to_string()),
            Err(_) => {
                set_last_error(FfiError::InvalidUtf8);
                return ResultCode::ErrorInvalidUtf8;
            }
        }
    };

    match propose_transaction(inputs_slice, tx_request.clone(), change_addr) {
        Ok(pczt) => {
            let boxed_pczt = Box::new(pczt);
            *pczt_out = Box::into_raw(boxed_pczt) as *mut PcztHandle;
            ResultCode::Success
        }
        Err(e) => {
            set_last_error(FfiError::Proposal(e));
            ResultCode::ErrorProposal
        }
    }
}

/// Adds proofs to a PCZT.
///
/// # Ownership
/// This function ALWAYS consumes the input PCZT handle, even on error.
/// On success, `pczt_out` contains the new PCZT with proofs.
/// On error, the input handle is invalidated and cannot be reused.
///
/// If you need to retry on failure, call `pczt_serialize()` before this
/// function to create a backup that can be restored with `pczt_parse()`.
#[no_mangle]
pub unsafe extern "C" fn pczt_prove_transaction(
    pczt: *mut PcztHandle,
    pczt_out: *mut *mut PcztHandle,
) -> ResultCode {
    if pczt.is_null() || pczt_out.is_null() {
        set_last_error(FfiError::NullPointer);
        return ResultCode::ErrorNullPointer;
    }

    let rust_pczt = Box::from_raw(pczt as *mut Pczt);

    match prove_transaction(*rust_pczt) {
        Ok(proved_pczt) => {
            let boxed_pczt = Box::new(proved_pczt);
            *pczt_out = Box::into_raw(boxed_pczt) as *mut PcztHandle;
            ResultCode::Success
        }
        Err(e) => {
            set_last_error(FfiError::Prover(e));
            ResultCode::ErrorProver
        }
    }
}

/// Verifies the PCZT before signing
#[no_mangle]
pub unsafe extern "C" fn pczt_verify_before_signing(
    pczt: *const PcztHandle,
    request: *const TransactionRequestHandle,
    expected_change: *const CTransparentOutput,
    expected_change_len: usize,
) -> ResultCode {
    if pczt.is_null() || request.is_null() {
        set_last_error(FfiError::NullPointer);
        return ResultCode::ErrorNullPointer;
    }

    let rust_pczt = &*(pczt as *const Pczt);
    let tx_request = &*(request as *const TransactionRequest);

    // Parse expected change outputs
    let mut change_outputs = Vec::new();
    if !expected_change.is_null() && expected_change_len > 0 {
        let change_slice = slice::from_raw_parts(expected_change, expected_change_len);

        for c_output in change_slice {
            if c_output.script_pub_key.is_null() {
                set_last_error(FfiError::NullPointer);
                return ResultCode::ErrorNullPointer;
            }

            let script_bytes = slice::from_raw_parts(
                c_output.script_pub_key,
                c_output.script_pub_key_len
            );

            // Parse script with CompactSize prefix
            use zcash_encoding::CompactSize;
            let mut script_with_prefix = Vec::new();
            if let Err(_) = CompactSize::write(&mut script_with_prefix, script_bytes.len()) {
                set_last_error(FfiError::Verification(
                    crate::error::VerificationFailure::OutputMismatch("Invalid script length".to_string())
                ));
                return ResultCode::ErrorVerification;
            }
            script_with_prefix.extend_from_slice(script_bytes);

            let script = match zcash_transparent::address::Script::read(&script_with_prefix[..]) {
                Ok(s) => s,
                Err(_) => {
                    set_last_error(FfiError::Verification(
                        crate::error::VerificationFailure::OutputMismatch("Invalid script".to_string())
                    ));
                    return ResultCode::ErrorVerification;
                }
            };

            let value = match zcash_protocol::value::Zatoshis::from_u64(c_output.value) {
                Ok(v) => v,
                Err(_) => {
                    set_last_error(FfiError::Verification(
                        crate::error::VerificationFailure::OutputMismatch("Invalid value".to_string())
                    ));
                    return ResultCode::ErrorVerification;
                }
            };

            change_outputs.push(zcash_transparent::bundle::TxOut::new(value, script));
        }
    }

    match verify_before_signing(rust_pczt, tx_request, &change_outputs) {
        Ok(_) => ResultCode::Success,
        Err(e) => {
            set_last_error(FfiError::Verification(e));
            ResultCode::ErrorVerification
        }
    }
}

/// Gets the signature hash for an input
#[no_mangle]
pub unsafe extern "C" fn pczt_get_sighash(
    pczt: *const PcztHandle,
    input_index: usize,
    sighash_out: *mut [u8; 32],
) -> ResultCode {
    if pczt.is_null() || sighash_out.is_null() {
        set_last_error(FfiError::NullPointer);
        return ResultCode::ErrorNullPointer;
    }

    let rust_pczt = &*(pczt as *const Pczt);

    match get_sighash(rust_pczt, input_index) {
        Ok(sighash) => {
            *sighash_out = *sighash.as_bytes();
            ResultCode::Success
        }
        Err(e) => {
            set_last_error(FfiError::Sighash(e));
            ResultCode::ErrorSighash
        }
    }
}

/// Appends a signature to the PCZT.
///
/// # Ownership
/// This function ALWAYS consumes the input PCZT handle, even on error.
/// On success, `pczt_out` contains the new PCZT with the signature appended.
/// On error, the input handle is invalidated and cannot be reused.
///
/// If you need to retry on failure, call `pczt_serialize()` before this
/// function to create a backup that can be restored with `pczt_parse()`.
#[no_mangle]
pub unsafe extern "C" fn pczt_append_signature(
    pczt: *mut PcztHandle,
    input_index: usize,
    signature: *const [u8; 64],
    pczt_out: *mut *mut PcztHandle,
) -> ResultCode {
    if pczt.is_null() || signature.is_null() || pczt_out.is_null() {
        set_last_error(FfiError::NullPointer);
        return ResultCode::ErrorNullPointer;
    }

    let rust_pczt = Box::from_raw(pczt as *mut Pczt);
    let sig = *signature;

    match append_signature(*rust_pczt, input_index, sig) {
        Ok(signed_pczt) => {
            let boxed_pczt = Box::new(signed_pczt);
            *pczt_out = Box::into_raw(boxed_pczt) as *mut PcztHandle;
            ResultCode::Success
        }
        Err(e) => {
            set_last_error(FfiError::Signature(e));
            ResultCode::ErrorSignature
        }
    }
}

/// Finalizes and extracts the transaction.
///
/// # Ownership
/// This function ALWAYS consumes the input PCZT handle, even on error.
/// On success, `tx_bytes_out` and `tx_bytes_len_out` contain the transaction bytes.
/// On error, the input handle is invalidated and cannot be reused.
///
/// If you need to retry on failure, call `pczt_serialize()` before this
/// function to create a backup that can be restored with `pczt_parse()`.
#[no_mangle]
pub unsafe extern "C" fn pczt_finalize_and_extract(
    pczt: *mut PcztHandle,
    tx_bytes_out: *mut *mut u8,
    tx_bytes_len_out: *mut usize,
) -> ResultCode {
    if pczt.is_null() || tx_bytes_out.is_null() || tx_bytes_len_out.is_null() {
        set_last_error(FfiError::NullPointer);
        return ResultCode::ErrorNullPointer;
    }

    let rust_pczt = Box::from_raw(pczt as *mut Pczt);

    match finalize_and_extract(*rust_pczt) {
        Ok(tx_bytes) => {
            let len = tx_bytes.len();
            let mut boxed_bytes = tx_bytes.into_boxed_slice();
            *tx_bytes_out = boxed_bytes.as_mut_ptr();
            *tx_bytes_len_out = len;
            std::mem::forget(boxed_bytes); // Prevent deallocation
            ResultCode::Success
        }
        Err(e) => {
            set_last_error(FfiError::Finalization(e));
            ResultCode::ErrorFinalization
        }
    }
}

/// Parses a PCZT from bytes
#[no_mangle]
pub unsafe extern "C" fn pczt_parse(
    pczt_bytes: *const u8,
    pczt_bytes_len: usize,
    pczt_out: *mut *mut PcztHandle,
) -> ResultCode {
    if pczt_bytes.is_null() || pczt_out.is_null() {
        set_last_error(FfiError::NullPointer);
        return ResultCode::ErrorNullPointer;
    }

    let bytes = slice::from_raw_parts(pczt_bytes, pczt_bytes_len);

    match parse_pczt(bytes) {
        Ok(pczt) => {
            let boxed_pczt = Box::new(pczt);
            *pczt_out = Box::into_raw(boxed_pczt) as *mut PcztHandle;
            ResultCode::Success
        }
        Err(e) => {
            set_last_error(FfiError::Parse(e));
            ResultCode::ErrorParse
        }
    }
}

/// Serializes a PCZT to bytes
#[no_mangle]
pub unsafe extern "C" fn pczt_serialize(
    pczt: *const PcztHandle,
    bytes_out: *mut *mut u8,
    bytes_len_out: *mut usize,
) -> ResultCode {
    if pczt.is_null() || bytes_out.is_null() || bytes_len_out.is_null() {
        set_last_error(FfiError::NullPointer);
        return ResultCode::ErrorNullPointer;
    }

    let rust_pczt = &*(pczt as *const Pczt);
    let serialized = serialize_pczt(rust_pczt);

    let len = serialized.len();
    let mut boxed_bytes = serialized.into_boxed_slice();
    *bytes_out = boxed_bytes.as_mut_ptr();
    *bytes_len_out = len;
    std::mem::forget(boxed_bytes); // Prevent deallocation

    ResultCode::Success
}

/// Combines multiple PCZTs into one.
///
/// This is useful for parallel signing workflows where different parts of the transaction
/// are processed independently and need to be merged.
///
/// # Ownership
/// This function ALWAYS consumes ALL input PCZT handles, even on error.
/// On success, `pczt_out` contains the combined PCZT.
/// On error, all input handles are invalidated and cannot be reused.
///
/// If you need to retry on failure, call `pczt_serialize()` on each PCZT before
/// this function to create backups that can be restored with `pczt_parse()`.
///
/// # Arguments
/// * `pczts` - Array of PCZT handles to combine
/// * `num_pczts` - Number of PCZTs in the array
/// * `pczt_out` - Output pointer for the combined PCZT handle
///
/// # Returns
/// * `ResultCode::Success` on success
/// * `ResultCode::ErrorCombine` if combination fails
#[no_mangle]
pub unsafe extern "C" fn pczt_combine(
    pczts: *const *mut PcztHandle,
    num_pczts: usize,
    pczt_out: *mut *mut PcztHandle,
) -> ResultCode {
    if pczts.is_null() || pczt_out.is_null() {
        set_last_error(FfiError::NullPointer);
        return ResultCode::ErrorNullPointer;
    }

    if num_pczts == 0 {
        set_last_error(FfiError::Combine(crate::error::CombineError::NoPczts));
        return ResultCode::ErrorCombine;
    }

    // Collect PCZT handles into Vec, taking ownership
    let pczt_ptrs = slice::from_raw_parts(pczts, num_pczts);
    let mut rust_pczts = Vec::with_capacity(num_pczts);

    for &ptr in pczt_ptrs {
        if ptr.is_null() {
            set_last_error(FfiError::NullPointer);
            return ResultCode::ErrorNullPointer;
        }
        // Take ownership of each PCZT
        rust_pczts.push(*Box::from_raw(ptr as *mut Pczt));
    }

    match combine(rust_pczts) {
        Ok(combined) => {
            let boxed = Box::new(combined);
            *pczt_out = Box::into_raw(boxed) as *mut PcztHandle;
            ResultCode::Success
        }
        Err(e) => {
            set_last_error(FfiError::Combine(e));
            ResultCode::ErrorCombine
        }
    }
}

/// Frees a PCZT handle
#[no_mangle]
pub unsafe extern "C" fn pczt_free(pczt: *mut PcztHandle) {
    if !pczt.is_null() {
        drop(Box::from_raw(pczt as *mut Pczt));
    }
}

/// Frees a byte buffer allocated by the library
#[no_mangle]
pub unsafe extern "C" fn pczt_free_bytes(bytes: *mut u8, len: usize) {
    if !bytes.is_null() {
        drop(Vec::from_raw_parts(bytes, len, len));
    }
}

/// Calculates the ZIP-317 transaction fee.
///
/// This is a pure function with no side effects - it simply computes the fee
/// based on the transaction shape. Use this to calculate fees before building
/// a transaction, e.g., for "send max" functionality.
///
/// # Arguments
/// * `num_transparent_inputs` - Number of transparent UTXOs to spend
/// * `num_transparent_outputs` - Number of transparent outputs (including change)
/// * `num_orchard_outputs` - Number of Orchard (shielded) outputs
///
/// # Returns
/// The fee in zatoshis (always succeeds, no error possible)
///
/// # Example
/// For a transaction with 1 input, 1 payment output, 1 change output:
/// ```c
/// uint64_t fee = pczt_calculate_fee(1, 2, 0); // Returns 10000
/// ```
#[no_mangle]
pub extern "C" fn pczt_calculate_fee(
    num_transparent_inputs: usize,
    num_transparent_outputs: usize,
    num_orchard_outputs: usize,
) -> u64 {
    crate::calculate_fee(num_transparent_inputs, num_transparent_outputs, num_orchard_outputs)
}
