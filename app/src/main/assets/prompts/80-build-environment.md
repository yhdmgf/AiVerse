<!-- 构建环境与注意事项：在 8GB 级容器内构建本项目，供所有 AI 知晓 -->
## 构建环境

- 本容器总内存约 7.6GB，空闲时可用也常仅约 3.5GB。
- 项目 `gradle.properties` 默认 `org.gradle.jvmargs=-Xmx7168m`（按 16GB 机器配置）。在此容器内**直接构建会因堆过大触发 OOM**：Kotlin 编译任务（`compile*Kotlin`）被 SIGKILL，表现为日志停在该任务、`ps`/`/proc` 无任何 java 进程残留、终端看似"假死"（实际是编译 JVM 被杀、外层 gradlew 在等它返回）。
- **构建请覆盖堆为 3GB**（命令行覆盖，不改项目文件）：
  `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ANDROID_HOME=/root/android/sdk sh gradlew :app:assembleUniversalDebug -Dorg.gradle.jvmargs="-Xmx3072m -XX:MaxMetaspaceSize=768m -XX:+UseParallelGC" --stacktrace`
- 冒烟构建用 `:app:assembleUniversalDebug`（勿用 `assembleDebug`/`build` 聚合任务，会跨 flavor 全跑）；推送前单测 `:app:testUniversalDebugUnitTest`；迁移对账 `python3 scripts/check_migrations.py`。
- `gradlew` 无执行位（文件系统限制），一律 `sh gradlew` 调用；`chmod +x` 无效。
- 依赖 Gradle wrapper 8.14、AGP 8.9.3、android-36 已就绪；`local.properties` 的 `sdk.dir=/root/android/sdk`。

## 构建耗时与增量

- 构建是增量的：没改动的任务会自动跳过，日志里 up-to-date 越多、跳过越多、越快。只改提示词这类非代码文件时，通常一两分钟就能出包。
- 只要不碰 Kotlin 代码和构建配置（build.gradle.kts、gradle.properties），改动越小越快；一改代码就得重新编译，十几分钟很正常。
- 如果清了缓存（clean、删 build 或 .gradle 目录）或者第一次构建，那就得全量重来，约二三十分钟，这时务必带上 3GB 堆参数，不然会被 OOM 杀掉。
- 构建跑到一半别中断，缓存不完整的话下次还得补跑。

## Flutter 构建

- 容器默认**没有** Flutter/Dart，这不是自带环境，**需要先安装**才能构建 Flutter 项目；装好 Flutter SDK（如 `/opt/flutter`）后即可 `flutter build apk`。
- 安装方式：Flutter 官方 Linux 发布只有 x64 包；ARM64 用 GitHub 源码包安装，且 SDK 必须做成合法 git 仓库（`bin/internal/engine.version` 需被 git 跟踪），否则 flutter 报 “not a clone”。首次运行会自动下载 ARM64 Dart SDK 与引擎（storage.googleapis.com）。
- 调用前 `export PATH=/opt/flutter/bin:$PATH`，直接 `flutter build apk`（不用 `sh gradlew`）。
- 项目名不能叫 `flutter_test`（与 Flutter 内置测试包冲突）。
- 首次构建会下载 pub 依赖与 Gradle 依赖，较慢；Flutter 项目的 Gradle 也吃内存，注意堆设置。
