# OplusUpdater Android

一个基于 Oplus 官方 OTA 接口的更新查询工具，包含 Android 客户端和 Go 核心实现。

## Screenshots

![pintu-fulicat com-1739372828559](https://github.com/user-attachments/assets/bfeecf8b-35c0-4e7d-83db-720b9f2326cf)

## Features

- 查询 Oplus / OPPO / Realme 设备 OTA 更新信息
- 支持多区域配置切换
- 展示版本信息、发布时间、安全补丁、下载链接和 MD5
- 解析远程 OTA ZIP 中的 `META-INF/com/android/metadata`
- 解析 `payload.bin` 并列出可提取分区
- 导出单个分区镜像到下载目录

## Android 查询参数

当前 Android UI 已接入 tracker 风格 OTA 查询参数：

- `Gray`：CN 灰度通道查询
- `Anti`：反查询限制绕过
- `GrayNew`：先 taste 探测、再切 gray 的追灰流程
- `Update Mode`：当前 UI 暂开放 `manual / taste`
- `Genshin`：`Off / YS / Ovt` 前缀装饰
- `Preview Query`：切换 `pre`
- `GUID`：64 位十六进制设备标识
- `Components`：增量 OTA 组件输入，格式为 `name:version,name:version`

当前 UI 的联动规则如下：

- `Gray / Anti / GrayNew` 现在是独立开关，不再被收敛成互斥的单选策略
- `Anti` 不再在表单层强制锁定 `taste`；它只会在前缀自动补全链路里由核心内部按 tracker 语义使用 `taste`
- `GrayNew` 不再在表单层强制锁定稳定模式；其 taste 探测阶段由核心内部处理，最终 gray 查询沿用用户显式选择的 `Update Mode`
- 非 `CN` 区域不会显示 `Gray` 和 `GrayNew`
- 预览版查询与用户显式选择 `Taste` 模式的查询，都要求填写 64 位 `GUID`
- `Anti / GrayNew` 的内部 taste 探测不会单独触发 GUID 必填；只有 `Preview Query` 或用户显式选择 `Taste` 才会要求 `GUID`
- `Genshin` 与 `Preview Query` 可同时开启，OTA 前缀装饰优先级遵循 Go 核心 / tracker：`YS / Ovt` 优先于 `PRE`
- 搜索历史会保存并恢复 `gray / anti / graynew / mode / genshin / pre / guid / components`

## Project Layout

- `app/`：Android Compose 客户端
- `OplusUpdater/`：Go OTA 查询核心，同时提供 CLI 和 `gomobile` 绑定来源

面向开发和维护的规格与架构说明见：

- [SPEC.md](SPEC.md)
- [ARCHITECTURE.md](ARCHITECTURE.md)
- [README_EN.md](README_EN.md)

## Current Baseline

- Go：`1.26.2`
- Android 本地构建：`JDK 17`
- Go 绑定产物：本地生成的 `OplusUpdater/updater.aar`

## Build Android App

先安装 `gomobile` 工具并生成 Go 绑定产物：

```powershell
git clone https://github.com/chlink2025/Oplusupdater-android OplusUpdater-Android
cd OplusUpdater-Android\OplusUpdater
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init
gomobile bind -target=android -androidapi 26 -v -o updater.aar ./pkg/updater
```

Windows 本地生成 `updater.aar` 时，如果 `javac` 报 `GBK` 不可映射字符错误，请显式设置：

```powershell
$env:JAVA_HOME='<JDK17_PATH>'
$env:JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8'
$env:ANDROID_HOME='<ANDROID_SDK_PATH>'
$env:ANDROID_NDK_HOME='<ANDROID_NDK_PATH>'
```

再构建 Android：

```powershell
cd ..
.\gradlew.bat :app:assembleDebug
```

## Go Core

`OplusUpdater/` 目录下包含核心 Go 实现，可用于：

- CLI 查询 OTA 信息
- 生成 Android 绑定
- 独立调试协议请求与加解密逻辑

更多 Go 侧说明见 [OplusUpdater/README.md](OplusUpdater/README.md)。

## Verified Status

截至 `2026-04-27`，当前仓库已验证：

- `OplusUpdater/` 下 `go test ./...` 通过
- `gomobile bind` 可生成新的 `updater.aar`
- Android `:app:assembleDebug` 通过，确认新绑定可被 UI 层消费

## Credits

- [original project(now already delete (repository)](https://github.com/houvven/OplusUpdater-android)
- [miuix](https://github.com/miuix-kotlin-multiplatform/miuix)
- [go-mobile](https://github.com/golang/mobile)
- [Payload-Dumper-Compose](https://github.com/rcmiku/Payload-Dumper-Compose)
- [OPlus-Tracker](https://github.com/JerryTse-OSS/OPlus-Tracker)
