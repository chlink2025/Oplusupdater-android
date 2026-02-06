package updater

import (
	"encoding/base64"
	"encoding/json"
	"github.com/deatil/go-cryptobin/cryptobin/crypto"
	"github.com/go-resty/resty/v2"
	"net/url"
	"strings"
	"time"
)

type QueryUpdateArgs struct {
	OtaVersion string
	Region     string
	Model      string
	NvCarrier  string
	Mode       string
	IMEI       string
	Proxy      string
}

func (args *QueryUpdateArgs) post() {
	if len(strings.Split(args.OtaVersion, "_")) < 3 || len(strings.Split(args.OtaVersion, ".")) < 3 {
		args.OtaVersion += ".01_0001_197001010000"
	}
	if r := strings.TrimSpace(args.Region); len(r) == 0 {
		args.Region = RegionCn
	}
	if m := strings.TrimSpace(args.Model); len(m) == 0 {
		// 简单的自动补全 Model 逻辑
		baseModel := strings.Split(args.OtaVersion, "_")[0]
		suffix := ""
		if args.Region == RegionEu {
			suffix = "EEA"
		} else if args.Region == RegionIn {
			suffix = "IN"
		}
		args.Model = baseModel + suffix
	}
}

func QueryUpdate(args *QueryUpdateArgs) (*ResponseResult, error) {
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

	var deviceId string
	// 如果没有提供 IMEI，生成随机的 64位 deviceId (对应 Python 的 guid 逻辑)
	if len(strings.TrimSpace(args.IMEI)) == 0 {
		deviceId = GenerateDefaultDeviceId() // 在 utils.go 中原本是全0，建议改为随机字符以匹配 tomboy_pro 的 random_string(64)
	} else {
		deviceId = GenerateDeviceId(args.IMEI)
	}

	requestUrl := url.URL{Host: config.Host, Scheme: "https", Path: "/update/v6"}
	requestHeaders := map[string]string{
		"language":       config.Language,
		"androidVersion": "unknown",
		"colorOSVersion": "unknown",
		"otaVersion":     args.OtaVersion,
		"model":          args.Model,
		"mode":           args.Mode,
		"nvCarrier":      args.NvCarrier,
		"version":        config.Version,
		"deviceId":       deviceId,
		"Content-Type":   "application/json; charset=utf-8",
	}

	pkm := map[string]CryptoConfig{
		"SCENE_1": {
			ProtectedKey:       protectedKey,
			Version:            GenerateProtectedVersion(),
			NegotiationVersion: config.PublicKeyVersion,
		},
	}
	if pk, err := json.Marshal(pkm); err == nil {
		requestHeaders["protectedKey"] = string(pk)
	} else {
		return nil, err
	}

	// 构造 Request Body
	// 参照 tomboy_pro.py:
	// type: "0" (Go 原版是 "1")
	// opex: {"check": true} (Go 原版缺失)
	requestPayload := map[string]any{
		"mode":     "0",
		"time":     time.Now().UnixMilli(),
		"isRooted": "0",
		"isLocked": true,
		"type":     "0", // 修正为 0
		"deviceId": deviceId,
		"opex": map[string]bool{
			"check": true,
		},
	}

	// 如果有 Component 参数，这里可以添加逻辑加入 requestPayload["components"]
	// 当前暂不处理 components 参数

	var requestBody string
	if r, err := json.Marshal(requestPayload); err == nil {
		// 加密 Payload
		bytes, err := json.Marshal(RequestBody{
			Cipher: crypto.FromBytes(r).
				Aes().CTR().NoPadding().
				WithKey(key).WithIv(iv).
				Encrypt().
				ToBase64String(),
			Iv: base64.StdEncoding.EncodeToString(iv),
		})
		if err != nil {
			return nil, err
		} else {
			requestBody = string(bytes)
		}
	} else {
		return nil, err
	}

	client := resty.New()
	if p := strings.TrimSpace(args.Proxy); len(p) > 0 {
		client.SetProxy(p)
	}
	
	// 设置 Content-Type 为 JSON，Resty 会自动处理 Body
	// 注意：根据 tomboy_pro，body 结构为 {"params": json_string}
	response, err := client.R().
		SetHeaders(requestHeaders).
		SetBody(map[string]string{"params": requestBody}).
		Post(requestUrl.String())

	if err != nil {
		return nil, err
	}

	var responseResult *ResponseResult
	if json.Unmarshal(response.Body(), &responseResult) != nil {
		return nil, err
	}

	// 解密响应体
	if err := responseResult.DecryptBody(key); err != nil {
		return nil, err
	}

	return responseResult, nil
}
