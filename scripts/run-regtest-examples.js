#!/usr/bin/env node
/**
 * Run all regtest examples for all language bindings
 *
 * This script:
 * 1. Starts zebrad regtest container
 * 2. Waits for mining to produce blocks
 * 3. Runs setup + all examples for each language
 * 4. Resets the container between languages (fresh state)
 *
 * Usage:
 *   node scripts/run-regtest-examples.js           # Run all languages
 *   node scripts/run-regtest-examples.js --ts      # TypeScript only
 *   node scripts/run-regtest-examples.js --go      # Go only
 *   node scripts/run-regtest-examples.js --kotlin  # Kotlin only
 *   node scripts/run-regtest-examples.js --java    # Java only
 */

import { spawn, execSync } from 'child_process';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';
import { setTimeout as delay } from 'timers/promises';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = join(__dirname, '..');

const INFRA_DIR = join(ROOT, 'infra', 'zebrad-regtest');
const RPC_URL = 'http://localhost:18232';

// Colors for output
const colors = {
  reset: '\x1b[0m',
  bright: '\x1b[1m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  red: '\x1b[31m',
  cyan: '\x1b[36m',
  magenta: '\x1b[35m',
};

function log(msg, color = colors.reset) {
  console.log(`${color}${msg}${colors.reset}`);
}

function logSection(msg) {
  console.log();
  log(`${'='.repeat(60)}`, colors.cyan);
  log(`  ${msg}`, colors.bright + colors.cyan);
  log(`${'='.repeat(60)}`, colors.cyan);
  console.log();
}

function logStep(msg) {
  log(`>>> ${msg}`, colors.yellow);
}

function logSuccess(msg) {
  log(`✓ ${msg}`, colors.green);
}

function logError(msg) {
  log(`✗ ${msg}`, colors.red);
}

// Run a command and return promise
function run(cmd, cwd = ROOT, options = {}) {
  return new Promise((resolve, reject) => {
    const [command, ...args] = cmd.split(' ');
    const proc = spawn(command, args, {
      cwd,
      stdio: options.silent ? ['ignore', 'pipe', 'pipe'] : ['ignore', 'inherit', 'inherit'],
      shell: true,
      env: { ...process.env },
      detached: false,
    });

    let stdout = '';
    let stderr = '';

    if (options.silent) {
      proc.stdout?.on('data', (data) => { stdout += data; });
      proc.stderr?.on('data', (data) => { stderr += data; });
    }

    proc.on('exit', (code) => {
      // Small delay to let child processes fully terminate
      setTimeout(() => {
        if (code === 0) {
          resolve({ stdout, stderr });
        } else {
          reject(new Error(`Command failed with code ${code}: ${cmd}`));
        }
      }, 100);
    });

    proc.on('error', reject);
  });
}

// Check if zebrad RPC is responding
async function isZebraReady() {
  try {
    const result = execSync(`curl -sf -X POST -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","method":"getblockchaininfo","params":[],"id":1}' ${RPC_URL}`, {
      encoding: 'utf8',
      timeout: 5000,
    });
    const json = JSON.parse(result);
    return json.result?.blocks > 0;
  } catch {
    return false;
  }
}

// Wait for zebrad to be ready with blocks
async function waitForZebra(maxWaitSeconds = 300) {
  logStep(`Waiting for zebrad to be ready (max ${maxWaitSeconds}s)...`);

  const startTime = Date.now();
  let lastBlockCount = 0;

  while ((Date.now() - startTime) < maxWaitSeconds * 1000) {
    try {
      const result = execSync(`curl -sf -X POST -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","method":"getblockchaininfo","params":[],"id":1}' ${RPC_URL}`, {
        encoding: 'utf8',
        timeout: 5000,
      });
      const json = JSON.parse(result);
      const blocks = json.result?.blocks || 0;

      if (blocks !== lastBlockCount) {
        log(`  Block height: ${blocks}`, colors.cyan);
        lastBlockCount = blocks;
      }

      // Need at least 120 blocks for coinbase maturity (100) + buffer
      if (blocks >= 120) {
        logSuccess(`Zebrad ready with ${blocks} blocks`);
        return true;
      }
    } catch {
      // Not ready yet
    }

    await delay(3000);
  }

  throw new Error('Timeout waiting for zebrad');
}

// Start docker container
async function startZebra() {
  logStep('Starting zebrad regtest container...');
  await run('docker-compose up -d', INFRA_DIR);
  await waitForZebra();
}

// Stop and reset docker container
async function resetZebra() {
  logStep('Resetting zebrad container (cleaning data)...');
  await run('docker-compose down -v', INFRA_DIR);
}

// Stop docker container
async function stopZebra() {
  logStep('Stopping zebrad container...');
  await run('docker-compose down', INFRA_DIR);
}

// Run TypeScript examples
async function runTypeScript() {
  logSection('TypeScript Examples');

  const examplesDir = join(ROOT, 'bindings', 'typescript', 'examples', 'zebrad-regtest');

  logStep('Installing dependencies...');
  await run('npm install', examplesDir);

  logStep('Running setup...');
  await run('npm run setup', examplesDir);

  // Run examples individually to ensure clean process exit between each
  const examples = [1, 2, 3, 4, 5, 6, 7, 8, 9];

  for (const n of examples) {
    logStep(`Running example ${n}...`);
    await run(`npm run example:${n}`, examplesDir);
  }

  logSuccess('TypeScript examples completed');
}

// Run Go examples
async function runGo() {
  logSection('Go Examples');

  const examplesDir = join(ROOT, 'bindings', 'go', 'examples', 'zebrad-regtest');
  const dataDir = join(examplesDir, 'data');

  // Set environment variable for data directory
  process.env.T2Z_DATA_DIR = dataDir;

  logStep('Running setup...');
  await run('go run ./setup', examplesDir);

  const examples = [
    '1-single-output',
    '2-multiple-outputs',
    '3-utxo-consolidation',
    '4-attack-scenario',
    '5-shielded-output',
    '6-multiple-shielded',
    '7-mixed-outputs',
    '8-combine-workflow',
    '9-offline-signing',
  ];

  for (const example of examples) {
    logStep(`Running example ${example}...`);
    await run(`go run ./${example}`, examplesDir);
  }

  logSuccess('Go examples completed');
}

// Run Kotlin examples
async function runKotlin() {
  logSection('Kotlin Examples');

  const kotlinLibDir = join(ROOT, 'bindings', 'kotlin');
  const examplesDir = join(ROOT, 'bindings', 'kotlin', 'examples', 'zebrad-regtest');

  // Build the Kotlin library first
  logStep('Building Kotlin library...');
  await run('./gradlew build', kotlinLibDir);

  logStep('Running setup...');
  await run('./gradlew setup', examplesDir);

  const examples = [1, 2, 3, 4, 5, 6, 7, 8, 9];

  for (const n of examples) {
    logStep(`Running example ${n}...`);
    await run(`./gradlew example${n}`, examplesDir);
  }

  logSuccess('Kotlin examples completed');
}

// Run Java examples
async function runJava() {
  logSection('Java Examples');

  const javaLibDir = join(ROOT, 'bindings', 'java');
  const examplesDir = join(ROOT, 'bindings', 'java', 'examples', 'zebrad-regtest');

  // Build the Java library first
  logStep('Building Java library...');
  await run('./gradlew build', javaLibDir);

  logStep('Running setup...');
  await run('./gradlew setup', examplesDir);

  const examples = [1, 2, 3, 4, 5, 6, 7, 8, 9];

  for (const n of examples) {
    logStep(`Running example ${n}...`);
    await run(`./gradlew example${n}`, examplesDir);
  }

  logSuccess('Java examples completed');
}

// Parse command line arguments
function parseArgs() {
  const args = process.argv.slice(2);

  if (args.length === 0) {
    return { all: true };
  }

  return {
    ts: args.includes('--ts') || args.includes('--typescript'),
    go: args.includes('--go'),
    kotlin: args.includes('--kotlin'),
    java: args.includes('--java'),
  };
}

// Main
async function main() {
  const args = parseArgs();
  const languages = [];

  if (args.all) {
    languages.push(
      { name: 'Go', run: runGo },
      { name: 'TypeScript', run: runTypeScript },
      { name: 'Kotlin', run: runKotlin },
      { name: 'Java', run: runJava },
    );
  } else {
    if (args.ts) languages.push({ name: 'TypeScript', run: runTypeScript });
    if (args.go) languages.push({ name: 'Go', run: runGo });
    if (args.kotlin) languages.push({ name: 'Kotlin', run: runKotlin });
    if (args.java) languages.push({ name: 'Java', run: runJava });
  }

  if (languages.length === 0) {
    console.log('Usage: node scripts/run-regtest-examples.js [--ts] [--go] [--kotlin] [--java]');
    process.exit(1);
  }

  logSection('t2z Regtest Examples Runner');
  log(`Languages: ${languages.map(l => l.name).join(', ')}`);

  const results = [];

  // Check if zebra is running and what state it's in
  let needsReset = false;
  let needsStart = false;

  try {
    const result = execSync(`curl -sf -X POST -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","method":"getblockchaininfo","params":[],"id":1}' ${RPC_URL}`, {
      encoding: 'utf8',
      timeout: 5000,
    });
    const json = JSON.parse(result);
    const blocks = json.result?.blocks || 0;
    if (blocks > 0 && blocks < 2000) {
      log(`Zebra already running with ${blocks} blocks, reusing...`, colors.green);
    } else if (blocks >= 2000) {
      log(`Zebra has ${blocks} blocks (>2000), resetting...`, colors.yellow);
      needsReset = true;
    }
  } catch {
    // RPC not responding - container might not be running
    log('Zebra RPC not responding, checking container...', colors.yellow);
    needsStart = true;
  }

  // If we need to start, try starting first without reset
  if (needsStart) {
    try {
      logStep('Starting zebrad container...');
      await run('docker-compose up -d', INFRA_DIR);
      await delay(5000); // Give it a moment to start

      // Check if it's responding now
      const result = execSync(`curl -sf -X POST -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","method":"getblockchaininfo","params":[],"id":1}' ${RPC_URL}`, {
        encoding: 'utf8',
        timeout: 10000,
      });
      const json = JSON.parse(result);
      const blocks = json.result?.blocks || 0;

      if (blocks > 0 && blocks < 2000) {
        log(`Container started, found ${blocks} blocks, reusing...`, colors.green);
        needsStart = false;
      } else if (blocks >= 2000) {
        log(`Container has ${blocks} blocks (>2000), resetting...`, colors.yellow);
        needsReset = true;
      } else {
        log(`Container started but no blocks yet, waiting for mining...`, colors.yellow);
        needsStart = false; // It's started, just needs to mine
      }
    } catch {
      log('Container failed to respond, will reset...', colors.yellow);
      needsReset = true;
    }
  }

  if (needsReset) {
    await resetZebra();
    await startZebra();
  } else {
    // Container already running or was started above, just ensure we have enough blocks
    await waitForZebra();
  }

  for (let i = 0; i < languages.length; i++) {
    const lang = languages[i];
    log(`DEBUG: Starting ${lang.name} (${i + 1}/${languages.length})`, colors.magenta);

    try {
      await lang.run();

      results.push({ name: lang.name, success: true });

      // Add delay between languages and verify zebrad is still responsive
      if (i < languages.length - 1) {
        log('Waiting for processes to settle...', colors.cyan);
        await delay(2000);

        // Verify zebrad is still responsive before continuing
        log('Verifying zebrad is responsive...', colors.cyan);
        for (let attempt = 0; attempt < 10; attempt++) {
          if (await isZebraReady()) {
            log('Zebrad is responsive', colors.green);
            break;
          }
          if (attempt === 9) {
            log('Warning: zebrad not responding, restarting container...', colors.yellow);
            await run('docker restart zebrad-regtest', INFRA_DIR, { silent: true });
            await waitForZebra();
          }
          await delay(1000);
        }
      }
    } catch (error) {
      logError(`${lang.name} failed: ${error.message}`);
      results.push({ name: lang.name, success: false, error: error.message });
    }
  }

  // Leave container running for future runs
  log('Leaving zebrad container running for reuse...', colors.cyan);

  // Summary
  logSection('Results Summary');

  let allPassed = true;
  for (const result of results) {
    if (result.success) {
      logSuccess(`${result.name}: PASSED`);
    } else {
      logError(`${result.name}: FAILED - ${result.error}`);
      allPassed = false;
    }
  }

  console.log();

  if (allPassed) {
    logSuccess('All examples passed!');
    process.exit(0);
  } else {
    logError('Some examples failed');
    process.exit(1);
  }
}

main().catch((error) => {
  logError(`Fatal error: ${error.message}`);
  process.exit(1);
});
