// Testnet Demo: Mixed Transparent + Shielded (T→T+Z)
// Sends ZEC to both a transparent and shielded address in one transaction.
//
// Usage: PRIVATE_KEY=<hex> go run main.go
package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"os"

	"github.com/decred/dcrd/dcrec/secp256k1/v4"
	"github.com/decred/dcrd/dcrec/secp256k1/v4/ecdsa"
	"t2z"
	"golang.org/x/crypto/ripemd160"
)

const (
	zebraRPC        = "http://localhost:18232"
	shieldedAddress = "u1eq7cm60un363n2sa862w4t5pq56tl5x0d7wqkzhhva0sxue7kqw85haa6w6xsz8n8ujmcpkzsza8knwgglau443s7ljdgu897yrvyhhz"
)

func main() {
	privKeyHex := os.Getenv("PRIVATE_KEY")
	if privKeyHex == "" {
		fmt.Println("t2z Testnet Demo - Mixed T→T+Z Transaction")
		fmt.Println("\nUsage:")
		fmt.Println("  PRIVATE_KEY=<hex> go run main.go")
		fmt.Println("\nThe demo will automatically fetch UTXOs from your Zebra node.")
		os.Exit(1)
	}

	privKeyBytes, _ := hex.DecodeString(privKeyHex)
	privKey := secp256k1.PrivKeyFromBytes(privKeyBytes)
	pubkey := privKey.PubKey().SerializeCompressed()
	address := pubkeyToTestnetAddress(pubkey)

	fmt.Printf("Address: %s\n", address)
	fmt.Print("Fetching UTXOs... ")

	utxos, err := getUTXOs(address)
	if err != nil {
		fmt.Printf("✗ %v\n", err)
		os.Exit(1)
	}
	if len(utxos) == 0 {
		fmt.Println("✗ No UTXOs found. Get testnet ZEC from faucet.zecpages.com")
		os.Exit(1)
	}
	fmt.Printf("✓ Found %d UTXO(s)\n\n", len(utxos))

	// Use first UTXO
	utxo := utxos[0]
	txid, _ := hex.DecodeString(utxo.Txid)
	var txidArr [32]byte
	copy(txidArr[:], txid)

	// Build P2PKH script
	h := sha256.Sum256(pubkey)
	r := ripemd160.New()
	r.Write(h[:])
	pkh := r.Sum(nil)
	script := append([]byte{0x76, 0xa9, 0x14}, pkh...)
	script = append(script, 0x88, 0xac)

	input := t2z.TransparentInput{Pubkey: pubkey, TxID: txidArr, Vout: uint32(utxo.OutputIndex), Amount: uint64(utxo.Satoshis), ScriptPubKey: script}

	// Calculate amounts: 40% transparent, 40% shielded, 20% change
	fee := t2z.CalculateFee(1, 2, 1)
	available := uint64(utxo.Satoshis) - fee
	tAmt, zAmt := available*40/100, available*40/100

	fmt.Printf("Input:       %.8f ZEC\n", float64(utxo.Satoshis)/1e8)
	fmt.Printf("Transparent: %.8f ZEC → %s...\n", float64(tAmt)/1e8, address[:20])
	fmt.Printf("Shielded:    %.8f ZEC → %s...\n", float64(zAmt)/1e8, shieldedAddress[:20])
	fmt.Printf("Fee:         %.8f ZEC\n\n", float64(fee)/1e8)

	request, _ := t2z.NewTransactionRequest([]t2z.Payment{
		{Address: address, Amount: tAmt},
		{Address: shieldedAddress, Amount: zAmt},
	})
	defer request.Free()
	request.SetUseMainnet(false) // testnet
	request.SetTargetHeight(3_000_000)

	fmt.Print("Proposing... ")
	pczt, err := t2z.ProposeTransaction([]t2z.TransparentInput{input}, request)
	if err != nil {
		fmt.Printf("✗ %v\n", err)
		os.Exit(1)
	}
	fmt.Println("✓")

	fmt.Print("Proving... ")
	proved, err := t2z.ProveTransaction(pczt)
	if err != nil {
		fmt.Printf("✗ %v\n", err)
		os.Exit(1)
	}
	fmt.Println("✓")

	fmt.Print("Signing... ")
	sighash, _ := t2z.GetSighash(proved, 0)
	sig := ecdsa.SignCompact(privKey, sighash[:], true)
	var sigBytes [64]byte
	copy(sigBytes[:], sig[1:])
	signed, _ := t2z.AppendSignature(proved, 0, sigBytes)
	fmt.Println("✓")

	fmt.Print("Finalizing... ")
	txBytes, _ := t2z.FinalizeAndExtract(signed)
	fmt.Println("✓")

	fmt.Print("Broadcasting... ")
	txidResult, err := broadcast(hex.EncodeToString(txBytes))
	if err != nil {
		fmt.Printf("✗ %v\n", err)
		os.Exit(1)
	}
	fmt.Printf("✓\n\nTXID: %s\n", txidResult)
}

type UTXO struct {
	Txid        string `json:"txid"`
	OutputIndex int    `json:"outputIndex"`
	Satoshis    int64  `json:"satoshis"`
}

func getUTXOs(address string) ([]UTXO, error) {
	body, _ := json.Marshal(map[string]any{
		"jsonrpc": "2.0",
		"method":  "getaddressutxos",
		"params":  []any{map[string]any{"addresses": []string{address}}},
		"id":      1,
	})
	resp, err := http.Post(zebraRPC, "application/json", bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	var result struct {
		Result []UTXO `json:"result"`
		Error  *struct{ Message string } `json:"error"`
	}
	json.NewDecoder(resp.Body).Decode(&result)
	if result.Error != nil {
		return nil, fmt.Errorf("%s", result.Error.Message)
	}
	return result.Result, nil
}

func pubkeyToTestnetAddress(pubkey []byte) string {
	h := sha256.Sum256(pubkey)
	r := ripemd160.New()
	r.Write(h[:])
	pkh := r.Sum(nil)
	data := append([]byte{0x1d, 0x25}, pkh...) // testnet prefix
	check := sha256.Sum256(data)
	check = sha256.Sum256(check[:])
	return base58Encode(append(data, check[:4]...))
}

func base58Encode(data []byte) string {
	const alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
	var result []byte
	for _, b := range data {
		carry := int(b)
		for i := len(result) - 1; i >= 0; i-- {
			carry += 256 * int(result[i])
			result[i] = byte(carry % 58)
			carry /= 58
		}
		for carry > 0 {
			result = append([]byte{byte(carry % 58)}, result...)
			carry /= 58
		}
	}
	for _, b := range data {
		if b != 0 {
			break
		}
		result = append([]byte{0}, result...)
	}
	out := make([]byte, len(result))
	for i, b := range result {
		out[i] = alphabet[b]
	}
	return string(out)
}

func broadcast(txHex string) (string, error) {
	body, _ := json.Marshal(map[string]any{"jsonrpc": "2.0", "method": "sendrawtransaction", "params": []string{txHex}, "id": 1})
	resp, err := http.Post(zebraRPC, "application/json", bytes.NewReader(body))
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	var result struct {
		Result string `json:"result"`
		Error  *struct{ Message string } `json:"error"`
	}
	json.NewDecoder(resp.Body).Decode(&result)
	if result.Error != nil {
		return "", fmt.Errorf("%s", result.Error.Message)
	}
	return result.Result, nil
}
