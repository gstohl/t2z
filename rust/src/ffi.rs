use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_int, c_uchar};
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

/// C-compatible transaction input
#[repr(C)]
pub struct CTransparentInput {
    pub prevout_hash: [u8; 32],
    pub prevout_index: u32,
    pub script_pub_key: *const c_uchar,
    pub script_pub_key_len: usize,
    pub value: u64,
}

/// C-compatible transaction output
#[repr(C)]
pub struct CTransparentOutput {
    pub script_pub_key: *const c_uchar,
    pub script_pub_key_len: usize,
    pub value: u64,
}

/// Error message buffer
const ERROR_MSG_SIZE: usize = 512;

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

/// Proposes a new transaction (DEPRECATED - use pczt_propose_transaction_v2)
#[no_mangle]
pub unsafe extern "C" fn pczt_propose_transaction(
    _inputs: *const CTransparentInput,
    _num_inputs: usize,
    _request: *const TransactionRequestHandle,
    _pczt_out: *mut *mut PcztHandle,
) -> ResultCode {
    set_last_error(FfiError::NotImplemented(
        "Use pczt_propose_transaction_v2 with serialized inputs instead".to_string()
    ));
    ResultCode::ErrorNotImplemented
}

/// Proposes a new transaction using serialized input bytes
///
/// This is the recommended FFI function that accepts inputs in the binary serialization format.
#[no_mangle]
pub unsafe extern "C" fn pczt_propose_transaction_v2(
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

/// Adds proofs to a PCZT
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

/// Appends a signature to the PCZT
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

/// Finalizes and extracts the transaction
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
