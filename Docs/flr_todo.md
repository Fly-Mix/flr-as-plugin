# Flr 待办 / 已知问题（后续专项处理）

## 1. pubspec.yaml 写回会丢失注释 / 重排格式

> 方案研究见 [`flr_pubspec_comment_preservation.md`](./flr_pubspec_comment_preservation.md)（推荐方案 B：YAML PSI 定点编辑）。**实施中**。

**现象**：所有写 `pubspec.yaml` 的路径（Init / Generate / 设置页 Apply）都经 `FlrFileUtil.dumpPubspecConfigToFile`
→ snakeyaml **全量 dump** 整个文档。snakeyaml 的 dump 不保留原文件的注释，也可能重排键顺序 / 调整格式。

**影响**：用户手写在 `pubspec.yaml` 里的注释、以及非 flr 相关的格式，在任一写操作后可能丢失/变化。

**现状**：这是**既有行为**（Java 版即如此），并非某次重构引入；设置页（U1）只是新增了又一个触发点。

**方向（专项）**：改为「保留注释的最小化写入」，可选方案——
- 用保留注释的 YAML 库（如 SnakeYAML Engine + 自定义，或改用能 round-trip 注释的方案）；
- 或只对 `flr` / `flutter` 节点做**局部文本编辑**（定位节点范围后就地替换，不整篇重序列化）；
- 或引入 pubspec 的结构化编辑（PSI / YAML 插件 API）做定点修改。

**决策**：2026-07-10 记为 TODO，暂不在 UI 升级阶段处理，后续单独立项。
