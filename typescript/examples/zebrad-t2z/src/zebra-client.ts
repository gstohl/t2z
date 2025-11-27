/**
 * Zebra RPC Client
 *
 * Pure fetch-based JSON-RPC client for Zebra - no external dependencies.
 */

export interface BlockchainInfo {
  chain: string;
  blocks: number;
  headers: number;
  bestblockhash: string;
  difficulty: number;
  verificationprogress: number;
}

export interface RawTransaction {
  txid: string;
  version: number;
  locktime: number;
  vin: any[];
  vout: any[];
  confirmations?: number;
}

export interface UTXO {
  txid: string;
  vout: number;
  address: string;
  scriptPubKey: string;
  amount: number;
  height: number;
}

export class ZebraClient {
  private url: string;
  private authHeader: string | null = null;
  private idCounter = 0;

  constructor(host?: string, port?: number) {
    const h = host || process.env.ZEBRA_HOST || 'localhost';
    const p = port || parseInt(process.env.ZEBRA_PORT || '18232');
    const user = process.env.RPC_USER;
    const pass = process.env.RPC_PASSWORD;

    this.url = `http://${h}:${p}`;

    // Set auth header if credentials provided
    if (user && pass) {
      this.authHeader = 'Basic ' + Buffer.from(`${user}:${pass}`).toString('base64');
    }
  }

  /**
   * Make a raw JSON-RPC call
   */
  private async rawCall<T>(method: string, params: any[] = []): Promise<T> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (this.authHeader) {
      headers['Authorization'] = this.authHeader;
    }

    const response = await fetch(this.url, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        jsonrpc: '2.0',
        method,
        params,
        id: ++this.idCounter,
      }),
    });

    if (!response.ok) {
      throw new Error(`HTTP error: ${response.status} ${response.statusText}`);
    }

    const json = await response.json();
    if (json.error) {
      throw new Error(`RPC error: ${json.error.message || JSON.stringify(json.error)}`);
    }

    return json.result;
  }

  /**
   * Wait for the node to be ready
   */
  async waitForReady(maxAttempts = 30, delayMs = 1000): Promise<void> {
    for (let i = 0; i < maxAttempts; i++) {
      try {
        await this.getBlockchainInfo();
        return;
      } catch (error) {
        if (i === maxAttempts - 1) {
          throw new Error(`Node not ready after ${maxAttempts} attempts`);
        }
        await new Promise((resolve) => setTimeout(resolve, delayMs));
      }
    }
  }

  /**
   * Get blockchain info
   */
  async getBlockchainInfo(): Promise<BlockchainInfo> {
    return this.rawCall<BlockchainInfo>('getblockchaininfo');
  }

  /**
   * Get current block count
   */
  async getBlockCount(): Promise<number> {
    return this.rawCall<number>('getblockcount');
  }

  /**
   * Get block hash by height
   */
  async getBlockHash(height: number): Promise<string> {
    return this.rawCall<string>('getblockhash', [height]);
  }

  /**
   * Get block by hash
   */
  async getBlock(hash: string, verbosity: 0 | 1 | 2 = 1): Promise<any> {
    return this.rawCall<any>('getblock', [hash, verbosity]);
  }

  /**
   * Generate blocks (zcashd regtest with wallet)
   */
  async generate(numBlocks: number): Promise<string[]> {
    return this.rawCall<string[]>('generate', [numBlocks]);
  }

  /**
   * Mine blocks to a specific address (regtest only)
   */
  async generateToAddress(numBlocks: number, address: string): Promise<string[]> {
    return this.rawCall<string[]>('generatetoaddress', [numBlocks, address]);
  }

  /**
   * Mine blocks - tries generate first (zcashd), falls back to generatetoaddress (zebra)
   */
  async mineBlocks(numBlocks: number, address?: string): Promise<string[]> {
    try {
      return await this.generate(numBlocks);
    } catch (e) {
      if (address) {
        return this.generateToAddress(numBlocks, address);
      }
      throw e;
    }
  }

  /**
   * Get a new transparent address from wallet (zcashd)
   */
  async getNewAddress(): Promise<string> {
    return this.rawCall<string>('getnewaddress');
  }

  /**
   * Get wallet balance (zcashd)
   */
  async getBalance(): Promise<number> {
    return this.rawCall<number>('getbalance');
  }

  /**
   * List unspent outputs (zcashd)
   */
  async listUnspent(minConf: number = 1, maxConf: number = 9999999, addresses?: string[]): Promise<any[]> {
    return this.rawCall<any[]>('listunspent', [minConf, maxConf, addresses || []]);
  }

  /**
   * Dump private key for address (zcashd)
   */
  async dumpPrivKey(address: string): Promise<string> {
    return this.rawCall<string>('dumpprivkey', [address]);
  }

  /**
   * Validate address and get info (zcashd)
   */
  async validateAddress(address: string): Promise<any> {
    return this.rawCall<any>('validateaddress', [address]);
  }

  /**
   * Send to address from wallet (zcashd)
   */
  async sendToAddress(address: string, amount: number): Promise<string> {
    return this.rawCall<string>('sendtoaddress', [address, amount]);
  }

  /**
   * Send a raw transaction
   */
  async sendRawTransaction(hexString: string): Promise<string> {
    return this.rawCall<string>('sendrawtransaction', [hexString]);
  }

  /**
   * Get raw transaction
   */
  async getRawTransaction(txid: string, verbose: boolean = false): Promise<string | RawTransaction> {
    return this.rawCall<string | RawTransaction>('getrawtransaction', [txid, verbose ? 1 : 0]);
  }

  /**
   * Decode a raw transaction
   */
  async decodeRawTransaction(hexString: string): Promise<RawTransaction> {
    return this.rawCall<RawTransaction>('decoderawtransaction', [hexString]);
  }

  /**
   * Get transaction output (for checking if UTXO exists and is unspent)
   */
  async getTxOut(txid: string, vout: number, includeMempool: boolean = true): Promise<any | null> {
    return this.rawCall<any>('gettxout', [txid, vout, includeMempool]);
  }

  /**
   * Get address UTXOs using getaddressutxos (if available)
   * Note: This requires addressindex to be enabled in Zebra
   */
  async getAddressUtxos(address: string): Promise<UTXO[]> {
    try {
      return this.rawCall<UTXO[]>('getaddressutxos', [{ addresses: [address] }]);
    } catch (e) {
      // Fallback: address indexing might not be enabled
      console.warn('getaddressutxos not available, address indexing may be disabled');
      return [];
    }
  }

  /**
   * Get mempool info
   */
  async getMempoolInfo(): Promise<any> {
    return this.rawCall<any>('getmempoolinfo');
  }

  /**
   * Get raw mempool
   */
  async getRawMempool(verbose: boolean = false): Promise<string[] | Record<string, any>> {
    return this.rawCall<string[] | Record<string, any>>('getrawmempool', [verbose]);
  }

  /**
   * Get block template for mining
   */
  async getBlockTemplate(): Promise<any> {
    return this.rawCall<any>('getblocktemplate', []);
  }

  /**
   * Submit a mined block
   */
  async submitBlock(blockHex: string): Promise<string | null> {
    return this.rawCall<string | null>('submitblock', [blockHex]);
  }

  /**
   * Mine multiple blocks (waits for internal miner)
   * Returns when target height is reached
   */
  async waitForBlocks(targetHeight: number, timeoutMs: number = 120000): Promise<number> {
    const startTime = Date.now();
    let lastHeight = 0;

    while (Date.now() - startTime < timeoutMs) {
      const info = await this.getBlockchainInfo();
      if (info.blocks >= targetHeight) {
        return info.blocks;
      }
      if (info.blocks !== lastHeight) {
        lastHeight = info.blocks;
        console.log(`  Block height: ${info.blocks}`);
      }
      await new Promise((r) => setTimeout(r, 1000));
    }

    throw new Error(`Timeout waiting for height ${targetHeight}`);
  }
}
