import * as bitcoin from 'bitcoinjs-lib';
import { TEST_KEYPAIR } from './keys.js';

const txHex = '0400008085202f89010000000000000000000000000000000000000000000000000000000000000000ffffffff025100ffffffff0140be4025000000001976a9143442193e1bb70916e914552172cd4e2dbc9df81188ac00000000010000000000000000000000000000';

function parseTxOutputs(txHex: string): Array<{ value: bigint; scriptPubKey: Buffer }> {
  const tx = Buffer.from(txHex, 'hex');
  let offset = 0;
  
  console.log('TX length:', tx.length);

  // Skip header (4 bytes version + 4 bytes version group id)
  console.log('Header:', tx.slice(0, 8).toString('hex'));
  offset += 8;

  // Read vin count (varint)
  const vinCount = tx[offset];
  console.log('Vin count:', vinCount);
  offset += 1;

  // Skip all inputs (for coinbase there's 1 input with fixed structure)
  for (let i = 0; i < vinCount; i++) {
    console.log(`Input ${i}:`);
    console.log('  prevout:', tx.slice(offset, offset + 36).toString('hex'));
    offset += 32; // prev txid
    offset += 4; // prev vout
    const scriptLen = tx[offset];
    console.log('  scriptLen:', scriptLen);
    offset += 1 + scriptLen; // script length + script
    console.log('  sequence:', tx.slice(offset, offset + 4).toString('hex'));
    offset += 4; // sequence
  }

  // Read vout count (varint)
  const voutCount = tx[offset];
  console.log('Vout count:', voutCount);
  offset += 1;

  const outputs: Array<{ value: bigint; scriptPubKey: Buffer }> = [];

  for (let i = 0; i < voutCount; i++) {
    // Read value (8 bytes, little-endian)
    const value = tx.readBigUInt64LE(offset);
    console.log(`Output ${i}: value = ${value} zatoshis (${Number(value) / 1e8} ZEC)`);
    offset += 8;

    // Read script length (varint) and script
    const scriptLen = tx[offset];
    console.log(`  scriptLen: ${scriptLen}`);
    offset += 1;
    const scriptPubKey = tx.slice(offset, offset + scriptLen);
    console.log(`  scriptPubKey: ${scriptPubKey.toString('hex')}`);
    offset += scriptLen;

    outputs.push({ value, scriptPubKey: Buffer.from(scriptPubKey) });
  }

  return outputs;
}

const outputs = parseTxOutputs(txHex);
console.log('\n--- Checking against our pubkey ---');
const expectedPubkeyHash = bitcoin.crypto.hash160(TEST_KEYPAIR.publicKey);
console.log('Our pubkey hash:', expectedPubkeyHash.toString('hex'));

for (let i = 0; i < outputs.length; i++) {
  const output = outputs[i];
  console.log(`Output ${i}:`);
  console.log('  Length:', output.scriptPubKey.length);
  if (output.scriptPubKey.length >= 25) {
    console.log('  Bytes [0]:', output.scriptPubKey[0].toString(16), '(expect 76)');
    console.log('  Bytes [1]:', output.scriptPubKey[1].toString(16), '(expect a9)');
    console.log('  Bytes [2]:', output.scriptPubKey[2].toString(16), '(expect 14)');
    console.log('  Bytes [23]:', output.scriptPubKey[23].toString(16), '(expect 88)');
    console.log('  Bytes [24]:', output.scriptPubKey[24].toString(16), '(expect ac)');
    if (output.scriptPubKey.length === 25 &&
        output.scriptPubKey[0] === 0x76 &&
        output.scriptPubKey[1] === 0xa9 &&
        output.scriptPubKey[2] === 0x14 &&
        output.scriptPubKey[23] === 0x88 &&
        output.scriptPubKey[24] === 0xac) {
      const pubkeyHashInScript = output.scriptPubKey.slice(3, 23);
      console.log('  Pubkey hash in script:', pubkeyHashInScript.toString('hex'));
      console.log('  MATCH:', pubkeyHashInScript.equals(expectedPubkeyHash));
    }
  }
}
