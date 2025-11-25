/**
 * Basic Example - Simple Transparent Transaction
 *
 * This example demonstrates the complete workflow for creating a transparent
 * Zcash transaction using the t2z library.
 *
 * Flow:
 * 1. Create payment request
 * 2. Add transparent UTXOs to spend
 * 3. Propose transaction (create PCZT)
 * 4. Add Orchard proofs
 * 5. Get signature hash
 * 6. Sign with private key
 * 7. Append signature
 * 8. Finalize and extract transaction bytes
 */

import {
  Payment,
  TransparentInput,
  TransactionRequest,
  proposeTransaction,
  proveTransaction,
  getSighash,
  appendSignature,
  finalizeAndExtract,
  signMessage,
} from 't2z';

// Test keys for demonstration
// WARNING: Never use these keys in production!
const PRIVATE_KEY = Buffer.alloc(32, 1); // [1u8; 32]
const PUBLIC_KEY = Buffer.from(
  '031b84c5567b126440995d3ed5aaba0565d71e1834604819ff9c17f5e9d5dd078f',
  'hex'
);
const SCRIPT_PUBKEY = Buffer.from(
  '1976a91479b000887626b294a914501a4cd226b58b23598388ac',
  'hex'
);

async function main() {
  console.log('='.repeat(60));
  console.log('t2z Basic Example - Transparent Transaction');
  console.log('='.repeat(60));
  console.log();

  try {
    // Step 1: Create payment request
    console.log('Step 1: Creating payment request...');
    const payments: Payment[] = [
      {
        address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
        amount: 100_000n, // 0.001 ZEC
      },
    ];
    const request = new TransactionRequest(payments);
    console.log('✓ Payment request created');
    console.log(`  Recipient: ${payments[0].address}`);
    console.log(`  Amount: ${payments[0].amount} zatoshis (0.001 ZEC)`);
    console.log();

    // Step 2: Add transparent UTXOs
    console.log('Step 2: Adding transparent inputs...');
    const inputs: TransparentInput[] = [
      {
        pubkey: PUBLIC_KEY,
        txid: Buffer.alloc(32, 0),
        vout: 0,
        amount: 100_000_000n, // 1 ZEC
        scriptPubKey: SCRIPT_PUBKEY,
      },
    ];
    console.log('✓ Transparent input added');
    console.log(`  Amount: ${inputs[0].amount} zatoshis (1 ZEC)`);
    console.log(`  Change: ~${inputs[0].amount - payments[0].amount - 10_000n} zatoshis`);
    console.log();

    // Step 3: Propose transaction (create PCZT)
    console.log('Step 3: Proposing transaction...');
    const pczt = proposeTransaction(inputs, request);
    console.log('✓ PCZT created');
    console.log();

    // Step 4: Add Orchard proofs
    console.log('Step 4: Adding Orchard proofs...');
    const proved = proveTransaction(pczt);
    console.log('✓ Proofs added');
    console.log();

    // Step 5: Get signature hash
    console.log('Step 5: Getting signature hash...');
    const sighash = getSighash(proved, 0);
    console.log('✓ Signature hash computed');
    console.log(`  Sighash: ${sighash.toString('hex')}`);
    console.log();

    // Step 6: Sign with private key
    console.log('Step 6: Signing transaction...');
    const signature = await signMessage(PRIVATE_KEY, sighash);
    console.log('✓ Signature created');
    console.log(`  Signature: ${signature.toString('hex').substring(0, 32)}...`);
    console.log();

    // Step 7: Append signature
    console.log('Step 7: Appending signature to PCZT...');
    const signed = appendSignature(proved, 0, signature);
    console.log('✓ Signature appended');
    console.log();

    // Step 8: Finalize and extract
    console.log('Step 8: Finalizing transaction...');
    const txBytes = finalizeAndExtract(signed);
    console.log('✓ Transaction finalized');
    console.log(`  Transaction size: ${txBytes.length} bytes`);
    console.log(`  Transaction hex: ${txBytes.toString('hex').substring(0, 64)}...`);
    console.log();

    console.log('='.repeat(60));
    console.log('SUCCESS! Transaction ready for broadcast');
    console.log('='.repeat(60));
    console.log();
    console.log('Next steps:');
    console.log('  1. Broadcast transaction to Zcash network');
    console.log('  2. Monitor confirmation status');
    console.log();

    request.free();
  } catch (error) {
    console.error('Error:', error);
    process.exit(1);
  }
}

main();
