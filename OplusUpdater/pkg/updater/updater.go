package updater

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/url"
	"strings"
	"time"

	"github.com/deatil/go-cryptobin/cryptobin/crypto"
	"github.com/go-resty/resty/v2"
)

const zeroGUID = "0000000000000000000000000000000000000000000000000000000000000000"

type QueryUpdateArgs struct {
	OtaVersion     string
	Region         string
	Model          string
	NvCarrier      string
	Mode           string
	IMEI           string
	Proxy          string
	Guid           string
	Pre            bool
	CustomLanguage string
	RomVersion     string
	AndroidVersion string
	ColorOSVersion string
	PipelineKey    string
	Operator       string
	CompanyID      string
}

func (args *QueryUpdateArgs) post() {
	args.OtaVersion = strings.TrimSpace(args.OtaVersion)
	if len(strings.Split(args.OtaVersion, "_")) < 3 || len(strings.Split(args.OtaVersion, ".")) < 3 {
		args.OtaVersion += ".01_0001_197001010000"
	}

	if region := strings.TrimSpace(args.Region); region == "" {
		args.Region = RegionCn
	} else {
		args.Region = strings.ToUpper(region)
	}

	if model := strings.TrimSpace(args.Model); model == "" {
		baseModel := strings.Split(args.OtaVersion, "_")[0]
		suffix := ""
		if args.Region == RegionEu {
			suffix = "EEA"
		} else if args.Region == RegionIn {
			suffix = "IN"
		}
		args.Model = baseModel + suffix
	} else {
		args.Model = model
	}

	if mode := strings.TrimSpace(args.Mode); mode == "" {
		args.Mode = "manual"
	} else {
		args.Mode = mode
	}

	if guid := strings.TrimSpace(args.Guid); guid == "" {
		args.Guid = zeroGUID
	} else {
		args.Guid = guid
	}
}

func defaultString(value, fallback string) string {
	if value == "" {
		return fallback
	}
	return value
}

func buildRequestHeaders(args *QueryUpdateArgs, config *Config, headerDeviceID, protectedKey string) (map[string]string, error) {
	pkm := map[string]CryptoConfig{
		"SCENE_1": {
			ProtectedKey:       protectedKey,
			Version:            GenerateProtectedVersion(),
			NegotiationVersion: config.PublicKeyVersion,
		},
	}

	protectedKeyJSON, err := json.Marshal(pkm)
	if err != nil {
		return nil, err
	}

	language := defaultString(strings.TrimSpace(args.CustomLanguage), config.Language)
	pipelineKey := defaultString(strings.TrimSpace(args.PipelineKey), "ALLNET")
	operator := defaultString(strings.TrimSpace(args.Operator), pipelineKey)

	return map[string]string{
		"language":       language,
		"newLanguage":    language,
		"androidVersion": defaultString(strings.TrimSpace(args.AndroidVersion), "unknown"),
		"colorOSVersion": defaultString(strings.TrimSpace(args.ColorOSVersion), "unknown"),
		"romVersion":     defaultString(strings.TrimSpace(args.RomVersion), "unknown"),
		"infVersion":     "1",
		"otaVersion":     args.OtaVersion,
		"model":          args.Model,
		"mode":           args.Mode,
		"nvCarrier":      args.NvCarrier,
		"pipelineKey":    pipelineKey,
		"operator":       operator,
		"companyId":      strings.TrimSpace(args.CompanyID),
		"version":        config.Version,
		"deviceId":       headerDeviceID,
		"Content-Type":   "application/json; charset=utf-8",
		"protectedKey":   string(protectedKeyJSON),
	}, nil
}

func buildRequestPayload(bodyDeviceID string) map[string]any {
	return map[string]any{
		"mode":     "0",
		"time":     time.Now().UnixMilli(),
		"isRooted": "0",
		"isLocked": true,
		"type":     "0",
		"deviceId": bodyDeviceID,
		"opex": map[string]bool{
			"check": true,
		},
	}
}

func resolveUpdatePath(args *QueryUpdateArgs) string {
	if args.Pre || !strings.EqualFold(strings.TrimSpace(args.Guid), zeroGUID) {
		return "/update/v6"
	}
	return "/update/v3"
}

func QueryUpdate(args *QueryUpdateArgs) (*ResponseResult, error) {
	if args == nil {
		return nil, fmt.Errorf("query args cannot be nil")
	}

	args.post()

	config := GetConfig(args.Region)
	if args.NvCarrier == "" {
		args.NvCarrier = config.CarrierID
	}

	iv, err := RandomIv()
	if err != nil {
		return nil, err
	}

	key, err := RandomKey()
	if err != nil {
		return nil, err
	}

	protectedKey, err := GenerateProtectedKey(key, []byte(config.PublicKey))
	if err != nil {
		return nil, err
	}

	headerDeviceID := GenerateDefaultDeviceId()
	if imei := strings.TrimSpace(args.IMEI); imei != "" {
		headerDeviceID = GenerateDeviceId(imei)
	}
	bodyDeviceID := strings.ToLower(strings.TrimSpace(args.Guid))

	requestHeaders, err := buildRequestHeaders(args, config, headerDeviceID, protectedKey)
	if err != nil {
		return nil, err
	}

	requestPayload := buildRequestPayload(bodyDeviceID)

	requestBodyBytes, err := json.Marshal(requestPayload)
	if err != nil {
		return nil, err
	}

	requestBody, err := json.Marshal(RequestBody{
		Cipher: crypto.FromBytes(requestBodyBytes).
			Aes().CTR().NoPadding().
			WithKey(key).WithIv(iv).
			Encrypt().
			ToBase64String(),
		Iv: base64.StdEncoding.EncodeToString(iv),
	})
	if err != nil {
		return nil, err
	}

	requestURL := url.URL{
		Host:   config.Host,
		Scheme: "https",
		Path:   resolveUpdatePath(args),
	}

	client := resty.New()
	if proxy := strings.TrimSpace(args.Proxy); proxy != "" {
		client.SetProxy(proxy)
	}

	response, err := client.R().
		SetHeaders(requestHeaders).
		SetBody(map[string]string{"params": string(requestBody)}).
		Post(requestURL.String())
	if err != nil {
		return nil, err
	}

	responseResult := new(ResponseResult)
	if err := json.Unmarshal(response.Body(), responseResult); err != nil {
		return nil, err
	}

	if err := responseResult.DecryptBody(key); err != nil {
		return nil, err
	}

	return responseResult, nil
}
