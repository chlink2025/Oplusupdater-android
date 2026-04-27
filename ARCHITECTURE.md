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
  - 页面渲染与极少量页面级交互

- `ui/screen/home/HomeViewModel.kt`
  - 查询执行入口
  - 表单状态、对话框状态、查询结果状态、历史记录状态与消息流

- `ui/screen/home/components/UpdateLogViewModel.kt`
  - 更新日志 HTML 的加载入口
  - 更新日志的缓存、错误与重试状态

- `ui/screen/home/components/UpdateQueryResponseCardViewModel.kt`
  - OTA 元数据读取入口
  - 组件下载 URL 解析状态与结果缓存

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
  -> Oplus OTA API (/update/v3 or /update/v6)
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

- Go 模块声明：`go 1.26.2`
- CI Workflow：通过 `OplusUpdater/go.mod` 自动读取 Go 版本
- Android 构建建议：JDK 17

说明：Android 本地构建仍建议显式使用 JDK 17，不使用系统默认 Java 25。

### 4.3 当前已验证的绑定构建前提

- 使用 `golang.org/x/mobile/cmd/gomobile` 生成 Android 绑定
- `OplusUpdater/tools.go` 固定保留 `golang.org/x/mobile/bind` 在模块图中
- 生成前执行 `gomobile init`
- Windows 本地生成时显式设置 `JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8`
- 在线测试与绑定前清理代理环境变量，避免请求被错误转发

## 5. 当前架构优点

- KISS：Go 协议核心集中在 `pkg/updater`，逻辑路径短。
- YAGNI：没有额外后端或复杂基础设施，适合快速验证 OTA 接口。
- DRY：区域配置集中在 `config.go`，协议辅助逻辑集中在 `utils.go`。
- SRP：多数工具类职责单一，例如 ZIP 定位、HTTP Range、Metadata 解析各自独立。

## 6. 当前架构问题

### 6.1 Go 核心

- 基础协议正确性已在 2026-04-24 收敛一轮
  - 已对齐主查询链路的关键 Header 字段
  - 已改为按 `Guid / Pre` 在 `/update/v3` 与 `/update/v6` 间切换
  - 已拆分 Header `deviceId` 与 Body `deviceId`
  - 已修复 `QueryUpdate` 返回旧 JSON 解析错误的问题

- 仍缺少高级查询能力
  - `NvCarrier` 已可覆盖，但还没有更贴近 tracker 命名的 `nvid` 封装入口
  - 已完成首版 `components` 请求体注入、`anti` 前缀策略、CN `gray` host 切换与 `graynew` taste-to-gray 双阶段流
- `genshin` 已迁移到 Go 核心与 CLI，`components` 也还缺少稳定的在线 Delta 回归样本，`genshin` 也仍缺少稳定的在线专项回归样本

### 6.2 Android UI

- `HomeScreen.kt`
  - 已通过 `HomeViewModel` 拆走表单状态、对话框状态、查询执行、历史记录与消息流，但仍直接协调部分页面事件与组合细节。

- `UpdateLogDialog.kt`
  - 已补最小加载/失败/重试三态，并收敛 `WebView` 的文件与内容访问权限。
  - 当前已改为纯渲染弹窗，由 `UpdateLogViewModel` 承接 HTML 拉取、缓存与重试。

- `UpdateQueryResponseCard.kt`
  - 已不再直接执行 OTA 元数据读取与组件下载 URL 解析。
  - 当前改为在响应变化时统一派发加载事件，由 `UpdateQueryResponseCardViewModel` 维护二级状态。

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

### 第一阶段：收口 Go 核心与构建基线

1. 已完成：基于 `go 1.26.2` 重新生成 `updater.aar` / `updater-sources.jar`，并确认 Android 侧已吃到新查询协议
2. 继续收敛 `OplusUpdater/README.md` 与实际协议暴露面
3. 把 gomobile 产物生成流程整理成稳定脚本
4. 为 `genshin` 与 `components` 补稳定的在线回归样本，再评估后续更多 tracker 差异能力是否需要继续进入 CLI

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
- 已执行 `OplusUpdater/` 下的 `go test ./test -v`
- 已执行 `OplusUpdater/` 下的 `go mod tidy`
- 已执行 `gomobile bind` 重新生成 Android AAR
- 已执行 Android `:app:assembleDebug`

### 9.2 本轮审查结论

- Go 核心主查询协议已建立“对齐参考实现 + 在线回归断言”的稳定基线
- Android 工程不是开箱即构建，先依赖 `gomobile bind` 生成 AAR；当前这条本地联调链路已跑通
- 后续迭代优先级应为：
1. 整理 `gomobile` 产物生成流程与自动化脚本
2. 为 `genshin` 与 `components` 补稳定在线回归样本
3. 拆解 Android 侧状态与 IO 耦合
4. 处理下载链路与现代存储兼容性

## 10. To Do List

当前执行原则：

- 先修“查询结果错误”这种正确性问题，再处理结构优化
- 先改 `OplusUpdater/`，暂时不扩大到 Android UI 重构
- 每修一层，都补一层回归验证，避免再次回到旧 OTA 包

### P0 查询正确性

1. 已完成：对齐 `OplusUpdater/pkg/updater` 主查询协议，修正“返回旧 OTA 包”的主根因。
2. 已完成：补齐当前主链路所需的关键字段和 endpoint 切换逻辑。
3. 已完成：修正 `QueryUpdate` 的 JSON 解析错误返回。
4. 已完成：用典型机型和区域样本确认当前返回不再明显回退到旧包。
5. 已完成：重新生成 `updater.aar` 并在 Android UI 侧完成最小联调复核。

### P1 回归测试

1. 已完成：把 `OplusUpdater/test/` 从“打印结果”升级为“断言结果”。
2. 已完成：覆盖 `CN / EU / IN / SG / RU / TR / TH` 等不同配置路径。
3. 已完成：对稳定样本添加“版本号不回退”的下限断言，并为 `taste` 无结果场景固化协议预期。
4. 已完成：对纯本地逻辑补充单元测试，覆盖参数补全、请求头、请求体、endpoint 与默认 `deviceId`。
5. 已完成：补 `IN / SG系` 的在线回归样本，其中 `IN` 通过显式 `Model` 覆盖，`SG` 通过自定义 `NvCarrier` 覆盖。

### P2 Go 核心整理

1. 已完成：统一 `go.mod` 模块路径与当前仓库地址。
2. 已完成：修正 `OplusUpdater/README.md` 中过期的安装和 CLI 用法说明。
3. 已完成：让 CI 通过 `go-version-file` 与 `go.mod` 自动对齐。
4. 已完成：把当前 `QueryUpdateArgs` 已支持的高级字段开放到 CLI，并补本地参数测试。
5. 已完成：将 `QueryUpdate` 拆成单次查询与 `anti` 策略层，并补本地策略测试与在线回归样本。
6. 已完成：补齐 tracker 风格的 CN `gray` host 选择，并增加 `--gray` 与在线回归样本。
7. 已完成：补齐 tracker 风格的 `graynew` 双阶段前缀查询，并增加 `--graynew` 与在线回归样本。
8. 已完成：补齐 tracker 风格的 `components` 请求体注入，并增加 CLI 参数与本地解析测试。
9. 已完成：通过 `tools.go` 固定 `golang.org/x/mobile/bind` 工具依赖，避免 `go mod tidy` 后 `gomobile bind` 失效。
10. 待完成：为 `genshin` 与 `components` 补稳定的在线专项回归样本，并继续评估后续 tracker 差异能力。

### P3 Android 绑定与客户端

1. 已完成：确认 Go 修复后，Android 已通过新的 `updater.aar` 获取更新后的查询逻辑，并完成 `getConfig(region, gray)` 签名适配。
2. 已完成：修正 `UpdateQueryResponseCard.kt` 中“复用第一个组件 URL 到全部组件”的问题，并让分区解析入口按组件使用各自的最终下载地址。
3. 已完成：修正 `HomeScreen.kt` 中 `MutableSharedFlow` 未 `remember` 的状态风险，避免重组后消息流实例漂移。
4. 已完成首轮：为 `HomeScreen` 引入最小 `HomeViewModel`，先拆出查询执行、历史记录、结果状态与消息流。
5. 已完成第二轮：把 `otaVersion / model / carrier / region / mode / expandMoreParameters` 收敛进 `HomeViewModel`，统一查询参数来源。
6. 已完成第三轮：把 `AboutInfoDialog` 开关迁入 `HomeViewModel`，进一步压缩 `HomeScreen` 本地状态。
7. 已完成第四轮：为 `UpdateLogDialog` 增加加载/失败/重试三态，避免空白弹窗，并为 `WebView` 增加保守的访问限制。
8. 已完成第五轮：新增 `UpdateLogViewModel`，把更新日志 HTML 的加载、缓存、错误与重试从 `UpdateLogDialog` 下沉出 Composable。
9. 已完成第六轮：新增 `UpdateQueryResponseCardViewModel`，把 OTA 元数据读取与组件下载 URL 解析从 `UpdateQueryResponseCard` 下沉出 Composable。
10. 待完成：继续把更多 UI 事件与数据加载边界从 `HomeScreen` 收敛到更清晰的状态模型中，再评估是否继续扩大 `ViewModel` 职责。

### P4 下载与分区导出

1. 升级 Android 外部存储写入方案，摆脱旧 API 依赖。
2. 逐步补齐 `PayloadParser.kt` 对更多 operation type 的支持。
3. 视需要再优化并发读取、下载体验和错误提示。
