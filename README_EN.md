# OplusUpdater Android

An OTA query tool based on the official Oplus update API, with an Android client and a Go core.

## Features

- Query OTA updates for Oplus / OPPO / Realme devices
- Switch between multiple OTA regions
- Show version info, publish time, security patch, download URL, and MD5
- Read `META-INF/com/android/metadata` from remote OTA ZIP files
- Parse `payload.bin` and list extractable partitions
- Export a single partition image to the downloads directory

## Project Layout

- `app/`: Android Compose client
- `OplusUpdater/`: Go OTA core, CLI, and the source for `gomobile` bindings

Developer-facing documents:

- [SPEC.md](SPEC.md)
- [ARCHITECTURE.md](ARCHITECTURE.md)
- [README.md](README.md)

## Build Android App

Generate the Go binding first:

```powershell
git clone https://github.com/chlink2025/Oplusupdater-android OplusUpdater-Android
cd OplusUpdater-Android\OplusUpdater
go get golang.org/x/mobile/bind
gomobile init
gomobile bind -target=android -androidapi 26 -v ./pkg/updater
```

Then build the Android app:

```powershell
cd ..
.\gradlew.bat assemble
```

If `gomobile` is not installed:

```powershell
go install golang.org/x/mobile/cmd/gomobile@latest
```

## Go Core

The `OplusUpdater/` directory contains the Go implementation used for:

- CLI OTA queries
- Android binding generation
- protocol and crypto debugging

See [OplusUpdater/README.md](OplusUpdater/README.md) for more Go-specific details.
