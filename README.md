## Screenshots
![pintu-fulicat com-1739372828559](https://github.com/user-attachments/assets/bfeecf8b-35c0-4e7d-83db-720b9f2326cf)



## How to build?
```shell
git clone https://github.com/chlink2025/Oplusupdater-android OplusUpdater-Android
cd OplusUpdater-Android/OplusUpdater
go get golang.org/x/mobile/bind
gomobile init
gomobile bind -target=android -androidapi 26 -v ./pkg/updater
cd ..
./gradlew assemble
```
if you not install gomobile, you can install it by:
```shell
go install golang.org/x/mobile/cmd/gomobile@latest
```

## Credits
- [original project(now already delete (repository)](https://github.com/houvven/OplusUpdater-android)
- [miuix](https://github.com/miuix-kotlin-multiplatform/miuix)
- [go-mobile](https://github.com/golang/mobile)
