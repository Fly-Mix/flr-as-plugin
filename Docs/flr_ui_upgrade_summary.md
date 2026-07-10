# Flr UI 功能升级小结（Kotlin UI DSL v2）

> 状态：**已完成落地**（U1–U3，2026-07-10）。
> 前置：第一阶段重构见 [`flr_refactor_plan.md`](./flr_refactor_plan.md)；第二阶段 Java→Kotlin 迁移见
> [`flr_kotlin_migration_assessment.md`](./flr_kotlin_migration_assessment.md)（全工程已 100% Kotlin）。
> 待办：见 [`flr_todo.md`](./flr_todo.md)。

## 1. 背景与目标

第二阶段完成后全工程 Kotlin。第三阶段用 **Kotlin UI DSL v2**（`com.intellij.ui.dsl.builder`：
`panel { row { cell } }` + `bindText`/`bindIntText`/`bindSelected`；平台自带，无新增 gradle 依赖）为插件新增
**表单类**界面。

说明：GUI Designer `.form` 已在 Kotlin 迁移 Phase 4 删除；当时的工具窗仅剩「分栏 + pubspec 树 + console」这类
**非表单**布局（不属 UI DSL v2 适用对象）。因此本阶段本质是**新增/改造表单界面 + 重排工具窗布局**，而非机械替换。

参考：[Kotlin UI DSL v2 官方文档](https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl-version-2.html)

## 2. 交付内容

### 共享基础（U1/U3 复用）
- `settings/FlrConfig`：flr 配置可编辑模型（`assetDirs`/`fontDirs`/`dartfmtLineLength`/只读 `coreVersion`）。
- `settings/FlrConfigIO`：读/写主工程 `pubspec.yaml` 的 `flr` 节点（复用 `FlrFileUtil` load/dump + `PubspecKeys`）。
- `settings/FlrConfigForm`：UI DSL v2 可复用表单 `Panel.flrConfigRows(project, model)` + `DirListEditor`
  （`JBList` + `ToolbarDecorator` 增删，"增"走目录选择器，路径存**相对工程根**）。
- `settings/NoopFlrReporter`：无 console 场景的 `FlrReporter` 占位。

### U1 — Flr 设置页（Configurable）
- `settings/FlrConfigurable`（`BoundSearchableConfigurable`）：`createPanel()` 用 `flrConfigRows`；
  `reset()` 从 pubspec 重载入 model、`apply()` 回写并落盘；isModified 由 DSL panel 跟踪（含列表 cell 的 `onIsModified`）。
- `plugin.xml`：`<projectConfigurable parentId="tools" id/instance=com.flr.settings.FlrConfigurable displayName="Flr"/>`。
- 无可读 pubspec 时显示只读提示。

### U2 — 工具窗工具栏 + 状态面板 + 布局重排
- `plugin.xml`：新增 `<group id="flr.toolbar">`，`<reference>` **复用**现有 5 个 action（Init/Generate/Monitor/Version/Recommend；不改动作实现、不入菜单）。
- `toolWindow/FlrStatusPanel`（UI DSL v2）：工程名 / 监控状态 / "打开 Flr 设置"链接（`ShowSettingsUtil` 跳 U1）；`refresh()` 同步监控状态。
- `toolWindow/FlrToolWindowFactory` 布局：**NORTH** = `ActionToolbar(flr.toolbar)` + 状态面板；**CENTER** = `OnePixelSplitter`（左 pubspec 树 / 右 console）。
- `toolWindow/FlrToolWindowService`：增持 `statusPanel` 引用；`actions/FlrMonitorAction` 启停后调 `statusPanel?.refresh()`。
- **工具栏图标**（后续 `13670dc`）：按 IntelliJ 惯例给 5 个动作配平台 `AllIcons`（同时作用于 Tools 菜单）——
  Init `General.Add` / Generate `Actions.Compile` / Version `General.Information` / Recommend `Actions.IntentionBulb`；
  Monitor 在 `FlrMonitorAction.update()` 中随状态切换 `Actions.Execute`（未启动）↔ `Actions.Suspend`（运行中）。

### U3 — Init 运行前配置对话框
- `settings/FlrConfigDialog`（`DialogWrapper`）：`createCenterPanel()` 用共享 `flrConfigRows`；OK 时 `panel.apply()` 回写 model。
- `actions/FlrInitAction`：Init 前 `FlrConfigIO.load` → 弹框；OK 则 `save` 落盘后执行 `initAll`，Cancel 不改动/不执行；
  无可读 pubspec 时跳过对话框（行为同旧）。

## 3. 设计取舍

- **工具窗用 `OnePixelSplitter`/`ActionToolbar` 而非 UI DSL v2**：分栏 + 树 + console 是复合布局，非表单；UI DSL v2 用于
  设置页 / 状态面板 / 对话框这些**表单**处。
- **U1 与 U3 复用同一 `flrConfigRows`**：保证两处表单一致、减少重复。
- **不改 use case 内核**：设置页/对话框只在 generate/init 外层提供配置入口；`FlrConfigIO` 写回后由既有 `initAll` 读取合并。
- **nullsafety 不入对话框**：由 SDK 版本探测决定（`IdeFlutterEnvironment`），非用户可选项。

## 4. 提交记录

U1 `c64d1bb` → U2 `a2b77f6` → U3 `6266f2f` → 工具栏图标 `13670dc`。

## 5. 验证

- 每子阶段：`compileKotlin`/`test`/`buildPlugin` 全绿（三测试类 2/1/1；本阶段不动 generate 逻辑，黄金测试仅作回归护栏）；
  `buildPlugin` 校验新 `projectConfigurable` / `flr.toolbar` 组。
- 三个界面均**真机 E2E** 验收通过（设置页显示/增删/落盘/回滚；工具栏与状态面板/Monitor 联动；Init 对话框预填/OK 落盘/Cancel 不改动）。
- UI 无渲染单测——后续改动仍以真机 E2E 为主要验收手段。

## 6. 已知限制 / 后续

- **pubspec.yaml 写回丢注释/重排**：✅ **已解决**（改用 YAML PSI 定点编辑，见 [`flr_pubspec_comment_preservation.md`](./flr_pubspec_comment_preservation.md) / [`flr_todo.md`](./flr_todo.md) §1）。
- **状态面板刷新时机**：仅在工具窗创建时 + Monitor 启停后刷新，未挂 `ToolWindowManagerListener`（切走再切回不实时刷新）。
- **分栏比例**：`OnePixelSplitter` proportion 固定 `0.3f`，未持久化用户调整。
- **目录列表编辑器**：首版仅增/删，无拖拽排序等高级交互。
- 可选补充：为 `FlrConfigIO` 加 headless 往返测试（读→改→写→复读断言），给读写逻辑加自动覆盖（渲染部分仍靠 E2E）。
