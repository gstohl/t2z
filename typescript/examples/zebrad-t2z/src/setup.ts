/**
 * Setup script to initialize zcashd regtest environment
 *
 * This script:
 * 1. Waits for zcashd to be ready
 * 2. Mines blocks for coinbase maturity
 * 3. Creates test addresses
 * 4. Funds addresses with test ZEC
 */

import { ZcashdClient } from './zcashd-client.js';
import { zecToZatoshi } from './utils.js';

async function main() {
  console.log('🚀 Setting up zcashd regtest environment...\n');

  const client = new ZcashdClient();

  // Wait for zcashd to be ready
  console.log('Waiting for zcashd...');
  await client.waitForReady();
  console.log('✅ Zcashd is ready\n');

  // Get blockchain info
  const info = await client.getBlockchainInfo();
  console.log(`Chain: ${info.chain}`);
  console.log(`Current height: ${info.blocks}\n`);

  // Mine blocks if we don't have enough
  if (info.blocks < 101) {
    console.log('Mining blocks for coinbase maturity...');
    await client.mineBlocks(101);
  }

  // Create transparent address for receiving funds
  console.log('\n📍 Creating transparent address...');
  const tAddr = await client.getNewAddress();
  console.log(`Transparent address: ${tAddr}`);

  // Get pubkey for the address
  const addrInfo = await client.validateAddress(tAddr);
  console.log(`Public key: ${addrInfo.pubkey}\n`);

  // Fund the address
  console.log('💰 Funding address with 10 ZEC...');
  const fundTxid = await client.sendToAddress(tAddr, 10);
  console.log(`Funding TXID: ${fundTxid}`);

  // Mine a block to confirm
  await client.generate(1);
  console.log('✅ Mined 1 confirmation block\n');

  // Check balance
  const balance = await client.getBalance();
  console.log(`Current wallet balance: ${balance} ZEC\n`);

  // List UTXOs for the address
  const utxos = await client.listUnspent(1, 9999999, [tAddr]);
  console.log(`💎 Available UTXOs for ${tAddr}:`);
  utxos.forEach((utxo) => {
    console.log(`  - ${utxo.txid}:${utxo.vout} → ${utxo.amount} ZEC`);
  });

  // Create a unified address (Orchard)
  console.log('\n🛡️  Creating Orchard account...');
  const { account, address: uAddr } = await client.createAccount();
  console.log(`Account: ${account}`);
  console.log(`Unified address: ${uAddr}\n`);

  console.log('✅ Setup complete!');
  console.log('\nYou can now run the examples:');
  console.log('  npm run example:1  # Single output');
  console.log('  npm run example:2  # Multiple outputs');
  console.log('  npm run example:3  # Mixed outputs (transparent + shielded)');
  console.log('  npm run example:4  # Attack scenario');
  console.log('  npm run all        # Run all examples\n');

  // Save addresses to a temp file for examples to use
  const fs = await import('fs/promises');
  await fs.writeFile(
    'test-addresses.json',
    JSON.stringify(
      {
        transparent: tAddr,
        unified: uAddr,
        account,
        setupAt: new Date().toISOString(),
      },
      null,
      2
    )
  );
  console.log('💾 Saved test addresses to test-addresses.json\n');
}

main().catch((error) => {
  console.error('❌ Setup failed:', error);
  process.exit(1);
});
