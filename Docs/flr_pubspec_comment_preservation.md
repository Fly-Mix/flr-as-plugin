# 方案研究：pubspec.yaml 写回保留注释

> 关联待办：[`flr_todo.md`](./flr_todo.md) §1。
> 状态：方案研究（未实施）。**推荐方案 B（YAML PSI 定点编辑）**。

## 1. 问题与根因

所有写 `pubspec.yaml` 的路径最终都汇入 `FlrFileUtil.dumpPubspecConfigToFile`：

```
InitUseCase / GenerateUseCase / settings.FlrConfigIO
   -> FlrFileUtil.dumpPubspecConfigToFile(project, 整个 pubspecConfig: Map, file)
      -> snakeyaml(1.26) yaml.dump(整份 Map, writer)   // 全量重序列化
      -> writeTextViaVfs(...)                          // 经 VFS 落盘
```

snakeyaml 的 `dump` 从 `Map` 重建整份文档：**不保留注释**，且可能重排键顺序、改变引号/缩进/空行等格式。
于是用户手写在 pubspec 里的注释与非 flr 相关格式，在任一次写操作后丢失/变化。

**这是既有行为**（Java 版即如此），非某次重构引入。

## 2. 实际改动面很小（关键前提）

尽管每次都全量 dump，Flr 真正修改的只有少数固定键：

- `flr` 节点：`core_version`、`dartfmt_line_length`、`assets`（序列）、`fonts`（序列）
- `flutter` 节点：`assets`（序列）、`fonts`（序列）
- `dependencies` 节点：`r_dart_library`（标量）

其余（`name`/`description`/`environment`/`dev_dependencies`/注释/空行……）Flr 从不改。
**→ 只需定点编辑这几个键、其余原样保留**，即可解决问题。

## 3. 候选方案对比

| 方案 | 做法 | 优点 | 缺点 |
|---|---|---|---|
| **A. snakeyaml 注释 round-trip** | 升级 snakeyaml 到 2.x，用 `LoaderOptions.setProcessComments(true)` + Node/compose API（非 Map），定位并改具体 Node 后 `serialize` 回写 | 不引入平台 YAML 依赖 | 需升级 snakeyaml（与平台/Dart 插件可能自带的 snakeyaml 版本冲突风险）；注释挂载/回写在 snakeyaml 仍不完美（行尾注释、块注释边界）；未改动标量的引号/流样式仍可能变；Node API 冗长 |
| **B. YAML PSI 定点编辑**（推荐） | 用平台自带 `org.jetbrains.yaml` 把 pubspec 解析为 `YAMLFile`，只替换上述几个键的值子树，其余文本逐字保留 | **注释/格式/键序完美保留**（未触及处零变化）；IntelliJ 原生、天然走 Document/undo；**无新增第三方依赖**（YAML 平台自带）；**可 headless 单测** | 需 `<depends>org.jetbrains.plugins.yaml</depends>` + `bundledPlugin('org.jetbrains.plugins.yaml')`；PSI 编辑代码比 Map dump 冗长；需处理线程（EDT + Read/WriteAction）与"键不存在则新建"等边界 |
| **C. 文本区间拼接** | 自己定位 flr/flutter 节点的字节范围，只替换那几段文本 | 无依赖 | 脆弱（缩进/嵌套/多文档），等于重造 YAML 编辑器；不推荐 |
| **D. 第三方 round-trip 库**（eo-yaml 等）| 换用能 round-trip 注释的库 | — | 引入较重新依赖、非原生；不推荐 |

## 4. 推荐方案：B（YAML PSI 定点编辑）

平台 2024.3 已自带 `org.jetbrains.yaml`（`YAMLFile`/`YAMLMapping`/`YAMLKeyValue`/`YAMLSequence`/`YAMLElementGenerator` 均在 `plugins/yaml`）。
Android Studio 亦捆绑 YAML 插件，兼容性无忧。

### 4.1 设计

- **依赖声明**：`plugin.xml` 加 `<depends>org.jetbrains.plugins.yaml</depends>`；`build.gradle` 的
  `dependencies { intellijPlatform { bundledPlugin('org.jetbrains.plugins.yaml') } }`。
- **新增 `command/util/PubspecPsiWriter.kt`**：入参为「要写的目标值」（而非整份 Map）——
  `assets/fonts`（`List<String>`）、`flutter.assets/flutter.fonts`、`flr.core_version/dartfmt_line_length`、`dependencies.r_dart_library`。
  - 取 `PsiManager.findFile(vFile) as YAMLFile` → 顶层 `YAMLMapping`；
  - 对每个受管键：`mapping.getKeyValueByKey("flr")` 等；用 `YAMLElementGenerator.getInstance(project)` 从**片段文本**生成
    新的 `YAMLKeyValue`/序列/标量，`existing.setValue(...)` 或 `mapping.putKeyValue(...)` 替换；键不存在则新增。
  - **粒度**：`flutter` / `dependencies` **只替换其下受管子键**（`assets`/`fonts` / `r_dart_library`），
    保留同节点内其它键（如 `uses-material-design`）及其注释；`flr` 节点由 Flr 全权管理，可整体替换其值。
  - 全程 `WriteCommandAction.runWriteCommandAction(project) { … }`（EDT）；导航用 `ReadAction`；
    后台线程调用处用 `invokeAndWait` 切 EDT（与现 `writeTextViaVfs` 一致）。编辑直接作用于 PSI/Document，天然可 undo、无需手动 refresh。
- **接线**：`FlrFileUtil.dumpPubspecConfigToFile` 的 3 个调用点改为调 `PubspecPsiWriter`（传目标值而非整份 Map）。
  用例内部仍可先构建 Map 做计算，只是**落盘**换成定点 PSI 写入。`dumpPubspecConfigToFile` 可保留为兜底/删除。

### 4.2 影响范围

- 改动集中在「落盘」这一层：新增 `PubspecPsiWriter`，改 3 处调用；`plugin.xml` + `build.gradle` 各加一处 YAML 依赖。
- 不改 generate/init/settings 的业务算法；输出的 flr/flutter/deps 语义不变，仅写入机制从"全量 dump"变"定点编辑"。

### 4.3 风险与缓解

| 风险 | 缓解 |
|---|---|
| YAML 插件依赖 | 平台/AS 均捆绑；`<depends>` 声明即可；`pluginVerifier` 会校验 |
| PSI 线程模型 | 导航 ReadAction、修改 WriteCommandAction/EDT，后台 `invokeAndWait`（复用现有范式） |
| 键缺失/空序列/首次 init | Writer 覆盖"新增键 / 空列表 / flr 节点不存在"分支，单测覆盖 |
| 序列/缩进风格与用户既有不完全一致 | 只影响被替换的受管值本身；未触及处零变化。可按 pubspec 习惯（2 空格、block 样式）生成 |

### 4.4 可测试性（相较 UI 是优势）

`PubspecPsiWriter` 可在 `BasePlatformTestCase` 下 headless 单测：
- 用 `PsiFileFactory` / fixture 造一份**带注释**的 pubspec 文本 → 调 Writer 改 assets/fonts/line-length/r_dart_library →
  断言：(1) 注释、无关键、键序**逐字保留**；(2) 受管键值正确更新；(3) 幂等（再写一次内容稳定）。
- 覆盖：首次新增 flr 节点、空 assets/fonts、既有 flutter 其它键保留、dependencies 其它项保留。

## 5. 备选/过渡

若暂不引入 YAML 平台依赖：可先做 **方案 A**（snakeyaml 2.x + processComments Node API）作过渡，接受其注释保留不完美；
但鉴于平台 YAML 自带且 B 可单测、保留度最佳，**建议直接上 B**。

## 6. 落地顺序（建议）

1. build.gradle + plugin.xml 加 YAML 依赖；
2. 实现 `PubspecPsiWriter`（含各边界）；
3. 加 headless 单测（注释保留 + 各键更新 + 首次 init 场景）；
4. 3 处落盘调用切到 Writer；`compileKotlin/test/buildPlugin` 全绿；
5. 真机 E2E：手写带注释的 pubspec，跑 Init/Generate/设置页 Apply，`git diff` 确认仅受管键变化、注释保留；黄金基线仍通过。
