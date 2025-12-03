#!/usr/bin/env node
/**
 * Cross-platform test runner script
 * Runs tests for Rust core and all language bindings
 *
 * Usage: node scripts/test-all.js [options]
 *
 * Options:
 *   --rust-only     Run only Rust tests
 *   --bindings-only Run only binding tests (skip Rust)
 *   --skip-build    Skip building before testing
 */

const { execSync, spawnSync } = require('child_process');
const path = require('path');
const fs = require('fs');
const os = require('os');

const ROOT = path.resolve(__dirname, '..');

// Parse command line arguments
const args = process.argv.slice(2);
const rustOnly = args.includes('--rust-only');
const bindingsOnly = args.includes('--bindings-only');
const skipBuild = args.includes('--skip-build');

// Platform detection for library names
const PLATFORMS = {
  'darwin-arm64':  { dir: 'darwin-arm64',  ext: 'dylib', prefix: 'lib' },
  'darwin-x64':    { dir: 'darwin-x64',    ext: 'dylib', prefix: 'lib' },
  'linux-x64':     { dir: 'linux-x64',     ext: 'so',    prefix: 'lib' },
  'linux-arm64':   { dir: 'linux-arm64',   ext: 'so',    prefix: 'lib' },
  'win32-x64':     { dir: 'windows-x64',   ext: 'dll',   prefix: ''    },
};

function run(cmd, cwd, description) {
  console.log(`\n${'='.repeat(70)}`);
  console.log(`  ${description}`);
  console.log('='.repeat(70));
  console.log(`> ${cmd}\n`);

  const result = spawnSync(cmd, {
    cwd,
    shell: true,
    stdio: 'inherit',
  });

  if (result.status !== 0) {
    console.error(`\n❌ FAILED: ${description}`);
    return false;
  }
  console.log(`\n✓ PASSED: ${description}`);
  return true;
}

function checkNativeLibrary() {
  const platform = os.platform();
  const arch = os.arch();
  const key = `${platform}-${arch}`;
  const p = PLATFORMS[key];

  if (!p) {
    console.error(`Unsupported platform: ${key}`);
    return false;
  }

  const libName = `${p.prefix}t2z.${p.ext}`;
  const goLib = path.join(ROOT, 'bindings', 'go', 'lib', p.dir, libName);
  const tsLib = path.join(ROOT, 'bindings', 'typescript', 'lib', p.dir, libName);

  if (!fs.existsSync(goLib) || !fs.existsSync(tsLib)) {
    console.log('\nNative libraries not found. Run build-dev.js first or use --skip-build=false');
    return false;
  }
  return true;
}

function main() {
  console.log('T2Z Test Suite');
  console.log('==============\n');

  const results = [];

  // Build if needed
  if (!skipBuild && !rustOnly) {
    if (!checkNativeLibrary()) {
      console.log('Building native library first...');
      const buildScript = path.join(ROOT, 'scripts', 'build-dev.js');
      const buildResult = spawnSync('node', [buildScript], { stdio: 'inherit' });
      if (buildResult.status !== 0) {
        console.error('Build failed!');
        process.exit(1);
      }
    }
  }

  // 1. Rust tests
  if (!bindingsOnly) {
    const rustDir = path.join(ROOT, 'core', 'rust');
    results.push({
      name: 'Rust Core',
      passed: run('cargo test', rustDir, 'Running Rust tests'),
    });
  }

  // 2. Go tests
  if (!rustOnly) {
    const goDir = path.join(ROOT, 'bindings', 'go');
    if (fs.existsSync(goDir)) {
      results.push({
        name: 'Go Bindings',
        passed: run('go test -v ./...', goDir, 'Running Go tests'),
      });
    }
  }

  // 3. TypeScript tests
  if (!rustOnly) {
    const tsDir = path.join(ROOT, 'bindings', 'typescript');
    if (fs.existsSync(tsDir)) {
      // Install deps if needed
      const nodeModules = path.join(tsDir, 'node_modules');
      if (!fs.existsSync(nodeModules)) {
        console.log('\nInstalling TypeScript dependencies...');
        execSync('npm install', { cwd: tsDir, stdio: 'inherit' });
      }

      results.push({
        name: 'TypeScript Bindings',
        passed: run('npm test', tsDir, 'Running TypeScript tests'),
      });
    }
  }

  // 4. Kotlin tests
  if (!rustOnly) {
    const kotlinDir = path.join(ROOT, 'bindings', 'kotlin');
    const gradlew = path.join(kotlinDir, 'gradlew');
    if (fs.existsSync(kotlinDir) && fs.existsSync(gradlew)) {
      const gradleCmd = os.platform() === 'win32' ? 'gradlew.bat test' : './gradlew test';
      results.push({
        name: 'Kotlin Bindings',
        passed: run(gradleCmd, kotlinDir, 'Running Kotlin tests'),
      });
    }
  }

  // 5. Java tests
  if (!rustOnly) {
    const javaDir = path.join(ROOT, 'bindings', 'java');
    const gradlew = path.join(javaDir, 'gradlew');
    if (fs.existsSync(javaDir) && fs.existsSync(gradlew)) {
      const gradleCmd = os.platform() === 'win32' ? 'gradlew.bat test' : './gradlew test';
      results.push({
        name: 'Java Bindings',
        passed: run(gradleCmd, javaDir, 'Running Java tests'),
      });
    }
  }

  // Summary
  console.log('\n' + '='.repeat(70));
  console.log('  TEST SUMMARY');
  console.log('='.repeat(70));

  let allPassed = true;
  for (const r of results) {
    const status = r.passed ? '✓ PASS' : '✗ FAIL';
    console.log(`  ${status}  ${r.name}`);
    if (!r.passed) allPassed = false;
  }

  console.log('='.repeat(70));

  if (allPassed) {
    console.log('\n✓ All tests passed!\n');
    process.exit(0);
  } else {
    console.log('\n✗ Some tests failed!\n');
    process.exit(1);
  }
}

main();
