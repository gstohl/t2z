# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.1.x   | :white_check_mark: |

## Reporting a Vulnerability

If you discover a security vulnerability in t2z, please report it responsibly:

1. **Do NOT** open a public GitHub issue
2. Email the maintainers directly at: [dominik.gstoehl@icloud.com](mailto:dominik.gstoehl@icloud.com)
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Any suggested fixes (optional)

## Response Timeline

- **Initial response**: Within 48 hours
- **Status update**: Within 7 days
- **Fix timeline**: Depends on severity (critical: ASAP, high: 14 days, medium: 30 days)

## Security Considerations

### Cryptographic Operations

This library handles sensitive cryptographic operations for Zcash transactions:

- **Private keys**: Never logged, never stored by the library
- **Signatures**: Created externally via `get_sighash` + `append_signature` (hardware wallet compatible)
- **Proofs**: Generated using official Zcash proving keys

### FFI Boundary

The library exposes a C FFI for cross-language bindings:

- All pointer inputs are validated for null
- Buffer sizes are checked before writes
- Error messages don't leak sensitive data

### Dependencies

- Core cryptography: Official `zcash/librustzcash`
- Secp256k1: Well-audited `secp256k1` crate
- No network operations in core library

## Best Practices for Users

1. **Never commit private keys** - Use environment variables or secure key management
2. **Verify transactions** - Use `verify_before_signing` before signing
3. **Hardware wallets** - Use `get_sighash` for external signing when possible
4. **Keep updated** - Watch for security advisories

## Audit Status

This library has not yet undergone a formal security audit. Use in production at your own risk.
