package main

import (
	"strings"
	"testing"
)

func TestQueryArgsFromCommandBasicFlags(t *testing.T) {
	cmd := newRootCmd()
	if err := cmd.Flags().Parse([]string{
		"--region", "EU",
		"--model", "CPH2653EEA",
		"--carrier", "01000100",
		"--mode", "taste",
		"--imei", "864290073152698",
		"--proxy", "http://127.0.0.1:8080",
	}); err != nil {
		t.Fatalf("Parse returned error: %v", err)
	}

	args, err := queryArgsFromCommand(cmd, "CPH2653_11.A")
	if err != nil {
		t.Fatalf("queryArgsFromCommand returned error: %v", err)
	}

	if args.OtaVersion != "CPH2653_11.A" {
		t.Fatalf("unexpected ota version: %s", args.OtaVersion)
	}
	if args.Region != "EU" {
		t.Fatalf("unexpected region: %s", args.Region)
	}
	if args.Model != "CPH2653EEA" {
		t.Fatalf("unexpected model: %s", args.Model)
	}
	if args.NvCarrier != "01000100" {
		t.Fatalf("unexpected carrier: %s", args.NvCarrier)
	}
	if args.Mode != "taste" {
		t.Fatalf("unexpected mode: %s", args.Mode)
	}
	if args.IMEI != "864290073152698" {
		t.Fatalf("unexpected imei: %s", args.IMEI)
	}
	if args.Proxy != "http://127.0.0.1:8080" {
		t.Fatalf("unexpected proxy: %s", args.Proxy)
	}
}

func TestQueryArgsFromCommandAdvancedFlags(t *testing.T) {
	cmd := newRootCmd()
	validGUID := strings.Repeat("a", 64)
	if err := cmd.Flags().Parse([]string{
		"--guid", validGUID,
		"--anti",
		"--gray",
		"--graynew",
		"--components", "System:PJX110_11.C.35_1350_202508010000,Vendor:13.1.0",
		"--pre",
		"--language", "en-IN",
		"--rom-version", "RMX3301_15.0.0.1410(EX01)",
		"--android-version", "Android 15",
		"--coloros-version", "ColorOS15.0",
		"--pipeline-key", "ALLNET",
		"--operator", "OP01",
		"--company-id", "COMPANY",
	}); err != nil {
		t.Fatalf("Parse returned error: %v", err)
	}

	args, err := queryArgsFromCommand(cmd, "RMX3301_11.H")
	if err != nil {
		t.Fatalf("queryArgsFromCommand returned error: %v", err)
	}

	if args.Guid != validGUID {
		t.Fatalf("unexpected guid: %s", args.Guid)
	}
	if !args.Anti {
		t.Fatal("expected anti flag to be true")
	}
	if !args.Gray {
		t.Fatal("expected gray flag to be true")
	}
	if !args.GrayNew {
		t.Fatal("expected graynew flag to be true")
	}
	if !args.Pre {
		t.Fatal("expected pre flag to be true")
	}
	if args.ComponentsInput != "System:PJX110_11.C.35_1350_202508010000,Vendor:13.1.0" {
		t.Fatalf("unexpected components input: %s", args.ComponentsInput)
	}
	if args.CustomLanguage != "en-IN" {
		t.Fatalf("unexpected language: %s", args.CustomLanguage)
	}
	if args.RomVersion != "RMX3301_15.0.0.1410(EX01)" {
		t.Fatalf("unexpected rom version: %s", args.RomVersion)
	}
	if args.AndroidVersion != "Android 15" {
		t.Fatalf("unexpected android version: %s", args.AndroidVersion)
	}
	if args.ColorOSVersion != "ColorOS15.0" {
		t.Fatalf("unexpected coloros version: %s", args.ColorOSVersion)
	}
	if args.PipelineKey != "ALLNET" {
		t.Fatalf("unexpected pipeline key: %s", args.PipelineKey)
	}
	if args.Operator != "OP01" {
		t.Fatalf("unexpected operator: %s", args.Operator)
	}
	if args.CompanyID != "COMPANY" {
		t.Fatalf("unexpected company id: %s", args.CompanyID)
	}
}

func TestQueryArgsFromCommandRejectsInvalidGUID(t *testing.T) {
	cmd := newRootCmd()
	if err := cmd.Flags().Parse([]string{"--guid", "not-a-guid"}); err != nil {
		t.Fatalf("Parse returned error: %v", err)
	}

	if _, err := queryArgsFromCommand(cmd, "PJX110_11.A"); err == nil {
		t.Fatal("expected invalid guid to return an error")
	}
}

func TestIsHexGUID(t *testing.T) {
	if !isHexGUID(strings.Repeat("0", 64)) {
		t.Fatal("expected zero guid to be valid")
	}
	if !isHexGUID(strings.Repeat("A", 64)) {
		t.Fatal("expected uppercase guid to be valid")
	}
	if isHexGUID(strings.Repeat("g", 64)) {
		t.Fatal("expected non-hex guid to be invalid")
	}
	if isHexGUID(strings.Repeat("a", 63)) {
		t.Fatal("expected short guid to be invalid")
	}
}
