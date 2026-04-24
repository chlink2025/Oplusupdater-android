package updater

import (
	"encoding/json"
	"fmt"
	"regexp"
	"strings"
)

var (
	antiOTASuffixes         = []string{"_11.A", "_11.C", "_11.F", "_11.H", "_11.J"}
	simpleOTAVersionPattern = regexp.MustCompile(`_\d{2}\.[A-Z]`)
)

type queryRunner func(*QueryUpdateArgs) (*ResponseResult, error)

type queryResponseBody struct {
	OtaVersion     string `json:"otaVersion"`
	RealOtaVersion string `json:"realOtaVersion"`
	FakeOTAVersion string `json:"fakeOtaVersion"`
	VersionCode    int    `json:"versionCode"`
}

func cloneQueryUpdateArgs(args *QueryUpdateArgs) *QueryUpdateArgs {
	if args == nil {
		return nil
	}

	cloned := *args
	return &cloned
}

func normalizeRegion(region string) string {
	if trimmed := strings.TrimSpace(region); trimmed != "" {
		return strings.ToUpper(trimmed)
	}
	return RegionCn
}

func normalizeOTAVersion(otaVersion string) string {
	normalized := strings.TrimSpace(otaVersion)
	if normalized == "" {
		return normalized
	}
	if len(strings.Split(normalized, "_")) < 3 || len(strings.Split(normalized, ".")) < 3 {
		return normalized + ".01_0001_197001010000"
	}
	return normalized
}

func extractBaseModel(otaVersion string) string {
	parts := strings.Split(strings.TrimSpace(otaVersion), "_")
	if len(parts) == 0 {
		return ""
	}
	return parts[0]
}

func defaultModelForSingleQuery(otaVersion, region string) string {
	baseModel := extractBaseModel(otaVersion)
	if baseModel == "" {
		return ""
	}

	suffix := ""
	switch normalizeRegion(region) {
	case RegionEu:
		suffix = "EEA"
	case RegionIn:
		suffix = "IN"
	}

	return baseModel + suffix
}

func isSimpleOTAVersion(otaVersion string) bool {
	normalized := strings.ToUpper(strings.TrimSpace(otaVersion))
	return simpleOTAVersionPattern.MatchString(normalized) || strings.Count(normalized, "_") >= 3
}

func processOTAPrefix(otaPrefix, region string, pre bool, customModel string) (string, string) {
	processedPrefix := strings.TrimSpace(otaPrefix)
	baseModel := extractBaseModel(processedPrefix)
	model := strings.TrimSpace(customModel)

	if model == "" {
		switch normalizeRegion(region) {
		case RegionEu, RegionRu, RegionTr:
			model = baseModel + normalizeRegion(region)
		default:
			model = baseModel
		}
	}

	if pre && !strings.Contains(processedPrefix, "PRE") {
		model = baseModel
		processedPrefix = strings.Replace(processedPrefix, baseModel, baseModel+"PRE", 1)
		baseModel = extractBaseModel(processedPrefix)
	}

	if strings.Contains(processedPrefix, "PRE") {
		model = strings.Replace(baseModel, "PRE", "", 1)
	}

	return normalizeOTAVersion(processedPrefix), model
}

func parseQueryResponseBody(result *ResponseResult) (queryResponseBody, error) {
	if result == nil || len(result.DecryptedBodyBytes) == 0 {
		return queryResponseBody{}, nil
	}

	var body queryResponseBody
	if err := json.Unmarshal(result.DecryptedBodyBytes, &body); err != nil {
		return queryResponseBody{}, err
	}
	return body, nil
}

func isSuccessfulResponse(result *ResponseResult) bool {
	return result != nil && result.ResponseCode == 200
}

func appendModelSuffix(model, suffix string) string {
	normalizedModel := strings.TrimSpace(model)
	normalizedSuffix := strings.ToUpper(strings.TrimSpace(suffix))
	if normalizedModel == "" || normalizedSuffix == "" {
		return normalizedModel
	}
	if strings.HasSuffix(strings.ToUpper(normalizedModel), normalizedSuffix) {
		return normalizedModel
	}
	return normalizedModel + normalizedSuffix
}

func queryWithIndiaFallback(args *QueryUpdateArgs, runner queryRunner, allowFallback bool) (*ResponseResult, error) {
	result, err := runner(args)
	if err != nil || !allowFallback || result == nil || result.ResponseCode != 2004 || normalizeRegion(args.Region) != RegionIn {
		return result, err
	}

	retryArgs := cloneQueryUpdateArgs(args)
	retryArgs.Model = appendModelSuffix(args.Model, "IN")
	return runner(retryArgs)
}

func queryUpdateAnti(args *QueryUpdateArgs, runner queryRunner) (*ResponseResult, error) {
	if args == nil {
		return nil, fmt.Errorf("query args cannot be nil")
	}

	basePrefix := strings.TrimSpace(args.OtaVersion)
	customModel := strings.TrimSpace(args.Model)
	hasCustomModel := customModel != ""
	normalizedRegion := normalizeRegion(args.Region)

	var (
		bestResult      *ResponseResult
		bestVersionCode int
		lastResult      *ResponseResult
		lastErr         error
		lastSuccessFake string
	)

	for _, suffix := range antiOTASuffixes {
		candidatePrefix := basePrefix + suffix
		processedOTA, processedModel := processOTAPrefix(candidatePrefix, normalizedRegion, args.Pre, customModel)

		currentArgs := cloneQueryUpdateArgs(args)
		currentArgs.OtaVersion = processedOTA
		currentArgs.Region = normalizedRegion
		currentArgs.Model = processedModel
		currentArgs.Mode = "taste"
		currentArgs.Anti = false

		result, err := queryWithIndiaFallback(currentArgs, runner, !hasCustomModel)
		if err != nil {
			lastErr = err
			continue
		}
		lastResult = result

		if result != nil && result.ResponseCode == 2004 && lastSuccessFake != "" {
			retryOTA, retryModel := processOTAPrefix(lastSuccessFake, normalizedRegion, args.Pre, customModel)

			retryArgs := cloneQueryUpdateArgs(args)
			retryArgs.OtaVersion = retryOTA
			retryArgs.Region = normalizedRegion
			retryArgs.Model = retryModel
			retryArgs.Mode = "taste"
			retryArgs.Anti = false

			retryResult, retryErr := queryWithIndiaFallback(retryArgs, runner, !hasCustomModel)
			if retryErr != nil {
				lastErr = retryErr
			} else {
				result = retryResult
				lastResult = retryResult
			}
		}

		if !isSuccessfulResponse(result) {
			continue
		}

		body, err := parseQueryResponseBody(result)
		if err != nil {
			lastErr = err
			continue
		}

		if fake := strings.TrimSpace(body.FakeOTAVersion); fake != "" && !strings.EqualFold(fake, "N/A") {
			lastSuccessFake = fake
		}
		if bestResult == nil || body.VersionCode >= bestVersionCode {
			bestResult = result
			bestVersionCode = body.VersionCode
		}
	}

	if bestResult != nil {
		return bestResult, nil
	}
	if lastResult != nil {
		return lastResult, nil
	}
	if lastErr != nil {
		return nil, lastErr
	}
	return nil, fmt.Errorf("anti query returned no result")
}
