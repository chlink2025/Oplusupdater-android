package updater

type Config struct {
	CarrierID        string
	Host             string
	Language         string
	PublicKey        string
	PublicKeyVersion string
	Version          string
}

type Region = string

const (
	RegionCn = "CN"
	RegionEu = "EU"
	RegionIn = "IN"
	
	// 以下为使用 SG Host 的全球及其他地区
	RegionSg = "SG"
	RegionRu = "RU"
	RegionTr = "TR"
	RegionTh = "TH"
	RegionGl = "GL" // Global / US
	RegionId = "ID" // Indonesia
	RegionTw = "TW" // Taiwan
	RegionMy = "MY" // Malaysia
	RegionVn = "VN" // Vietnam
)

const (
	hostCn = "component-ota-cn.allawntech.com"
	hostEu = "component-ota-eu.allawnos.com"
	hostIn = "component-ota-in.allawnos.com"
	hostSg = "component-ota-sg.allawnos.com"
)

func GetConfig(region string) *Config {
	// 初始化基础配置
	c := &Config{
		Version: "2",
	}

	switch region {
	case RegionEu:
		c.CarrierID = "01000100"
		c.Host = hostEu
		c.Language = "en-GB"
		c.PublicKey = publicKeyEU
		c.PublicKeyVersion = "1615897067573"
		return c

	case RegionIn:
		c.CarrierID = "00011011"
		c.Host = hostIn
		c.Language = "en-IN"
		c.PublicKey = publicKeyIN
		c.PublicKeyVersion = "1615896309308"
		return c

	// CN Region
	case RegionCn:
		c.CarrierID = "10010111"
		c.Host = hostCn
		c.Language = "zh-CN"
		c.PublicKey = publicKeyCN
		c.PublicKeyVersion = "1615879139745"
		return c

	// 所有其他地区都使用 SG 的基础设施 (Host 和 PublicKey)，但 CarrierID 和 Language 不同
	default:
		c.Host = hostSg
		c.PublicKey = publicKeySG
		c.PublicKeyVersion = "1615895993238"

		switch region {
		case RegionRu:
			c.CarrierID = "00110111"
			c.Language = "ru-RU"
		case RegionTr:
			c.CarrierID = "01010001"
			c.Language = "tr-TR"
		case RegionTh:
			c.CarrierID = "00111001"
			c.Language = "th-TH"
		case RegionGl: // Global
			c.CarrierID = "10100111"
			c.Language = "en-US"
		case RegionId: // Indonesia
			c.CarrierID = "00110011"
			c.Language = "id-ID"
		case RegionTw: // Taiwan
			c.CarrierID = "00011010"
			c.Language = "zh-TW"
		case RegionMy: // Malaysia
			c.CarrierID = "00111000"
			c.Language = "ms-MY"
		case RegionVn: // Vietnam
			c.CarrierID = "00111100"
			c.Language = "vi-VN"
		case RegionSg: // Singapore
			fallthrough
		default:
			// 默认为 SG 配置
			c.CarrierID = "01011010"
			c.Language = "en-SG"
		}
		return c
	}
}
