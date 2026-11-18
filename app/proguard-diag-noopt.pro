# ⚠️ 仅诊断构建使用（配合 -Pdiag=noopt）。
# 在「不混淆」的基础上再关掉代码优化，用于判定问题来自 optimize 而不是 shrink。
-dontoptimize
