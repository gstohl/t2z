#!/usr/bin/env node
/**
 * Cross-platform clean script
 * Removes build artifacts and native libraries from all bindings
 *
 * Usage: node scripts/clean.js
 */

const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..');

function main() {
  console.log('Cleaning build artifacts...\n');

  // 1. Clean Rust target directory
  const rustTarget = path.join(ROOT, 'core', 'rust', 'target');
  if (fs.existsSync(rustTarget)) {
    console.log('Removing Rust target directory...');
    fs.rmSync(rustTarget, { recursive: true, force: true });
    console.log('  -> core/rust/target removed');
  } else {
    console.log('  -> core/rust/target (not present)');
  }

  // 2. Clean native libraries from bindings
  const libDirs = [
    'bindings/typescript/lib',
    'bindings/go/lib',
    'bindings/kotlin/src/main/resources',
    'bindings/java/src/main/resources',
  ];

  console.log('\nRemoving native libraries from bindings:');
  for (const libDir of libDirs) {
    const fullPath = path.join(ROOT, libDir);
    if (fs.existsSync(fullPath)) {
      // Remove platform-specific subdirectories
      const entries = fs.readdirSync(fullPath, { withFileTypes: true });
      for (const entry of entries) {
        if (entry.isDirectory()) {
          const subDir = path.join(fullPath, entry.name);
          fs.rmSync(subDir, { recursive: true, force: true });
          console.log(`  -> ${libDir}/${entry.name} removed`);
        }
      }
    }
  }

  // 3. Clean TypeScript build artifacts
  const tsDistDirs = [
    'bindings/typescript/dist',
    'bindings/typescript/node_modules',
  ];

  console.log('\nRemoving TypeScript build artifacts:');
  for (const dir of tsDistDirs) {
    const fullPath = path.join(ROOT, dir);
    if (fs.existsSync(fullPath)) {
      fs.rmSync(fullPath, { recursive: true, force: true });
      console.log(`  -> ${dir} removed`);
    } else {
      console.log(`  -> ${dir} (not present)`);
    }
  }

  // 4. Clean Kotlin/Java build artifacts
  const jvmBuildDirs = [
    'bindings/kotlin/build',
    'bindings/kotlin/.gradle',
    'bindings/java/build',
    'bindings/java/.gradle',
  ];

  console.log('\nRemoving JVM build artifacts:');
  for (const dir of jvmBuildDirs) {
    const fullPath = path.join(ROOT, dir);
    if (fs.existsSync(fullPath)) {
      fs.rmSync(fullPath, { recursive: true, force: true });
      console.log(`  -> ${dir} removed`);
    } else {
      console.log(`  -> ${dir} (not present)`);
    }
  }

  // 5. Clean Go test cache (optional, doesn't remove go.sum)
  console.log('\nClearing Go test cache...');
  try {
    execSync('go clean -testcache', { cwd: path.join(ROOT, 'bindings', 'go'), stdio: 'ignore' });
    console.log('  -> Go test cache cleared');
  } catch (err) {
    console.log('  -> Go test cache (skipped)');
  }

  console.log('\nDone! All build artifacts cleaned.');
}

main();
