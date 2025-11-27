/**
 * Setup script to initialize Zebra regtest environment
 *
 * This script:
 * 1. Waits for Zebra to be ready
 * 2. Waits for coinbase maturity (101 blocks)
 * 3. Saves test keypair data for examples
 *
 * Note: The Zebra internal miner mines to the miner_address configured in
 * zebra.toml (tmEUfekwCArJoFTMEL2kFwQyrsDMCNX5ZFf), which matches our TEST_KEYPAIR.
 *
 * Examples fetch fresh UTXOs dynamically from the blockchain at runtime,
 * so we don't store a stale UTXO list.
 */

import { ZebraClient } from './zebra-client.js';
import { bytesToHex, TEST_KEYPAIR } from './keys.js';
import { getMatureCoinbaseUtxos, zatoshiToZec, clearSpentUtxos } from './utils.js';

async function main() {
  console.log('Setting up Zebra regtest environment...\n');

  // Clear spent UTXOs tracker from previous runs
  await clearSpentUtxos();
  console.log('Cleared spent UTXO tracker\n');

  const client = new ZebraClient();

  // Wait for Zebra to be ready
  console.log('Waiting for Zebra...');
  await client.waitForReady();
  console.log('Zebra is ready\n');

  // Get blockchain info
  const info = await client.getBlockchainInfo();
  console.log(`Chain: ${info.chain}`);
  console.log(`Current height: ${info.blocks}\n`);

  // Mine blocks for coinbase maturity (101 blocks needed)
  // Zebra's internal miner (configured with internal_miner = true) auto-mines blocks
  // We wait for the blocks to be mined
  const TARGET_HEIGHT = 101;
  if (info.blocks < TARGET_HEIGHT) {
    console.log(`Waiting for internal miner to reach height ${TARGET_HEIGHT}...`);
    console.log('(Zebra internal miner auto-mines blocks every ~30 seconds)\n');

    const finalHeight = await client.waitForBlocks(TARGET_HEIGHT, 600000); // 10 min timeout
    console.log(`Reached height ${finalHeight}\n`);
  }

  // Use our pre-defined test keypair (configured in zebra.toml as miner_address)
  console.log('Using test keypair from keys.ts:');
  console.log(`  Address: ${TEST_KEYPAIR.address}`);
  console.log(`  WIF: ${TEST_KEYPAIR.wif}\n`);

  // Get final blockchain info
  const finalInfo = await client.getBlockchainInfo();
  console.log(`Final height: ${finalInfo.blocks}\n`);

  // Check for mature coinbase UTXOs using the proper parsing function
  // Note: Zebra doesn't support gettxout, so we assume fresh regtest UTXOs are unspent
  console.log('Checking for mature coinbase UTXOs...\n');

  const utxos = await getMatureCoinbaseUtxos(client, TEST_KEYPAIR, 10);

  console.log(`Found ${utxos.length} mature coinbase UTXOs for our address`);
  utxos.forEach((utxo, i) => {
    console.log(`  [${i}] ${utxo.txid.toString('hex').slice(0, 16)}...:${utxo.vout} = ${zatoshiToZec(utxo.amount)} ZEC`);
  });

  // Save test keypair data for examples
  // Note: UTXOs are NOT stored here - examples fetch them dynamically from the blockchain
  const fs = await import('fs/promises');

  const testData = {
    transparent: {
      address: TEST_KEYPAIR.address,
      publicKey: bytesToHex(TEST_KEYPAIR.publicKey),
      privateKey: bytesToHex(TEST_KEYPAIR.privateKey),
      wif: TEST_KEYPAIR.wif,
    },
    network: info.chain,
    setupHeight: finalInfo.blocks,
    setupAt: new Date().toISOString(),
  };

  // Write to local data directory
  const path = await import('path');
  const { fileURLToPath } = await import('url');
  const __dirname = path.dirname(fileURLToPath(import.meta.url));
  const dataDir = path.join(__dirname, '..', 'data');
  try {
    await fs.mkdir(dataDir, { recursive: true });
  } catch (e) {
    // Directory may already exist
  }
  await fs.writeFile(path.join(dataDir, 'test-addresses.json'), JSON.stringify(testData, null, 2));
  console.log(`\nSaved test data to ${dataDir}/test-addresses.json`);

  console.log('\nSetup complete!');
  console.log('\nYou can now run the examples:');
  console.log('  npm run example:1  # Single transparent output (T→T)');
  console.log('  npm run example:2  # Multiple transparent outputs (T→T×2)');
  console.log('  npm run example:3  # UTXO consolidation (2 inputs → 1 output)');
  console.log('  npm run example:4  # Attack scenario detection');
  console.log('  npm run example:5  # Single shielded output (T→Z)');
  console.log('  npm run example:6  # Multiple shielded outputs (T→Z×2)');
  console.log('  npm run example:7  # Mixed transparent + shielded (T→T+Z)\n');
}

main().catch((error) => {
  console.error('Setup failed:', error);
  process.exit(1);
});
