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

## Project Layout

- `app/`：Android Compose 客户端
- `OplusUpdater/`：Go OTA 查询核心，同时提供 CLI 和 `gomobile` 绑定来源

面向开发和维护的规格与架构说明见：

- [SPEC.md](SPEC.md)
- [ARCHITECTURE.md](ARCHITECTURE.md)
- [README_EN.md](README_EN.md)

## Build Android App

先生成 Go 绑定产物：

```powershell
git clone https://github.com/chlink2025/Oplusupdater-android OplusUpdater-Android
cd OplusUpdater-Android\OplusUpdater
go get golang.org/x/mobile/bind
gomobile init
gomobile bind -target=android -androidapi 26 -v ./pkg/updater
```

再构建 Android：

```powershell
cd ..
.\gradlew.bat assemble
```

如果未安装 `gomobile`：

```powershell
go install golang.org/x/mobile/cmd/gomobile@latest
```

## Go Core

`OplusUpdater/` 目录下包含核心 Go 实现，可用于：

- CLI 查询 OTA 信息
- 生成 Android 绑定
- 独立调试协议请求与加解密逻辑

更多 Go 侧说明见 [OplusUpdater/README.md](OplusUpdater/README.md)。

## Credits

- [original project(now already delete (repository)](https://github.com/houvven/OplusUpdater-android)
- [miuix](https://github.com/miuix-kotlin-multiplatform/miuix)
- [go-mobile](https://github.com/golang/mobile)
- [Payload-Dumper-Compose](https://github.com/rcmiku/Payload-Dumper-Compose)
- [OPlus-Tracker](https://github.com/JerryTse-OSS/OPlus-Tracker)
