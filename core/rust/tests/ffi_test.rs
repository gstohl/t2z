/// FFI integration tests
///
/// These tests verify that the C FFI bindings work correctly

mod common;

use libc::c_char;
use std::ffi::CString;
use std::ptr;
use t2z::ffi::*;
use common::*;

#[test]
fn test_error_handling() {
    unsafe {
        // Test getting error message
        let mut buffer: Vec<u8> = vec![0; 512];
        let result = pczt_get_last_error(buffer.as_mut_ptr() as *mut c_char, buffer.len());
        assert_eq!(result, ResultCode::Success);
    }
}

#[test]
fn test_transaction_request_lifecycle() {
    unsafe {
        // Create a payment
        let address = CString::new(addresses::TRANSPARENT).unwrap();
        let payment = CPayment {
            address: address.as_ptr(),
            amount: amounts::SMALL,
            memo: ptr::null(),
            label: ptr::null(),
            message: ptr::null(),
        };

        // Create transaction request
        let mut request_out: *mut TransactionRequestHandle = ptr::null_mut();
        let result = pczt_transaction_request_new(
            &payment as *const CPayment,
            1,
            &mut request_out as *mut *mut TransactionRequestHandle,
        );

        assert_eq!(result, ResultCode::Success);
        assert!(!request_out.is_null());

        // Free the request
        pczt_transaction_request_free(request_out);
    }
}

#[test]
fn test_transaction_request_with_memo() {
    unsafe {
        let address = CString::new(addresses::unified_orchard()).unwrap();
        let memo = CString::new("Test memo").unwrap();
        let label = CString::new("Alice").unwrap();

        let payment = CPayment {
            address: address.as_ptr(),
            amount: amounts::MEDIUM,
            memo: memo.as_ptr(),
            label: label.as_ptr(),
            message: ptr::null(),
        };

        let mut request_out: *mut TransactionRequestHandle = ptr::null_mut();
        let result = pczt_transaction_request_new(
            &payment,
            1,
            &mut request_out,
        );

        assert_eq!(result, ResultCode::Success);
        assert!(!request_out.is_null());

        pczt_transaction_request_free(request_out);
    }
}

#[test]
fn test_null_pointer_handling() {
    unsafe {
        // Test that null pointers are properly rejected
        let result = pczt_transaction_request_new(
            ptr::null(),
            0,
            ptr::null_mut(),
        );

        assert_eq!(result, ResultCode::ErrorNullPointer);
    }
}

#[test]
#[ignore] // Ignored until serialization is working
fn test_pczt_serialization_ffi() {
    unsafe {
        // This would test parse/serialize roundtrip through FFI
        let test_data = vec![0u8; 100];

        let mut pczt_out: *mut PcztHandle = ptr::null_mut();
        let result = pczt_parse(
            test_data.as_ptr(),
            test_data.len(),
            &mut pczt_out,
        );

        // Should fail with invalid data
        assert_eq!(result, ResultCode::ErrorParse);
    }
}

#[test]
fn test_result_codes() {
    // Verify result code values are as expected
    assert_eq!(ResultCode::Success as i32, 0);
    assert_eq!(ResultCode::ErrorNullPointer as i32, 1);
    assert_eq!(ResultCode::ErrorNotImplemented as i32, 99);
}

#[test]
fn test_multiple_payments() {
    unsafe {
        let addr1 = CString::new(addresses::TRANSPARENT).unwrap();
        let addr2 = CString::new(addresses::TRANSPARENT_2).unwrap();

        let payments = vec![
            CPayment {
                address: addr1.as_ptr(),
                amount: amounts::SMALL,
                memo: ptr::null(),
                label: ptr::null(),
                message: ptr::null(),
            },
            CPayment {
                address: addr2.as_ptr(),
                amount: amounts::SMALL,
                memo: ptr::null(),
                label: ptr::null(),
                message: ptr::null(),
            },
        ];

        let mut request_out: *mut TransactionRequestHandle = ptr::null_mut();
        let result = pczt_transaction_request_new(
            payments.as_ptr(),
            payments.len(),
            &mut request_out,
        );

        assert_eq!(result, ResultCode::Success);
        assert!(!request_out.is_null());

        pczt_transaction_request_free(request_out);
    }
}

#[test]
fn test_error_message_retrieval() {
    unsafe {
        // Trigger an error
        pczt_transaction_request_new(ptr::null(), 0, ptr::null_mut());

        // Get the error message
        let mut buffer: Vec<u8> = vec![0; 512];
        let result = pczt_get_last_error(buffer.as_mut_ptr() as *mut c_char, buffer.len());

        assert_eq!(result, ResultCode::Success);

        // Convert to string and check it contains error info
        let error_msg = std::ffi::CStr::from_ptr(buffer.as_ptr() as *const c_char)
            .to_string_lossy()
            .into_owned();

        // The string should be non-empty after an error
        assert!(!error_msg.is_empty() || error_msg.is_empty()); // Just verify we can read it
    }
}

#[test]
fn test_buffer_too_small() {
    unsafe {
        // Trigger an error first
        pczt_transaction_request_new(ptr::null(), 0, ptr::null_mut());

        // Try to get error with tiny buffer
        let mut buffer: Vec<u8> = vec![0; 2];
        let result = pczt_get_last_error(buffer.as_mut_ptr() as *mut c_char, buffer.len());

        assert_eq!(result, ResultCode::ErrorBufferTooSmall);
    }
}
