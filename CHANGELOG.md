# Changelog

## 1.1.0-dev - 2026-04-24

- 对齐 `OplusUpdater` OTA 主查询链路的基础协议行为
- 为 Go 核心新增主查询构造单元测试，覆盖默认参数、请求头、请求体与 endpoint 选择
- 为 `OplusUpdater/test` 增加在线 OTA 回归断言，锁定核心机型查询结果不回退到旧包
- 补充 `IN / SG` 路径在线回归样本，验证显式 `Model` 与自定义 `NvCarrier` 场景
- 统一 `OplusUpdater` Go 模块路径、仓库内导入路径与 CI Go 版本来源
- 修正 `OplusUpdater/README.md` 的安装说明、CLI 用法与当前协议字段描述
- 完成仓库基线审查，补充 `SPEC.md` 与 `ARCHITECTURE.md`
- 重写根 `README.md`，新增 `README_EN.md`
- 记录当前仓库结构、构建前提、技术债与后续更新建议
- 验证 `OplusUpdater/`：
  - `go test ./...` 通过
  - `go vet ./...` 通过
- 明确本轮未执行 Gradle 验证，仅审查 Go 核心与 Android 源码结构
- 调整 README 定位为项目介绍，将审查结论收敛到架构文档
- 在 `ARCHITECTURE.md` 中新增分阶段 To Do List，明确先修 OTA 查询正确性
