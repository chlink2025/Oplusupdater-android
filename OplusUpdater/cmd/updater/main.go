package main

import (
	"fmt"
	"log"
	"strings"

	"github.com/chlink2025/Oplusupdater-android/OplusUpdater/pkg/updater"
	"github.com/spf13/cobra"
)

func isHexGUID(value string) bool {
	if len(value) != 64 {
		return false
	}

	for _, ch := range value {
		switch {
		case ch >= '0' && ch <= '9':
		case ch >= 'a' && ch <= 'f':
		case ch >= 'A' && ch <= 'F':
		default:
			return false
		}
	}

	return true
}

func queryArgsFromCommand(cmd *cobra.Command, otaVersion string) (*updater.QueryUpdateArgs, error) {
	getString := func(name string) (string, error) {
		return cmd.Flags().GetString(name)
	}
	getBool := func(name string) (bool, error) {
		return cmd.Flags().GetBool(name)
	}

	region, err := getString("region")
	if err != nil {
		return nil, err
	}
	model, err := getString("model")
	if err != nil {
		return nil, err
	}
	carrier, err := getString("carrier")
	if err != nil {
		return nil, err
	}
	mode, err := getString("mode")
	if err != nil {
		return nil, err
	}
	imei, err := getString("imei")
	if err != nil {
		return nil, err
	}
	proxy, err := getString("proxy")
	if err != nil {
		return nil, err
	}
	guid, err := getString("guid")
	if err != nil {
		return nil, err
	}
	pre, err := getBool("pre")
	if err != nil {
		return nil, err
	}
	anti, err := getBool("anti")
	if err != nil {
		return nil, err
	}
	gray, err := getBool("gray")
	if err != nil {
		return nil, err
	}
	grayNew, err := getBool("graynew")
	if err != nil {
		return nil, err
	}
	customLanguage, err := getString("language")
	if err != nil {
		return nil, err
	}
	romVersion, err := getString("rom-version")
	if err != nil {
		return nil, err
	}
	androidVersion, err := getString("android-version")
	if err != nil {
		return nil, err
	}
	colorOSVersion, err := getString("coloros-version")
	if err != nil {
		return nil, err
	}
	pipelineKey, err := getString("pipeline-key")
	if err != nil {
		return nil, err
	}
	operator, err := getString("operator")
	if err != nil {
		return nil, err
	}
	companyID, err := getString("company-id")
	if err != nil {
		return nil, err
	}

	guid = strings.TrimSpace(guid)
	if guid != "" && !isHexGUID(guid) {
		return nil, fmt.Errorf("guid must be a 64-character hexadecimal string")
	}

	return &updater.QueryUpdateArgs{
		OtaVersion:     otaVersion,
		Region:         region,
		Model:          model,
		NvCarrier:      carrier,
		Mode:           mode,
		IMEI:           imei,
		Proxy:          proxy,
		Guid:           guid,
		Anti:           anti,
		Gray:           gray,
		GrayNew:        grayNew,
		Pre:            pre,
		CustomLanguage: customLanguage,
		RomVersion:     romVersion,
		AndroidVersion: androidVersion,
		ColorOSVersion: colorOSVersion,
		PipelineKey:    pipelineKey,
		Operator:       operator,
		CompanyID:      companyID,
	}, nil
}

func newRootCmd() *cobra.Command {
	rootCmd := &cobra.Command{
		Use:   "updater <otaVersion>",
		Short: " Use Oplus official api to query OPlus,OPPO and Realme Mobile 's OS version update.",
		Example: "  updater RMX5010_11.A --region CN\n" +
			"  updater RMX5010_11.A --region CN --gray\n" +
			"  updater PHP110 --region CN --graynew\n" +
			"  updater CPH2653_11.A --region EU --model CPH2653EEA\n" +
			"  updater RMX3301 --region IN --anti\n" +
			"  updater RMX3301_11.H --region SG --model RMX3301 --carrier 00011011\n" +
			"  updater PJX110_11.A --region CN --pre --guid 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
		Args: cobra.ExactArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			queryArgs, err := queryArgsFromCommand(cmd, args[0])
			if err != nil {
				return err
			}

			result, err := updater.QueryUpdate(queryArgs)
			if err != nil {
				return err
			}

			result.PrettyPrint()
			return nil
		},
	}

	rootCmd.Flags().String("region", "CN", "Server zone: CN (default), EU, IN, SG, RU, TR, TH, GL (Global), ID, TW, MY, VN, e.g., --region=CN")
	rootCmd.Flags().String("model", "", "Device model, e.g., --model=RMX3820")
	rootCmd.Flags().String("carrier", "", "Found in `my_manifest/build.prop` file, under the `NV_ID` reference, e.g., --carrier=01000100")
	rootCmd.Flags().String("mode", "manual", "Mode: manual (stable, default) or taste (public testing), e.g., --mode=manual")
	rootCmd.Flags().String("imei", "", "IMEI, e.g., --imei=86429XXXXXXXX98")
	rootCmd.Flags().StringP("proxy", "p", "", "Proxy server, e.g., --proxy=type://@host:port or --proxy=type://user:password@host:port, support http and socks proxy")
	rootCmd.Flags().String("guid", "", "Preview GUID, must be a 64-character hexadecimal string")
	rootCmd.Flags().Bool("anti", false, "Expand a device prefix through the anti taste strategy before selecting the best result")
	rootCmd.Flags().Bool("gray", false, "Use the CN gray OTA host when region is CN")
	rootCmd.Flags().Bool("graynew", false, "Run the tracker-style taste-to-gray prefix strategy before selecting the best result")
	rootCmd.Flags().Bool("pre", false, "Use preview query path, typically with --guid")
	rootCmd.Flags().String("language", "", "Override request language tag, e.g., --language=en-IN")
	rootCmd.Flags().String("rom-version", "", "Override romVersion header, e.g., --rom-version=RMX3301_15.0.0.1410(EX01)")
	rootCmd.Flags().String("android-version", "", "Override androidVersion header, e.g., --android-version=Android 15")
	rootCmd.Flags().String("coloros-version", "", "Override colorOSVersion header, e.g., --coloros-version=ColorOS15.0")
	rootCmd.Flags().String("pipeline-key", "", "Override pipelineKey header, defaults to ALLNET when omitted")
	rootCmd.Flags().String("operator", "", "Override operator header, defaults to pipelineKey when omitted")
	rootCmd.Flags().String("company-id", "", "Override companyId header")

	return rootCmd
}

func main() {
	if err := newRootCmd().Execute(); err != nil {
		log.Fatal(err)
	}
}
