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
const PLATFORMS = {
  'darwin-arm64':  { dir: 'darwin-arm64',  jna: 'darwin-aarch64', ext: 'dylib', prefix: 'lib' },
  'darwin-x64':    { dir: 'darwin-x64',    jna: 'darwin-x86-64',  ext: 'dylib', prefix: 'lib' },
  'linux-x64':     { dir: 'linux-x64',     jna: 'linux-x86-64',   ext: 'so',    prefix: 'lib' },
  'linux-arm64':   { dir: 'linux-arm64',   jna: 'linux-aarch64',  ext: 'so',    prefix: 'lib' },
  'win32-x64':     { dir: 'windows-x64',   jna: 'win32-x86-64',   ext: 'dll',   prefix: ''    },
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

  // 3. Find the built library
  const libName = `${p.prefix}t2z.${p.ext}`;
  const src = path.join(ROOT, 'core', 'rust', 'target', 'release', libName);

  if (!fs.existsSync(src)) {
    console.error(`Library not found: ${src}`);
    process.exit(1);
  }

  console.log(`Built: ${src}`);
  console.log('');

  // 4. Copy to each binding
  const targets = [
    { path: `bindings/typescript/lib/${p.dir}`, name: libName },
    { path: `bindings/go/lib/${p.dir}`, name: libName },
    { path: `bindings/kotlin/src/main/resources/${p.jna}`, name: libName },
    { path: `bindings/java/src/main/resources/${p.jna}`, name: libName },
  ];

  console.log('Copying to bindings:');
  for (const target of targets) {
    const dir = path.join(ROOT, target.path);
    const dest = path.join(dir, target.name);

    fs.mkdirSync(dir, { recursive: true });
    fs.copyFileSync(src, dest);
    console.log(`  -> ${target.path}/${target.name}`);

    // On macOS, fix the install_name so the library can be loaded from any location
    if (platform === 'darwin') {
      try {
        execSync(`install_name_tool -id "@rpath/${libName}" "${dest}"`, { stdio: 'pipe' });
      } catch (err) {
        // Ignore errors - install_name_tool may not be available
      }
    }
  }

  console.log('');
  console.log('Done! Native library copied to all bindings.');
}

main();
