# flr-as-plugin 重构计划

> 面向 IntelliJ Platform（Android Studio）插件的现代化重构方案。
> 依据 JetBrains 官方《IntelliJ Platform Plugin SDK》文档的当前最佳实践编写。
> 生成日期：2026-07-06

---

## 0. 结论摘要（TL;DR）

`flr-as-plugin` 是一个功能完整、可用的 Flutter 资源管理插件，但代码停留在早期 IntelliJ SDK 写法上，存在若干**正确性隐患**（多项目串号、后台线程裸读 VFS、Application 级监听）和大量**可维护性问题**（1414 行巨类、元组传参、调试输出遗留、零测试）。

重构按风险与收益分 5 个阶段推进，**优先修复正确性缺陷**，再做架构与工程化现代化：

| 阶段 | 主题 | 风险 | 收益 |
|------|------|------|------|
| P0 | 正确性缺陷修复（服务单例、线程模型、监听器） | 高 | 高 |
| P1 | 工程化（Gradle 2.x、JDK、依赖、plugin.xml） | 中 | 高 |
| P2 | 架构分层（拆分 FlrCommand、领域模型） | 中 | 高 |
| P3 | 代码质量（惯用写法、常量、注解统一、清理调试） | 低 | 中 |
| P4 | 测试与 CI、国际化、文档 | 低 | 中 |

---

## 1. 项目现状盘点

### 1.1 结构

```
src/main/java/com/flr/
├── FlrApp.java              # 项目级服务入口（60）
├── FlrConstant.java         # 常量（48）
├── FlrException.java        # 自定义异常（16）
├── actions/                 # 5 个菜单 Action（Init/Generate/Monitor/Version/Recommend）
├── command/
│   ├── FlrCommand.java      # 核心业务巨类（1414）★
│   ├── FlrChecker.java      # 环境/配置校验（169）
│   ├── FlrListener.java     # VFS 资源变化监听（86）
│   └── util/                # FlrFileUtil(543) / FlrCodeUtil(588) / FlrAssetUtil(401) / FlrUtil(156)
├── logConsole/              # 日志控制台（FlrLogConsole / FlrColoredLogEntity）
├── messageBox/              # 通知封装（FlrMessageBox）
├── pubspecFileTree/         # 工具窗左侧文件树
└── toolWindow/              # 工具窗工厂 + .form UI
src/main/resources/META-INF/plugin.xml
build.gradle                 # Groovy DSL，Gradle IntelliJ Plugin 1.17.1
```

- 目标平台：`IC-243`（Android Studio Meerkat 2024.3.1），`platformVersion = 2024.3.1`
- 依赖插件：`java`、`Dart:243.23177`、`io.flutter:82.2.4`
- 无 `src/test`，无 CI 配置，`gradle-wrapper.properties` 未纳入版本库。

### 1.2 已核实的问题清单（grep/通读确认）

- `System.out.println` 调试输出遗留：**14 处**（FlrListener、FlrToolWindowFactory 等）。
- 没有任何 Action 重写 `getActionUpdateThread()`。
- 后台线程中**没有任何** `ReadAction` 包裹 VFS 访问。
- `plugin.xml` **未声明** `<notificationGroup>`，但 FlrMessageBox 直接以字符串 `"Flr"` 构造 `Notification`（新平台已弃用该构造方式）。
- 无测试目录、无 CI。

---

## 2. 核心问题分析（按严重度）

### 2.1 【严重·正确性】FlrApp 静态单例导致多项目串号

`FlrApp` 同时使用了三套互相冲突的机制：

```java
@Service(Service.Level.PROJECT)              // ① 平台已保证按 project 单例
@State(name="FlrApp", storages=@Storage("flr.xml"))  // ② 声明了持久化但未实现 PersistentStateComponent（无效）
public final class FlrApp {
    private static FlrApp instance;          // ③ 手写 static 单例 —— 致命
    public static FlrApp getInstance(Project project) {
        if (instance == null) { ... instance = project.getService(FlrApp.class); }
        return instance;                     // 多个项目窗口共享第一个 project 的实例！
    }
}
```

- `static instance` 被所有打开的项目共享 → 在第二个项目里拿到的是第一个项目的 `FlrCommand`/`Project`，监控、生成会作用到错误的工程。
- 还残留 `import ProjectComponent`（已弃用）、`import ServiceManager`（未使用）。
- 手动订阅 `ProjectManager.TOPIC` 调 `dispose()`，与 `@Service(PROJECT)` 的生命周期职责重叠。

**方向**：删除 static 单例与 `@State`（无实际持久化需求），`getInstance` 直接返回 `project.getService(FlrApp.class)`；让 `FlrApp` / `FlrCommand` 实现 `Disposable`，交平台随项目关闭自动释放，移除手动 TOPIC 订阅。参考官方 *Plugin Services* / *Light Services*。

### 2.2 【严重·线程】后台线程裸读 VFS，未加读锁

各 Action 在 `Task.Backgroundable.run()`（后台线程）里直接调用：

```java
LocalFileSystem.getInstance().findFileByIoFile(...)
virtualFile.getChildren()
VfsUtilCore.visitChildrenRecursively(...)   // FlrFileUtil 多处
```

官方《Threading Model》要求：PSI/VFS/项目模型**非线程安全**，读取必须在 read action 下进行。当前完全没有 `ReadAction` 包裹，在索引/写操作并发时可能抛 `Already disposed` / 数据竞争 / 读到不一致状态。

**方向**：
- 扫描类只读逻辑用 `ReadAction.compute(...)` 或非阻塞 `ReadAction.nonBlocking(...)` 包裹；
- 文件写入改用 `WriteAction` + VFS API（见 2.5），并统一到 `WriteCommandAction`；
- 长耗时扫描配合 `ProgressIndicator` 支持取消（当前 `Task.Backgroundable(..., false)` 不可取消）。

### 2.3 【严重·线程】FlrListener 订阅 Application 级 MessageBus + 无防抖

```java
connection = ApplicationManager.getApplication().getMessageBus().connect(); // 应为 project 级
connection.subscribe(VirtualFileManager.VFS_CHANGES, this);
...
public void after(List<? extends VFileEvent> events) {
    VirtualFile eventFile = event.getFile();      // 可能为 null → NPE
    String fileFullPath = eventFile.getPath();
    ...
    if (shouldCallback) curAssetChangesEventCallback.run(); // 直接触发 generateAll（重活）
}
```

问题：
- 监听全 IDE 而非当前项目；`connect()` 未绑定 `Disposable` 父对象；
- `event.getFile()` 可能为 `null`（如 `VFileDeleteEvent`），存在 NPE；
- 用 `path.contains(dir)` 做子串匹配，易误匹配（`.../images_bak` 命中 `.../images`）；
- 每个事件批次直接同步跑 `generateAll`，无**防抖/合并**，连续保存会风暴式重复生成。

**方向**：改用 `project.getMessageBus().connect(disposable)`；用 `VfsUtilCore.isAncestor` 精确判断；对回调做防抖（例如 `Alarm` 延迟合并 300–500ms 再执行一次）；`after()` 里只做轻量判断，重活切后台线程 + 读锁。

### 2.4 【严重·线程】FlrLogConsole 每行一个 WriteCommandAction

```java
public void println(String text, LogType logType) {
    ApplicationManager.getApplication().invokeLater(() ->
        WriteCommandAction.runWriteCommandAction(curProject, () -> {
            curConsoleView.print(text + "\n", contentType);   // print 本身线程安全，无需写命令
        }));
}
```

- `ConsoleView.print` 无需写锁；用 `WriteCommandAction` 会产生大量无意义的可撤销命令（污染 undo 栈、性能差）；
- `println(FlrColoredLogEntity)` 递归调用会产生 N 次 `invokeLater`，日志顺序与批量刷新不可控。

**方向**：直接 `consoleView.print(...)`（必要时 `invokeLater` 到 EDT），去掉 `WriteCommandAction`；把 `FlrLogConsole` 做成随工具窗生命周期存在的对象，而非每次 `getLogConsole` 都 `new`（见 2.6）。

### 2.5 【中·正确性】文件写用 java.io 绕过 VFS

`FlrFileUtil.writeContentToFile` / `dumpPubspecConfigToFile` 用 `FileWriter`/`Files.newBufferedWriter` 直接写磁盘，再 `LocalFileSystem.refresh`。这绕过 VFS，与 IDE 内文档/缓存可能不一致，且非事务性。

**方向**：统一走 `WriteAction` + `VirtualFile.setBinaryContent`/`getOutputStream`，或经 `Document` API 写入；写 YAML/`r.g.dart` 都在写命令内完成并触发保存。

### 2.6 【中·健壮性】ToolWindow 内容靠强制类型转换定位

```java
JSplitPane splitPane = (JSplitPane) flrToolWindowContent.getComponent(0);
JPanel consoleContainer = (JPanel) splitPane.getRightComponent();
ConsoleView consoleView = (ConsoleView) consoleContainer.getComponent(0);
FlrLogConsole flrLogConsole = new FlrLogConsole(project, consoleView); // 每次都 new
```

- 依赖 UI 组件树的固定索引，`.form` 一改就崩；
- 每次调用都新建 `FlrLogConsole` 与查找组件，脆弱且低效。

**方向**：把 `ConsoleView`/`FlrLogConsole`/`FlrPubspecFileTree` 的引用收敛到一个**项目级 UI 服务**（如 `FlrToolWindowService` 或复用 `FlrApp`）中持有，Action 通过服务获取，不再遍历组件树。

### 2.7 【中·可维护性】FlrCommand 巨类（1414 行）

`initOne`/`generateOne` 各百余行，单个方法内混合了：环境校验、YAML 读写、资源扫描、asset-id 生成、Dart 代码拼接、UI 反馈、异常处理。`generateOne` 有 22 个 `Step-N` 注释段——这是明显的「一个方法一个流水线」信号。

**方向**：拆分为职责单一的协作类（见第 3 节）。

### 2.8 【低-中·代码质量】散落问题

- **元组传参**：`List<List<String>>`、`List<List<VirtualFile>>`、`resourceDirResultTuple.get(0/1/2)` 全靠下标语义，极易错 → 引入领域模型/record。
- **调试输出**：14 处 `System.out.println` → 换 `Logger.getInstance(...)` 或删除。
- **非惯用写法**：`Boolean` 当 `boolean`、`== false`、`instanceof Map == false`、`Integer.valueOf` 拆装箱。
- **注解混用**：`com.sun.istack.NotNull` 与 `org.jetbrains.annotations.NotNull` 混用 → 统一为后者。
- **全类 `@SuppressWarnings("unchecked")`**：掩盖大量未检查转换 → 收敛到最小作用域并显式校验。
- **硬编码字符串**：`"pubspec.yaml"`、`"flr"`、`"assets"`、`"fonts"`、`"lib/r.g.dart"`、`"flutter.pub.get"` 等散落 → 提到 `FlrConstant`。
- **YAML import 不一致**：`FlrCommand` 引用了 `org.snakeyaml.engine.v2.exceptions.YamlEngineException`（snakeyaml-engine），而依赖只声明 `org.yaml:snakeyaml:1.26` → 澄清并统一 YAML 库。
- **`versionCompare` 语义反直觉**：小于返回 `2`（而非 -1）→ 收敛为标准 `-1/0/1` 或改用 `Comparator`。

### 2.9 【中·工程化】构建与 plugin.xml

- **Gradle IntelliJ Plugin 1.17.1（1.x）**：官方新项目推荐 **IntelliJ Platform Gradle Plugin 2.x**。
- `build.gradle` 用 Groovy + 巨大的 `PlatformType` 枚举维护全历史版本；`sourceCompatibility` 用 switch 决定，实际 243 用 JDK17。
- `plugin.xml`：
  - 未声明 `<notificationGroup id="Flr" .../>`（FlrMessageBox 需要）；
  - `<depends>com.intellij.modules.java</depends>` 对纯 Flutter 插件可能非必要，需核实（会限制可安装的 IDE）；
  - `pluginDescription`/`changeNotes` 内嵌在 gradle 脚本里，建议外置到文件（`patchPluginXml` 从 README/CHANGELOG 读取）。
- `gradle-wrapper.properties` 未入库 → 团队/CI 构建不可复现。

### 2.10 【中】完全没有测试

零单元测试、零 UI/集成测试。资源扫描与代码生成（`FlrAssetUtil`/`FlrCodeUtil`）是纯逻辑，非常适合单测，却毫无覆盖，重构风险高。

---

## 3. 目标架构

将「Action（触发）→ 服务（协调）→ 用例（业务）→ 基础设施（IO/VFS/代码生成）」分层：

```
actions/                         # 仅负责取 Project、切后台任务、调服务；薄
  FlrInitAction / FlrGenerateAction / FlrMonitorAction / ...
services/
  FlrProjectService (@Service PROJECT, Disposable)   # 取代 FlrApp：持有 monitor 状态、UI 引用
  FlrToolWindowService                               # 持有 ConsoleView / FileTree 引用
core/                            # 平台无关的纯业务（可脱离 IDE 单测）
  usecase/  InitUseCase / GenerateUseCase / MonitorUseCase
  scan/     AssetScanner                             # 输入目录 → 领域对象
  codegen/  RDartGenerator（原 FlrCodeUtil）
  model/    ResourceScanResult / FlrConfig / FontFamilyConfig / AssetId ...（record）
infra/                           # IDE/平台交互
  PubspecReaderWriter（VFS + YAML）
  VfsAssetRepository（ReadAction 封装扫描）
  DartFormatter（DartStyleAction 封装）
  FlrNotifier（NotificationGroupManager）
ui/
  FlrLogConsole / FlrColoredLogEntity / FlrPubspecFileTree / FlrToolWindowFactory
util/
  FlrConstant / VersionUtil / FlrException
```

关键原则（对齐官方文档）：
- **服务不做构造期重活**，依赖服务在方法内按需 `project.getService(...)` 获取（官方明确：构造注入已弃用且 Light Service 不支持）。
- **读走 ReadAction、写走 WriteCommandAction**，长任务走 `Task.Backgroundable` + 可取消 `ProgressIndicator`。
- **core 层不 import 任何 `com.intellij.*`**，用接口把 VFS/通知/格式化抽象出去，使核心逻辑可单测。

---

## 4. 分阶段实施计划

> 每阶段结束都应能编译、装载、手工验证「Init / Generate / Monitor / Version / Recommend」五个入口。建议每阶段独立 PR。

### P0 — 正确性缺陷修复（最高优先）
1. **FlrApp → FlrProjectService**：删 `static instance` 与 `@State`；`getInstance` 直接 `project.getService(...)`；实现 `Disposable`，去掉手动 `ProjectManager.TOPIC` 订阅；删除 `ProjectComponent`/`ServiceManager` 残留 import。
2. **VFS 读加锁**：`FlrFileUtil` 中所有 `findFileByIoFile`/`getChildren`/`visitChildrenRecursively` 的调用点用 `ReadAction.compute` 包裹（或整段扫描用 `nonBlocking`）。
3. **FlrListener 重写**：改 project 级 messageBus、绑定 `Disposable` 父级、`event.getFile()==null` 保护、`VfsUtilCore.isAncestor` 精确匹配、`Alarm` 防抖合并。
4. **FlrLogConsole**：去掉 `WriteCommandAction`，直接 `print`，必要时 `invokeLater` 到 EDT。
5. **可取消任务**：`Task.Backgroundable(..., true)`，扫描循环检查 `indicator.checkCanceled()`。

**验收**：多开两个 Flutter 工程互不串号；大量资源保存不触发生成风暴；后台运行不再偶发 VFS 相关异常。

### P1 — 工程化现代化
1. 迁移到 **IntelliJ Platform Gradle Plugin 2.x**（或至少锁定 1.17.x 并入库 `gradle-wrapper.properties`）。
2. 采用 `gradle.properties` + `libs.versions.toml` 管理平台版本/依赖；`build.gradle` 可选转 Kotlin DSL。
3. 统一 `sourceCompatibility = 17`（243 目标），清理不再维护的历史 `PlatformType`。
4. `plugin.xml`：新增 `<notificationGroup id="Flr" displayType="BALLOON"/>`；核实并按需移除 `com.intellij.modules.java` 依赖；`pluginDescription`/`changeNotes` 外置到 `README`/`CHANGELOG` 由 `patchPluginXml` 注入。
5. 澄清 YAML 依赖（snakeyaml vs snakeyaml-engine），统一为一套并显式声明版本。

**验收**：`./gradlew buildPlugin verifyPlugin runIde` 全绿；Plugin Verifier 无兼容性/弃用阻断项。

### P2 — 架构分层
1. 抽取 `core/model` 领域对象，消灭 `List<List<...>>` 元组（`ResourceScanResult`、`FontFamilyConfig` 等 record）。
2. 把 `FlrCommand` 的 `initOne`/`generateOne` 流水线拆为 `InitUseCase`/`GenerateUseCase` + `AssetScanner` + `RDartGenerator` + `PubspecReaderWriter`。
3. `FlrToolWindowFactory` 不再靠 `getComponent(index)` 定位控件，改由 `FlrToolWindowService` 持有引用。
4. `FlrNotifier` 用 `NotificationGroupManager.getInstance().getNotificationGroup("Flr")`。

**验收**：core 层可在无 IDE 环境编译；单个类 < ~300 行；行为与重构前一致。

### P3 — 代码质量
1. 清理 14 处 `System.out.println` → `com.intellij.openapi.diagnostic.Logger`。
2. 统一注解为 `org.jetbrains.annotations.*`；`Boolean`→`boolean`；消除 `== false`/`instanceof X == false`。
3. 收敛 `@SuppressWarnings("unchecked")` 到最小范围并加显式类型校验。
4. 硬编码字符串集中到 `FlrConstant`；`versionCompare` 语义规范化。
5. Action 重写 `getActionUpdateThread()`（如需 `update()` 逻辑）。

### P4 — 测试 / CI / i18n / 文档
1. 建 `src/test`，对 `AssetScanner`/`RDartGenerator`/`VersionUtil`/`PubspecReaderWriter` 写单元测试；用 `BasePlatformTestCase` 覆盖服务/Action 关键路径。
   - **进阶**：把 5.1 的基准回归升级为 `BasePlatformTestCase` 集成测试——headless 加载 `flutter_r_demo`、跑 generate 逻辑、以同样两条标准断言（r.g.dart 逐字节、flutter 节点 YAML 对象），纳入 CI。需处理测试环境的 Flutter/Dart SDK 与 dartfmt 依赖（可对无 SDK 环节做桩）。
2. 加 GitHub Actions：`buildPlugin` + `verifyPlugin` + `test`（含基准回归校验）。
3. 用户可见文案抽到 `messages/FlrBundle.properties`（`DynamicBundle`）。
4. 更新 README / `flr_plugin_workflow`，补充本重构后的架构说明。

---

## 5. 风险与缓解

### 5.1 基准回归安全网（黄金文件）★

已存在基准工程：`/Users/York/dist_Insta360/AiSpace/flr-workspace/flutter_r_demo`
（git 分支 `test/flr-core-3.2.0-nullsafety`，基线提交 `4de8f45`「基于 flr-cli-3.2.0 建立基准测试」）。
其 committed 的 `lib/r.g.dart` 与 `pubspec.yaml` 就是用 flr-cli 3.2.0 生成的**黄金输出**，
即 **git HEAD 本身就是黄金基线**，无需另存。

**工具链版本坑（已实测）**：黄金 `r.g.dart` 是较早 Dart SDK 的 `dart format` 产物（旧风格）；
Dart 3.7 起启用了不可逆、不可配置的新格式化器（"tall style"），当前 Dart 3.12.2 **无法逐字节复现旧黄金**。
因此验收标准1 采用**归一化比较**脱离 formatter 版本（见下）。

**两条验收标准（粒度不同，务必区分）：**

1. `lib/r.g.dart` 与基线一致，采用**归一化比较（脱离 formatter 版本）**：
   用同一个 `dart format` 把「黄金」与「当前」两侧都重新格式化后再逐字节比对。
   formatter 输出只取决于代码 AST，与原始排版无关——旧/新风格（尾逗号、`)` 换行等）被抹平，
   真实代码差异（标识符/字符串/asset 列表）仍会暴露。已正反向实测：
   把当前产物转新 tall-style（字节 172 行 diff）仍判一致；植入 package 名差异则精确报出。
   本机 dart：`/Users/York/.flutter/flutter/bin/dart`（Flutter 3.44.5 / Dart 3.12.2）。
2. `pubspec.yaml` 的 `flutter` 节点与基线 **按 YAML 对象一致**（**不是**字符串比对）。
   因为插件每次 generate 会用 SnakeYAML 整体重写 pubspec.yaml（丢注释、可能改引号/缩进/键序），
   字符串比对必然误报；只有解析成对象后深比较才有意义。
   注意：mapping 键序不敏感，但 `assets` 等 sequence 的**顺序敏感**（顺序变化判为不一致）。
   `flr` 节点是配置回显，刻意不纳入验收范围。

**已落地的校验工具**（本仓库 `scripts/`）：

```bash
# 1) 在 Android Studio 中对基准工程执行一次 Flr-Generate
# 2) 运行校验（首次会自动建隔离 venv + 装 pyyaml）
scripts/verify_flr_baseline.sh
# 可选：scripts/verify_flr_baseline.sh --project <path> --rev <commit>
```

脚本对上述两条标准分别判定并给出精确差异（r.g.dart 首个差异行号；flutter 节点缺失/多出的 asset 明细），
退出码 0=通过 / 1=不一致。已通过正反向自测：能抓字节级与语义级差异，且不会因纯重排版/注释误报。

**重构工作流**：每完成一个阶段 → 对基准工程 Generate → 跑 `verify_flr_baseline.sh`。
- 全绿 = 行为未变，安全推进；
- 变红 = 停下判断：无意破坏则改回；确属刻意变更则在基准工程更新黄金文件并在 commit 说明。

### 5.2 其它风险
- **平台线程模型仍在演进**（2024.1 协程、2025.x write-intent 变更）：本插件为 Java，先落实经典 `ReadAction`/`WriteCommandAction`/`invokeLater`；仅在确有需要时再评估协程。
- **Gradle 2.x 迁移**成本集中在 DSL 变化：可作为独立分支验证 `runIde` 后再合并。
- **行为回归**：坚持每阶段可运行 + 手工验证五入口 + 黄金文件比对。

---

## 7. 实施进展（2026-07-07）

**P2 核心抽取（仅 generate 流水线）已落地并验证：**
- 新增三接缝 `com.flr.core.env.FlutterEnvironment` / `core.format.DartFormatter` / `core.report.FlrReporter`
  及 IDE 实现 `com.flr.infra.*`；`FlrCommand` 已注入前两者（`FlrReporter` 建好待 P2.3 接入）。
- 抽出纯核心 `core.scan.AssetScanner`、`core.codegen.RDartGenerator`、`core.model.ResourceScanResult`（消灭元组）；
  `FlrCommand.generateOne` 机械改为调用之，行为不变。
- 测试：`RDartGeneratorTest`（纯 JUnit，2 例）+ `GenerateGoldenTest`（纯 JUnit：从黄金 pubspec 重建
  ResourceScanResult → RDartGenerator → 与黄金 r.g.dart 归一化比对，1 例）。`./gradlew test` 全绿；`./gradlew buildPlugin` 产出 zip。
- 构建：本机以缓存的 **Gradle 8.13** 生成了钉版 wrapper（`gradlew` + `gradle/wrapper/`）。
  **注意**：gradle-intellij-plugin **1.17.1 不兼容 Gradle 9.x**（`DefaultArtifactPublicationSet` 被移除），sync 必须用 8.x。

**已知限制 / 后续：**
- VFS 依赖的 headless 全流程测试（走 `BasePlatformTestCase`）在 **gradle-intellij-plugin 1.x + 2024.3 平台**下
  因平台测试 bootstrap 兼容问题（`SettingsController` 未注册，JetBrains IJPL-155647）无法启动；
  故当前 headless 测试只覆盖 codegen（不启平台），扫描器与 pubspec 合并的端到端回归依赖 `scripts/verify_flr_baseline.sh`（真实 IDE）。
  该 headless 集成测试待 **P1 迁移到 IntelliJ Platform Gradle Plugin 2.x** 后补齐。
- **P2.3（GenerateUseCase 完整抽取 + FlrReporter 接入）** 已推迟：对 headless 不必需，待本轮在 Android Studio 跑通黄金回归后再进行。
- 建议把 gradle wrapper 纳入版本库（当前 `.gitignore` 排除，导致构建不可复现）——并入 P1。

### 8. 实施进展（2026-07-08）：P1 = 迁移到 IntelliJ Platform Gradle Plugin 2.x（B 计划）

已完成并本地验证（`buildPlugin` + `test` 全绿，产物 `flr-as-plugin-243.3.4.0.zip`）：
- 插件 `org.jetbrains.intellij` 1.17.1 → **`org.jetbrains.intellij.platform` 2.17.0**；重写 `repositories`/`dependencies.intellijPlatform`
  （`intellijIdeaCommunity` + `bundledPlugin com.intellij.java` + `plugin Dart/io.flutter` + `testFramework(Platform)` + `jetbrainsRuntime()` + `pluginVerifier/zipSigner`）；
  `intellij{}`+`patchPluginXml{}` → `intellijPlatform { buildSearchableOptions=false; pluginConfiguration{...} }`。**保留** `PlatformType` 枚举/switch（仅改消费端）。
- **关键收益兑现**：`GenerateHeadlessTest`（走 VFS 的全流程 headless 测试）在 2.x + `testFramework` 下**恢复并通过**——1.x 下的 `SettingsController` bootstrap 问题（IJPL-155647）已解。

**迁移中确认的硬性要求（修正先前说法）：**
- 最新 2.x（2.17.0）**要求 Gradle ≥ 9.0**（非早期文档所说 8.13）→ wrapper 已从 8.13 **升级到 9.6.0**（重新入库）。
- 构建**要求 JDK 21**（2.x 为 2024.3 平台把编译/运行工具链设为 21）→ Gradle JVM 需为 JDK/JBR 21（Android Studio 自带 JBR 21）。
- 2.x 从 `download.jetbrains.com` 下载平台为 **~887MB .dmg**（macOS）→ 首次构建较慢；受限/离线网络可用 `-PflrLocalIde=/path/to/installed-ide` 指向本地 IDE 绕开下载。
- `until-build` 现为开放上限（`since-build=243`，无上限）；1.x 曾默认封顶 `243.*`。如需恢复封顶，在 `ideaVersion { untilBuild = "243.*" }` 设置。

**待办**：`verifyPlugin`/`runIde` 收尾校验、真机 E2E（`verify_flr_baseline.sh`）最终确认；把 wrapper(9.6.0)+build.gradle+测试提交。

## 6. 官方文档参考

- Developing Plugins — https://plugins.jetbrains.com/docs/intellij/developing-plugins.html
- Plugin Services — https://plugins.jetbrains.com/docs/intellij/plugin-services.html
- Light Services（`@Service`）— https://plugins.jetbrains.com/docs/intellij/plugin-services.html#light-services
- Threading Model — https://plugins.jetbrains.com/docs/intellij/threading-model.html
- Background Processes — https://plugins.jetbrains.com/docs/intellij/background-processes.html
- Components（已弃用，需迁移）— https://plugins.jetbrains.com/docs/intellij/plugin-components.html
- Notable Changes 2025.* — https://plugins.jetbrains.com/docs/intellij/api-notable-list-2025.html
- IntelliJ Platform Gradle Plugin 2.x — https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
