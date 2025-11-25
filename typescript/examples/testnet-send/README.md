# Testnet Send Example

This example demonstrates **sending real ZEC on testnet** from a transparent address to a shielded (Orchard) address using the t2z library.

## Two Versions Available

**🌟 Recommended: lightwalletd version** (`npm start`)
- Works with public servers (same as Zashi wallet)
- No node setup required
- Uses `testnet.zec.rocks:443` by default
- gRPC-based protocol

**⚙️ Alternative: JSON-RPC version** (`npm run start:rpc`)
- Requires your own zcashd node
- Full node control
- Traditional JSON-RPC protocol

## Prerequisites

1. **Transparent address with testnet ZEC**
2. **Shielded recipient address** (unified address preferred)
3. **Internet connection** (for lightwalletd version)

**Note:** The default lightwalletd version uses the same infrastructure as Zashi wallet - no node setup needed!

## Setup

### 1. Generate Keys (Optional)

If you don't have a testnet address yet, generate one:

```bash
npm install
npm run generate-keys
```

This will generate:
- A new random private key
- The corresponding transparent testnet address
- Sample `.env` configuration

Example output:
```
================================================================================
🔐  Generate Zcash Testnet Keys
================================================================================

✅ Generated New Keys

📋 Private Key (Hex):
   a1b2c3d4e5f6...
   ⚠️  Keep this secret! Never share or commit to git!

📧 Transparent Address (TESTNET):
   tmXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
   Fund this address at: https://faucet.testnet.z.cash/
```

### 2. Fund Your Address

Get testnet ZEC from the faucet:
- Visit: https://faucet.testnet.z.cash/
- Enter your testnet address
- Wait for 1-2 confirmations

### 3. Configure Environment

Copy the example configuration:

```bash
cp .env.example .env
```

Edit `.env` and fill in your values:

```bash
# Your 32-byte private key (64 hex characters)
PRIVATE_KEY=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef

# Your transparent testnet address (starts with "tm")
TRANSPARENT_ADDRESS=tmXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

# Recipient shielded address (starts with "utest")
SHIELDED_ADDRESS=utest1abcdef...

# Lightwalletd server (public server, no credentials needed!)
LIGHTWALLETD_URL=https://testnet.zec.rocks:443

# Amount to send in ZEC
AMOUNT=0.001
```

**Easy mode:** The default `testnet.zec.rocks` works out of the box - no node setup required!

### 4. Install Dependencies

```bash
npm install
```

## Usage

### Generate New Keys

To create a new testnet address:

```bash
npm run generate-keys
```

This generates a random private key and shows the testnet address. Copy the values into your `.env` file.

### Send Transaction

Run the main example (uses lightwalletd):

```bash
npm start
```

**Alternative:** If you have a local zcashd node with JSON-RPC enabled:

```bash
npm run start:rpc
```

## What It Does

The example performs these steps:

1. **Load Configuration** - Reads private key, addresses, and RPC URL from `.env`
2. **Derive Keys** - Derives public key and script from private key
3. **Connect to Node** - Verifies connection to Zcash RPC
4. **Query UTXOs** - Fetches available unspent outputs
5. **Build Transaction** - Creates a PCZT with transparent inputs and shielded output
6. **Add Proofs** - Generates Orchard zero-knowledge proofs
7. **Sign** - Signs all transparent inputs with secp256k1
8. **Finalize** - Extracts the final transaction bytes
9. **Broadcast** - Sends the transaction to the network

## Expected Output

```
================================================================================
🌿  T2Z Testnet Example: Transparent → Shielded Transaction
================================================================================

📋 Loading configuration from .env...
   ✅ Configuration loaded
   📧 Recipient: utest1abc...
   💰 Amount: 0.001 ZEC

🔑 Deriving keys...
   Public Key: 031b84c5567b126440995d3ed5aaba0565d71e1834604819ff9c17f5e9d5dd078f
   PubKey Hash: 79b000887626b294a914501a4cd226b58b235983
   Script: 76a91479b000887626b294a914501a4cd226b58b23598388ac

🌐 Connecting to Zcash node...
   ✅ Connected! Block height: 2500000

💰 Fetching UTXOs...
   Found 1 confirmed UTXO(s)

   UTXOs:
   1. abc123...def:0
      Amount: 1.00000000 ZEC (10 confirmations)

📊 Transaction Summary:
   Available: 1.00000000 ZEC
   Sending: 0.00100000 ZEC
   Fee: 0.00015000 ZEC
   Total: 0.00115000 ZEC

🔨 Building transaction...
   Creating transaction request...
   Proposing transaction with 1 input(s)...
   Adding Orchard proofs (this may take a moment)...

✍️  Signing transaction...
   Signing input 1/1...
   ✅ All inputs signed

📦 Finalizing transaction...
   Transaction size: 2345 bytes
   Transaction hex: 0500008085202f89...

📡 Broadcasting transaction...
   ✅ Transaction broadcast successful!

================================================================================
🎉 SUCCESS! Transaction sent!
================================================================================

   TXID: abc123def456...

   View on explorer:
   https://testnet.zcashblockexplorer.com/transactions/abc123def456...
```

## Troubleshooting

### "Failed to connect to RPC"

- Check that your Zcash node is running
- Verify RPC credentials in `~/.zcash/zcash.conf`
- Ensure RPC is enabled: `server=1` in config

### "No confirmed UTXOs found"

- Fund your transparent address at: https://faucet.testnet.z.cash/
- Wait for at least 1 confirmation
- Verify the address matches your private key

### "Insufficient funds"

- Check your available balance
- Reduce the AMOUNT in `.env`
- Remember to account for the fee (0.00015 ZEC)

### "Failed to broadcast"

- Check the full error message
- Verify your transaction is valid
- Ensure inputs are confirmed and unspent
- Check node is fully synced

## Technical Details

### Transaction Flow

```
Transparent UTXOs → [PCZT Creation] → [Orchard Proofs] → [Signing] → Shielded Output
```

### Fee Structure

This example uses **ZIP-317** fee calculation:
- Transparent → Shielded: **15,000 zatoshis** (0.00015 ZEC)
- Fee is automatically deducted from change

### Security Notes

- **Never commit your .env file** - It contains your private key!
- **Use testnet only** - This is for testing purposes
- **Verify addresses** - Double-check recipient address before sending
- **Keep backups** - Save your private key securely

## Next Steps

After successfully sending:

1. **View on Explorer** - Check the transaction on the testnet explorer
2. **Wait for Confirmations** - Shielded outputs need time to confirm
3. **Recipient Checks** - The recipient needs to scan with their wallet

## Resources

- [Zcash Testnet Faucet](https://faucet.testnet.z.cash/)
- [Zcash Testnet Explorer](https://testnet.zcashblockexplorer.com/)
- [ZIP-317: Conventional Fee](https://zips.z.cash/zip-0317)
- [t2z Documentation](../../README.md)

## Support

For issues or questions:
- Check the main README
- Review error messages carefully
- Verify all configuration values
- Test with small amounts first
