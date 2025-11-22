/// Mock implementations for testing without expensive crypto operations
use pczt::Pczt;

#[allow(unused_imports)]
use t2z::error::*;

/// Mock prover that doesn't actually generate proofs (for fast testing)
#[cfg(feature = "mock-crypto")]
pub fn mock_prove_transaction(_pczt: Pczt) -> Result<Pczt, ProverError> {
    // In mock mode, just return the PCZT unchanged
    // This allows testing the workflow without expensive proof generation
    Err(ProverError::NotImplemented) // TODO: Return mock proved PCZT
}

/// Mock signature verification that always succeeds
#[cfg(feature = "mock-crypto")]
pub fn mock_verify_signature(_sighash: &[u8; 32], _signature: &[u8; 64]) -> bool {
    // In mock mode, accept any signature
    true
}

/// Creates a mock PCZT for testing (when we can't create a real one yet)
pub fn create_mock_pczt() -> Result<Pczt, String> {
    // TODO: Create a minimal valid PCZT
    // For now, this is a placeholder
    Err("Mock PCZT creation not implemented".to_string())
}

/// Mock sighash calculation for testing
#[cfg(feature = "mock-crypto")]
pub fn mock_get_sighash(_pczt: &Pczt, input_index: usize) -> Result<[u8; 32], SighashError> {
    // Return a deterministic but fake sighash based on input index
    let mut sighash = [0u8; 32];
    sighash[0] = input_index as u8;
    sighash[31] = 0xFF;
    Ok(sighash)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    #[cfg(feature = "mock-crypto")]
    fn test_mock_sighash() {
        // This test only runs when mock-crypto feature is enabled
        let sighash = mock_get_sighash(&create_mock_pczt().unwrap(), 0).unwrap();
        assert_eq!(sighash[0], 0);
        assert_eq!(sighash[31], 0xFF);
    }

    #[test]
    #[cfg(feature = "mock-crypto")]
    fn test_mock_signature_verification() {
        let sighash = [1u8; 32];
        let signature = [2u8; 64];
        assert!(mock_verify_signature(&sighash, &signature));
    }
}
