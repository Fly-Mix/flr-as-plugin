# Flr 待办 / 已知问题（后续专项处理）

## 1. pubspec.yaml 写回会丢失注释 / 重排格式 — ✅ 已解决（2026-07-10）

> 方案研究见 [`flr_pubspec_comment_preservation.md`](./flr_pubspec_comment_preservation.md)（方案 B：YAML PSI 定点编辑）。

**原现象**：所有写 `pubspec.yaml` 的路径（Init / Generate / 设置页 Apply）都经 `FlrFileUtil.dumpPubspecConfigToFile`
→ snakeyaml **全量 dump** 整个文档，不保留注释、可能重排键顺序 / 调整格式（既有行为，非某次重构引入）。

**解决**：新增 `command/util/PubspecPsiWriter`，用平台自带 YAML PSI（`org.jetbrains.plugins.yaml`）**定点写入受管键**
（`flr` 整节点、`flutter.assets/fonts`、`dependencies.r_dart_library`），其余文本逐字保留；`dumpPubspecConfigToFile`
改为**先 PSI 定点写、失败回退旧全量 dump**，三处落盘零改动全部受益。`PubspecPsiWriterTest` 覆盖注释保留/受管键更新/
幂等/首次 init；真机 E2E 确认注释与非受管键在 Init/Generate/设置页 Apply 后均保留。

**残留**：被替换的**受管键值本身**内的行内注释（如 `r_dart_library: x  # 注`）在值变更时仍会丢（已加"值未变则不动"守卫，
Generate 不会误伤）；其余注释均保留。可接受。

## 2. UI 打磨（开放，低优先）

- **工具窗状态面板刷新时机**：目前仅在工具窗创建时 + Monitor 启停后刷新（`FlrStatusPanel.refresh()`），未挂
  `ToolWindowManagerListener`；切走再切回不实时刷新。可加监听器实现随激活刷新。
- **分栏比例未持久化**：`FlrToolWindowFactory` 的 `OnePixelSplitter` proportion 固定 `0.3f`，用户拖动不记忆。
- **目录列表编辑器**：`DirListEditor` 仅支持增/删，无拖拽排序等高级交互。

## 3. 测试 / CI（开放）

- **`FlrConfigIO` 往返单测**：读→改→写→复读断言（`BasePlatformTestCase` + 临时 pubspec），为设置/对话框的读写加自动覆盖。
- **P4 CI**：✅ 已加基础 CI（`.github/workflows/ci.yml`：`test` + `buildPlugin` + 产物上传，JDK21、Gradle 缓存）。
  **仍开放**：(a) `verifyPlugin`（需先在 build.gradle 配 `pluginVerification { ides { recommended() } }`）；
  (b) **CI 内完整黄金回归**（需在 CI 提供基准工程 flutter_r_demo + Dart SDK，注入 `-Pflr.demo.path`/`-Pflr.dart.path`；
  当前这两个 golden 测试在 CI 无夹具时自动 skip）。
- **P4 i18n**：用户可见文案抽到 `messages/FlrBundle.properties`（`DynamicBundle`）——尚未做。

## 4. 验证约定（备忘）

- **真机 E2E 会写脏基准工程 `flutter_r_demo`**（Init/Generate/设置页 Apply 写 `pubspec.yaml`、`lib/r.g.dart`、`pubspec.lock`），
  会使黄金测试（对比 git HEAD）失配。**验完需还原**：
  `git -C <…>/flutter_r_demo checkout -- pubspec.yaml lib/r.g.dart pubspec.lock`。
