# SPEC

## 1. 目标

为 Oplus / OPPO / Realme OTA 更新提供一个可维护的查询与解析基线：

- Go 核心负责协议请求、加密解密、区域配置与响应包装。
- Android 客户端负责输入、展示、辅助解析和分区导出。
- 文档目标不是承诺未来能力，而是准确描述当前可工作的范围与限制。

## 2. 范围

### 2.1 已覆盖

- OTA 版本查询
- 多区域配置切换
- 更新日志页面展示
- 下载 URL 解析
- 远程 ZIP 元数据读取
- `payload.bin` 分区列表解析
- 分区镜像导出与哈希校验

### 2.2 当前不覆盖

- 服务端代理或中转
- 用户认证体系
- 本地 OTA 包完整下载管理
- 多任务并行下载
- 全量 payload 操作类型支持
- Android 构建自动生成 AAR 的一键流水线

## 3. 用户视角功能

### 3.1 Android 应用

用户可以：

- 输入 `OTA Version`
- 选择 `Region`
- 查看自动推导的 `Model` 与 `NvCarrier`
- 选择 `Stable` 或 `Taste` 更新模式
- 查看最近 10 条查询历史
- 发起查询并查看返回状态
- 复制原始下载链接、最终下载链接、MD5
- 查看发布时间、安全补丁、版本信息
- 打开更新日志弹层
- 解析并显示 OTA 分区列表
- 导出单个分区到下载目录

### 3.2 Go CLI / Go Core

Go CLI 与 Go 核心当前共同支持以下输入：

- `OtaVersion`
- `Region`
- `Model`
- `NvCarrier`
- `Mode`
- `IMEI`
- `Proxy`
- `Guid`
- `Pre`
- `CustomLanguage`
- `RomVersion`
- `AndroidVersion`
- `ColorOSVersion`
- `PipelineKey`
- `Operator`
- `CompanyID`

Go CLI 当前实现使用“位置参数 OTA 版本 + 可选 flag”的形式，而不是旧文档中的 `-o/--ota-version`。其中 `Guid` 在 CLI 侧要求为 64 位十六进制字符串。

## 4. 输入输出规格

### 4.1 区域配置

按区域映射以下基础配置：

- Host
- 默认 CarrierID
- 语言
- RSA 公钥
- 公钥版本

当前代码显式处理：

- `CN`
- `EU`
- `IN`
- `SG`
- `RU`
- `TR`
- `TH`
- `GL`
- `ID`
- `TW`
- `MY`
- `VN`

### 4.2 Go 请求构造

请求流程如下：

1. 规范化 `OtaVersion / Region / Model`
2. 为 `Mode / Guid` 等参数补默认值
3. 按区域装配配置
4. 生成随机 AES Key / IV
5. 用区域 RSA 公钥加密 AES Key，生成 `protectedKey`
6. 组装 Header
7. Header `deviceId` 使用随机 64 位值
8. Body `deviceId` 使用 `guid.lower()`，默认全零 GUID
9. 用 AES-CTR 加密请求体
10. 按 `Pre / Guid` 选择请求路径：
    - 默认 `/update/v3`
    - `Pre == true` 或 `Guid != 0*64` 时 `/update/v6`
11. POST 到对应 OTA 接口
12. 解密响应正文

当前主查询会使用以下关键 Header：

- `language`
- `newLanguage`
- `androidVersion`
- `colorOSVersion`
- `romVersion`
- `infVersion`
- `otaVersion`
- `model`
- `mode`
- `nvCarrier`
- `pipelineKey`
- `operator`
- `companyId`
- `version`
- `deviceId`
- `protectedKey`

### 4.3 Android 响应展示

Android 侧把解密后的正文反序列化为 `UpdateQueryResponse`，展示：

- 版本类型
- 版本名
- OTA 版本
- Android 版本
- OS 版本
- 安全补丁
- 发布时间
- 更新日志 URL
- 组件列表
- 包大小
- MD5
- 下载链接

## 5. payload 相关规格

### 5.1 元数据读取

从远程 OTA ZIP 中读取：

- `META-INF/com/android/metadata`
- `post-timestamp`
- `post-security-patch-level`

当前实现前提：

- 目标文件可通过 HTTP Range 读取
- `metadata` 文件未压缩或可按当前简单逻辑读取

### 5.2 payload 解析

当前 Android 端支持：

- 定位 ZIP 内 `payload.bin`
- 解析 `CrAU` header
- 读取 `DeltaArchiveManifest`
- 列出分区名、大小、校验值

当前只支持以下安装操作类型：

- `REPLACE`
- `REPLACE_BZ`
- `REPLACE_XZ`
- `ZERO`

其余操作会直接抛出 `Unsupported operation type`。

## 6. 依赖与前置条件

### 6.1 Android

- Kotlin 2.2.0
- AGP 8.12.0
- Compose BOM 2025.07.00
- `gomobile bind` 生成的 `updater.aar`
- JDK 17

### 6.2 Go

- `go 1.24.0`
- `toolchain go1.24.1`
- `resty`
- `go-cryptobin`
- `cobra`

## 7. 已确认验证结果

验证日期：2026-04-24

- `OplusUpdater/`：`go test ./...` 通过
- `OplusUpdater/`：`go vet ./...` 通过
- `OplusUpdater/`：`go test ./test -v` 通过
- Android Gradle：本轮未执行

当前已确认的在线 OTA 回归基线：

- `CPH2653_EU`：`versionCode >= 450`
- `RMX5010_CN`：`versionCode >= 560`
- `RMX5010_CN` 的 `gray` 查询：`versionCode >= 560`
- `RMX3301_IN`：`versionCode >= 4210`
- `RMX3301_IN` 的 `anti` 前缀查询（`otaVersion=RMX3301`）：`versionCode >= 3290`
- `RMX3301_SG`（`NvCarrier=00011011`）：`versionCode >= 4210`
- `RMX5011_RU`：`versionCode >= 1050`
- `PHP110_CN`：`versionCode >= 2290`
- `PHP110_CN` 的 `graynew` 前缀查询（`otaVersion=PHP110`）：`versionCode >= 3070`
- `CPH2653_EU` 的 `taste` 无结果场景：外层 `responseCode == 2004`，解密体 `paramFlag == 0`

## 8. 当前限制

- Android app 不是开箱即构建，必须先生成 AAR。
- Android 下载链路默认围绕“第一个组件 URL”工作，不适合多组件差异化场景。
- 分区导出依赖旧版外部存储 API。
- UI 状态与网络/IO 状态耦合较重，缺少 ViewModel / Repository 分层。
- Go 集成测试依赖真实 OTA 服务，结果具有外部环境依赖。
- 当前 Go 公共查询 API 已支持 `anti`、`gray`、`graynew` 与 `NvCarrier` 覆盖，但还没有 `components`、`genshin` 等高级能力。
- 在当前开发环境中，在线 OTA 测试前需要清理代理环境变量，否则请求可能被错误转发到 `127.0.0.1:9`。
