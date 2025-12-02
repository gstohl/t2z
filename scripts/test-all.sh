#!/bin/bash
set -e
cd "$(dirname "$0")/.."

echo "=== Testing Rust core ==="
(cd core/rust && cargo test)

echo ""
echo "=== Testing Go bindings ==="
(cd bindings/go && go test -v)

echo ""
echo "=== Testing TypeScript bindings ==="
(cd bindings/typescript && npm test)

echo ""
echo "=== Testing Java bindings ==="
(cd bindings/java && ./gradlew test)

echo ""
echo "=== Testing Kotlin bindings ==="
(cd bindings/kotlin && ./gradlew test)

echo ""
echo "All tests passed."
