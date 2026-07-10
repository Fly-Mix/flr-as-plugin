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
