#!/usr/bin/env node
/**
 * Cross-platform build script for development
 * Builds the Rust library and copies it to all binding directories
 *
 * Usage: node scripts/build-dev.js
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const os = require('os');

const ROOT = path.resolve(__dirname, '..');

// Platform configurations
// Go uses static libraries (.a), others use dynamic (.dylib/.so/.dll)
const PLATFORMS = {
  'darwin-arm64':  { dir: 'darwin-arm64',  jna: 'darwin-aarch64', dynExt: 'dylib', staticExt: 'a',   prefix: 'lib' },
  'darwin-x64':    { dir: 'darwin-x64',    jna: 'darwin-x86-64',  dynExt: 'dylib', staticExt: 'a',   prefix: 'lib' },
  'linux-x64':     { dir: 'linux-x64',     jna: 'linux-x86-64',   dynExt: 'so',    staticExt: 'a',   prefix: 'lib' },
  'linux-arm64':   { dir: 'linux-arm64',   jna: 'linux-aarch64',  dynExt: 'so',    staticExt: 'a',   prefix: 'lib' },
  'win32-x64':     { dir: 'windows-x64',   jna: 'win32-x86-64',   dynExt: 'dll',   staticExt: 'lib', prefix: ''    },
};

function main() {
  // 1. Detect platform
  const platform = os.platform();
  const arch = os.arch();
  const key = `${platform}-${arch}`;

  const p = PLATFORMS[key];
  if (!p) {
    console.error(`Unsupported platform: ${key}`);
    console.error('Supported platforms:', Object.keys(PLATFORMS).join(', '));
    process.exit(1);
  }

  console.log(`Platform: ${key}`);
  console.log('');

  // 2. Build Rust library
  console.log('Building Rust library...');
  try {
    execSync('cargo build --release', {
      cwd: path.join(ROOT, 'core', 'rust'),
      stdio: 'inherit',
    });
  } catch (err) {
    console.error('Rust build failed');
    process.exit(1);
  }
  console.log('');

  // 3. Find the built libraries
  const dynLibName = `${p.prefix}t2z.${p.dynExt}`;
  const staticLibName = `${p.prefix}t2z.${p.staticExt}`;
  const dynSrc = path.join(ROOT, 'core', 'rust', 'target', 'release', dynLibName);
  const staticSrc = path.join(ROOT, 'core', 'rust', 'target', 'release', staticLibName);

  if (!fs.existsSync(dynSrc)) {
    console.error(`Dynamic library not found: ${dynSrc}`);
    process.exit(1);
  }
  if (!fs.existsSync(staticSrc)) {
    console.error(`Static library not found: ${staticSrc}`);
    process.exit(1);
  }

  console.log(`Built: ${dynSrc}`);
  console.log(`Built: ${staticSrc}`);
  console.log('');

  // 4. Copy to each binding
  // - Go: static library (single binary, no runtime dependency)
  // - TypeScript/Kotlin/Java: dynamic library (loaded via FFI)
  const targets = [
    { path: `bindings/typescript/lib/${p.dir}`, name: dynLibName, src: dynSrc, type: 'dynamic' },
    { path: `bindings/go/lib/${p.dir}`, name: staticLibName, src: staticSrc, type: 'static' },
    { path: `bindings/kotlin/src/main/resources/${p.jna}`, name: dynLibName, src: dynSrc, type: 'dynamic' },
    { path: `bindings/java/src/main/resources/${p.jna}`, name: dynLibName, src: dynSrc, type: 'dynamic' },
  ];

  console.log('Copying to bindings:');
  for (const target of targets) {
    const dir = path.join(ROOT, target.path);
    const dest = path.join(dir, target.name);

    fs.mkdirSync(dir, { recursive: true });
    fs.copyFileSync(target.src, dest);
    console.log(`  -> ${target.path}/${target.name} (${target.type})`);
  }

  console.log('');
  console.log('Done! Native libraries copied to all bindings.');
  console.log('  - Go: static library (linked into binary)');
  console.log('  - TypeScript/Kotlin/Java: dynamic library (loaded at runtime)');
}

main();
