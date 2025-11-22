# Testing Guide for PCZT Library

This guide explains the testing infrastructure and how to use it for rapid development iteration.

## Testing Philosophy

We use a multi-layered testing approach:

1. **Unit Tests** - Test individual functions in isolation
2. **Integration Tests** - Test complete workflows
3. **FFI Tests** - Verify C bindings work correctly
4. **Mock Testing** - Fast iteration without expensive crypto

## Test Structure

```
rust/
├── src/
│   ├── lib.rs          # Inline unit tests with #[cfg(test)]
│   ├── types.rs        # Type tests
│   └── error.rs        # Error tests
└── tests/
    ├── common/
    │   ├── mod.rs      # Test module exports
    │   ├── fixtures.rs # Test data and addresses
    │   └── mock.rs     # Mock implementations
    ├── integration_test.rs   # Full workflow tests
    └── ffi_test.rs          # FFI binding tests
```

## Running Tests

### Run All Tests

```bash
cargo test
```

### Run Only Unit Tests

```bash
cargo test --lib
```

### Run Only Integration Tests

```bash
cargo test --test integration_test
```

### Run Only FFI Tests

```bash
cargo test --test ffi_test
```

### Run With Mock Crypto (Fast!)

```bash
cargo test --features mock-crypto
```

This skips expensive proof generation for rapid iteration.

### Run Specific Test

```bash
cargo test test_payment_request_creation
```

### Run Tests in Parallel

```bash
cargo test -- --test-threads=4
```

### Show Test Output

```bash
cargo test -- --nocapture
```

## Fast Iteration Workflow

### Option 1: Using cargo-watch (Recommended)

Install cargo-watch:

```bash
cargo install cargo-watch
```

Auto-run tests on file changes:

```bash
# Run all tests on change
cargo watch -x test

# Run only fast tests (with mocking)
cargo watch -x 'test --features mock-crypto'

# Run specific test file
cargo watch -x 'test --test integration_test'

# Run and show output
cargo watch -x 'test -- --nocapture'
```

### Option 2: Using cargo-nextest (Faster Test Runner)

Install nextest:

```bash
cargo install cargo-nextest
```

Run tests faster:

```bash
cargo nextest run
```

### Option 3: Manual Iteration

```bash
# 1. Edit code
# 2. Run tests
cargo test --features mock-crypto

# 3. Repeat
```

## Development Workflow

### When Implementing a New Feature

1. **Write the test first** (TDD approach):
   ```bash
   # tests/integration_test.rs
   #[test]
   #[ignore]
   fn test_new_feature() {
       // Your test here
   }
   ```

2. **Run the test** (it should fail):
   ```bash
   cargo test test_new_feature -- --ignored
   ```

3. **Implement the feature** in `src/`

4. **Remove `#[ignore]`** and run again:
   ```bash
   cargo test test_new_feature
   ```

5. **Use auto-watch** for rapid iteration:
   ```bash
   cargo watch -x 'test test_new_feature'
   ```

### When Fixing a Bug

1. **Write a failing test** that reproduces the bug
2. **Fix the bug** until the test passes
3. **Ensure other tests still pass**

## Feature Flags

### `mock-crypto`

Enables mock implementations of expensive operations:

- Mock proof generation (instant)
- Mock signature verification (always succeeds)
- Deterministic test sighashes

**Use when**: Developing workflow logic, not cryptography

```bash
cargo test --features mock-crypto
```

### `test-utils`

Enables additional test utilities (if needed):

```bash
cargo test --features test-utils
```

## Test Categories

### Unit Tests (in src/)

- Fast execution
- Test single functions
- Use `#[cfg(test)]` modules
- Example:

```rust
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_something() {
        // Test here
    }
}
```

### Integration Tests (in tests/)

- Test public API
- Test workflows
- Can use test fixtures
- Example:

```rust
mod common;
use common::*;

#[test]
fn test_workflow() {
    let request = simple_payment_request();
    // Test workflow
}
```

### Ignored Tests

Tests marked with `#[ignore]` are skipped by default.

Run ignored tests:

```bash
cargo test -- --ignored
```

Run all tests including ignored:

```bash
cargo test -- --include-ignored
```

## Test Fixtures

Located in `tests/common/fixtures.rs`:

### Available Fixtures

- **Addresses**: `addresses::TRANSPARENT`, `addresses::UNIFIED_ORCHARD`
- **Amounts**: `amounts::SMALL`, `amounts::MEDIUM`, `amounts::LARGE`
- **Requests**: `simple_payment_request()`, `shielded_payment_request()`
- **Mock Data**: `sample_transparent_inputs()`, `sample_signature()`

### Using Fixtures

```rust
use common::*;

#[test]
fn my_test() {
    let request = simple_payment_request();
    let inputs = sample_transparent_inputs();
    // ...
}
```

## Continuous Testing

### Pre-commit Hook

Create `.git/hooks/pre-commit`:

```bash
#!/bin/sh
cargo test --features mock-crypto
```

Make it executable:

```bash
chmod +x .git/hooks/pre-commit
```

### CI/CD Integration

```yaml
# GitHub Actions example
- name: Run tests
  run: |
    cargo test --all-features
    cargo test --features mock-crypto
```

## Debugging Tests

### Print Debug Info

```rust
#[test]
fn test_something() {
    let value = some_function();
    println!("Debug value: {:?}", value); // Won't show unless --nocapture
    assert_eq!(value, expected);
}
```

Run with:

```bash
cargo test test_something -- --nocapture
```

### Use debugger (lldb/gdb)

```bash
rust-lldb target/debug/deps/integration_test-<hash>
```

## Performance Testing

### Benchmarking (using criterion)

```bash
cargo bench
```

(Note: Benchmark tests would go in `benches/` directory)

## Best Practices

1. ✅ **Write tests before implementation** (TDD)
2. ✅ **Use mock-crypto for rapid iteration**
3. ✅ **Mark expensive tests as `#[ignore]`**
4. ✅ **Use descriptive test names**
5. ✅ **Test both success and error cases**
6. ✅ **Use fixtures for common test data**
7. ✅ **Run tests frequently** (use cargo-watch)
8. ✅ **Keep tests independent** (no shared state)

## Common Issues

### Tests are slow

**Solution**: Use `--features mock-crypto` or mark slow tests with `#[ignore]`

### Tests fail intermittently

**Solution**: Ensure tests don't share mutable state

### Compilation is slow

**Solution**: Use `cargo check` or run specific test files

## Test Coverage

Install tarpaulin:

```bash
cargo install cargo-tarpaulin
```

Generate coverage report:

```bash
cargo tarpaulin --out Html
```

View `tarpaulin-report.html` in browser.

## Quick Reference

| Command | Purpose |
|---------|---------|
| `cargo test` | Run all tests |
| `cargo test --features mock-crypto` | Fast tests with mocking |
| `cargo watch -x test` | Auto-run tests on changes |
| `cargo test -- --nocapture` | Show println! output |
| `cargo test test_name` | Run specific test |
| `cargo test --test integration_test` | Run one test file |
| `cargo nextest run` | Faster test runner |

## Next Steps

1. Implement core functionality
2. Remove `#[ignore]` from tests as features are completed
3. Add more test cases
4. Add benchmark tests in `benches/`
5. Integrate with CI/CD
