import * as bitcoin from 'bitcoinjs-lib';
import ECPairFactory from 'ecpair';
import * as ecc from 'tiny-secp256k1';

const ECPairMod = (ECPairFactory as any).default || ECPairFactory;
const ECPair = ECPairMod(ecc);

// Test private key from keys.ts
const TEST_PRIVKEY_HEX = 'e8f32e723decf4051aefac8e2c93c9c5b214313817cdb01a1494b917c8436b35';
const privateKey = Buffer.from(TEST_PRIVKEY_HEX, 'hex');
const ecpair = ECPair.fromPrivateKey(privateKey, { compressed: true });
const publicKey = Buffer.from(ecpair.publicKey);
const pubkeyHash = bitcoin.crypto.hash160(publicKey);

console.log('Public key:', publicKey.toString('hex'));
console.log('Pubkey hash:', pubkeyHash.toString('hex'));
console.log('');
console.log('Coinbase tx pubkey hash from Zebra:');
console.log('Expected:   3442193e1bb70916e914552172cd4e2dbc9df811');
console.log('Match:', pubkeyHash.toString('hex') === '3442193e1bb70916e914552172cd4e2dbc9df811');
