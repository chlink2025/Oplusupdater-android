package updater

import (
	"encoding/json"
	"testing"
)

func newTestResponseResult(t *testing.T, responseCode int, body map[string]any) *ResponseResult {
	t.Helper()

	bodyBytes, err := json.Marshal(body)
	if err != nil {
		t.Fatalf("Marshal returned error: %v", err)
	}

	return &ResponseResult{
		ResponseCode:       responseCode,
		DecryptedBodyBytes: bodyBytes,
	}
}

func responseBodyVersionCode(t *testing.T, result *ResponseResult) int {
	t.Helper()

	body, err := parseQueryResponseBody(result)
	if err != nil {
		t.Fatalf("parseQueryResponseBody returned error: %v", err)
	}
	return body.VersionCode
}

func TestQueryResponseBodyEffectiveOTAVersion(t *testing.T) {
	body := queryResponseBody{
		OtaVersion:     "RMX5010_11.A.55_0550_202501010000",
		RealOtaVersion: "RMX5010_11.A.56_0560_202502020000",
	}
	if got := body.effectiveOTAVersion(); got != "RMX5010_11.A.56_0560_202502020000" {
		t.Fatalf("unexpected effective ota version: %s", got)
	}

	body.RealOtaVersion = ""
	if got := body.effectiveOTAVersion(); got != "RMX5010_11.A.55_0550_202501010000" {
		t.Fatalf("unexpected fallback ota version: %s", got)
	}
}

func TestParseComponentsInput(t *testing.T) {
	components := parseComponentsInput(" System : PJX110_11.C.35_1350_202508010000 , invalid , Vendor: 13.1.0 , Empty: ")
	if len(components) != 2 {
		t.Fatalf("unexpected component count: got %d want 2", len(components))
	}
	if components[0].ComponentName != "System" || components[0].ComponentVersion != "PJX110_11.C.35_1350_202508010000" {
		t.Fatalf("unexpected first component: %+v", components[0])
	}
	if components[1].ComponentName != "Vendor" || components[1].ComponentVersion != "13.1.0" {
		t.Fatalf("unexpected second component: %+v", components[1])
	}
}

func TestBuildRequestPayloadIncludesComponents(t *testing.T) {
	args := &QueryUpdateArgs{
		ComponentsInput: "System:PJX110_11.C.35_1350_202508010000,Vendor:13.1.0",
	}

	payload := buildRequestPayload(args, "device-guid")
	rawComponents, ok := payload["components"]
	if !ok {
		t.Fatal("expected payload to include components")
	}

	components, ok := rawComponents.([]requestComponent)
	if !ok {
		t.Fatalf("unexpected components type: %T", rawComponents)
	}
	if len(components) != 2 {
		t.Fatalf("unexpected component count: got %d want 2", len(components))
	}
	if payload["deviceId"] != "device-guid" {
		t.Fatalf("unexpected device id: %v", payload["deviceId"])
	}
}

func TestIsSimpleOTAVersion(t *testing.T) {
	tests := []struct {
		name       string
		otaVersion string
		want       bool
	}{
		{name: "base model only", otaVersion: "RMX3301", want: false},
		{name: "simple ota prefix", otaVersion: "RMX3301_11.H", want: true},
		{name: "full ota version", otaVersion: "RMX3301_11.H.01_0001_197001010000", want: true},
		{name: "preview prefix", otaVersion: "PJX110PRE_11.A", want: true},
	}

	for _, tc := range tests {
		if got := isSimpleOTAVersion(tc.otaVersion); got != tc.want {
			t.Fatalf("%s: got %v want %v", tc.name, got, tc.want)
		}
	}
}

func TestProcessOTAPrefixMatchesTrackerRules(t *testing.T) {
	tests := []struct {
		name        string
		otaPrefix   string
		region      string
		pre         bool
		customModel string
		wantOTA     string
		wantModel   string
	}{
		{
			name:      "in keeps base model",
			otaPrefix: "RMX3301_11.H",
			region:    RegionIn,
			wantOTA:   "RMX3301_11.H.01_0001_197001010000",
			wantModel: "RMX3301",
		},
		{
			name:      "ru appends region suffix",
			otaPrefix: "RMX5011_11.A",
			region:    RegionRu,
			wantOTA:   "RMX5011_11.A.01_0001_197001010000",
			wantModel: "RMX5011RU",
		},
		{
			name:        "custom model wins",
			otaPrefix:   "CPH2653_11.A",
			region:      RegionEu,
			customModel: "CPH2653EEA",
			wantOTA:     "CPH2653_11.A.01_0001_197001010000",
			wantModel:   "CPH2653EEA",
		},
		{
			name:      "pre decorates ota and strips model suffix",
			otaPrefix: "PJX110_11.A",
			region:    RegionCn,
			pre:       true,
			wantOTA:   "PJX110PRE_11.A.01_0001_197001010000",
			wantModel: "PJX110",
		},
	}

	for _, tc := range tests {
		gotOTA, gotModel := processOTAPrefix(tc.otaPrefix, tc.region, tc.pre, tc.customModel)
		if gotOTA != tc.wantOTA {
			t.Fatalf("%s: unexpected ota version: got %s want %s", tc.name, gotOTA, tc.wantOTA)
		}
		if gotModel != tc.wantModel {
			t.Fatalf("%s: unexpected model: got %s want %s", tc.name, gotModel, tc.wantModel)
		}
	}
}

func TestQueryUpdateAntiSelectsHighestVersionCode(t *testing.T) {
	args := &QueryUpdateArgs{
		OtaVersion: "RMX3301",
		Region:     RegionCn,
		Anti:       true,
	}

	var seenModes []string
	runner := func(current *QueryUpdateArgs) (*ResponseResult, error) {
		seenModes = append(seenModes, current.Mode)

		switch current.OtaVersion {
		case "RMX3301_11.A.01_0001_197001010000":
			return newTestResponseResult(t, 200, map[string]any{
				"otaVersion":     current.OtaVersion,
				"fakeOtaVersion": "RMX3301_11.A.01_0001_197001010000",
				"versionCode":    120,
			}), nil
		case "RMX3301_11.C.01_0001_197001010000":
			return newTestResponseResult(t, 200, map[string]any{
				"otaVersion":     current.OtaVersion,
				"fakeOtaVersion": "RMX3301_11.C.01_0001_197001010000",
				"versionCode":    220,
			}), nil
		default:
			return newTestResponseResult(t, 2004, map[string]any{"paramFlag": 0}), nil
		}
	}

	result, err := queryUpdateAnti(args, runner)
	if err != nil {
		t.Fatalf("queryUpdateAnti returned error: %v", err)
	}
	if got := responseBodyVersionCode(t, result); got != 220 {
		t.Fatalf("unexpected version code: got %d want 220", got)
	}
	for _, mode := range seenModes {
		if mode != "taste" {
			t.Fatalf("anti strategy should force taste mode, got %s", mode)
		}
	}
}

func TestQueryUpdateAntiRetriesIndiaModelSuffix(t *testing.T) {
	args := &QueryUpdateArgs{
		OtaVersion: "RMX3301",
		Region:     RegionIn,
		Anti:       true,
	}

	type call struct {
		otaVersion string
		model      string
	}

	var calls []call
	runner := func(current *QueryUpdateArgs) (*ResponseResult, error) {
		calls = append(calls, call{otaVersion: current.OtaVersion, model: current.Model})

		if current.OtaVersion == "RMX3301_11.A.01_0001_197001010000" && current.Model == "RMX3301" {
			return newTestResponseResult(t, 2004, map[string]any{"paramFlag": 0}), nil
		}
		if current.OtaVersion == "RMX3301_11.A.01_0001_197001010000" && current.Model == "RMX3301IN" {
			return newTestResponseResult(t, 200, map[string]any{
				"otaVersion":     current.OtaVersion,
				"fakeOtaVersion": current.OtaVersion,
				"versionCode":    410,
			}), nil
		}
		return newTestResponseResult(t, 2004, map[string]any{"paramFlag": 0}), nil
	}

	result, err := queryUpdateAnti(args, runner)
	if err != nil {
		t.Fatalf("queryUpdateAnti returned error: %v", err)
	}
	if got := responseBodyVersionCode(t, result); got != 410 {
		t.Fatalf("unexpected version code: got %d want 410", got)
	}
	if len(calls) < 2 {
		t.Fatalf("expected at least 2 calls, got %d", len(calls))
	}
	if calls[0].model != "RMX3301" {
		t.Fatalf("unexpected first call model: %s", calls[0].model)
	}
	if calls[1].model != "RMX3301IN" {
		t.Fatalf("unexpected second call model: %s", calls[1].model)
	}
}

func TestQueryUpdateAntiRetriesLastFakeVersion(t *testing.T) {
	args := &QueryUpdateArgs{
		OtaVersion: "RMX3301",
		Region:     RegionCn,
		Anti:       true,
	}

	const fakeOTA = "RMX3301_11.B.01_0001_197001010000"

	var retriedFake bool
	runner := func(current *QueryUpdateArgs) (*ResponseResult, error) {
		switch current.OtaVersion {
		case "RMX3301_11.A.01_0001_197001010000":
			return newTestResponseResult(t, 200, map[string]any{
				"otaVersion":     current.OtaVersion,
				"fakeOtaVersion": fakeOTA,
				"versionCode":    100,
			}), nil
		case "RMX3301_11.C.01_0001_197001010000":
			return newTestResponseResult(t, 2004, map[string]any{"paramFlag": 0}), nil
		case fakeOTA:
			retriedFake = true
			return newTestResponseResult(t, 200, map[string]any{
				"otaVersion":     fakeOTA,
				"fakeOtaVersion": fakeOTA,
				"versionCode":    150,
			}), nil
		default:
			return newTestResponseResult(t, 2004, map[string]any{"paramFlag": 0}), nil
		}
	}

	result, err := queryUpdateAnti(args, runner)
	if err != nil {
		t.Fatalf("queryUpdateAnti returned error: %v", err)
	}
	if !retriedFake {
		t.Fatal("expected anti strategy to retry last fake ota version")
	}
	if got := responseBodyVersionCode(t, result); got != 150 {
		t.Fatalf("unexpected version code: got %d want 150", got)
	}
}

func TestQueryUpdateGrayNewRunsTasteThenGrayAndSelectsBestResult(t *testing.T) {
	args := &QueryUpdateArgs{
		OtaVersion: "RMX5010",
		Region:     RegionCn,
		GrayNew:    true,
	}

	type call struct {
		otaVersion string
		mode       string
		gray       bool
		pre        bool
	}

	var calls []call
	runner := func(current *QueryUpdateArgs) (*ResponseResult, error) {
		calls = append(calls, call{
			otaVersion: current.OtaVersion,
			mode:       current.Mode,
			gray:       current.Gray,
			pre:        current.Pre,
		})

		switch current.OtaVersion {
		case "RMX5010_11.A.01_0001_197001010000":
			if current.Mode != "taste" || current.Gray {
				t.Fatalf("unexpected taste stage args: %+v", current)
			}
			return newTestResponseResult(t, 200, map[string]any{
				"otaVersion": "RMX5010_11.A.55_0550_202501010000",
			}), nil
		case "RMX5010_11.A.55_0550_202501010000":
			if !current.Gray || current.Pre {
				t.Fatalf("unexpected final stage args: %+v", current)
			}
			return newTestResponseResult(t, 200, map[string]any{
				"realOtaVersion": current.OtaVersion,
				"versionCode":    550,
			}), nil
		case "RMX5010_11.C.01_0001_197001010000":
			return newTestResponseResult(t, 200, map[string]any{
				"otaVersion": "RMX5010_11.C.66_0660_202503030000",
			}), nil
		case "RMX5010_11.C.66_0660_202503030000":
			if !current.Gray || current.Pre {
				t.Fatalf("unexpected gray stage args: %+v", current)
			}
			return newTestResponseResult(t, 200, map[string]any{
				"realOtaVersion": current.OtaVersion,
				"versionCode":    660,
			}), nil
		default:
			return newTestResponseResult(t, 2004, map[string]any{"paramFlag": 0}), nil
		}
	}

	result, err := queryUpdateGrayNew(args, runner)
	if err != nil {
		t.Fatalf("queryUpdateGrayNew returned error: %v", err)
	}
	if got := responseBodyVersionCode(t, result); got != 660 {
		t.Fatalf("unexpected version code: got %d want 660", got)
	}
	if len(calls) < 4 {
		t.Fatalf("expected at least 4 calls, got %d", len(calls))
	}
}

func TestQueryUpdateGrayNewSkipsTasteMisses(t *testing.T) {
	args := &QueryUpdateArgs{
		OtaVersion: "RMX5010",
		Region:     RegionCn,
		GrayNew:    true,
	}

	runner := func(current *QueryUpdateArgs) (*ResponseResult, error) {
		switch current.OtaVersion {
		case "RMX5010_11.A.01_0001_197001010000":
			return newTestResponseResult(t, 2004, map[string]any{"paramFlag": 0}), nil
		case "RMX5010_11.C.01_0001_197001010000":
			return newTestResponseResult(t, 200, map[string]any{
				"otaVersion": "RMX5010_11.C.66_0660_202503030000",
			}), nil
		case "RMX5010_11.C.66_0660_202503030000":
			return newTestResponseResult(t, 200, map[string]any{
				"realOtaVersion": current.OtaVersion,
				"versionCode":    660,
			}), nil
		default:
			return newTestResponseResult(t, 2004, map[string]any{"paramFlag": 0}), nil
		}
	}

	result, err := queryUpdateGrayNew(args, runner)
	if err != nil {
		t.Fatalf("queryUpdateGrayNew returned error: %v", err)
	}
	if got := responseBodyVersionCode(t, result); got != 660 {
		t.Fatalf("unexpected version code: got %d want 660", got)
	}
}
