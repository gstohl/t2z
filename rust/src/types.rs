use serde::{Deserialize, Serialize};

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

/// Represents a payment request as per ZIP 321
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TransactionRequest {
    /// List of payment recipients
    pub payments: Vec<Payment>,
    /// Optional memo for the transaction
    pub memo: Option<String>,
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
