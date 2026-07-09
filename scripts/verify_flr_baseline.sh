#!/usr/bin/env bash
# flr-as-plugin 基准回归校验入口。
# 自动准备隔离 venv（含 pyyaml），再运行比对脚本。
#
# 用法：
#   scripts/verify_flr_baseline.sh                 # 用默认基准工程与 HEAD 基线
#   scripts/verify_flr_baseline.sh --project /path/to/flutter_r_demo
#   scripts/verify_flr_baseline.sh --rev <commit>  # 指定黄金基线修订
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV="$SCRIPT_DIR/.venv"

if [ ! -x "$VENV/bin/python" ]; then
  echo "首次运行：创建隔离 venv 并安装 pyyaml ..."
  python3 -m venv "$VENV"
  "$VENV/bin/pip" install --quiet --upgrade pip
  "$VENV/bin/pip" install --quiet pyyaml
fi

exec "$VENV/bin/python" "$SCRIPT_DIR/verify_flr_baseline.py" "$@"
