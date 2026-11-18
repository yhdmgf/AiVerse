#!/usr/bin/env bash
# release 崩溃栈还原：把 R8 混淆后的 a/b/c 帧还原成真实类名 + 源文件行号。
#
# 用法：./scripts/retrace-release-crash.sh <mapping.txt> <crash.txt>
#   mapping.txt：CI 里 "上传 R8 mapping（retrace 用）" 产物，必须是**同一次构建**的
#                （日志里的 r8-map-id 不同 = 不同构建，不能混用）。
#   crash.txt  ：崩溃报告原文（从 logs 目录拷出来即可）。
set -euo pipefail

MAPPING="${1:?用法: retrace-release-crash.sh <mapping.txt> <crash.txt>}"
CRASH="${2:?用法: retrace-release-crash.sh <mapping.txt> <crash.txt>}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"

RETRACE_BIN="$(ls -1 "$SDK"/cmdline-tools/*/bin/retrace 2>/dev/null | head -n1 || true)"
if [ -n "${RETRACE_BIN:-}" ]; then
  exec "$RETRACE_BIN" --stacktrace "$CRASH" "$MAPPING"
fi

# 没有 cmdline-tools 时退回 build-tools 自带的 R8
D8_JAR="$(ls -1 "$SDK"/build-tools/*/lib/d8.jar 2>/dev/null | tail -n1 || true)"
if [ -n "${D8_JAR:-}" ]; then
  exec java -cp "$D8_JAR" com.android.tools.r8.retrace.Retrace --stacktrace "$CRASH" "$MAPPING"
fi

echo "未找到 retrace 或 d8.jar，请设置 ANDROID_HOME。" >&2
exit 1
