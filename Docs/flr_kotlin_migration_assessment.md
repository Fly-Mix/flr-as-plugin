# flr-as-plugin：Java → Kotlin 迁移可行性评估

> 状态：评估通过，待启动。
> 关联：第一阶段重构见 [`flr_refactor_plan.md`](./flr_refactor_plan.md)（P0~P3 + VFS 写入 + 常量集中，均已落地）。
> 决策记录：**UI 部分采纳「用 Kotlin 重写」方案**（2026-07-09 用户确认）。

## 1. 结论

**可行，风险低。** 语言迁移本身不受 UI 框架阻挡。唯一的真实摩擦点是一个 GUI Designer `.form` 文件，
而它又小、又脆弱、又正好落在计划中的 UI 升级范围内——因此「迁 Kotlin」与「升级 UI」合并推进，`.form` 在
UI 重写中自然被淘汰。

## 2. 当前事实（评估依据）

| 项 | 现状 |
|---|---|
| 代码规模 | 36 个 Java 文件、约 4274 行；**当前 0 个 Kotlin 文件** |
| 构建 | IntelliJ Platform Gradle Plugin 2.17.0 / Gradle 9.6 / JDK 17 / 平台 IC-243（2024.3.1） |
| UI 框架 | **Swing**（平台原生）：手写 Swing + **1 个 GUI Designer `.form`**；通知走平台 `Notification` API |
| UI 相关文件 | `toolWindow/FlrToolWindowFactory.java`（拼 `ConsoleView`+`JSplitPane`+`JPanel`）、`toolWindow/FlrToolWindowProvider.{java,form}`（GUI Designer 表单，3 个字段）、`pubspecFileTree/FlrPubspecFileTree.java`（`JTree`）、`logConsole/*`、`messageBox/FlrMessageBox.java` |
| 已知隐患 | 构建期出现 `Class to bind does not exist: com.flr.toolWindow.FlrToolWindowProvider` 插桩告警——`.form` 绑定本就脆弱 |

## 3. UI 框架是否允许迁移到 Kotlin

**允许。** 分两点：

1. **平台 UI 是 Swing，与语言无关。** Kotlin 调用同一套 `javax.swing` / `com.intellij.ui` API；
   Kotlin↔Java 编译为同一 JVM 字节码，互操作无缝，可逐文件混编。`@Service` / `AnAction` /
   `BulkFileListener` / `ConsoleView` 等在 Kotlin 中均为一等公民。

2. **唯一摩擦点 = GUI Designer `.form`。** IntelliJ GUI Designer 面向 Java：`.form` 的 `bind-to-class`
   与 `$$$setupUI$$$` 字节码插桩只支持绑定 **Java** 类，**不支持 Kotlin 类**（JetBrains 官方亦建议新代码
   改用 Kotlin UI DSL 而非 GUI Designer）。
   - 该 `.form` 内容极简（BorderLayout 面板 + `JSplitPane` + console 容器，共 3 个字段），且已有插桩告警；
   - 它正是要大改的 UI。
   - → **不迁 `.form`（技术上不可行），而是在 UI 升级时丢弃它、用 Kotlin 重建。**

## 4. 采纳的方案：UI 用 Kotlin 重写

- **不做机械翻译**：现有 Swing/`.form` UI 不逐行搬到 Kotlin，而是随功能升级用
  **Kotlin UI DSL v2**（`com.intellij.ui.dsl.builder`，JetBrains 当前工具窗/对话框/设置页标准做法）重建。
- 淘汰 `FlrToolWindowProvider.{java,form}`；工具窗内容（pubspec 文件树 + 日志 console 的分栏布局）
  以 Kotlin 重新组织；`FlrPubspecFileTree`、`FlrToolWindowFactory` 一并 Kotlin 化。
- `FlrToolWindowFactory.getLogConsole` 现有靠 `getComponent(index)` 取 ConsoleView 的脆弱写法，
  在重写中一并消除（改为工具窗服务持有 ConsoleView 引用）。
- 未来 UI 备选：Compose for IntelliJ(Jewel) 更新更重、成熟度与成本更高，暂不采用；如后续需要再评估。
  无论哪种，Kotlin 都是前提（DSL 本身是 Kotlin）。

## 5. 迁移策略（增量、每步可编译、测试守护）

Kotlin 与 Java 在同一模块共存，Gradle 一起编译，故可逐文件、叶子优先迁移，每步保持构建与测试绿。

**5.1 构建接入（一次性）**
- 加 `org.jetbrains.kotlin.jvm` 插件；`jvmTarget = 17`。
- **关键：`kotlin.stdlib.default.dependency=false`**（`gradle.properties`），**不打包 kotlin-stdlib**，
  依赖平台内置 Kotlin 运行时——否则类加载器版本冲突。
- `languageVersion` / `apiVersion` 对齐 IC-243 平台内置 Kotlin 版本，保守 pin。
- 源码目录：`.kt` 与 `.java` 可并存于现有 `src/main/java`（或新增 `src/main/kotlin`）。

**5.2 迁移顺序（叶子优先）**
1. 纯逻辑叶子：`FlrConstant` / `FlrPaths` / `FlrIds` / `PubspecKeys` / `core/model` / `FlrException` / 纯 util；
2. core 层：`core/scan` / `core/codegen` / `core/usecase`；
3. IDE infra：`core/env` / `core/format` / `core/report` / `infra/*` / `actions/*` / `command/FlrListener`；
4. **UI 最后**，且按第 4 节以 Kotlin UI DSL v2 **重写**（非翻译）。

**5.3 测试**
- 现有黄金/headless 测试（标准1：`r.g.dart` 归一化一致；标准2：`pubspec.yaml` 的 `flutter` 节点 YAML 对象一致）
  全程作安全网；JUnit 对 Java/Kotlin 通吃，测试可暂留 Java、后续再迁。
- UI 重写后补充：真机 E2E 确认工具窗布局、pubspec 树、日志 console、通知均正常。

## 6. 风险与缓解

| 风险 | 缓解 |
|---|---|
| `.form` 不可迁 Kotlin | 重写而非翻译；属 UI 升级收益点，非额外成本 |
| kotlin-stdlib 冲突 | `kotlin.stdlib.default.dependency=false`，依赖平台内置 |
| Kotlin 版本不对齐 | `apiVersion` 不高于平台内置 Kotlin，保守 pin |
| 多平台 switch（191~243） | 若仍需支持老平台，需确认各平台内置 Kotlin 版本；建议 UI 升级只面向较新平台 |
| Dart / io.flutter 依赖 | 语言无关，无影响 |
| UI 重写行为回归 | core/usecase 已与 UI 解耦（三接缝 + Reporter），UI 仅为展现层；逻辑由测试守护，UI 靠真机 E2E |

## 7. 建议推进路径

分两轨，可并行也可串行：

- **A 轨（机械迁移，安全、可暂停）**：非 UI 的 Java 按 5.2 顺序逐步迁 Kotlin，叶子优先，每步测试守护——纯收益、低风险。
- **B 轨（UI 重写）**：按第 4 节，随功能升级用 Kotlin + UI DSL v2 重建工具窗/树/日志视图，淘汰 `.form`
  与 `getComponent(index)` 脆弱取法。

**推荐启动动作**：先做一个 spike——接通 Kotlin 构建（5.1）+ 迁 1 个叶子文件（如 `FlrPaths`）跑通
`compileKotlin`/`compileJava`/`test`，作为构建配置的可行性实证；随后按 A 轨铺开，B 轨随 UI 升级节奏进行。
