/**
 * Zebra RPC Client
 *
 * Pure Java HttpClient-based JSON-RPC client for Zebra - minimal dependencies.
 */
package com.zcash.t2z.examples;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

public class ZebraClient {
    private final String url;
    private final String authHeader;
    private int idCounter = 0;
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public ZebraClient() {
        this(null, null);
    }

    public ZebraClient(String host, Integer port) {
        String h = host != null ? host : System.getenv("ZEBRA_HOST");
        if (h == null) h = "localhost";

        int p = port != null ? port : 18232;
        String portEnv = System.getenv("ZEBRA_PORT");
        if (portEnv != null && port == null) {
            try {
                p = Integer.parseInt(portEnv);
            } catch (NumberFormatException ignored) {
            }
        }

        this.url = "http://" + h + ":" + p;

        String user = System.getenv("RPC_USER");
        String pass = System.getenv("RPC_PASSWORD");
        if (user != null && pass != null) {
            this.authHeader = "Basic " + Base64.getEncoder().encodeToString((user + ":" + pass).getBytes());
        } else {
            this.authHeader = null;
        }
    }

    /**
     * Make a raw JSON-RPC call.
     */
    private JsonElement rawCall(String method, JsonArray params) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("method", method);
        request.add("params", params != null ? params : new JsonArray());
        request.addProperty("id", ++idCounter);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(request)));

        if (authHeader != null) {
            requestBuilder.header("Authorization", authHeader);
        }

        HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new Exception("HTTP error: " + response.statusCode());
        }

        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);

        JsonElement error = jsonResponse.get("error");
        if (error != null && !error.isJsonNull()) {
            throw new Exception("RPC error: " + error);
        }

        return jsonResponse.get("result");
    }

    /**
     * Wait for the node to be ready.
     */
    public void waitForReady(int maxAttempts, long delayMs) throws Exception {
        for (int i = 0; i < maxAttempts; i++) {
            try {
                getBlockchainInfo();
                return;
            } catch (Exception e) {
                if (i == maxAttempts - 1) {
                    throw new Exception("Node not ready after " + maxAttempts + " attempts: " + e.getMessage());
                }
                Thread.sleep(delayMs);
            }
        }
    }

    /**
     * Get blockchain info.
     */
    public BlockchainInfo getBlockchainInfo() throws Exception {
        JsonObject result = rawCall("getblockchaininfo", null).getAsJsonObject();
        return new BlockchainInfo(
                result.get("chain").getAsString(),
                result.get("blocks").getAsInt(),
                result.get("bestblockhash").getAsString()
        );
    }

    /**
     * Get current block count.
     */
    public int getBlockCount() throws Exception {
        return rawCall("getblockcount", null).getAsInt();
    }

    /**
     * Get block hash by height.
     */
    public String getBlockHash(int height) throws Exception {
        JsonArray params = new JsonArray();
        params.add(height);
        return rawCall("getblockhash", params).getAsString();
    }

    /**
     * Get block by hash.
     */
    public JsonObject getBlock(String hash, int verbosity) throws Exception {
        JsonArray params = new JsonArray();
        params.add(hash);
        params.add(verbosity);
        return rawCall("getblock", params).getAsJsonObject();
    }

    /**
     * Send a raw transaction.
     */
    public String sendRawTransaction(String hexString) throws Exception {
        JsonArray params = new JsonArray();
        params.add(hexString);
        return rawCall("sendrawtransaction", params).getAsString();
    }

    /**
     * Get raw transaction.
     */
    public JsonElement getRawTransaction(String txid, boolean verbose) throws Exception {
        JsonArray params = new JsonArray();
        params.add(txid);
        params.add(verbose ? 1 : 0);
        return rawCall("getrawtransaction", params);
    }

    /**
     * Wait for blocks to reach target height.
     */
    public int waitForBlocks(int targetHeight, long timeoutMs) throws Exception {
        long startTime = System.currentTimeMillis();
        int lastHeight = 0;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            BlockchainInfo info = getBlockchainInfo();
            if (info.blocks >= targetHeight) {
                return info.blocks;
            }
            if (info.blocks != lastHeight) {
                lastHeight = info.blocks;
                System.out.println("  Block height: " + info.blocks);
            }
            Thread.sleep(1000);
        }

        throw new Exception("Timeout waiting for height " + targetHeight);
    }

    public void close() {
        // HttpClient doesn't need explicit close
    }

    /**
     * Blockchain info data class.
     */
    public static class BlockchainInfo {
        public final String chain;
        public final int blocks;
        public final String bestblockhash;

        public BlockchainInfo(String chain, int blocks, String bestblockhash) {
            this.chain = chain;
            this.blocks = blocks;
            this.bestblockhash = bestblockhash;
        }
    }
}
