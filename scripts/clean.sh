#!/bin/bash
cd "$(dirname "$0")/.."

echo "Cleaning Rust..."
rm -rf core/rust/target

echo "Cleaning TypeScript..."
rm -rf bindings/typescript/dist
rm -rf bindings/typescript/node_modules

echo "Cleaning Java..."
rm -rf bindings/java/build
rm -rf bindings/java/.gradle
rm -rf bindings/java/examples/zebrad-t2z/build
rm -rf bindings/java/examples/zebrad-t2z/.gradle

echo "Cleaning Kotlin..."
rm -rf bindings/kotlin/build
rm -rf bindings/kotlin/.gradle
rm -rf bindings/kotlin/examples/zebrad-t2z/build
rm -rf bindings/kotlin/examples/zebrad-t2z/.gradle

echo "Clean complete."
