#!/bin/bash
set -e
cd "$(dirname "$0")/.."

echo "=== Building Rust core ==="
(cd core/rust && cargo build --release)

echo ""
echo "=== Building Go bindings ==="
(cd bindings/go && go build ./...)

echo ""
echo "=== Building TypeScript bindings ==="
(cd bindings/typescript && npm run build)

echo ""
echo "=== Building Java bindings ==="
(cd bindings/java && ./gradlew build -q)

echo ""
echo "=== Building Kotlin bindings ==="
(cd bindings/kotlin && ./gradlew build -q)

echo ""
echo "All builds complete."
