# OplusUpdater Android

An OTA query tool based on the official Oplus update API, with an Android client and a Go core.

## Features

- Query OTA updates for Oplus / OPPO / Realme devices
- Switch between multiple OTA regions
- Show version info, publish time, security patch, download URL, and MD5
- Read `META-INF/com/android/metadata` from remote OTA ZIP files
- Parse `payload.bin` and list extractable partitions
- Export a single partition image to the downloads directory

## Android Query Parameters

The Android UI now exposes the tracker-style OTA query parameters:

- `Gray`: CN gray channel query
- `Anti`: anti-restriction query flow
- `GrayNew`: taste probe first, then gray follow-up flow
- `Update Mode`: the current UI exposes `manual / taste`
- `Genshin`: `Off / YS / Ovt` OTA prefix decoration
- `Preview Query`: toggles `pre`
- `GUID`: 64-character hexadecimal device identifier
- `Components`: incremental OTA component input using `name:version,name:version`

Current UI-side behavior:

- `Gray / Anti / GrayNew` are now independent flags instead of a mutually exclusive strategy selector
- `Anti` no longer locks the form mode to `taste`; the core only applies tracker-style `taste` probing inside the prefix auto-complete flow
- `GrayNew` no longer locks the form mode to stable/manual; the core handles the internal taste probe and keeps the user-selected `Update Mode` for the final gray query
- Non-`CN` regions do not expose `Gray` or `GrayNew`
- Preview queries and user-selected `Taste` queries require a 64-character `GUID`
- The internal taste probing used by `Anti / GrayNew` does not independently require a `GUID`; only `Preview Query` or an explicit `Taste` selection does
- `Genshin` and `Preview Query` can be enabled together; OTA prefix decoration follows the Go core / tracker precedence rules, where `YS / Ovt` wins over `PRE`
- Search history restores `gray / anti / graynew / mode / genshin / pre / guid / components`

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
