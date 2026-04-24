# ARCHITECTURE

## 1. 总览

仓库是一个“双层实现”：

- `OplusUpdater/` 提供协议核心，负责跟 Oplus OTA 接口通信。
- `app/` 提供 Android UI，并通过 `gomobile` 绑定消费 Go 能力。

这不是一个纯 Android 工程，也不是一个单独的 Go 工具仓库，而是“Go 核心 + Android 壳层”的组合。

## 2. 目录职责

### 2.1 `OplusUpdater/`

- `cmd/updater/main.go`
  - CLI 入口
  - 读取位置参数与 flags
  - 调用 `updater.QueryUpdate`

- `pkg/updater/config.go`
  - 区域常量
  - Host / Language / PublicKey / CarrierID 映射

- `pkg/updater/utils.go`
  - AES Key / IV 生成
  - `protectedKey` 生成
  - 默认 `deviceId` 与 IMEI 派生 `deviceId`

- `pkg/updater/updater.go`
  - 请求参数标准化
  - Header 构造
  - 请求体加密
  - 响应解析与解密

- `pkg/updater/types.go`
  - 响应结构
  - 响应解密
  - 控制台 Pretty Print

- `test/`
  - 真实 OTA 服务的集成测试

### 2.2 `app/`

- `MainActivity.kt`
  - 仅负责设置主题并进入 `HomeScreen`

- `ui/screen/home/HomeScreen.kt`
  - Android 入口页面
  - 表单状态、查询触发、历史记录、消息提示

- `domain/UpdateQueryResponse.kt`
  - OTA 返回体序列化模型

- `ui/screen/home/components/UpdateQueryResponseCard.kt`
  - 响应信息渲染
  - 下载 URL 解析
  - 元数据读取
  - 触发分区列表展示

- `ui/screen/home/components/UpdateLogDialog.kt`
  - 加载并展示 OTA 更新日志 HTML

- `ui/screen/home/components/PartitionListView.kt`
  - payload 解析入口
  - 分区导出进度与复制操作

- `utils/MetadataUtils.kt`
  - 远程 ZIP 元数据 Range 读取

- `utils/HttpRangeUtil.kt`
  - 远程文件随机读

- `utils/PayloadParser.kt`
  - payload header / manifest 解析
  - 分区抽取

- `utils/ZipFileUtils.kt`
  - ZIP central directory 与 local file header 定位

## 3. 运行时数据流

### 3.1 OTA 查询流

```text
HomeScreen
  -> updater.QueryUpdateArgs (gomobile binding)
  -> OplusUpdater/pkg/updater.QueryUpdate
  -> Oplus OTA API (/update/v6)
  -> ResponseResult
  -> UpdateQueryResponseCard
  -> UpdateQueryResponse
```

### 3.2 下载链接与元数据流

```text
UpdateQueryResponseCard
  -> DownloadUrlResolver.resolveUrl
  -> MetadataUtils.getMetadata
  -> ZIP metadata fields
  -> UI 展示发布时间 / 安全补丁
```

### 3.3 payload 分区导出流

```text
PartitionListView
  -> PayloadParser.initPayload
  -> HttpRangeUtil + ZipFileUtils
  -> DeltaArchiveManifest
  -> PartitionInfo list
  -> extractPartition
  -> Downloads/OplusUpdater/<systemVersion>/*.img
```

## 4. 构建关系

### 4.1 Android 依赖 Go 绑定

Android 不是直接依赖 Go 源码，而是依赖生成后的：

- `OplusUpdater/updater.aar`
- `OplusUpdater/updater-sources.jar`

关系如下：

```text
pkg/updater/*.go
  -> gomobile bind
  -> updater.aar / updater-sources.jar
  -> app/build.gradle.kts
  -> Android APK
```

### 4.2 当前版本与工具链

- Go 模块声明：`go 1.24.0` / `toolchain go1.24.1`
- CI Workflow：仍写死 `go-version: '1.22'`
- Android 构建建议：JDK 17

说明：当前 CI 与 Go 模块版本存在显式偏差，后续更新时应先统一。

## 5. 当前架构优点

- KISS：Go 协议核心集中在 `pkg/updater`，逻辑路径短。
- YAGNI：没有额外后端或复杂基础设施，适合快速验证 OTA 接口。
- DRY：区域配置集中在 `config.go`，协议辅助逻辑集中在 `utils.go`。
- SRP：多数工具类职责单一，例如 ZIP 定位、HTTP Range、Metadata 解析各自独立。

## 6. 当前架构问题

### 6.1 Go 核心

- `pkg/updater/updater.go`
  - `json.Unmarshal(response.Body(), &responseResult)` 的错误被错误地返回成旧 `err`，会隐藏真实 JSON 解析失败原因。

- 模块路径与仓库路径不一致
  - `go.mod` 仍声明为 `github.com/Houvven/OplusUpdater`
  - 这会影响当前 fork 的发布、安装说明和下游引用一致性。

- CI 版本不一致
  - `go.mod` 要求 1.24.x
  - Workflow 仍使用 1.22

### 6.2 Android UI

- `HomeScreen.kt`
  - `MutableSharedFlow` 未 `remember`，重组后可能丢失消息通道。
  - 页面同时承担输入、请求、消息、历史、结果渲染触发，状态职责偏重。

- `UpdateQueryResponseCard.kt`
  - 只解析第一个组件的 URL，却把解析结果复用到所有组件卡片。
  - `PartitionListView` 也默认使用第一个组件的下载地址。

- `UpdateLogDialog.kt`
  - 直接加载远程 HTML 到 `WebView`，缺少统一的加载错误处理与安全边界说明。

- `PartitionListView.kt`
  - 使用 `Environment.getExternalStoragePublicDirectory` 与 `getExternalStorageDirectory`，是旧版存储方案。
  - 对现代 Android 的分区导出兼容性与权限处理不够稳健。

### 6.3 payload 工具链

- `PayloadParser.kt`
  - 只支持 `REPLACE / REPLACE_BZ / REPLACE_XZ / ZERO`
  - 一旦遇到 `SOURCE_COPY / PUFFDIFF / BROTLI_BSDIFF` 等操作会直接失败。

- `HttpRangeUtil.kt`
  - 采用对象级全局可变状态，天然偏向单任务串行模型，不适合并行读取或多任务下载。

## 7. 后续更新建议顺序

### 第一阶段：先稳住 Go 核心与构建基线

1. 对齐 `go.mod`、仓库路径与 README 安装说明
2. 修复 `QueryUpdate` 的 JSON 解析错误处理
3. 统一 CI 的 Go 版本
4. 把 gomobile 产物生成流程整理成稳定脚本

### 第二阶段：拆 Android 的状态边界

1. 引入 ViewModel 层
2. 把查询状态、元数据状态、payload 状态拆开
3. 消除 UI 直接操作 IO 细节的写法

### 第三阶段：升级下载与导出链路

1. 为每个组件独立解析 URL
2. 升级到现代存储方案
3. 扩展 payload 操作类型支持范围

## 8. 本轮结论

这个仓库目前最适合的更新方式，不是直接大改 UI，而是先把“Go 核心正确性 + 构建元信息 + Android 状态边界”这三块打平。这样后面做功能增强时，才不会在构建、协议和状态同步上反复返工。

## 9. 审查基线

### 9.1 本轮审查范围

- 已审阅 Android 与 Go 主要源码、构建文件、工作流和现有文档
- 已执行 `OplusUpdater/` 下的 `go test ./...`
- 已执行 `OplusUpdater/` 下的 `go vet ./...`
- 未执行 Gradle 验证

### 9.2 本轮审查结论

- Go 核心当前可作为下一轮更新的稳定起点
- Android 工程不是开箱即构建，先依赖 `gomobile bind` 生成 AAR
- 后续迭代优先级应为：
1. 修正 Go 核心与仓库元信息不一致
2. 拆解 Android 侧状态与 IO 耦合
3. 处理下载链路与现代存储兼容性

## 10. To Do List

当前执行原则：

- 先修“查询结果错误”这种正确性问题，再处理结构优化
- 先改 `OplusUpdater/`，暂时不扩大到 Android UI 重构
- 每修一层，都补一层回归验证，避免再次回到旧 OTA 包

### P0 查询正确性

1. 以 `OplusUpdater/pkg/updater` 为主战场，优先定位“返回旧 OTA 包”的根因。
2. 对照仓库中已有的 OTA 请求样例与 `OplusUpdater/README.md` 记录，核对当前请求头和请求体缺失字段。
3. 重点检查并补齐 `QueryUpdate` 中影响服务端命中结果的关键字段。
字段优先关注：`newLanguage`、`romVersion`、`androidVersion`、`colorOSVersion`、`infVersion`、`pipelineKey`、`companyId`、`prjNum`、`brand`、`brandSota`、`osType`、`components`、`securityPatch`、`strategyVersion`、`sota`、`cota`、`opex`。
4. 修正 `pkg/updater/updater.go` 中 `json.Unmarshal(response.Body(), &responseResult)` 的错误处理，确保能看到真实解析错误。
5. 在修复后，以几个典型机型和区域重新确认：返回的是当前可用 OTA，而不是明显过期的历史包。

### P1 回归测试

1. 把 `OplusUpdater/test/` 从“打印结果”升级为“断言结果”。
2. 至少覆盖 `CN / EU / IN / SG系区域` 这些不同配置路径。
3. 断言重点不是固定版本号，而是：
- `responseCode` 正常
- `otaVersion` / `versionName` 非空
- 返回结果不是明显旧包
- 解密后的 `body` 能稳定解析
4. 对纯本地逻辑补单元测试：
- 区域配置映射
- `deviceId` 生成
- `protectedKey` 生成
- 请求参数补全逻辑

### P2 Go 核心整理

1. 统一 `go.mod` 模块路径与当前仓库地址。
2. 修正 `OplusUpdater/README.md` 中过期的 CLI 用法说明。
3. 对齐 CI 中的 Go 版本与 `go.mod` / `toolchain`。
4. 把“构造请求”拆成更小的职责块，避免 `QueryUpdate` 继续膨胀。

### P3 Android 绑定与客户端

1. 确认 Go 修复后，Android 通过新的 `updater.aar` 获取正确查询结果。
2. 修正 `UpdateQueryResponseCard.kt` 中“复用第一个组件 URL 到全部组件”的问题。
3. 修正 `HomeScreen.kt` 中 `MutableSharedFlow` 未 `remember` 的状态风险。
4. 之后再考虑引入 `ViewModel`，不要在查询主链路未稳定前提前做大重构。

### P4 下载与分区导出

1. 升级 Android 外部存储写入方案，摆脱旧 API 依赖。
2. 逐步补齐 `PayloadParser.kt` 对更多 operation type 的支持。
3. 视需要再优化并发读取、下载体验和错误提示。
