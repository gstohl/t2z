#![deny(clippy::all)]

use napi::bindgen_prelude::*;
use napi_derive::napi;
use std::ffi::CString;
use std::ptr;

// C-compatible payment structure matching t2z.h
#[repr(C)]
struct CPayment {
    address: *const std::os::raw::c_char,
    amount: u64,
    memo: *const std::os::raw::c_char,
    label: *const std::os::raw::c_char,
    message: *const std::os::raw::c_char,
}

// C-compatible transparent output structure matching t2z.h
#[repr(C)]
struct CTransparentOutput {
    script_pub_key: *const u8,
    script_pub_key_len: usize,
    value: u64,
}

// Link against the t2z static library
#[link(name = "t2z", kind = "static")]
extern "C" {
    fn pczt_transaction_request_new(
        payments: *const CPayment,
        num_payments: usize,
        request_out: *mut *mut std::ffi::c_void,
    ) -> u32; // Returns ResultCode

    fn pczt_transaction_request_free(request_handle: *mut std::ffi::c_void);

    fn pczt_transaction_request_set_target_height(
        request_handle: *mut std::ffi::c_void,
        target_height: u32,
    ) -> u32;

    fn pczt_propose_transaction_v2(
        inputs_bytes: *const u8,
        inputs_bytes_len: usize,
        request: *const std::ffi::c_void,
        change_address: *const std::os::raw::c_char,
        pczt_out: *mut *mut std::ffi::c_void,
    ) -> u32;

    fn pczt_prove_transaction(
        pczt: *mut std::ffi::c_void,
        pczt_out: *mut *mut std::ffi::c_void,
    ) -> u32;

    fn pczt_verify_before_signing(
        pczt: *const std::ffi::c_void,
        request: *const std::ffi::c_void,
        expected_change: *const CTransparentOutput,
        expected_change_len: usize,
    ) -> u32;

    fn pczt_get_sighash(
        pczt: *const std::ffi::c_void,
        input_index: u32,
        sighash_out: *mut u8,
    ) -> u32;

    fn pczt_append_signature(
        pczt: *mut std::ffi::c_void,
        input_index: u32,
        signature: *const u8,
        pczt_out: *mut *mut std::ffi::c_void,
    ) -> u32;

    fn pczt_finalize_and_extract(
        pczt: *mut std::ffi::c_void,
        tx_bytes_out: *mut *mut u8,
        tx_bytes_len_out: *mut usize,
    ) -> u32;

    fn pczt_serialize(
        pczt: *const std::ffi::c_void,
        bytes_out: *mut *mut u8,
        bytes_len_out: *mut usize,
    ) -> u32;

    fn pczt_parse(
        bytes: *const u8,
        bytes_len: usize,
        pczt_out: *mut *mut std::ffi::c_void,
    ) -> u32;

    fn pczt_free(pczt: *mut std::ffi::c_void);

    fn pczt_free_bytes(bytes: *mut u8);

    fn pczt_get_last_error(buffer: *mut std::os::raw::c_char, buffer_len: usize) -> u32;
}

/// Result codes from the native library
#[napi]
pub enum ResultCode {
    Success = 0,
    ProposalError = 1,
    ProverError = 2,
    SignerError = 3,
    SigningError = 4,
    ExtractorError = 5,
    IoError = 6,
    ParseError = 7,
    VerificationError = 8,
    InvalidIndex = 9,
    InvalidSignature = 10,
    CombineError = 11,
}

/// Helper function to get detailed error message from the last error
fn get_last_error_message() -> String {
    let mut buffer = vec![0u8; 512];
    unsafe {
        pczt_get_last_error(buffer.as_mut_ptr() as *mut std::os::raw::c_char, buffer.len());
        // Find null terminator
        if let Some(null_pos) = buffer.iter().position(|&b| b == 0) {
            buffer.truncate(null_pos);
        }
        String::from_utf8_lossy(&buffer).to_string()
    }
}

/// Payment request with address, amount, and optional metadata
#[napi(object)]
pub struct Payment {
    /// Zcash address (unified, transparent, or shielded)
    pub address: String,
    /// Amount in zatoshis (as string to handle BigInt from JavaScript)
    pub amount: String,
    /// Optional memo for shielded outputs
    pub memo: Option<String>,
    /// Optional label
    pub label: Option<String>,
    /// Optional message
    pub message: Option<String>,
}

/// Transparent UTXO input to spend
#[napi(object)]
pub struct TransparentInput {
    /// Compressed public key (33 bytes)
    pub pubkey: Buffer,
    /// Transaction ID (32 bytes)
    pub txid: Buffer,
    /// Output index
    pub vout: u32,
    /// Amount in zatoshis (as string to handle BigInt from JavaScript)
    pub amount: String,
    /// P2PKH script pubkey
    pub script_pub_key: Buffer,
}

/// Transparent transaction output (for change verification)
#[napi(object)]
pub struct TransparentOutput {
    /// P2PKH script pubkey (raw bytes, no CompactSize prefix)
    pub script_pub_key: Buffer,
    /// Amount in zatoshis (as string to handle BigInt from JavaScript)
    pub value: String,
}

/// Transaction request containing multiple payments
#[napi]
pub struct TransactionRequest {
    handle: *mut std::ffi::c_void,
}

#[napi]
impl TransactionRequest {
    /// Create a new transaction request with a specific target block height
    #[napi(factory)]
    pub fn with_target_height(payments: Vec<Payment>, target_height: u32) -> Result<Self> {
        let mut req = Self::new(payments)?;

        // Set the target height using FFI
        let result_code = unsafe {
            pczt_transaction_request_set_target_height(req.handle, target_height)
        };

        if result_code != 0 {
            return Err(Error::from_reason(format!(
                "Failed to set target height: result code {}",
                result_code
            )));
        }

        Ok(req)
    }

    /// Create a new transaction request from payment array
    #[napi(constructor)]
    pub fn new(payments: Vec<Payment>) -> Result<Self> {
        // Convert Payment objects to CString buffers (must be kept alive)
        let mut address_cstrings: Vec<CString> = Vec::new();
        let mut memo_cstrings: Vec<Option<CString>> = Vec::new();
        let mut label_cstrings: Vec<Option<CString>> = Vec::new();
        let mut message_cstrings: Vec<Option<CString>> = Vec::new();
        let mut amounts: Vec<u64> = Vec::new();

        for payment in &payments {
            address_cstrings.push(
                CString::new(payment.address.clone())
                    .map_err(|e| Error::from_reason(format!("Invalid address: {}", e)))?,
            );

            amounts.push(
                payment
                    .amount
                    .parse::<u64>()
                    .map_err(|e| Error::from_reason(format!("Invalid amount '{}': {}", payment.amount, e)))?,
            );

            memo_cstrings.push(
                payment
                    .memo
                    .as_ref()
                    .map(|m| CString::new(m.clone()))
                    .transpose()
                    .map_err(|e| Error::from_reason(format!("Invalid memo: {}", e)))?,
            );

            label_cstrings.push(
                payment
                    .label
                    .as_ref()
                    .map(|l| CString::new(l.clone()))
                    .transpose()
                    .map_err(|e| Error::from_reason(format!("Invalid label: {}", e)))?,
            );

            message_cstrings.push(
                payment
                    .message
                    .as_ref()
                    .map(|m| CString::new(m.clone()))
                    .transpose()
                    .map_err(|e| Error::from_reason(format!("Invalid message: {}", e)))?,
            );
        }

        // Build CPayment array
        let c_payments: Vec<CPayment> = (0..payments.len())
            .map(|i| CPayment {
                address: address_cstrings[i].as_ptr(),
                amount: amounts[i],
                memo: memo_cstrings[i].as_ref().map_or(ptr::null(), |m| m.as_ptr()),
                label: label_cstrings[i].as_ref().map_or(ptr::null(), |l| l.as_ptr()),
                message: message_cstrings[i].as_ref().map_or(ptr::null(), |m| m.as_ptr()),
            })
            .collect();

        let mut request_handle: *mut std::ffi::c_void = ptr::null_mut();

        let result_code = unsafe {
            pczt_transaction_request_new(c_payments.as_ptr(), c_payments.len(), &mut request_handle)
        };

        if result_code != 0 {
            return Err(Error::from_reason(format!(
                "Failed to create transaction request: result code {}",
                result_code
            )));
        }

        Ok(Self {
            handle: request_handle,
        })
    }

    /// Set the target block height for consensus branch ID selection
    #[napi]
    pub fn set_target_height(&mut self, height: u32) -> Result<()> {
        let result_code = unsafe {
            pczt_transaction_request_set_target_height(self.handle, height)
        };

        if result_code != 0 {
            return Err(Error::from_reason(format!(
                "Failed to set target height: result code {}",
                result_code
            )));
        }

        Ok(())
    }

    /// Free the native resources
    #[napi]
    pub fn free(&mut self) {
        if !self.handle.is_null() {
            unsafe {
                pczt_transaction_request_free(self.handle);
            }
            self.handle = ptr::null_mut();
        }
    }
}

impl Drop for TransactionRequest {
    fn drop(&mut self) {
        self.free();
    }
}

/// Partially Constructed Zcash Transaction (PCZT)
#[napi]
pub struct PCZT {
    handle: *mut std::ffi::c_void,
}

#[napi]
impl PCZT {
    /// Free the native resources
    /// Note: Most operations consume the PCZT
    #[napi]
    pub fn free(&mut self) {
        if !self.handle.is_null() {
            unsafe {
                pczt_free(self.handle);
            }
            self.handle = ptr::null_mut();
        }
    }

    fn from_handle(handle: *mut std::ffi::c_void) -> Self {
        Self { handle }
    }

    fn take_handle(&mut self) -> *mut std::ffi::c_void {
        let handle = self.handle;
        self.handle = ptr::null_mut();
        handle
    }
}

impl Drop for PCZT {
    fn drop(&mut self) {
        self.free();
    }
}

/// Serialize transparent inputs to binary format
fn serialize_transparent_inputs(inputs: &[TransparentInput]) -> Result<Vec<u8>> {
    let mut data = Vec::new();

    // Write number of inputs (u16 LE)
    data.extend_from_slice(&(inputs.len() as u16).to_le_bytes());

    for input in inputs {
        // Validate pubkey length
        if input.pubkey.len() != 33 {
            return Err(Error::from_reason(format!(
                "Invalid pubkey length: expected 33, got {}",
                input.pubkey.len()
            )));
        }

        // Validate txid length
        if input.txid.len() != 32 {
            return Err(Error::from_reason(format!(
                "Invalid txid length: expected 32, got {}",
                input.txid.len()
            )));
        }

        // Write pubkey (33 bytes)
        data.extend_from_slice(&input.pubkey);

        // Write txid (32 bytes)
        data.extend_from_slice(&input.txid);

        // Write vout (u32 LE)
        data.extend_from_slice(&input.vout.to_le_bytes());

        // Parse and write amount (u64 LE)
        let amount = input.amount.parse::<u64>()
            .map_err(|e| Error::from_reason(format!("Invalid amount '{}': {}", input.amount, e)))?;
        data.extend_from_slice(&amount.to_le_bytes());

        // Write script length (u16 LE)
        let script_len = input.script_pub_key.len() as u16;
        data.extend_from_slice(&script_len.to_le_bytes());

        // Write script pubkey
        data.extend_from_slice(&input.script_pub_key);
    }

    Ok(data)
}

/// Create a PCZT from transparent inputs and transaction request
#[napi]
pub fn propose_transaction(
    inputs: Vec<TransparentInput>,
    request: &TransactionRequest,
) -> Result<PCZT> {
    let input_data = serialize_transparent_inputs(&inputs)?;

    let mut pczt_handle: *mut std::ffi::c_void = ptr::null_mut();

    let result_code = unsafe {
        pczt_propose_transaction_v2(
            input_data.as_ptr(),
            input_data.len(),
            request.handle,
            ptr::null(), // No change address
            &mut pczt_handle,
        )
    };

    if result_code != 0 {
        let error_msg = get_last_error_message();
        return Err(Error::from_reason(format!(
            "Failed to propose transaction: {} (code {})",
            error_msg, result_code
        )));
    }

    Ok(PCZT::from_handle(pczt_handle))
}

/// Create a PCZT with explicit change handling
#[napi]
pub fn propose_transaction_with_change(
    inputs: Vec<TransparentInput>,
    request: &TransactionRequest,
    change_address: String,
) -> Result<PCZT> {
    let input_data = serialize_transparent_inputs(&inputs)?;
    let change_cstr = CString::new(change_address)
        .map_err(|e| Error::from_reason(format!("Invalid change address: {}", e)))?;

    let mut pczt_handle: *mut std::ffi::c_void = ptr::null_mut();

    let result_code = unsafe {
        pczt_propose_transaction_v2(
            input_data.as_ptr(),
            input_data.len(),
            request.handle,
            change_cstr.as_ptr(),
            &mut pczt_handle,
        )
    };

    if result_code != 0 {
        let error_msg = get_last_error_message();
        return Err(Error::from_reason(format!(
            "Failed to propose transaction with change: {} (code {})",
            error_msg, result_code
        )));
    }

    Ok(PCZT::from_handle(pczt_handle))
}

/// Add Orchard proofs to the PCZT
#[napi]
pub fn prove_transaction(pczt: &mut PCZT) -> Result<PCZT> {
    let pczt_handle = pczt.take_handle();

    let mut proved_handle: *mut std::ffi::c_void = ptr::null_mut();

    let result_code = unsafe {
        pczt_prove_transaction(pczt_handle, &mut proved_handle)
    };

    if result_code != 0 {
        let error_msg = get_last_error_message();
        return Err(Error::from_reason(format!(
            "Failed to prove transaction: {} (code {})",
            error_msg, result_code
        )));
    }

    Ok(PCZT::from_handle(proved_handle))
}

/// Verify the PCZT before signing
#[napi]
pub fn verify_before_signing(
    pczt: &PCZT,
    request: &TransactionRequest,
    expected_change: Vec<TransparentOutput>,
) -> Result<()> {
    // Convert expected_change to C-compatible format
    // Keep script buffers alive by storing them alongside C structs
    let script_buffers: Vec<Vec<u8>> = expected_change
        .iter()
        .map(|output| output.script_pub_key.to_vec())
        .collect();

    let c_change_outputs: Vec<CTransparentOutput> = expected_change
        .iter()
        .enumerate()
        .map(|(i, output)| {
            let value = output
                .value
                .parse::<u64>()
                .map_err(|e| Error::from_reason(format!("Invalid value '{}': {}", output.value, e)))?;

            Ok(CTransparentOutput {
                script_pub_key: script_buffers[i].as_ptr(),
                script_pub_key_len: script_buffers[i].len(),
                value,
            })
        })
        .collect::<Result<Vec<_>>>()?;

    let change_ptr = if c_change_outputs.is_empty() {
        ptr::null()
    } else {
        c_change_outputs.as_ptr()
    };

    let result_code = unsafe {
        pczt_verify_before_signing(
            pczt.handle,
            request.handle,
            change_ptr,
            c_change_outputs.len(),
        )
    };

    if result_code != 0 {
        let error_msg = get_last_error_message();
        return Err(Error::from_reason(format!(
            "Verification failed: {} (code {})",
            error_msg, result_code
        )));
    }

    Ok(())
}

/// Get signature hash for a transparent input
#[napi]
pub fn get_sighash(pczt: &PCZT, index: u32) -> Result<Buffer> {
    let mut sighash = [0u8; 32];

    let result_code = unsafe {
        pczt_get_sighash(pczt.handle, index, sighash.as_mut_ptr())
    };

    if result_code != 0 {
        return Err(Error::from_reason(format!(
            "Failed to get sighash: result code {}",
            result_code
        )));
    }

    Ok(Buffer::from(sighash.to_vec()))
}

/// Append an external signature to the PCZT
#[napi]
pub fn append_signature(
    pczt: &mut PCZT,
    index: u32,
    signature: Buffer,
) -> Result<PCZT> {
    if signature.len() != 64 {
        return Err(Error::from_reason(format!(
            "Invalid signature length: expected 64, got {}",
            signature.len()
        )));
    }

    let pczt_handle = pczt.take_handle();

    let mut signed_handle: *mut std::ffi::c_void = ptr::null_mut();

    let result_code = unsafe {
        pczt_append_signature(
            pczt_handle,
            index,
            signature.as_ref().as_ptr(),
            &mut signed_handle,
        )
    };

    if result_code != 0 {
        return Err(Error::from_reason(format!(
            "Failed to append signature: result code {}",
            result_code
        )));
    }

    Ok(PCZT::from_handle(signed_handle))
}

/// Combine multiple PCZTs into one (not implemented in C FFI)
#[napi]
pub fn combine(_pczts: Vec<&mut PCZT>) -> Result<PCZT> {
    Err(Error::from_reason("combine not yet implemented in C FFI"))
}

/// Finalize the PCZT and extract transaction bytes
#[napi]
pub fn finalize_and_extract(pczt: &mut PCZT) -> Result<Buffer> {
    let pczt_handle = pczt.take_handle();

    let mut tx_data: *mut u8 = ptr::null_mut();
    let mut tx_len: usize = 0;

    let result_code = unsafe {
        pczt_finalize_and_extract(
            pczt_handle,
            &mut tx_data,
            &mut tx_len,
        )
    };

    if result_code != 0 {
        return Err(Error::from_reason(format!(
            "Failed to finalize transaction: result code {}",
            result_code
        )));
    }

    // Copy data to Buffer and free C memory
    let result = unsafe { std::slice::from_raw_parts(tx_data, tx_len).to_vec() };

    unsafe {
        pczt_free_bytes(tx_data);
    }

    Ok(Buffer::from(result))
}

/// Serialize PCZT to bytes
#[napi]
pub fn serialize(pczt: &PCZT) -> Result<Buffer> {
    let mut data: *mut u8 = ptr::null_mut();
    let mut len: usize = 0;

    let result_code = unsafe {
        pczt_serialize(pczt.handle, &mut data, &mut len)
    };

    if result_code != 0 {
        return Err(Error::from_reason(format!(
            "Failed to serialize PCZT: result code {}",
            result_code
        )));
    }

    // Copy data to Buffer and free C memory
    let result = unsafe { std::slice::from_raw_parts(data, len).to_vec() };

    unsafe {
        pczt_free_bytes(data);
    }

    Ok(Buffer::from(result))
}

/// Parse PCZT from bytes
#[napi]
pub fn parse(bytes: Buffer) -> Result<PCZT> {
    let mut pczt_handle: *mut std::ffi::c_void = ptr::null_mut();

    let result_code = unsafe {
        pczt_parse(
            bytes.as_ref().as_ptr(),
            bytes.len(),
            &mut pczt_handle,
        )
    };

    if result_code != 0 {
        return Err(Error::from_reason(format!(
            "Failed to parse PCZT: result code {}",
            result_code
        )));
    }

    Ok(PCZT::from_handle(pczt_handle))
}
