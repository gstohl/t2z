use thiserror::Error;

/// Errors that can occur during transaction proposal
#[derive(Error, Debug)]
pub enum ProposalError {
    #[error("No inputs provided")]
    NoInputs,

    #[error("Invalid transaction request: {0}")]
    InvalidRequest(String),

    #[error("Invalid address: {0}")]
    InvalidAddress(String),

    #[error("Insufficient funds")]
    InsufficientFunds,

    #[error("Fee calculation error: {0}")]
    FeeCalculation(String),

    #[error("Not implemented")]
    NotImplemented,

    #[error("PCZT creation error: {0}")]
    PcztCreation(String),
}

/// Errors that can occur during proving
#[derive(Error, Debug)]
pub enum ProverError {
    #[error("No Orchard outputs to prove")]
    NoOrchardOutputs,

    #[error("Proving key not available")]
    ProvingKeyUnavailable,

    #[error("Proof generation failed: {0}")]
    ProofGenerationFailed(String),

    #[error("Orchard proof creation failed: {0}")]
    OrchardProof(String),

    #[error("Not implemented")]
    NotImplemented,
}

/// Errors that can occur during verification
#[derive(Error, Debug)]
pub enum VerificationFailure {
    #[error("Transaction request mismatch")]
    RequestMismatch,

    #[error("Change output mismatch")]
    ChangeMismatch,

    #[error("Invalid fee")]
    InvalidFee,

    #[error("Output mismatch: {0}")]
    OutputMismatch(String),

    #[error("Not implemented")]
    NotImplemented,
}

/// Errors that can occur during signature hash calculation
#[derive(Error, Debug)]
pub enum SighashError {
    #[error("Invalid input index: {0}")]
    InvalidInputIndex(usize),

    #[error("Missing input data")]
    MissingInputData,

    #[error("Signature hash calculation failed: {0}")]
    CalculationFailed(String),

    #[error("Not implemented")]
    NotImplemented,
}

/// Errors that can occur when adding signatures
#[derive(Error, Debug)]
pub enum SignatureError {
    #[error("Invalid input index: {0}")]
    InvalidInputIndex(usize),

    #[error("Signature verification failed")]
    VerificationFailed,

    #[error("Invalid signature format")]
    InvalidFormat,

    #[error("Missing public key")]
    MissingPublicKey,

    #[error("Not implemented")]
    NotImplemented,
}

/// Errors that can occur during PCZT combination
#[derive(Error, Debug)]
pub enum CombineError {
    #[error("No PCZTs provided")]
    NoPczts,

    #[error("PCZT data mismatch - PCZTs represent different transactions")]
    DataMismatch,

    #[error("Incompatible PCZTs: {0}")]
    IncompatiblePczts(String),

    #[error("Combination failed: {0}")]
    CombinationFailed(String),

    #[error("Not implemented")]
    NotImplemented,
}

/// Errors that can occur during finalization and extraction
#[derive(Error, Debug)]
pub enum FinalizationError {
    #[error("Missing signatures")]
    MissingSignatures,

    #[error("Missing proofs")]
    MissingProofs,

    #[error("Spend finalization failed: {0}")]
    SpendFinalization(String),

    #[error("Transaction extraction failed: {0}")]
    TransactionExtraction(String),

    #[error("Transaction serialization failed: {0}")]
    Serialization(String),

    #[error("Verification failed: {0}")]
    VerificationFailed(String),

    #[error("Extraction failed: {0}")]
    ExtractionFailed(String),

    #[error("Not implemented")]
    NotImplemented,
}

/// Errors that can occur during PCZT parsing
#[derive(Error, Debug)]
pub enum ParseError {
    #[error("Invalid format: {0}")]
    InvalidFormat(String),

    #[error("Unsupported version")]
    UnsupportedVersion,

    #[error("Corrupted data")]
    CorruptedData,
}

/// Generic error type for FFI boundary
#[derive(Error, Debug)]
pub enum FfiError {
    #[error("Null pointer provided")]
    NullPointer,

    #[error("Invalid UTF-8 string")]
    InvalidUtf8,

    #[error("Buffer too small")]
    BufferTooSmall,

    #[error("Proposal error: {0}")]
    Proposal(#[from] ProposalError),

    #[error("Prover error: {0}")]
    Prover(#[from] ProverError),

    #[error("Verification error: {0}")]
    Verification(#[from] VerificationFailure),

    #[error("Sighash error: {0}")]
    Sighash(#[from] SighashError),

    #[error("Signature error: {0}")]
    Signature(#[from] SignatureError),

    #[error("Combine error: {0}")]
    Combine(#[from] CombineError),

    #[error("Finalization error: {0}")]
    Finalization(#[from] FinalizationError),

    #[error("Parse error: {0}")]
    Parse(#[from] ParseError),

    #[error("Not implemented: {0}")]
    NotImplemented(String),
}
