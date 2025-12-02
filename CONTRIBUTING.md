# Contributing to t2z

Thank you for your interest in contributing to **t2z (Transparent to Zcash)**! This document provides guidelines for contributing to the project.

## Repository Structure

This repository contains multiple language implementations:

```
t2z/
├── core/
│   └── rust/          # Core Rust library (FFI exports)
├── bindings/
│   ├── go/            # Go bindings
│   ├── typescript/    # TypeScript bindings
│   ├── java/          # Java bindings
│   └── kotlin/        # Kotlin bindings
└── infra/             # Docker infrastructure
```

## Language-Specific Guidelines

### Rust Library (Core FFI)

See the **Development Tips** section below for Rust-specific guidance.

### Go Bindings

When contributing to Go bindings:

- **Style**: Follow standard Go conventions (`gofmt`, `golint`)
- **Testing**: Minimum 80% coverage, 100% for critical paths
- **Documentation**: Use godoc format with examples
- **Memory**: Pay special attention to CGO memory management (see below)

## General Guidelines

### Getting Started

This is a **private repository**. To contribute:

1. **Get access**: Request collaborator access from repository owner
2. **Configure Git**: Set up SSH authentication
   ```bash
   ssh -T git@github.com
   ```
3. **Clone**: Clone the repository
   ```bash
   git clone git@github.com:gstohl/t2z.git
   ```

### Repository Access

Configure your environment for the private repo:

```bash
# For Go development
go env -w GOPRIVATE=github.com/gstohl/t2z

# For Rust development (if using as dependency)
# Add to ~/.cargo/config.toml:
[net]
git-fetch-with-cli = true
```

## Contribution Workflow

### 1. Create an Issue

Before starting work:

- **Bug**: Describe the issue with reproduction steps
- **Feature**: Describe the use case and proposed solution
- **Discussion**: Engage with maintainers on approach

### 2. Fork and Branch

```bash
# Create feature branch
git checkout -b feature/your-feature-name

# Or for bug fix
git checkout -b fix/issue-description
```

### 3. Make Changes

Follow language-specific guidelines:
- **Rust**: See [core/rust/CONTRIBUTING.md](core/rust/CONTRIBUTING.md)
- **Go**: See [bindings/go/CONTRIBUTING.md](bindings/go/CONTRIBUTING.md)

### 4. Test Thoroughly

```bash
# Test Rust library
cd core/rust
cargo test
cargo clippy

# Test Go bindings
cd bindings/go
go test -v
go test -race
```

### 5. Commit Guidelines

Use conventional commits:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `test`: Tests
- `refactor`: Code restructuring
- `perf`: Performance
- `chore`: Maintenance

**Scopes**:
- `rust`: Rust core library
- `go`: Go bindings
- `ffi`: FFI interface
- `docs`: Documentation
- `ci`: CI/CD

**Examples**:

```
feat(rust): add Combine PCZT function

Implements the Combiner role from ZIP 374 to merge
multiple partially signed PCZTs.

Closes #42
```

```
fix(go): prevent double-free in PCZT cleanup

Removed automatic finalizers that caused double-free
panics when explicitly calling Free().

Fixes #38
```

### 6. Submit Pull Request

Create a PR with:

- **Descriptive title**: Follows conventional commit format
- **Description**: What changed and why
- **Testing**: Test results and coverage
- **References**: Related issues

## Cross-Language Considerations

### FFI Changes

If modifying the FFI interface:

1. **Update Rust**: Modify `core/rust/src/ffi.rs`
2. **Regenerate headers**:
   ```bash
   cd core/rust
   cargo build
   cbindgen --output include/t2z.h
   ```
3. **Update Go**: Modify Go bindings to match
4. **Test both**: Ensure Rust and Go tests pass
5. **Update docs**: Document API changes

### Testing Consistency

Use **identical test vectors** across languages:

- Same test keys: `[1u8; 32]`
- Same pubkey: `031b84c5567b126440995d3ed5aaba0565d71e1834604819ff9c17f5e9d5dd078f`
- Same script: `1976a91479b000887626b294a914501a4cd226b58b23598388ac`

This ensures behavioral consistency.

## Documentation

### Code Documentation

- **Rust**: Use `///` doc comments
- **Go**: Use godoc format

### Examples

Add examples demonstrating new features:

- **Rust**: `examples/` directory or doc tests
- **Go**: `examples/` directory and `Example*` functions

### README Updates

Update relevant READMEs:

- Root README for high-level changes
- Language-specific READMEs for API changes

## Code Review

All contributions require:

1. **Automated checks**: CI must pass
2. **Code review**: At least one approval
3. **Testing**: Tests must pass with good coverage
4. **Documentation**: Docs must be updated

## Security

### Reporting Security Issues

**DO NOT** open public issues for security vulnerabilities.

Email maintainers directly with:
- Description of vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

### Security Considerations

When contributing:

- **Validate inputs**: All FFI boundaries
- **Prevent memory leaks**: Proper cleanup in all paths
- **Avoid panics**: Handle errors gracefully
- **Constant-time operations**: For cryptographic operations
- **Audit dependencies**: Be cautious with new dependencies

## Community

### Code of Conduct

- Be respectful and professional
- Welcome newcomers
- Focus on constructive feedback
- Respect maintainer decisions

### Communication

- **Issues**: For bugs and feature requests
- **Pull Requests**: For code contributions
- **Discussions**: For questions and ideas

## License

By contributing, you agree that your contributions will be licensed under the project's license.

## Development Tips

### Rust Development

```bash
# Build library
cd core/rust
cargo build --release

# Run tests
cargo test

# Lint
cargo clippy

# Format
cargo fmt

# Regenerate FFI headers
cbindgen --output include/t2z.h
```

### Go Development

```bash
# Build (requires Rust library)
cd go
go build

# Run tests
go test -v
go test -race         # with race detector
go test -cover        # with coverage

# Format
gofmt -s -w .

# Lint
golangci-lint run

# Run examples
cd examples/basic
go run main.go
```

### Memory Management (Go/CGO)

**Critical patterns to follow**:

```go
// ✅ Correct: Functions that consume handles
pczt, _ := ProposeTransaction(inputs, request)
proved, _ := ProveTransaction(pczt)  // pczt is consumed
// Don't call pczt.Free() - it's already freed by Rust

// ✅ Correct: Non-consuming functions
pczt, _ := ProposeTransaction(inputs, request)
sighash, _ := GetSighash(pczt, 0)   // pczt is NOT consumed
pczt.Free()                          // Safe to free

// ❌ Wrong: Double free
pczt, _ := ProposeTransaction(inputs, request)
proved, _ := ProveTransaction(pczt)
pczt.Free()  // WRONG! Already freed by ProveTransaction
```

## Questions?

- **General**: Open a GitHub issue
- **Security**: Email maintainers privately

---

Thank you for contributing to t2z! Your efforts help make Zcash more accessible. 🚀
