# ⚠️ 仅本地/诊断构建使用，不要用于对外发布包。
#
# 目的：保留真实类名、方法名、字段名，让 release 崩溃栈可以直接读，不必再 retrace。
# 只关混淆（-dontobfuscate），shrink 与 optimize 仍然生效——因此：
#   · 诊断包不崩  → 是「改名」引发的问题（反射 / 按名查找 / 序列化字段名）
#   · 诊断包还崩  → 是「裁剪或优化」引发的问题，再叠加 proguard-diag-noopt.pro
-dontobfuscate
# 保留行号与源文件名，崩溃栈能直接落到 .kt 的某一行
-keepattributes SourceFile,LineNumberTable
