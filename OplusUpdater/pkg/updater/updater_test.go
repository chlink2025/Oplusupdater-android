package updater

import "testing"

func TestQueryUpdateArgsPostDefaults(t *testing.T) {
	args := &QueryUpdateArgs{
		OtaVersion: "PJX110_11.A",
	}

	args.post()

	if args.OtaVersion != "PJX110_11.A.01_0001_197001010000" {
		t.Fatalf("unexpected ota version: %s", args.OtaVersion)
	}
	if args.Region != RegionCn {
		t.Fatalf("unexpected region: %s", args.Region)
	}
	if args.Model != "PJX110" {
		t.Fatalf("unexpected model: %s", args.Model)
	}
	if args.Mode != "manual" {
		t.Fatalf("unexpected mode: %s", args.Mode)
	}
	if args.Guid != zeroGUID {
		t.Fatalf("unexpected guid: %s", args.Guid)
	}
}

func TestQueryUpdateArgsPostRegionalModel(t *testing.T) {
	args := &QueryUpdateArgs{
		OtaVersion: "CPH2653_11.A",
		Region:     RegionEu,
	}

	args.post()

	if args.Model != "CPH2653EEA" {
		t.Fatalf("unexpected eu model: %s", args.Model)
	}
}

func TestBuildRequestHeadersIncludesTrackerFields(t *testing.T) {
	args := &QueryUpdateArgs{
		OtaVersion:  "PJX110_11.A.01_0001_197001010000",
		Model:       "PJX110",
		NvCarrier:   "10010111",
		Mode:        "manual",
		PipelineKey: "ALLNET",
	}
	config := GetConfig(RegionCn)

	headers, err := buildRequestHeaders(args, config, "HEADER_DEVICE_ID", "protected")
	if err != nil {
		t.Fatalf("buildRequestHeaders returned error: %v", err)
	}

	expected := map[string]string{
		"language":     "zh-CN",
		"newLanguage":  "zh-CN",
		"romVersion":   "unknown",
		"infVersion":   "1",
		"pipelineKey":  "ALLNET",
		"operator":     "ALLNET",
		"companyId":    "",
		"deviceId":     "HEADER_DEVICE_ID",
		"otaVersion":   args.OtaVersion,
		"model":        args.Model,
		"mode":         args.Mode,
		"nvCarrier":    args.NvCarrier,
		"Content-Type": "application/json; charset=utf-8",
	}

	for key, want := range expected {
		if got := headers[key]; got != want {
			t.Fatalf("header %s mismatch: got %q want %q", key, got, want)
		}
	}

	if headers["protectedKey"] == "" {
		t.Fatal("protectedKey header should not be empty")
	}
}

func TestBuildRequestPayloadUsesGuidDeviceID(t *testing.T) {
	payload := buildRequestPayload(zeroGUID)

	if got := payload["deviceId"]; got != zeroGUID {
		t.Fatalf("unexpected body deviceId: %v", got)
	}
	if got := payload["mode"]; got != "0" {
		t.Fatalf("unexpected mode: %v", got)
	}
	if got := payload["type"]; got != "0" {
		t.Fatalf("unexpected type: %v", got)
	}
	opex, ok := payload["opex"].(map[string]bool)
	if !ok || !opex["check"] {
		t.Fatalf("unexpected opex payload: %#v", payload["opex"])
	}
}

func TestResolveUpdatePath(t *testing.T) {
	if got := resolveUpdatePath(&QueryUpdateArgs{Guid: zeroGUID}); got != "/update/v3" {
		t.Fatalf("unexpected default path: %s", got)
	}
	if got := resolveUpdatePath(&QueryUpdateArgs{Guid: "ABCDEF"}); got != "/update/v6" {
		t.Fatalf("unexpected guid path: %s", got)
	}
	if got := resolveUpdatePath(&QueryUpdateArgs{Guid: zeroGUID, Pre: true}); got != "/update/v6" {
		t.Fatalf("unexpected pre path: %s", got)
	}
}

func TestGenerateDefaultDeviceIdFormat(t *testing.T) {
	deviceID := GenerateDefaultDeviceId()
	if len(deviceID) != 64 {
		t.Fatalf("unexpected device id length: %d", len(deviceID))
	}

	for _, ch := range deviceID {
		if !(ch >= 'A' && ch <= 'Z') && !(ch >= '0' && ch <= '9') {
			t.Fatalf("unexpected device id character: %q", ch)
		}
	}
}
