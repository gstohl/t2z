/**
 * Hardware Wallet Example - External Signing Workflow
 *
 * This example demonstrates how to use t2z with hardware wallets by:
 * 1. Creating an unsigned PCZT
 * 2. Serializing it for transmission to hardware device
 * 3. Simulating device signing
 * 4. Deserializing and finalizing the signed PCZT
 *
 * This pattern enables:
 * - Hardware wallet integration
 * - Multi-device signing
 * - Air-gapped signing
 * - Coordinator/signer separation
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
  serialize,
  parse,
  verifyBeforeSigning,
  signMessage as secp256k1Sign,
} from 't2z';

// Test keys for demonstration
const PRIVATE_KEY = Buffer.alloc(32, 1);
const PUBLIC_KEY = Buffer.from(
  '031b84c5567b126440995d3ed5aaba0565d71e1834604819ff9c17f5e9d5dd078f',
  'hex'
);
const SCRIPT_PUBKEY = Buffer.from(
  '1976a91479b000887626b294a914501a4cd226b58b23598388ac',
  'hex'
);

/**
 * Simulates a hardware wallet device
 */
class HardwareWalletSimulator {
  private privateKey: Buffer;

  constructor(privateKey: Buffer) {
    this.privateKey = privateKey;
  }

  /**
   * Sign a PCZT on the hardware device
   * In reality, this would happen on secure hardware
   */
  async signPczt(pcztBytes: Buffer): Promise<Buffer> {
    this.displayOnDevice('TRANSACTION REVIEW');
    console.log('  ┌─────────────────────────────────┐');
    console.log('  │  🔐 Hardware Wallet Display  │');
    console.log('  ├─────────────────────────────────┤');

    // Parse PCZT
    const pczt = parse(pcztBytes);
    this.displayOnDevice('Parsing transaction...');

    // Verify before signing
    try {
      verifyBeforeSigning(pczt);
      this.displayOnDevice('✓ Verification passed');
    } catch (error) {
      this.displayOnDevice('✗ Verification FAILED');
      throw error;
    }

    // Get sighash
    this.displayOnDevice('Computing signature hash...');
    const sighash = getSighash(pczt, 0);

    // Sign on device
    this.displayOnDevice('Signing transaction...');
    this.displayOnDevice('Waiting for user confirmation...');
    console.log('  │                                 │');
    console.log('  │  [✓ Approve] [✗ Reject]        │');
    console.log('  │                                 │');
    this.displayOnDevice('User approved!');

    const signature = await secp256k1Sign(this.privateKey, sighash);
    this.displayOnDevice('✓ Signature created');

    // Append signature
    const signed = appendSignature(pczt, 0, signature);

    // Serialize and return
    const signedBytes = serialize(signed);
    this.displayOnDevice('✓ Transaction signed');
    console.log('  └─────────────────────────────────┘');
    console.log();

    return signedBytes;
  }

  private displayOnDevice(message: string) {
    console.log(`  │ ${message.padEnd(31)} │`);
  }
}

async function main() {
  console.log('='.repeat(70));
  console.log('t2z Hardware Wallet Example - External Signing');
  console.log('='.repeat(70));
  console.log();
  console.log('This example demonstrates the PCZT workflow for hardware wallets:');
  console.log('  1. Coordinator creates unsigned PCZT');
  console.log('  2. PCZT is serialized and sent to hardware device');
  console.log('  3. Device verifies, signs, and returns signed PCZT');
  console.log('  4. Coordinator finalizes and broadcasts transaction');
  console.log();

  try {
    // ========================================
    // COORDINATOR: Create unsigned PCZT
    // ========================================
    console.log('─'.repeat(70));
    console.log('COORDINATOR: Creating unsigned PCZT');
    console.log('─'.repeat(70));
    console.log();

    console.log('Step 1: Creating payment request...');
    const payments: Payment[] = [
      {
        address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
        amount: 100_000n,
        memo: 'Payment from hardware wallet',
      },
    ];
    const request = new TransactionRequest(payments);
    console.log('✓ Payment request created');
    console.log();

    console.log('Step 2: Adding transparent inputs...');
    const inputs: TransparentInput[] = [
      {
        pubkey: PUBLIC_KEY,
        txid: Buffer.alloc(32, 0),
        vout: 0,
        amount: 100_000_000n,
        scriptPubKey: SCRIPT_PUBKEY,
      },
    ];
    console.log('✓ Inputs added');
    console.log();

    console.log('Step 3: Creating PCZT...');
    const pczt = proposeTransaction(inputs, request);
    console.log('✓ PCZT created');
    console.log();

    console.log('Step 4: Adding Orchard proofs...');
    const proved = proveTransaction(pczt);
    console.log('✓ Proofs added');
    console.log();

    console.log('Step 5: Serializing PCZT...');
    const pcztBytes = serialize(proved);
    console.log('✓ PCZT serialized');
    console.log(`  Size: ${pcztBytes.length} bytes`);
    console.log(`  Hex: ${pcztBytes.toString('hex').substring(0, 64)}...`);
    console.log();

    // ========================================
    // HARDWARE WALLET: Sign PCZT
    // ========================================
    console.log('─'.repeat(70));
    console.log('HARDWARE WALLET: Signing PCZT');
    console.log('─'.repeat(70));
    console.log();

    const hardwareWallet = new HardwareWalletSimulator(PRIVATE_KEY);
    const signedPcztBytes = await hardwareWallet.signPczt(pcztBytes);

    console.log('✓ Hardware wallet signed the transaction');
    console.log(`  Signed PCZT size: ${signedPcztBytes.length} bytes`);
    console.log();

    // ========================================
    // COORDINATOR: Finalize and extract
    // ========================================
    console.log('─'.repeat(70));
    console.log('COORDINATOR: Finalizing transaction');
    console.log('─'.repeat(70));
    console.log();

    console.log('Step 6: Parsing signed PCZT...');
    const signedPczt = parse(signedPcztBytes);
    console.log('✓ Signed PCZT parsed');
    console.log();

    console.log('Step 7: Finalizing transaction...');
    const txBytes = finalizeAndExtract(signedPczt);
    console.log('✓ Transaction finalized');
    console.log(`  Transaction size: ${txBytes.length} bytes`);
    console.log(`  Transaction hex: ${txBytes.toString('hex').substring(0, 64)}...`);
    console.log();

    console.log('='.repeat(70));
    console.log('SUCCESS! Transaction ready for broadcast');
    console.log('='.repeat(70));
    console.log();
    console.log('Summary:');
    console.log('  ✓ PCZT created by coordinator');
    console.log('  ✓ PCZT serialized and transmitted to hardware wallet');
    console.log('  ✓ Hardware wallet verified and signed PCZT');
    console.log('  ✓ Coordinator finalized transaction');
    console.log('  ✓ Ready for network broadcast');
    console.log();

    request.free();
    proved.free();
  } catch (error) {
    console.error('Error:', error);
    process.exit(1);
  }
}

main();
