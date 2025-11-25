use serde::{Deserialize, Serialize};
use zcash_transparent::bundle::{OutPoint, TxOut};
use zcash_transparent::address::Script;
use zcash_protocol::value::Zatoshis;

/// A signature hash used for signing transaction inputs
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct SigHash(pub [u8; 32]);

impl SigHash {
    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.0
    }

    pub fn to_vec(&self) -> Vec<u8> {
        self.0.to_vec()
    }
}

/// A transparent UTXO input to be spent
#[derive(Debug, Clone)]
pub struct TransparentInput {
    /// The compressed public key for this input (33 bytes)
    pub pubkey: secp256k1::PublicKey,
    /// The transaction ID of the UTXO being spent (32 bytes)
    pub txid: [u8; 32],
    /// The output index in the previous transaction
    pub vout: u32,
    /// The amount in zatoshis
    pub amount: u64,
    /// The script pubkey of the UTXO being spent
    pub script_pubkey: Vec<u8>,
}

impl TransparentInput {
    /// Convert to OutPoint for use with the Builder
    pub fn outpoint(&self) -> OutPoint {
        OutPoint::new(self.txid, self.vout)
    }

    /// Convert to TxOut for use with the Builder
    pub fn txout(&self) -> Result<TxOut, &'static str> {
        let value = Zatoshis::from_u64(self.amount)
            .map_err(|_| "Invalid amount")?;

        // The script_pubkey is stored as raw bytes (no CompactSize prefix)
        // We need to wrap it with a length prefix for Script::read()
        use zcash_encoding::CompactSize;
        let mut script_with_prefix = Vec::new();
        CompactSize::write(&mut script_with_prefix, self.script_pubkey.len())
            .map_err(|_| "Failed to write script length")?;
        script_with_prefix.extend_from_slice(&self.script_pubkey);

        let script = Script::read(&script_with_prefix[..])
            .map_err(|_| "Invalid script")?;
        Ok(TxOut::new(value, script))
    }
}

/// Parse transparent inputs from the serialized format
///
/// Format:
/// - [num_inputs: 2 bytes (u16 LE)]
/// - For each input:
///   - [pubkey: 33 bytes]
///   - [txid: 32 bytes]
///   - [vout: 4 bytes (u32 LE)]
///   - [amount: 8 bytes (u64 LE)]
///   - [script_len: 2 bytes (u16 LE)]
///   - [script: script_len bytes]
pub fn parse_transparent_inputs(data: &[u8]) -> Result<Vec<TransparentInput>, String> {
    if data.is_empty() {
        return Ok(Vec::new());
    }

    if data.len() < 2 {
        return Err("Input data too short for header".to_string());
    }

    let num_inputs = u16::from_le_bytes([data[0], data[1]]) as usize;
    let mut inputs = Vec::with_capacity(num_inputs);
    let mut offset = 2;

    for i in 0..num_inputs {
        // Read pubkey (33 bytes)
        if offset + 33 > data.len() {
            return Err(format!("Input {} truncated at pubkey", i));
        }
        let pubkey_bytes: [u8; 33] = data[offset..offset + 33]
            .try_into()
            .map_err(|_| format!("Invalid pubkey length for input {}", i))?;
        let pubkey = secp256k1::PublicKey::from_slice(&pubkey_bytes)
            .map_err(|e| format!("Invalid pubkey for input {}: {}", i, e))?;
        offset += 33;

        // Read txid (32 bytes)
        if offset + 32 > data.len() {
            return Err(format!("Input {} truncated at txid", i));
        }
        let txid: [u8; 32] = data[offset..offset + 32]
            .try_into()
            .map_err(|_| format!("Invalid txid length for input {}", i))?;
        offset += 32;

        // Read vout (4 bytes)
        if offset + 4 > data.len() {
            return Err(format!("Input {} truncated at vout", i));
        }
        let vout = u32::from_le_bytes([data[offset], data[offset + 1], data[offset + 2], data[offset + 3]]);
        offset += 4;

        // Read amount (8 bytes)
        if offset + 8 > data.len() {
            return Err(format!("Input {} truncated at amount", i));
        }
        let amount = u64::from_le_bytes([
            data[offset], data[offset + 1], data[offset + 2], data[offset + 3],
            data[offset + 4], data[offset + 5], data[offset + 6], data[offset + 7],
        ]);
        offset += 8;

        // Read script length (2 bytes)
        if offset + 2 > data.len() {
            return Err(format!("Input {} truncated at script length", i));
        }
        let script_len = u16::from_le_bytes([data[offset], data[offset + 1]]) as usize;
        offset += 2;

        // Read script
        if offset + script_len > data.len() {
            return Err(format!("Input {} truncated at script data", i));
        }
        let script_pubkey = data[offset..offset + script_len].to_vec();
        offset += script_len;

        inputs.push(TransparentInput {
            pubkey,
            txid,
            vout,
            amount,
            script_pubkey,
        });
    }

    Ok(inputs)
}

/// Serialize transparent inputs to the binary format
///
/// This is primarily for testing and for users who want to construct
/// inputs programmatically.
pub fn serialize_transparent_inputs(inputs: &[TransparentInput]) -> Vec<u8> {
    let mut data = Vec::new();

    // Write number of inputs (u16 LE)
    let num_inputs = inputs.len() as u16;
    data.extend_from_slice(&num_inputs.to_le_bytes());

    for input in inputs {
        // Write pubkey (33 bytes)
        data.extend_from_slice(&input.pubkey.serialize());

        // Write txid (32 bytes)
        data.extend_from_slice(&input.txid);

        // Write vout (u32 LE)
        data.extend_from_slice(&input.vout.to_le_bytes());

        // Write amount (u64 LE)
        data.extend_from_slice(&input.amount.to_le_bytes());

        // Write script length (u16 LE)
        let script_len = input.script_pubkey.len() as u16;
        data.extend_from_slice(&script_len.to_le_bytes());

        // Write script
        data.extend_from_slice(&input.script_pubkey);
    }

    data
}

/// Represents a payment request as per ZIP 321
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TransactionRequest {
    /// List of payment recipients
    pub payments: Vec<Payment>,
    /// Optional memo for the transaction
    pub memo: Option<String>,
    /// Optional target block height for consensus branch ID selection
    /// If None, defaults to a recent mainnet height
    pub target_height: Option<u32>,
}

/// A single payment to a recipient
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Payment {
    /// The recipient address (unified address with Orchard receiver or transparent address)
    pub address: String,
    /// Amount in zatoshis
    pub amount: u64,
    /// Optional memo for this specific payment
    pub memo: Option<String>,
    /// Optional label for the recipient
    pub label: Option<String>,
    /// Optional message
    pub message: Option<String>,
}

impl TransactionRequest {
    pub fn new(payments: Vec<Payment>) -> Self {
        Self {
            payments,
            memo: None,
            target_height: None,
        }
    }

    pub fn with_memo(mut self, memo: String) -> Self {
        self.memo = Some(memo);
        self
    }

    /// Calculate total amount across all payments
    pub fn total_amount(&self) -> u64 {
        self.payments.iter().map(|p| p.amount).sum()
    }

    /// Check if any payment is to a shielded address
    pub fn has_shielded_outputs(&self) -> bool {
        self.payments.iter().any(|p| !p.is_transparent())
    }
}

impl Payment {
    pub fn new(address: String, amount: u64) -> Self {
        Self {
            address,
            amount,
            memo: None,
            label: None,
            message: None,
        }
    }

    pub fn with_memo(mut self, memo: String) -> Self {
        self.memo = Some(memo);
        self
    }

    pub fn with_label(mut self, label: String) -> Self {
        self.label = Some(label);
        self
    }

    pub fn with_message(mut self, message: String) -> Self {
        self.message = Some(message);
        self
    }

    /// Check if this payment is to a transparent address
    pub fn is_transparent(&self) -> bool {
        // Simple heuristic: transparent addresses start with 't'
        // More robust parsing should be implemented
        self.address.starts_with('t')
    }

    /// Check if this payment is to a unified address
    pub fn is_unified(&self) -> bool {
        // Unified addresses start with 'u'
        self.address.starts_with('u')
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_transaction_request_total_amount() {
        let payments = vec![
            Payment::new("t1address".to_string(), 1000),
            Payment::new("u1address".to_string(), 2000),
        ];
        let request = TransactionRequest::new(payments);
        assert_eq!(request.total_amount(), 3000);
    }

    #[test]
    fn test_payment_address_detection() {
        let t_payment = Payment::new("t1transparent".to_string(), 1000);
        let u_payment = Payment::new("u1unified".to_string(), 2000);

        assert!(t_payment.is_transparent());
        assert!(!t_payment.is_unified());

        assert!(!u_payment.is_transparent());
        assert!(u_payment.is_unified());
    }
}
