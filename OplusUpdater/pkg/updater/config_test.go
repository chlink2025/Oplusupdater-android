package updater

import "testing"

func TestGetConfigCNHostSwitchesForGray(t *testing.T) {
	normal := GetConfig(RegionCn, false)
	if normal.Host != hostCn {
		t.Fatalf("unexpected CN host: got %s want %s", normal.Host, hostCn)
	}

	gray := GetConfig(RegionCn, true)
	if gray.Host != hostCnGray {
		t.Fatalf("unexpected CN gray host: got %s want %s", gray.Host, hostCnGray)
	}
	if gray.PublicKey != normal.PublicKey {
		t.Fatal("gray host should keep the CN public key")
	}
	if gray.PublicKeyVersion != normal.PublicKeyVersion {
		t.Fatal("gray host should keep the CN public key version")
	}
	if gray.CarrierID != normal.CarrierID {
		t.Fatal("gray host should keep the CN carrier id")
	}
	if gray.Language != normal.Language {
		t.Fatal("gray host should keep the CN language")
	}
}

func TestGetConfigNonCNGrayKeepsOriginalHost(t *testing.T) {
	eu := GetConfig(RegionEu, true)
	if eu.Host != hostEu {
		t.Fatalf("unexpected EU host: got %s want %s", eu.Host, hostEu)
	}

	in := GetConfig(RegionIn, true)
	if in.Host != hostIn {
		t.Fatalf("unexpected IN host: got %s want %s", in.Host, hostIn)
	}
}
