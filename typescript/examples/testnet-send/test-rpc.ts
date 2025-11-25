#!/usr/bin/env node
/**
 * Test RPC Connection
 *
 * Simple utility to test if your Zcash RPC is working
 *
 * Usage: npm run test-rpc
 */

import { config } from 'dotenv';

// Load .env file
config();

// ============================================================================
// RPC Client
// ============================================================================

class ZcashRpc {
  constructor(private url: string) {}

  async call<T>(method: string, params: any[] = []): Promise<T> {
    console.log(`\n📞 Calling RPC method: ${method}`);
    console.log(`   Params: ${JSON.stringify(params)}`);

    let response: Response;
    const startTime = Date.now();

    try {
      response = await fetch(this.url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          jsonrpc: '1.0',
          id: Date.now(),
          method,
          params,
        }),
      });
    } catch (error: any) {
      const elapsed = Date.now() - startTime;
      console.error(`   ❌ Network Error (${elapsed}ms)`);
      console.error(`   ${error.message}`);
      throw error;
    }

    const elapsed = Date.now() - startTime;
    console.log(`   ⏱️  Response time: ${elapsed}ms`);
    console.log(`   📊 HTTP Status: ${response.status} ${response.statusText}`);

    // Get response body as text first for debugging
    const text = await response.text();
    console.log(`   📄 Response length: ${text.length} bytes`);

    if (text.length > 0) {
      console.log(`   📝 Response preview: ${text.substring(0, 150)}${text.length > 150 ? '...' : ''}`);
    } else {
      console.log(`   📝 Response is empty`);
    }

    if (!response.ok) {
      console.error(`   ❌ HTTP error response`);
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    let json: any;
    try {
      json = JSON.parse(text);
    } catch (parseError: any) {
      console.error(`   ❌ Failed to parse JSON response`);
      console.error(`   Parse error: ${parseError.message}`);
      console.error(`   Full response body:`);
      console.error(text);
      throw new Error(`Invalid JSON response: ${parseError.message}`);
    }

    if (json.error) {
      console.error(`   ❌ RPC Error: ${json.error.message}`);
      console.error(`   Code: ${json.error.code}`);
      throw new Error(json.error.message);
    }

    console.log(`   ✅ Success!`);
    return json.result;
  }
}

// ============================================================================
// Main
// ============================================================================

async function main() {
  console.log('='.repeat(80));
  console.log('🧪 Testing Zcash RPC Connection');
  console.log('='.repeat(80));

  const rpcUrl = process.env.RPC_URL;

  if (!rpcUrl || rpcUrl.includes('user:pass')) {
    console.error('\n❌ Error: RPC_URL not set in .env file');
    console.error('   Copy .env.example to .env and configure your RPC endpoint');
    process.exit(1);
  }

  console.log(`\n🔗 RPC URL: ${rpcUrl.replace(/\/\/.*:.*@/, '//***:***@')}`);

  const rpc = new ZcashRpc(rpcUrl);

  // Test 1: getblockcount
  console.log('\n' + '─'.repeat(80));
  console.log('Test 1: Get Block Count');
  console.log('─'.repeat(80));
  try {
    const blockCount = await rpc.call<number>('getblockcount', []);
    console.log(`\n✅ Block Count: ${blockCount.toLocaleString()}`);
  } catch (error: any) {
    console.error(`\n❌ Failed: ${error.message}`);
    console.error('\n💡 This usually means:');
    console.error('   - The node is not running');
    console.error('   - Wrong hostname/port in RPC_URL');
    console.error('   - Firewall blocking the connection');
    process.exit(1);
  }

  // Test 2: getinfo (or getblockchaininfo)
  console.log('\n' + '─'.repeat(80));
  console.log('Test 2: Get Blockchain Info');
  console.log('─'.repeat(80));
  try {
    const info = await rpc.call<any>('getblockchaininfo', []);
    console.log(`\n✅ Chain: ${info.chain}`);
    console.log(`   Blocks: ${info.blocks.toLocaleString()}`);
    console.log(`   Best Block Hash: ${info.bestblockhash}`);
    console.log(`   Difficulty: ${info.difficulty.toLocaleString()}`);
    console.log(`   Verification Progress: ${(info.verificationprogress * 100).toFixed(2)}%`);

    if (info.verificationprogress < 0.9999) {
      console.log(`\n⚠️  Warning: Node is still syncing (${(info.verificationprogress * 100).toFixed(2)}%)`);
      console.log('   Wait for full sync before sending transactions');
    }
  } catch (error: any) {
    console.error(`\n❌ Failed: ${error.message}`);
  }

  // Test 3: getnetworkinfo
  console.log('\n' + '─'.repeat(80));
  console.log('Test 3: Get Network Info');
  console.log('─'.repeat(80));
  try {
    const netInfo = await rpc.call<any>('getnetworkinfo', []);
    console.log(`\n✅ Version: ${netInfo.version}`);
    console.log(`   Subversion: ${netInfo.subversion}`);
    console.log(`   Protocol Version: ${netInfo.protocolversion}`);
    console.log(`   Connections: ${netInfo.connections}`);
  } catch (error: any) {
    console.error(`\n❌ Failed: ${error.message}`);
  }

  // Test 4: Check testnet
  console.log('\n' + '─'.repeat(80));
  console.log('Test 4: Verify Testnet Mode');
  console.log('─'.repeat(80));
  try {
    const info = await rpc.call<any>('getblockchaininfo', []);
    if (info.chain === 'test') {
      console.log(`\n✅ Running on TESTNET`);
    } else if (info.chain === 'main') {
      console.log(`\n⚠️  WARNING: Running on MAINNET`);
      console.log('   This example is for testnet only!');
      console.log('   Start zcashd with -testnet flag');
    } else {
      console.log(`\n🔍 Chain: ${info.chain}`);
    }
  } catch (error: any) {
    console.error(`\n❌ Failed: ${error.message}`);
  }

  // Summary
  console.log('\n' + '='.repeat(80));
  console.log('✅ RPC Connection Test Complete');
  console.log('='.repeat(80));
  console.log('\nYour Zcash node is working correctly!');
  console.log('You can now run: npm start');
  console.log('');
}

main().catch((error) => {
  console.error('\n💥 Fatal Error:', error.message);
  console.error('');
  console.error('📋 Troubleshooting:');
  console.error('');
  console.error('1. Check if zcashd is running:');
  console.error('   ps aux | grep zcashd');
  console.error('');
  console.error('2. Start zcashd (testnet):');
  console.error('   zcashd -testnet -daemon');
  console.error('');
  console.error('3. Check zcash.conf:');
  console.error('   cat ~/.zcash/zcash.conf');
  console.error('   Should contain:');
  console.error('     server=1');
  console.error('     rpcuser=your_username');
  console.error('     rpcpassword=your_password');
  console.error('     rpcallowip=127.0.0.1');
  console.error('');
  console.error('4. Test with curl:');
  console.error('   curl --user user:pass --data-binary \'{"jsonrpc":"1.0","id":"test","method":"getblockcount","params":[]}\' http://127.0.0.1:18232/');
  console.error('');
  process.exit(1);
});
