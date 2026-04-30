package test

import (
	"fmt"
	"github.com/deatil/go-cryptobin/cryptobin/rsa"
	"testing"

	"github.com/chlink2025/Oplusupdater-android/OplusUpdater/pkg/updater"
)

func TestRsaPublicKeyGen(t *testing.T) {

	obj := rsa.New().
		GenerateKey(2048)

	PubKeyPem := obj.
		CreatePublicKey().
		ToKeyString()

	println("public key:", PubKeyPem)
}

func TestDeviceIdGen(t *testing.T) {
	id := updater.GenerateDeviceId("864290073152698")
	fmt.Printf("device id: %s, len: %d\n", id, len(id))
}
