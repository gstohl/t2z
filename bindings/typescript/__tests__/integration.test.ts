import { describe, it, expect } from 'vitest';
import { createHash } from 'crypto';
import {
  Payment,
  TransparentInput,
  TransparentOutput,
  TransactionRequest,
  proposeTransaction,
  proveTransaction,
  getSighash,
  appendSignature,
  finalizeAndExtract,
  serializePczt,
  parsePczt,
  verifyBeforeSigning,
  signMessage,
} from '../src';

// Test keys matching Go and Rust tests
const TEST_PRIVATE_KEY = Buffer.alloc(32, 1); // [1u8; 32]
const TEST_PUBLIC_KEY = Buffer.from(
  '031b84c5567b126440995d3ed5aaba0565d71e1834604819ff9c17f5e9d5dd078f',
  'hex'
);
// Script pubkey WITHOUT CompactSize prefix (raw 25 bytes for P2PKH)
// The old value had 0x19 prefix, but TransparentInput expects raw script bytes
const TEST_SCRIPT_PUBKEY = Buffer.from(
  '76a91479b000887626b294a914501a4cd226b58b23598388ac',
  'hex'
);
// Generate a realistic-looking txid (not all zeros, which fails validation)
const TEST_TXID = createHash('sha256').update('test transaction for t2z').digest();

describe('t2z TypeScript Bindings', () => {
  describe('TransactionRequest', () => {
    it('should create transaction request with single payment', () => {
      const payments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
          amount: (100_000n).toString(),
        },
      ];

      const request = new TransactionRequest(payments);
      expect(request).toBeDefined();
      request.free();
    });

    it('should create transaction request with multiple payments', () => {
      const payments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
          amount: (50_000n).toString(),
        },
        {
          address: 'tmBsTi2xWTjUdEXnuTceL7fecEQKeWzzhan',
          amount: (30_000n).toString(),
        },
      ];

      const request = new TransactionRequest(payments);
      expect(request).toBeDefined();
      request.free();
    });

    it('should create transaction request with memo', () => {
      const payments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
          amount: (100_000n).toString(),
          memo: 'Test payment',
          label: 'Alice',
          message: 'Hello from TypeScript',
        },
      ];

      const request = new TransactionRequest(payments);
      expect(request).toBeDefined();
      request.free();
    });

    it('should reject empty payment array', () => {
      // The C FFI does not allow null payment pointers
      expect(() => new TransactionRequest([])).toThrow(/Null pointer/);
    });
  });

  describe('Full Transparent Workflow', () => {
    it('should create, sign, and finalize transparent transaction', async () => {
      // Create payment request
      const payments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
          amount: (100_000n).toString(),
        },
      ];

      const request = new TransactionRequest(payments);

      // Create transparent input
      const inputs: TransparentInput[] = [
        {
          pubkey: TEST_PUBLIC_KEY,
          txid: TEST_TXID,
          vout: 0,
          amount: (100_000_000n).toString(), // 1 ZEC
          scriptPubKey: TEST_SCRIPT_PUBKEY,
        },
      ];

      // Propose transaction
      const pczt = proposeTransaction(inputs, request);
      expect(pczt).toBeDefined();

      // Prove transaction
      const proved = proveTransaction(pczt);
      expect(proved).toBeDefined();

      // Verify before signing (no change expected since we're spending exact amount + fee)
      expect(() => verifyBeforeSigning(proved, request, [])).not.toThrow();

      // Get sighash
      const sighash = getSighash(proved, 0);
      expect(sighash).toHaveLength(32);

      // Sign
      const signature = await signMessage(TEST_PRIVATE_KEY, sighash);
      expect(signature).toHaveLength(64);

      // Append signature
      const signed = appendSignature(proved, 0, signature);
      expect(signed).toBeDefined();

      // Finalize and extract
      const txBytes = finalizeAndExtract(signed);
      expect(txBytes.length).toBeGreaterThan(0);

      request.free();
    });

    it('should handle multiple inputs', async () => {
      const payments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
          amount: (150_000n).toString(),
        },
      ];

      const request = new TransactionRequest(payments);

      const inputs: TransparentInput[] = [
        {
          pubkey: TEST_PUBLIC_KEY,
          txid: TEST_TXID,
          vout: 0,
          amount: (100_000_000n).toString(),
          scriptPubKey: TEST_SCRIPT_PUBKEY,
        },
        {
          pubkey: TEST_PUBLIC_KEY,
          txid: createHash('sha256').update('second test transaction').digest(),
          vout: 1,
          amount: (100_000_000n).toString(),
          scriptPubKey: TEST_SCRIPT_PUBKEY,
        },
      ];

      const pczt = proposeTransaction(inputs, request);
      const proved = proveTransaction(pczt);

      // Sign both inputs
      const sighash1 = getSighash(proved, 0);
      const sig1 = await signMessage(TEST_PRIVATE_KEY, sighash1);
      const signed1 = appendSignature(proved, 0, sig1);

      const sighash2 = getSighash(signed1, 1);
      const sig2 = await signMessage(TEST_PRIVATE_KEY, sighash2);
      const signed2 = appendSignature(signed1, 1, sig2);

      const txBytes = finalizeAndExtract(signed2);
      expect(txBytes.length).toBeGreaterThan(0);

      request.free();
    });
  });

  describe('PCZT Serialization', () => {
    it('should serialize and parse PCZT', () => {
      const payments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
          amount: (100_000n).toString(),
        },
      ];

      const request = new TransactionRequest(payments);

      const inputs: TransparentInput[] = [
        {
          pubkey: TEST_PUBLIC_KEY,
          txid: TEST_TXID,
          vout: 0,
          amount: (100_000_000n).toString(),
          scriptPubKey: TEST_SCRIPT_PUBKEY,
        },
      ];

      const pczt = proposeTransaction(inputs, request);
      const proved = proveTransaction(pczt);

      // Serialize
      const serialized = serializePczt(proved);
      expect(serialized.length).toBeGreaterThan(0);

      // Parse
      const parsed = parsePczt(serialized);
      expect(parsed).toBeDefined();

      // Should be able to continue workflow with parsed PCZT
      const sighash = getSighash(parsed, 0);
      expect(sighash).toHaveLength(32);

      parsed.free();
      request.free();
    });
  });

  describe('Error Handling', () => {
    it('should reject invalid input index for getSighash', () => {
      const payments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
          amount: (100_000n).toString(),
        },
      ];

      const request = new TransactionRequest(payments);

      const inputs: TransparentInput[] = [
        {
          pubkey: TEST_PUBLIC_KEY,
          txid: TEST_TXID,
          vout: 0,
          amount: (100_000_000n).toString(),
          scriptPubKey: TEST_SCRIPT_PUBKEY,
        },
      ];

      const pczt = proposeTransaction(inputs, request);
      const proved = proveTransaction(pczt);

      // Index 999 doesn't exist
      expect(() => getSighash(proved, 999)).toThrow();

      proved.free();
      request.free();
    });

    it('should reject invalid signature length', () => {
      const payments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
          amount: (100_000n).toString(),
        },
      ];

      const request = new TransactionRequest(payments);

      const inputs: TransparentInput[] = [
        {
          pubkey: TEST_PUBLIC_KEY,
          txid: TEST_TXID,
          vout: 0,
          amount: (100_000_000n).toString(),
          scriptPubKey: TEST_SCRIPT_PUBKEY,
        },
      ];

      const pczt = proposeTransaction(inputs, request);
      const proved = proveTransaction(pczt);

      // Wrong signature length
      const badSig = Buffer.alloc(32); // Should be 64
      expect(() => appendSignature(proved, 0, badSig)).toThrow();

      proved.free();
      request.free();
    });

    it('should reject invalid pubkey length', () => {
      const payments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
          amount: (100_000n).toString(),
        },
      ];

      const request = new TransactionRequest(payments);

      const inputs: TransparentInput[] = [
        {
          pubkey: Buffer.alloc(32), // Should be 33
          txid: TEST_TXID,
          vout: 0,
          amount: (100_000_000n).toString(),
          scriptPubKey: TEST_SCRIPT_PUBKEY,
        },
      ];

      expect(() => proposeTransaction(inputs, request)).toThrow();

      request.free();
    });

    it('should reject invalid txid length', () => {
      const payments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
          amount: (100_000n).toString(),
        },
      ];

      const request = new TransactionRequest(payments);

      const inputs: TransparentInput[] = [
        {
          pubkey: TEST_PUBLIC_KEY,
          txid: Buffer.alloc(16), // Should be 32
          vout: 0,
          amount: (100_000_000n).toString(),
          scriptPubKey: TEST_SCRIPT_PUBKEY,
        },
      ];

      expect(() => proposeTransaction(inputs, request)).toThrow();

      request.free();
    });
  });

  describe('Verification (verifyBeforeSigning)', () => {
    it('should pass verification for valid transaction without change', () => {
      const payments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
          amount: (100_000n).toString(),
        },
      ];

      const request = new TransactionRequest(payments);

      const inputs: TransparentInput[] = [
        {
          pubkey: TEST_PUBLIC_KEY,
          txid: TEST_TXID,
          vout: 0,
          amount: (100_000_000n).toString(),
          scriptPubKey: TEST_SCRIPT_PUBKEY,
        },
      ];

      const pczt = proposeTransaction(inputs, request);
      const proved = proveTransaction(pczt);

      // No change expected - empty array
      expect(() => verifyBeforeSigning(proved, request, [])).not.toThrow();

      proved.free();
      request.free();
    });

    it('should pass verification for valid transaction with change', () => {
      const payments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
          amount: (50_000n).toString(),
        },
      ];

      const request = new TransactionRequest(payments);

      const inputs: TransparentInput[] = [
        {
          pubkey: TEST_PUBLIC_KEY,
          txid: TEST_TXID,
          vout: 0,
          amount: (100_000_000n).toString(),
          scriptPubKey: TEST_SCRIPT_PUBKEY,
        },
      ];

      const pczt = proposeTransaction(inputs, request);
      const proved = proveTransaction(pczt);

      // When change goes to shielded address (default behavior),
      // we don't provide expectedChange - just verify payments match
      expect(() => verifyBeforeSigning(proved, request, [])).not.toThrow();

      proved.free();
      request.free();
    });

    it('should fail verification when payment amount is wrong', () => {
      // Create transaction with one payment
      const payments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
          amount: (100_000n).toString(),
        },
      ];

      const request = new TransactionRequest(payments);

      const inputs: TransparentInput[] = [
        {
          pubkey: TEST_PUBLIC_KEY,
          txid: TEST_TXID,
          vout: 0,
          amount: (100_000_000n).toString(),
          scriptPubKey: TEST_SCRIPT_PUBKEY,
        },
      ];

      const pczt = proposeTransaction(inputs, request);
      const proved = proveTransaction(pczt);

      // Now verify against a DIFFERENT payment amount
      const wrongPayments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma', // Same address
          amount: (50_000n).toString(), // Different amount
        },
      ];
      const wrongRequest = new TransactionRequest(wrongPayments);

      // Should fail because the amounts don't match
      expect(() => verifyBeforeSigning(proved, wrongRequest, [])).toThrow(/not found in PCZT/);

      proved.free();
      request.free();
      wrongRequest.free();
    });

    it('should fail verification with wrong change amount', () => {
      const payments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
          amount: (50_000n).toString(),
        },
      ];

      const request = new TransactionRequest(payments);

      const inputs: TransparentInput[] = [
        {
          pubkey: TEST_PUBLIC_KEY,
          txid: TEST_TXID,
          vout: 0,
          amount: (100_000_000n).toString(),
          scriptPubKey: TEST_SCRIPT_PUBKEY,
        },
      ];

      const pczt = proposeTransaction(inputs, request);
      const proved = proveTransaction(pczt);

      // Provide wrong change amount
      const wrongChange = [
        {
          scriptPubKey: TEST_SCRIPT_PUBKEY,
          value: (1_000_000n).toString(), // Wrong amount
        },
      ];

      expect(() => verifyBeforeSigning(proved, request, wrongChange)).toThrow(/Change.*mismatch/);

      proved.free();
      request.free();
    });

    it('should fail verification with wrong change script', () => {
      const payments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
          amount: (50_000n).toString(),
        },
      ];

      const request = new TransactionRequest(payments);

      const inputs: TransparentInput[] = [
        {
          pubkey: TEST_PUBLIC_KEY,
          txid: TEST_TXID,
          vout: 0,
          amount: (100_000_000n).toString(),
          scriptPubKey: TEST_SCRIPT_PUBKEY,
        },
      ];

      const pczt = proposeTransaction(inputs, request);
      const proved = proveTransaction(pczt);

      // Calculate actual change amount
      const inputAmount = 100_000_000n;
      const paymentAmount = 50_000n;
      const fee = 15_000n;
      const changeAmount = inputAmount - paymentAmount - fee;

      // Provide wrong script
      const wrongScript = Buffer.from(
        '76a914aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa88ac',
        'hex'
      );

      const wrongChange = [
        {
          scriptPubKey: wrongScript, // Wrong script
          value: changeAmount.toString(),
        },
      ];

      expect(() => verifyBeforeSigning(proved, request, wrongChange)).toThrow(/Change.*mismatch/);

      proved.free();
      request.free();
    });

    it('should handle transaction with no change (exact amount)', () => {
      // This test verifies a transaction where input equals output + fee exactly
      const payments: Payment[] = [
        {
          address: 'tm9iMLAuYMzJ6jtFLcA7rzUmfreGuKvr7Ma',
          amount: (99_985_000n).toString(), // 1 ZEC - 15k fee
        },
      ];

      const request = new TransactionRequest(payments);

      const inputs: TransparentInput[] = [
        {
          pubkey: TEST_PUBLIC_KEY,
          txid: TEST_TXID,
          vout: 0,
          amount: (100_000_000n).toString(), // Exactly 1 ZEC
          scriptPubKey: TEST_SCRIPT_PUBKEY,
        },
      ];

      const pczt = proposeTransaction(inputs, request);
      const proved = proveTransaction(pczt);

      // No change - transaction uses exact input amount minus fee
      expect(() => verifyBeforeSigning(proved, request, [])).not.toThrow();

      proved.free();
      request.free();
    });
  });
});
