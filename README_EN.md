# OplusUpdater Android

An OTA query tool based on the official Oplus update API, with an Android client and a Go core.

## Features

- Query OTA updates for Oplus / OPPO / Realme devices
- Switch between multiple OTA regions
- Show version info, publish time, security patch, download URL, and MD5
- Read `META-INF/com/android/metadata` from remote OTA ZIP files
- Parse `payload.bin` and list extractable partitions
- Export a single partition image to the downloads directory

## Android Query Strategies

The Android UI now exposes the tracker-style query strategies plus the second batch of advanced parameters:

- `Normal`: regular query flow
- `Gray`: CN gray channel query
- `Anti`: anti-restriction query flow
- `GrayNew`: taste probe first, then gray follow-up flow
- `Genshin`: `Off / YS / Ovt` OTA prefix decoration
- `Preview Query`: toggles `pre`
- `GUID`: 64-character hexadecimal device identifier
- `Components`: incremental OTA component input using `name:version,name:version`

Current UI-side behavior:

- Selecting `Anti` automatically locks the query mode to `taste`
- Selecting `GrayNew` keeps the form in stable/manual mode; the Go core still performs the internal `taste -> gray` two-stage flow
- Non-`CN` regions do not expose `Gray` or `GrayNew`
- Preview queries and direct `Taste` queries require a 64-character `GUID`
- `Anti` and `GrayNew` may use taste internally, but they do not require a `GUID` by themselves unless `Preview Query` is also enabled
- `Genshin` and `Preview Query` can be enabled together; OTA prefix decoration follows the Go core / tracker precedence rules, where `YS / Ovt` wins over `PRE`
- Search history restores the query strategy, query mode, and `genshin / pre / guid / components`

## Project Layout

- `app/`: Android Compose client
- `OplusUpdater/`: Go OTA core, CLI, and the source for `gomobile` bindings

Developer-facing documents:

- [SPEC.md](SPEC.md)
- [ARCHITECTURE.md](ARCHITECTURE.md)
- [README.md](README.md)

## Current Baseline

- Go: `1.26.2`
- Local Android build: `JDK 17`
- Go binding artifact: locally generated `OplusUpdater/updater.aar`

## Build Android App

Install `gomobile` and generate the Go binding first:

```powershell
git clone https://github.com/chlink2025/Oplusupdater-android OplusUpdater-Android
cd OplusUpdater-Android\OplusUpdater
go install golang.org/x/mobile/cmd/gomobile@latest
gomobile init
gomobile bind -target=android -androidapi 26 -v -o updater.aar ./pkg/updater
```

On Windows, if `javac` reports unmappable `GBK` characters for generated `Updater.java`, set:

```powershell
$env:JAVA_HOME='<JDK17_PATH>'
$env:JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8'
$env:ANDROID_HOME='<ANDROID_SDK_PATH>'
$env:ANDROID_NDK_HOME='<ANDROID_NDK_PATH>'
```

Then build the Android app:

```powershell
cd ..
.\gradlew.bat :app:assembleDebug
```

## Go Core

The `OplusUpdater/` directory contains the Go implementation used for:

- CLI OTA queries
- Android binding generation
- protocol and crypto debugging

See [OplusUpdater/README.md](OplusUpdater/README.md) for more Go-specific details.

## Verified Status

As of `2026-04-27`, the repository has been verified with:

- `go test ./...` passing under `OplusUpdater/`
- a successful `gomobile bind` that regenerates `updater.aar`
- a successful Android `:app:assembleDebug` build consuming the new binding
