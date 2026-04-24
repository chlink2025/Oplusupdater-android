# Changelog

## 1.1.0-dev - 2026-04-24

- 对齐 `OplusUpdater` OTA 主查询链路的基础协议行为
- 为 Go 核心新增主查询构造单元测试，覆盖默认参数、请求头、请求体与 endpoint 选择
- 为 `OplusUpdater/test` 增加在线 OTA 回归断言，锁定核心机型查询结果不回退到旧包
- 补充 `IN / SG` 路径在线回归样本，验证显式 `Model` 与自定义 `NvCarrier` 场景
- 为 Go 核心新增 `anti` 前缀策略，按 tracker 风格在 `taste` 模式下展开候选版本并返回最佳结果
- 为 `updater` CLI 增加 `--anti`，并补充本地参数测试与 `RMX3301_IN` 在线回归样本
- 为 Go 核心新增 tracker 风格的 CN `gray` host 切换，并为 CLI 增加 `--gray`
- 为 `RMX5010_CN` 增加 `gray` 在线回归样本，锁定 CN 灰度主机查询不回退
- 为 Go 核心新增 tracker 风格的 `graynew` taste-to-gray 双阶段策略，并为 CLI 增加 `--graynew`
- 为 `PHP110_CN` 增加 `graynew` 在线回归样本，锁定前缀 graynew 查询结果
- 为 Go 核心新增 tracker 风格的 `components` 请求体注入，并为 CLI 增加 `--components`
- 为 `components` 解析与请求体构造补充本地单元测试
- 统一 `OplusUpdater` Go 模块路径、仓库内导入路径与 CI Go 版本来源
- 修正 `OplusUpdater/README.md` 的安装说明、CLI 用法与当前协议字段描述
- 为 `updater` CLI 补充 `guid/pre/language/rom-version/android-version/coloros-version/pipeline-key/operator/company-id` 等高级参数
- 修复 `updater` CLI 在查询失败时可能对空结果调用 `PrettyPrint()` 的崩溃路径
- 完成仓库基线审查，补充 `SPEC.md` 与 `ARCHITECTURE.md`
- 重写根 `README.md`，新增 `README_EN.md`
- 记录当前仓库结构、构建前提、技术债与后续更新建议
- 验证 `OplusUpdater/`：
  - `go test ./...` 通过
  - `go vet ./...` 通过
- 明确本轮未执行 Gradle 验证，仅审查 Go 核心与 Android 源码结构
- 调整 README 定位为项目介绍，将审查结论收敛到架构文档
- 在 `ARCHITECTURE.md` 中新增分阶段 To Do List，明确先修 OTA 查询正确性
