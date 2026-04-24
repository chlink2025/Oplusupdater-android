package test

import (
	"encoding/json"
	"testing"

	"github.com/chlink2025/Oplusupdater-android/OplusUpdater/pkg/updater"
)

type otaQueryBody struct {
	Body               json.RawMessage `json:"body"`
	RawBody            string          `json:"-"`
	EnvelopeErrMsg     string          `json:"-"`
	EnvelopeStatusCode int             `json:"-"`
	ParamFlag          int             `json:"paramFlag"`
	OtaVersion         string          `json:"otaVersion"`
	RealOtaVersion     string          `json:"realOtaVersion"`
	VersionCode        int             `json:"versionCode"`
	AndroidVersion     string          `json:"androidVersion"`
	RealAndroidVersion string          `json:"realAndroidVersion"`
	VersionName        string          `json:"versionName"`
	RealVersionName    string          `json:"realVersionName"`
}

func (body otaQueryBody) otaVersion() string {
	if body.RealOtaVersion != "" {
		return body.RealOtaVersion
	}
	return body.OtaVersion
}

func (body otaQueryBody) androidVersion() string {
	if body.RealAndroidVersion != "" {
		return body.RealAndroidVersion
	}
	return body.AndroidVersion
}

func (body otaQueryBody) versionName() string {
	if body.RealVersionName != "" {
		return body.RealVersionName
	}
	return body.VersionName
}

func queryUpdateBody(t *testing.T, args *updater.QueryUpdateArgs) otaQueryBody {
	t.Helper()

	result, err := updater.QueryUpdate(args)
	if err != nil {
		t.Fatalf("QueryUpdate returned error: %v", err)
	}
	if len(result.DecryptedBodyBytes) == 0 {
		t.Fatalf("empty decrypted body: envelope responseCode=%d err=%q", result.ResponseCode, result.ErrMsg)
	}

	var body otaQueryBody
	if err := json.Unmarshal(result.DecryptedBodyBytes, &body); err != nil {
		t.Fatalf("failed to unmarshal decrypted body: %v", err)
	}
	body.RawBody = string(result.DecryptedBodyBytes)
	body.EnvelopeStatusCode = result.ResponseCode
	body.EnvelopeErrMsg = result.ErrMsg

	return body
}

func assertStableUpdate(t *testing.T, args *updater.QueryUpdateArgs, minVersionCode int) {
	t.Helper()

	body := queryUpdateBody(t, args)
	if body.EnvelopeStatusCode != 200 {
		t.Fatalf("unexpected envelope responseCode: got %d want 200 (err=%q raw=%s body=%s)", body.EnvelopeStatusCode, body.EnvelopeErrMsg, body.RawBody, string(body.Body))
	}
	if body.otaVersion() == "" {
		t.Fatalf("ota version should not be empty (raw=%s body=%s)", body.RawBody, string(body.Body))
	}
	if body.VersionCode < minVersionCode {
		t.Fatalf("versionCode regressed: got %d want >= %d (raw=%s body=%s)", body.VersionCode, minVersionCode, body.RawBody, string(body.Body))
	}
	if body.androidVersion() == "" {
		t.Fatalf("android version should not be empty (raw=%s body=%s)", body.RawBody, string(body.Body))
	}
	if body.versionName() == "" {
		t.Fatalf("version name should not be empty (raw=%s body=%s)", body.RawBody, string(body.Body))
	}
}

func assertSmokeUpdate(t *testing.T, args *updater.QueryUpdateArgs) {
	t.Helper()

	body := queryUpdateBody(t, args)
	if body.EnvelopeStatusCode != 200 && body.EnvelopeStatusCode != 2004 {
		t.Fatalf("unexpected envelope responseCode: got %d want one of [200, 2004] (err=%q raw=%s body=%s)", body.EnvelopeStatusCode, body.EnvelopeErrMsg, body.RawBody, string(body.Body))
	}
	if body.otaVersion() != "" {
		return
	}
	if body.ParamFlag != 0 {
		t.Fatalf("unexpected no-result payload: paramFlag=%d raw=%s body=%s", body.ParamFlag, body.RawBody, string(body.Body))
	}
}

func TestQueryUpdate_CPH2653_EU(t *testing.T) {
	assertStableUpdate(t, &updater.QueryUpdateArgs{
		OtaVersion: "CPH2653_11.A",
		Region:     updater.RegionEu,
		Model:      "CPH2653EEA",
	}, 450)
}

func TestQueryUpdate_CPH2653_EU_TEST(t *testing.T) {
	body := queryUpdateBody(t, &updater.QueryUpdateArgs{
		OtaVersion: "CPH2653_11.A",
		Region:     updater.RegionEu,
		Model:      "CPH2653EEA",
		Mode:       "taste",
	})

	if body.EnvelopeStatusCode != 2004 {
		t.Fatalf("unexpected envelope responseCode: got %d want 2004 (err=%q raw=%s body=%s)", body.EnvelopeStatusCode, body.EnvelopeErrMsg, body.RawBody, string(body.Body))
	}
	if body.EnvelopeErrMsg == "" {
		t.Fatalf("taste no-result query should return a non-empty errMsg (raw=%s body=%s)", body.RawBody, string(body.Body))
	}
	if body.otaVersion() != "" {
		t.Fatalf("taste no-result query should not return ota version (raw=%s body=%s)", body.RawBody, string(body.Body))
	}
	if body.ParamFlag != 0 {
		t.Fatalf("taste no-result query should return paramFlag=0 (got %d, raw=%s body=%s)", body.ParamFlag, body.RawBody, string(body.Body))
	}
}

func TestQueryUpdate_RMX5010_CN(t *testing.T) {
	assertStableUpdate(t, &updater.QueryUpdateArgs{
		OtaVersion: "RMX5010_11.A",
		Region:     updater.RegionCn,
	}, 560)
}

func TestQueryUpdate_RMX5010_CN_Gray(t *testing.T) {
	assertStableUpdate(t, &updater.QueryUpdateArgs{
		OtaVersion: "RMX5010_11.A",
		Region:     updater.RegionCn,
		Gray:       true,
	}, 560)
}

func TestQueryUpdate_RMX5011_RU(t *testing.T) {
	assertStableUpdate(t, &updater.QueryUpdateArgs{
		OtaVersion: "RMX5011_11.A",
		Region:     updater.RegionRu,
		Model:      "RMX5011RU",
	}, 1050)
}

func TestQueryUpdate_RMX3301_IN(t *testing.T) {
	assertStableUpdate(t, &updater.QueryUpdateArgs{
		OtaVersion: "RMX3301_11.H",
		Region:     updater.RegionIn,
		Model:      "RMX3301",
	}, 4210)
}

func TestQueryUpdate_RMX3301_IN_Anti(t *testing.T) {
	assertStableUpdate(t, &updater.QueryUpdateArgs{
		OtaVersion: "RMX3301",
		Region:     updater.RegionIn,
		Anti:       true,
	}, 3290)
}

func TestQueryUpdate_RMX3301_SG_CustomCarrier(t *testing.T) {
	assertStableUpdate(t, &updater.QueryUpdateArgs{
		OtaVersion: "RMX3301_11.H",
		Region:     updater.RegionSg,
		Model:      "RMX3301",
		NvCarrier:  "00011011",
	}, 4210)
}

func TestQueryUpdate_RMX5011_TR(t *testing.T) {
	assertSmokeUpdate(t, &updater.QueryUpdateArgs{
		OtaVersion: "RMX5011_11.A",
		Region:     updater.RegionTr,
		Model:      "RMX5011TR",
	})
}

func TestQueryUpdate_RMX5011_TH(t *testing.T) {
	assertSmokeUpdate(t, &updater.QueryUpdateArgs{
		OtaVersion: "RMX5011_11.A",
		Region:     updater.RegionTh,
		Model:      "RMX5011",
	})
}

func TestQueryUpdate_PHP110_CN(t *testing.T) {
	assertStableUpdate(t, &updater.QueryUpdateArgs{
		OtaVersion: "PHP110_11.F",
		Region:     updater.RegionCn,
	}, 2290)
}

func TestQueryUpdate_PHP110_CN_GrayNew(t *testing.T) {
	assertStableUpdate(t, &updater.QueryUpdateArgs{
		OtaVersion: "PHP110",
		Region:     updater.RegionCn,
		GrayNew:    true,
	}, 3070)
}
