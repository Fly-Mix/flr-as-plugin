# scripts — 基准回归安全网

重构 flr-as-plugin 前后，用来验证「生成结果没被改坏」的工具。

## 基线

基准工程：`/Users/York/dist_Insta360/AiSpace/flr-workspace/flutter_r_demo`
其 **git HEAD** 中 committed 的 `lib/r.g.dart` 与 `pubspec.yaml` 即黄金输出（flr-cli 3.2.0 生成）。

## 两条验收标准

1. `lib/r.g.dart` 与基线一致，采用**归一化比较**：用同一个 `dart format` 把两侧都重新格式化后再比字节。
   —— 脱离 dart formatter 版本（Dart 3.7 换了不可逆的新 "tall style"，直接字节比会误报）。
   —— 需要 dart：默认 `/Users/York/.flutter/flutter/bin/dart`，可用 `--dart` 覆盖。
2. `pubspec.yaml` 的 `flutter` 节点与基线 **按 YAML 对象一致**（非字符串比对）。
   - 顺序敏感的 `assets` 列表顺序变化视为不一致；
   - mapping 键序、缩进、引号、注释等纯排版差异不影响判定；
   - `flr` 节点不在验收范围。

## 用法

```bash
# 1) 在 Android Studio 里对基准工程执行一次 Flr-Generate（修改其工作区）
# 2) 运行校验（首次自动创建隔离 venv 并安装 pyyaml；venv 已被 gitignore）
scripts/verify_flr_baseline.sh

# 指定其它工程 / 其它基线修订
scripts/verify_flr_baseline.sh --project /path/to/flutter_r_demo --rev <commit>
```

退出码：`0`=两条标准全过；`1`=存在不一致（会打印精确差异）；`2`=运行错误。

## 工作流

每完成一个重构阶段 → 对基准工程 Generate → 跑本脚本。
全绿则安全推进；变红则判断是无意破坏（改回）还是刻意变更（更新黄金文件并在 commit 说明）。

## 文件

- `verify_flr_baseline.sh` — 入口，自动准备 venv 后调用 py 脚本。
- `verify_flr_baseline.py` — 比对逻辑（标准1 字节 / 标准2 YAML 对象）。
- `.venv/` — 本地隔离环境（gitignore，勿入库）。
