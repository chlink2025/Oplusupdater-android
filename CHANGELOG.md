# Changelog

## 1.1.0-dev - 2026-04-24

- 完成仓库基线审查，补充 `SPEC.md` 与 `ARCHITECTURE.md`
- 重写根 `README.md`，新增 `README_EN.md`
- 记录当前仓库结构、构建前提、技术债与后续更新建议
- 验证 `OplusUpdater/`：
  - `go test ./...` 通过
  - `go vet ./...` 通过
- 明确本轮未执行 Gradle 验证，仅审查 Go 核心与 Android 源码结构
- 调整 README 定位为项目介绍，将审查结论收敛到架构文档
- 在 `ARCHITECTURE.md` 中新增分阶段 To Do List，明确先修 OTA 查询正确性
