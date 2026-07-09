#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
flr-as-plugin 基准回归校验（黄金文件比对）。

用法：
    先在 IDE 中对基准工程执行一次 Flr-Generate，
    再运行本脚本，比对「当前工作区产物」与「git 基线（黄金文件）」。

两条验收标准（与需求一致）：
  1. lib/r.g.dart 与基线一致，采用**归一化比较**（脱离 dart formatter 版本）：
     用同一个 `dart format` 把「黄金」与「当前」两侧都重新格式化后再逐字节比对。
     —— formatter 输出只取决于代码 AST，与原始排版无关；因此 Dart 3.7 前后
        （旧风格 vs 新 tall-style）的差异会被抹平，而真正的代码差异仍会暴露。
        这样验收不再被 SDK/formatter 版本漂移绑架。
  2. pubspec.yaml 的 `flutter` 节点与基线**按 YAML 对象一致**（非字符串比对）
     —— pubspec.yaml 每次会被 SnakeYAML 整体重写（丢注释、可能改引号/缩进/键序），
        字符串比对会误报；只有解析成对象后深比较才有意义。
        注意：mapping 的键序不敏感，sequence（如 assets 列表）的顺序敏感。

基线来源：基准工程的 git 指定修订（默认 HEAD）中 committed 的文件即黄金文件。

退出码：0 = 全部通过；1 = 存在不一致；2 = 运行错误。
"""

import argparse
import os
import subprocess
import sys
import tempfile

# 归一化用的 dart 可执行文件（用于抹平 formatter 版本差异）
DEFAULT_DART = "/Users/York/.flutter/flutter/bin/dart"
# 归一化时使用的行长，与 flr_config.dartfmt_line_length 保持一致；两侧一致即可
NORMALIZE_LINE_LENGTH = 100

try:
    import yaml
except ImportError:
    sys.stderr.write(
        "缺少 PyYAML。请用随附的 venv 运行，或先执行：\n"
        "  python3 -m venv scripts/.venv && scripts/.venv/bin/pip install pyyaml\n"
    )
    sys.exit(2)

DEFAULT_PROJECT = "/Users/York/dist_Insta360/AiSpace/flr-workspace/flutter_r_demo"

GREEN, RED, YELLOW, DIM, RESET = "\033[32m", "\033[31m", "\033[33m", "\033[2m", "\033[0m"


def git_show(project, rev, rel_path):
    """取基线修订中某文件的内容（bytes）。文件不存在时返回 None。"""
    r = subprocess.run(
        ["git", "-C", project, "show", f"{rev}:{rel_path}"],
        capture_output=True,
    )
    if r.returncode != 0:
        return None
    return r.stdout


def dart_normalize(dart, content_bytes):
    """用 dart format 归一化一段 Dart 源码，返回格式化后的 bytes。
    抹平 formatter 版本/风格差异，只保留 AST 层面的内容。失败抛异常。"""
    with tempfile.TemporaryDirectory() as td:
        fp = os.path.join(td, "r.g.dart")
        with open(fp, "wb") as f:
            f.write(content_bytes)
        r = subprocess.run(
            [dart, "format", "--line-length", str(NORMALIZE_LINE_LENGTH), fp],
            capture_output=True,
        )
        if r.returncode != 0:
            raise RuntimeError(
                f"dart format 失败（{dart}）：{r.stderr.decode('utf-8', 'replace')}"
            )
        with open(fp, "rb") as f:
            return f.read()


def check_rgdart(project, rev, dart):
    """标准 1：lib/r.g.dart 归一化比较（脱离 formatter 版本）。"""
    rel = "lib/r.g.dart"
    golden = git_show(project, rev, rel)
    if golden is None:
        print(f"{RED}[x] 基线中不存在 {rel}（rev={rev}）{RESET}")
        return False
    try:
        with open(f"{project}/{rel}", "rb") as f:
            actual = f.read()
    except FileNotFoundError:
        print(f"{RED}[x] 当前工作区不存在 {rel}——是否已执行 Generate？{RESET}")
        return False

    if not os.path.isfile(dart):
        print(f"{RED}[x] 找不到 dart 可执行文件：{dart}"
              f"（用 --dart 指定，或将 dart 加入 PATH）{RESET}")
        return False

    try:
        golden_n = dart_normalize(dart, golden)
        actual_n = dart_normalize(dart, actual)
    except RuntimeError as e:
        print(f"{RED}[x] {e}{RESET}")
        return False

    if actual_n == golden_n:
        print(f"{GREEN}[√] 标准1 通过：lib/r.g.dart 归一化后与基线一致"
              f"（归一化 {len(actual_n)} 字节；原始 当前{len(actual)}/基线{len(golden)}）{RESET}")
        return True

    print(f"{RED}[x] 标准1 失败：lib/r.g.dart 归一化后仍与基线不一致"
          f"（归一化 基线{len(golden_n)} / 当前{len(actual_n)} 字节）{RESET}")
    # 打印归一化后的首个差异行，便于定位真实代码差异
    g_lines = golden_n.decode("utf-8", "replace").splitlines()
    a_lines = actual_n.decode("utf-8", "replace").splitlines()
    for i in range(max(len(g_lines), len(a_lines))):
        g = g_lines[i] if i < len(g_lines) else "<无此行>"
        a = a_lines[i] if i < len(a_lines) else "<无此行>"
        if g != a:
            print(f"    {DIM}归一化后首个差异在第 {i + 1} 行：{RESET}")
            print(f"      {DIM}基线:{RESET} {g}")
            print(f"      {DIM}当前:{RESET} {a}")
            break
    return False


def _diff_list(name, golden, actual, indent="    "):
    """报告有序列表的差异（顺序敏感）。"""
    gset, aset = set(map(str, golden)), set(map(str, actual))
    only_g = [x for x in golden if str(x) not in aset]
    only_a = [x for x in actual if str(x) not in gset]
    if only_g:
        print(f"{indent}{YELLOW}基线有、当前缺（{len(only_g)}）:{RESET}")
        for x in only_g:
            print(f"{indent}  - {x}")
    if only_a:
        print(f"{indent}{YELLOW}当前多、基线无（{len(only_a)}）:{RESET}")
        for x in only_a:
            print(f"{indent}  + {x}")
    if not only_g and not only_a:
        # 元素集合相同但顺序不同
        print(f"{indent}{YELLOW}{name}：元素集合相同但**顺序**不同（顺序敏感，判为不一致）{RESET}")
        for i, (g, a) in enumerate(zip(golden, actual)):
            if g != a:
                print(f"{indent}  第 {i} 项：基线={g} / 当前={a}")


def check_pubspec_flutter(project, rev):
    """标准 2：pubspec.yaml 的 flutter 节点按 YAML 对象比对。"""
    rel = "pubspec.yaml"
    golden_bytes = git_show(project, rev, rel)
    if golden_bytes is None:
        print(f"{RED}[x] 基线中不存在 {rel}（rev={rev}）{RESET}")
        return False
    try:
        with open(f"{project}/{rel}", "rb") as f:
            actual_bytes = f.read()
    except FileNotFoundError:
        print(f"{RED}[x] 当前工作区不存在 {rel}{RESET}")
        return False

    golden = yaml.safe_load(golden_bytes) or {}
    actual = yaml.safe_load(actual_bytes) or {}
    g_flutter = golden.get("flutter")
    a_flutter = actual.get("flutter")

    if g_flutter == a_flutter:
        n_assets = len((a_flutter or {}).get("assets", []) or [])
        n_fonts = len((a_flutter or {}).get("fonts", []) or [])
        print(f"{GREEN}[√] 标准2 通过：pubspec.yaml 的 flutter 节点与基线一致"
              f"（assets {n_assets} 项 / fonts {n_fonts} 个 family）{RESET}")
        return True

    print(f"{RED}[x] 标准2 失败：pubspec.yaml 的 flutter 节点与基线不一致{RESET}")
    g_flutter = g_flutter or {}
    a_flutter = a_flutter or {}
    all_keys = list(dict.fromkeys(list(g_flutter.keys()) + list(a_flutter.keys())))
    for k in all_keys:
        gv, av = g_flutter.get(k), a_flutter.get(k)
        if gv == av:
            continue
        print(f"  {YELLOW}子键 '{k}' 不一致：{RESET}")
        if isinstance(gv, list) and isinstance(av, list):
            _diff_list(k, gv, av, indent="    ")
        else:
            print(f"    基线: {gv}")
            print(f"    当前: {av}")
    return False


def main():
    ap = argparse.ArgumentParser(description="flr-as-plugin 基准回归校验")
    ap.add_argument("--project", default=DEFAULT_PROJECT, help="基准工程根目录")
    ap.add_argument("--rev", default="HEAD", help="黄金基线的 git 修订（默认 HEAD）")
    ap.add_argument("--dart", default=DEFAULT_DART,
                    help="dart 可执行文件路径（标准1 归一化用）")
    args = ap.parse_args()

    print(f"基准工程: {args.project}")
    print(f"黄金基线: git rev = {args.rev}")
    print(f"归一化 dart: {args.dart}\n")

    ok1 = check_rgdart(args.project, args.rev, args.dart)
    print()
    ok2 = check_pubspec_flutter(args.project, args.rev)
    print()

    if ok1 and ok2:
        print(f"{GREEN}==> 全部通过：本次改动未改变生成结果。{RESET}")
        sys.exit(0)
    print(f"{RED}==> 存在不一致：请判断是无意破坏（改回）还是刻意变更（更新基线并说明）。{RESET}")
    sys.exit(1)


if __name__ == "__main__":
    main()
