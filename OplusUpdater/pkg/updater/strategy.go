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

type requestComponent struct {
	ComponentName    string `json:"componentName"`
	ComponentVersion string `json:"componentVersion"`
}

type queryResponseBody struct {
	OtaVersion     string `json:"otaVersion"`
	RealOtaVersion string `json:"realOtaVersion"`
	FakeOTAVersion string `json:"fakeOtaVersion"`
	VersionCode    int    `json:"versionCode"`
}

func (body queryResponseBody) effectiveOTAVersion() string {
	if otaVersion := strings.TrimSpace(body.RealOtaVersion); otaVersion != "" {
		return otaVersion
	}
	return strings.TrimSpace(body.OtaVersion)
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

func normalizeGenshin(genshin string) string {
	switch strings.TrimSpace(genshin) {
	case "1", "2":
		return strings.TrimSpace(genshin)
	default:
		return "0"
	}
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

func shouldUseTrackerOTAPrefixProcessing(otaVersion, genshin string, pre bool) bool {
	normalized := strings.ReplaceAll(strings.TrimSpace(otaVersion), "OVT", "Ovt")
	if normalized == "" || !strings.Contains(normalized, "_") {
		return false
	}

	return isSimpleOTAVersion(normalized) ||
		strings.Contains(normalized, "YS") ||
		strings.Contains(normalized, "Ovt") ||
		strings.Contains(normalized, "PRE") ||
		normalizeGenshin(genshin) != "0" ||
		pre
}

func processOTAPrefix(otaPrefix, region, genshin string, pre bool, customModel string) (string, string) {
	processedPrefix := strings.ReplaceAll(strings.TrimSpace(otaPrefix), "OVT", "Ovt")
	baseModel := extractBaseModel(processedPrefix)
	model := strings.TrimSpace(customModel)
	genshin = normalizeGenshin(genshin)

	if model == "" {
		switch normalizeRegion(region) {
		case RegionEu, RegionRu, RegionTr:
			model = baseModel + normalizeRegion(region)
		default:
			model = baseModel
		}
	}

	if genshin == "1" && !strings.Contains(processedPrefix, "YS") {
		model = baseModel
		processedPrefix = strings.Replace(processedPrefix, model, model+"YS", 1)
	} else if genshin == "2" && !strings.Contains(processedPrefix, "Ovt") {
		model = baseModel
		processedPrefix = strings.Replace(processedPrefix, model, model+"Ovt", 1)
	} else if pre && !strings.Contains(processedPrefix, "PRE") {
		model = baseModel
		processedPrefix = strings.Replace(processedPrefix, baseModel, baseModel+"PRE", 1)
	}

	if strings.Contains(processedPrefix, "YS") {
		model = strings.Replace(baseModel, "YS", "", 1)
	} else if strings.Contains(processedPrefix, "Ovt") {
		model = strings.Replace(baseModel, "Ovt", "", 1)
	} else if strings.Contains(processedPrefix, "PRE") {
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

func parseComponentsInput(componentsInput string) []requestComponent {
	if strings.TrimSpace(componentsInput) == "" {
		return nil
	}

	var components []requestComponent
	for _, pair := range strings.Split(componentsInput, ",") {
		if !strings.Contains(pair, ":") {
			continue
		}

		name, version, _ := strings.Cut(pair, ":")
		name = strings.TrimSpace(name)
		version = strings.TrimSpace(version)
		if name == "" || version == "" {
			continue
		}

		components = append(components, requestComponent{
			ComponentName:    name,
			ComponentVersion: version,
		})
	}

	return components
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
		processedOTA, processedModel := processOTAPrefix(candidatePrefix, normalizedRegion, args.Genshin, args.Pre, customModel)

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
			retryOTA, retryModel := processOTAPrefix(lastSuccessFake, normalizedRegion, args.Genshin, args.Pre, customModel)

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

func queryUpdateGrayNew(args *QueryUpdateArgs, runner queryRunner) (*ResponseResult, error) {
	if args == nil {
		return nil, fmt.Errorf("query args cannot be nil")
	}

	basePrefix := strings.TrimSpace(args.OtaVersion)
	customModel := strings.TrimSpace(args.Model)
	normalizedRegion := normalizeRegion(args.Region)

	var (
		bestResult      *ResponseResult
		bestVersionCode int
		lastResult      *ResponseResult
		lastErr         error
	)

	for _, suffix := range antiOTASuffixes {
		candidatePrefix := basePrefix + suffix
		tasteOTA, tasteModel := processOTAPrefix(candidatePrefix, normalizedRegion, args.Genshin, args.Pre, customModel)

		tasteArgs := cloneQueryUpdateArgs(args)
		tasteArgs.OtaVersion = tasteOTA
		tasteArgs.Region = normalizedRegion
		tasteArgs.Model = tasteModel
		tasteArgs.Mode = "taste"
		tasteArgs.Gray = false
		tasteArgs.Anti = false
		tasteArgs.GrayNew = false

		tasteResult, err := runner(tasteArgs)
		if err != nil {
			lastErr = err
			continue
		}
		lastResult = tasteResult
		if !isSuccessfulResponse(tasteResult) {
			continue
		}

		tasteBody, err := parseQueryResponseBody(tasteResult)
		if err != nil {
			lastErr = err
			continue
		}
		nextOTAVersion := tasteBody.effectiveOTAVersion()
		if nextOTAVersion == "" {
			continue
		}

		finalOTA, finalModel := processOTAPrefix(nextOTAVersion, normalizedRegion, "0", false, customModel)

		finalArgs := cloneQueryUpdateArgs(args)
		finalArgs.OtaVersion = finalOTA
		finalArgs.Region = normalizedRegion
		finalArgs.Model = finalModel
		finalArgs.Gray = true
		finalArgs.Pre = false
		finalArgs.Anti = false
		finalArgs.GrayNew = false

		finalResult, err := runner(finalArgs)
		if err != nil {
			lastErr = err
			continue
		}
		lastResult = finalResult
		if !isSuccessfulResponse(finalResult) {
			continue
		}

		finalBody, err := parseQueryResponseBody(finalResult)
		if err != nil {
			lastErr = err
			continue
		}
		if bestResult == nil || finalBody.VersionCode >= bestVersionCode {
			bestResult = finalResult
			bestVersionCode = finalBody.VersionCode
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
	return nil, fmt.Errorf("graynew query returned no result")
}
