import { ZebraClient } from './zebra-client.js';
import { TEST_KEYPAIR } from './keys.js';
import { getCoinbaseUtxo } from './utils.js';

async function main() {
  const client = new ZebraClient();
  
  console.log('Getting block hash for height 1...');
  const hash = await client.getBlockHash(1);
  console.log('Hash:', hash);
  
  console.log('\nGetting block with verbosity 2...');
  const block = await client.getBlock(hash, 2);
  console.log('Block keys:', Object.keys(block));
  console.log('tx is array:', Array.isArray(block.tx));
  if (block.tx) {
    console.log('tx length:', block.tx.length);
    console.log('tx[0] keys:', Object.keys(block.tx[0]));
    console.log('tx[0].hex:', block.tx[0].hex?.substring(0, 50) + '...');
  }
  
  console.log('\nCalling getCoinbaseUtxo...');
  const utxo = await getCoinbaseUtxo(client, 1, TEST_KEYPAIR);
  console.log('UTXO result:', utxo);
  if (utxo) {
    console.log('  txid:', utxo.txid.toString('hex'));
    console.log('  vout:', utxo.vout);
    console.log('  amount:', utxo.amount);
    console.log('  scriptPubKey:', utxo.scriptPubKey.toString('hex'));
  }
}

main().catch(console.error);
