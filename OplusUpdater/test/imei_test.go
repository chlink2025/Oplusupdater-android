package test

import (
	"fmt"
	"testing"

	"github.com/chlink2025/Oplusupdater-android/OplusUpdater/pkg/updater"
)

func TestCalculateIMEICheckDigit(t *testing.T) {
	imei := "86429007457802"
	digit, err := updater.CalculateIMEICheckDigit(imei)
	if err != nil {
		t.Fatal(err)
	}
	fmt.Printf("IMEI: %s, Check Digit: %s\n", imei, digit)
}
