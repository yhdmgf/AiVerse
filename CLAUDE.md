# AiVerse AI 编写

> 用途：本文作为系统提示词 / 上下文投喂给 AI。AI 读完应理解 AiVerse 的架构逻辑、代码组织方式、硬规则与改动执行流程。  
> 优先级：本文内联了仓库 `CLAUDE.md` 的硬规则；若仓库 `CLAUDE.md` 后续更新，以仓库最新 `CLAUDE.md` 为准，并同步回本文。  
> 默认语言：**永远使用中文回复。**

---

## 一、项目是什么与平台约束

AiVerse 是一个 **Android 端 AI 编程助手（Agentic Coding Client）**：用户在手机上通过对话驱动 AI Agent，Agent 调用工具（读写文件、终端命令、Git、搜索等）在本地 PRoot 容器或远程 SSH 环境里完成编程任务。对标 Claude Code / RikkaHub 这类移动端 Agent 客户端。

- **技术栈**：Kotlin + Jetpack Compose + Hilt（DI）+ Coroutines/Flow + Room。
- **模块**：`:app`（主应用）、`:terminal-emulator` / `:terminal-view`（Termux 终端组件）。
- **平台硬约束**：`targetSdk` 锁定 28，`minSdk = 26`。PRoot 需在 App 可写目录执行二进制，Android 10+ 的 W^X/SELinux 禁止，**别升 targetSdk**。
- **入口**：`AIEditorApp.kt` 初始化 `FileLogger`、`TerminalKeepaliveService`、`McpManager`。

---

## 二、架构总纲：feature-based 分层 + DDD

代码按**业务功能（feature）垂直切分**，每个 feature 内部再按 **DDD 分层**（domain / data / presentation）横向组织。

```text
com/aiverse/
├── core/          跨 feature 的基础设施（不含业务逻辑）
│   ├── datastore/   DataStore 偏好
│   ├── db/          MigrationLoader（文件式 SQL 迁移）
│   ├── net/         网络基础
│   ├── security/    安全
│   ├── theme/       AIEditorTheme（Spacing/Radius/语义色）+ UIDensity
│   ├── tts/         语音朗读
│   ├── ui/          可复用 Compose 组件（AppTextField / AppSwitch / AdaptiveModalBottomSheet / SwipeToDeleteRow / ListSelection 等）
│   ├── util/        FileLogger 等工具
│   └── watch/       文件监听
├── di/            全局 Hilt 模块
└── feature/       业务功能（每个 feature 自带 domain/data/presentation + DI 模块）
    ├── agent/       ★ AI Agent 核心（最大最复杂）
    ├── terminal/    终端与会话（本地 Termux+PRoot / 远程 sshj）
    ├── workspace/   工作区文件与 DocumentsProvider
    ├── editor/      sora-editor 代码编辑器
    ├── git/         Git 操作
    ├── settings/    设置页（provider / 外观 / 工具授权 / MCP / Skill …）
    ├── backup/      备份恢复与加密
    ├── credentials/ 凭据管理与注入容器
    ├── browser/     内置浏览器
    ├── phonecontrol/ 手机控制（无障碍/Shizuku）
    ├── plugins/     脚本插件
    ├── search/      搜索
    ├── onboarding/  引导
    ├── agreement/   协议
    └── web/         WebView 相关
```

### 一个 feature 内部三层职责

以 `feature/agent` 为例：

| 层 | 目录 | 职责 | 依赖方向 |
|---|---|---|---|
| **domain** | `domain/` | 纯业务逻辑与模型：tool / mcp / prompt / provider / session / skill / subagent / memory / permission / workflow 等子域 | 不依赖 data / presentation |
| **data** | `data/` | 数据源与仓库：`local/`（Room + DAO）、`remote/`（provider 适配 anthropic/openai/gemini）、`repository/`（桥接 domain 与数据源） | 依赖 domain |
| **presentation** | `presentation/` | Compose UI：ViewModel + component/ 可复用 UI 片段 | 依赖 domain，经 ViewModel 拿 data |

**依赖铁律**：`presentation → domain ← data`，domain 是核心、不反向依赖外层。跨 feature 只能依赖 `core/`，不能直接 import 别的 feature 内部；需要共享就提到 `core/`。

---

## 三、核心子系统逻辑

### 3.1 Agent 链路

1. **提示词注入**：`app/src/main/assets/prompts/`（`00-identity` 身份、`60-tools-and-paths` 工具与路径、`15-project-rules` 项目规则等）在会话开始时组装进系统提示词。**改 Agent 行为/工具说明 → 同步改 `prompts/`**（硬规则）。
2. **工具系统**：`feature/agent/domain/tool/` 下每个工具实现 `AgentTool` 接口，经 `ToolRegistry` 统一注册；执行权限由 `ToolPermissionManager` + `ToolPermissionPolicyEngine` 管控（哪些工具要用户确认、哪些可“始终允许”、AUTO 模式 vs 手动确认）。
3. **MCP**：`domain/mcp/`，`McpManager` 连接远端 MCP server 并把工具动态注册进 `ToolRegistry`，与内置工具同台。
4. **Provider 适配**：`data/remote/` 下 `anthropic` / `openai` / `gemini` 适配器，把统一的 Agent 请求转成各家 API。
5. **子代理 / 技能 / 工作流**：`domain/subagent`（派子代理并行干活）、`domain/skill`（可复用技能 = SKILL.md）、`domain/workflow`。
6. **会话持久化**：`data/local/database/AgentDatabase.kt`（Room，`SCHEMA_VERSION` 当前 63）+ 各 DAO；会话、消息、工具调用记录入库。

**接 Agent 行为/工具需求的思考路径**：  
工具增删改 → `domain/tool/` 写工具类 + `ToolRegistry` 注册 + 权限策略 → **同步 `assets/prompts/60-tools-and-paths.md`**（让 Agent 知道工具存在、参数签名）→ 需要持久化则动 Room（走迁移）→ UI 展示在 `presentation/component/`。

### 3.2 终端与远程链路

- **本地模式**：Termux 组件（`:terminal-emulator` / `:terminal-view`）+ PRoot（`LinuxContainerEngine`），在 App 可写目录跑 Linux 二进制。
- **远程模式**：`RemoteSshConnection`（共享 sshj client）+ `RemoteSshEngine`（执行命令）+ `RemoteSftpFileAccess`（文件）+ `RemoteTerminalSessionManager`（终端），四个角色协作。
- **工作区文件**：`feature/workspace` 的 DocumentsProvider 统一本地/远程文件访问，远程走 `RemoteSftpFileAccess`。

### 3.3 UI 编写逻辑（Compose 约定）

- **先复用 `core/ui/` 自定义组件，禁止裸写原生控件**（硬规则）：
  - 输入框：`core/ui/AppTextField.kt`（配套 `appTextFieldColors()`、弹窗专用 `dialogTextFieldColors()`）；禁止业务页无故裸写带长下划线的原生 `TextField`，禁止各模块私自重复包装 OutlinedTextField。
  - 开关：一律 `core/ui/AppSwitch.kt`，禁止原生 M3 `Switch`。
  - 模态底栏：一律 `core/ui/AdaptiveModalBottomSheet.kt`（已集成平板自适应与 fling fix），禁止直接调用原生 `ModalBottomSheet`；长表单容器外层注意 `.imePadding()` 避让软键盘。
  - 左滑删除：一律 `core/ui/SwipeToDeleteRow.kt`。
  - 多选：`ListSelection`；`SelectionActionBar` 被 6 个设置列表页共用。
  - 设置/表单页：复用 `feature/settings/presentation/component/SettingsGroupComponents.kt` 的 `SettingsGroup` / `SettingsRow` / `SettingsDivider` / `SettingsGroupHeader`，保持统一卡片层级。
- **文案双语**：用户可见中文文案必须进 `values/strings.xml`（中文）+ `values-en/strings.xml`（英文），代码用 `stringResource(R.string.xxx)`，**禁止在 `.kt` 硬编码中文 UI 文案**。命名用语义化英文小写下划线，跨页面复用加 `common_` 前缀。
- **危险操作**（批量删除等）一律走确认弹窗（如 `BatchDeleteConfirmDialog`）；溢出收纳一律 ⋯ 菜单 + 菜单项图标带文字。

### 3.4 设计尺寸统一规则（每个 dp 统一，硬规则，重点强调）

> **每个 dp 必须统一来源、统一 token、统一密度缩放；不是“所有数值一样”，而是“所有 dp 都走同一套规则”。**

- **禁止硬编码魔数 dp**：业务 UI 中不得出现随意写死的 `8.dp` / `12.dp` / `16.dp` / `24.dp` 等；所有间距、圆角、尺寸、线宽、图标尺寸、列表行高、卡片内边距等，**统一使用** `core/theme/AIEditorTheme.kt` 的 `Spacing` / `Radius` / `MaterialTheme.semanticColors`，以及 `core/theme/UIDensity.kt` 的 `LocalUIDensityTokens`。
- **每个 dp 统一走密度缩放**：界面密度相关尺寸一律通过 `LocalUIDensityTokens.current` 的 `scale()` 计算，随 xs~xxl 密度统一缩放；同一页面/同一组件内所有间距与圆角必须使用同一套 token 与同一密度来源，不允许一部分用 token、一部分写死 dp。
- **不新增页面级私有 dp 常量**：确需新增尺寸先补到 theme / UIDensity token 中，再全局复用；禁止在页面里就地造 `private val XxxPadding = 13.dp` 这类局部魔数。
- **`LocalUIDensityTokens.current` 只能在 `@Composable` 函数体内读取**，不能在函数签名默认参数里读。
- **主题 token 优先级**：`Spacing` > `Radius`（含 `Radius.mdLarge = 12.dp`）> `semanticColors`；禁止随意硬编码非标魔数与色彩。
- **审查口径**：代码审核时必须检查“每个 dp 是否统一走 token / scale”，发现硬编码 dp、局部包装、重复造组件，按不通过处理。

---

## 四、接到需求时的通用落位方法

1. **定位归属**：改动属于哪个 feature？是跨 feature 基础设施吗（→ `core/`）？
2. **顺分层走**：
   - 纯业务规则 → `domain/`
   - 数据读写 / API / DB → `data/`（repository 桥接）
   - 界面 / 交互 → `presentation/`（ViewModel + Compose component）
3. **先找已有轮子**：用 `rg` 搜同类实现（同款组件、同款工具、同款 repository 模式），能复用就复用，不重复造。
4. **检查硬约束**：
   - 动了 Room schema → 递增 `SCHEMA_VERSION` + 新增迁移文件（编号连续）+ 跑 `check_migrations.py`
   - 动了 Agent 工具/行为 → 同步 `assets/prompts/`
   - 动了 UI/交互/文案 → 同步 `docs-site/docs/` + 双语 `strings.xml`
   - 动了内置供应商 → 只改 `data/providers.json`（不碰 assets 快照）
   - 动了容器镜像 → 不手改生成结果，改脚本配置或 `sources` 表
   - 动了 UI 尺寸 → 检查每个 dp 是否统一走 token / UIDensity scale
5. **验证**：编译型代码改完跑 `:app:assembleUniversalDebug` 冒烟；推前跑单测 + 迁移对账。
6. **提交**：Conventional Commits（`fix(settings): …`），中文描述；修 issue 末尾每个 issue 单独一行 `Closes #N`。

---

## 五、改动执行流程（工作方式硬规则）

### 5.1 前置自检：默认专家模式与检测方式

- **默认属于专家模式**，但动手前必须先检测当前状态。
- **检测方式**：
  1. 查看当前会话/系统提供的模式标识与能力状态；
  2. 若无法明确确认处于专家模式，**一律视为非专家模式**；
  3. 非专家模式下**只做只读探索、方案设计、影响面分析**，**不写文件、不提交、不构建、不改代码**；
  4. 等用户切换到专家模式后再执行编写与构建。
- 禁止在非专家模式下“先改一点试试”。

### 5.2 异步任务与子代理状态：事件驱动，禁止轮询（硬规则，重点）

> 子代理完成/失败、编译构建、安装依赖、迁移、terminal 任务等异步任务，**完成后系统会自动通知主会话**，不需要手动查询。不要用指令反复查询子代理状态，不要反复拉取“变异状态”。

**禁止动作清单**：

- 禁止用 `task read` 反复轮询子代理状态；
- 禁止向子代理发“完成了吗 / 进度如何 / 好了吗”之类的轮询指令；
- 禁止 `sleep` 后反复读 terminal 输出；
- 禁止用 `Bash` 循环查进程、循环读日志；
- 禁止反复调用 `task list` 刷状态；
- 禁止因为“想确认”而重复拉取同一结果。

**允许动作清单**：

- `task list` 仅**只读列出一次**，用于判断名额与任务归属，不得循环调用；
- 耗时任务用 `terminal` 工具启动：`start` + `notify=true`，结束后自动通知；
- 收到完成通知后，**读取一次**输出/退出码/结果摘要即可；
- 只有“超过预期时间约 2 倍且无任何通知”时，做**一次**进程/日志诊断；仍无结果则向用户说明，不得进入轮询循环；
- 主会话在等待期间应做汇总准备、读代码、整理冲突清单，而不是查询状态。

**变异状态同理**：编译、构建、安装、迁移等状态变化，以通知为准；不要反复读日志、不要循环查进程。`Bash` 只用于短时、一次性查询/检查；常驻服务或交互式任务用 `terminal` 的 `read`/`send` 查看。

### 5.3 子代理调度与 prompt 模板

- 子代理拥有独立上下文，看不到主会话上下文；给它们的 prompt 必须写明目标、已知信息、约束、预期产出与冲突边界。
- 子代理全局**最多同时 5 个**（所有会话共享名额）。
- 四阶段按问题数量派发时，若问题数 > 5，必须**分批并发**：阶段内并发不超过 5，阶段间原则上串行（探索完成再编写）。
- 子代理完成后系统自动通知主会话；**不要轮询**。

**子代理 prompt 固定模板**：

```text
目标：
已知信息：
涉及文件/目录：
必须遵守的硬规则：
冲突边界（不要动哪些文件/编号）：
预期产出：
  - 改动文件列表
  - 关键 diff 摘要
  - 验证命令与结果
  - 遗留风险
禁止事项：
  - 不要轮询主会话
  - 不要改范围外文件
  - 不要手改生成文件
  - 不要硬编码 dp / 中文 UI 文案
```

### 5.4 四阶段干活法

1. **阶段一 · 探索**：先梳理本次任务有几个问题（改动点 / issue 数）。按问题数量派发对应数量子代理并行探索（不超过 5，超出分批）；同时识别可能冲突点（文件/行重叠、Room 迁移编号冲突、prompts 资产同步冲突、并发改同一文件等）。
2. **阶段二 · 编写**：按问题数量派发对应数量子代理并行编写（不超过 5，超出分批）。**把阶段一识别出的冲突点明确告知各子代理**，约定边界，避免各自为政。
3. **阶段三 · 解冲突**：派对应数量子代理解决冲突点（合并、协调、消除重复）。
4. **阶段四 · 审核 + 编译**：派对应数量子代理审核代码；审核发现问题就改，改完再审核，直到通过；最后编译验证。审核范围必须包含：编译、单测、迁移对账、`prompts/` 同步、`docs-site/docs/` 同步、双语 `strings.xml`、`providers.json` / `container-images.json` 是否误改、每个 dp 是否统一走 token / UIDensity scale。

### 5.5 完成定义 DoD

阶段四审核通过必须同时满足：

- 对应 flavor 冒烟编译通过；
- 推送前单测通过；
- 迁移对账通过（如动 DB）；
- `prompts/` 已同步（如动 Agent 工具/行为）；
- `docs-site/docs/` 已同步（如动 UI/功能）；
- 双语 `strings.xml` 已同步（如动用户可见文案）；
- `data/providers.json` / `container-images.json` 未误改；
- 每个 dp 统一走 theme token 与 UIDensity scale，无硬编码魔数；
- 提交格式与 `Closes #N` 正确；
- UI 改动已真机验证（如需要）。

### 5.6 构建约束与失败处理

- **执行方式**：耗时任务（构建、安装依赖、长编译等）用 `terminal` 工具启动（`start` + `notify=true`，结束后自动通知，无需 sleep/轮询），**不要用 `Bash` 一直读输出**。
- 本容器**总内存约 7.6GB，空闲时可用常仅约 3.5GB**。
- 项目 `gradle.properties` 默认 `org.gradle.jvmargs=-Xmx7168m`（按 16GB 机器配置）。在此容器内直接构建会因堆过大触发 OOM：Kotlin 编译任务被 SIGKILL，表现为日志停在编译任务、无 java 进程残留、终端看似“假死”。
- **构建请覆盖堆为 3GB**（命令行覆盖，**不改项目文件**）：

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 \
ANDROID_HOME=/root/android/sdk \
sh gradlew :app:assembleUniversalDebug \
-Dorg.gradle.jvmargs="-Xmx3072m -XX:MaxMetaspaceSize=768m -XX:+UseParallelGC" \
--stacktrace
```

- **冒烟构建**用 `:app:assembleUniversalDebug`（勿用 `assembleDebug` / `build` 聚合任务，会跨 flavor 全跑）；推送前单测 `:app:testUniversalDebugUnitTest`；迁移对账 `python3 scripts/check_migrations.py`（或 `./gradlew checkMigrations`）。
- `gradlew` 无执行位（文件系统限制），容器内一律 `sh gradlew` 调用；`chmod +x` 无效。
- 依赖 Gradle wrapper 8.14、AGP 8.9.3、android-36 已就绪；`local.properties` 的 `sdk.dir=/root/android/sdk`。

**失败处理**：

- **编译 OOM**：按 3GB 堆命令重跑；仍失败则检查是否误用聚合任务（`assembleDebug` / `build` / `test` 等跨 flavor 全跑任务）。
- **迁移失败**：不得手改已发布迁移；新增修正迁移。
- **冲突无法解决**：停下来问用户，不强行合并。
- **审核不通过**：改完再审核，直到通过；不跳过。
- **子代理失败**：主会话读一次失败摘要，决定重派/缩小范围/自己做，不轮询。
- **超时无通知**：只做一次诊断；仍无结果向用户说明，不进入轮询。

### 5.7 提交格式（关 issue）

- Conventional Commits（`fix(settings): …`），中文描述，格式 `<type>(<scope>): <subject>`，正文空行隔开。
- `type` ∈ `feat | fix | refactor | docs | style | chore | ci | build | perf | test`。
- `scope` 可选：`agent | settings | terminal | workspace | git | ui | mcp | db | core | docs | build | deps`。
- `subject` 一行简述，中英文均可，句末不加句号。
- 修多个 issue 时，**每个 issue 单独一行** `Closes #N`，**禁止写成一行**：

```text
Closes #72
Closes #73
```

- 仅紧急情况可用 `git commit --no-verify` 跳过校验。

### 5.8 代码质量准则

- **参考其他代码的样式**：编写前先读同目录 / 同 feature / 同 pattern 的已有代码，沿用其命名、格式、组织方式，保持全局一致，不凭空发明新写法。
- 遵循**“不要最简单，要最优解”**，保持**大局观**。
- 当前接到的只是**其中一个问题，并不是所有用户的问题**：通盘考虑整体架构与复用，不为一处改动引入局部最优而破坏整体；同时避免过度泛化，只解决本次问题范围。
- 文件读写用已有的文件工具，不用 `cat` / `sed` / `echo >` 代替。
- UI 尺寸必须“每个 dp 统一”：统一 token、统一密度缩放、禁止硬编码魔数。

---

## 六、数据库与迁移

Room：`feature/agent/data/local/database/AgentDatabase.kt` + 各 DAO。迁移分两套，**一个版本只能二选一**：

- **文件式（默认）**：`core/db/MigrationLoader.kt` 读 `app/src/main/assets/migrations/{VERSION}_description.sql`，由 `SqlScriptSplitter` 按语句切分（识别注释与字符串字面量，字符串里可放心写 `;`）；整段迁移包事务、任一条失败整体回滚，成功记入 `migration_history` 表。
- **AutoMigration**：纯 schema 变更（加列/建表/索引）可用 `@AutoMigration(from = N-1, to = N)` 编译期自动生成；改 entity 忘写迁移会直接编译失败；含数据清理/重命名/改约束的版本必须走文件式。

改 schema 三步：

1. 递增 `AgentDatabase.kt` 的 `SCHEMA_VERSION`（当前 63）。
2. 文件式：在 `app/src/main/assets/migrations/` 新建 `{VERSION}_description.sql`（如 `46_add_provider_multi_key.sql`），**编号必须连续**；AutoMigration：加注解，保证 `to == SCHEMA_VERSION` 且 `from` 衔接文件式最大版本。
3. 写入 DDL/SQL，启动时自动执行并记入 `migration_history` 表。

**跨分支冲突硬规则**：

- **发布即冻结**：已打 `v*` tag 的迁移文件内容与编号不可再改，补丁只能靠新增迁移修正。
- **合流后移**：RC/hotfix 若带数据库迁移，其迁移号一旦随 tag 发布即占用；合回 `main` 时，`main` 上所有编号 ≤ 已发布最大版本号的未发布迁移必须整体重编号到该上限之后（内容一字不改、编号连续），并同步递增 `SCHEMA_VERSION`。已发布号一律不可复用。
- **对账脚本**：`python3 scripts/check_migrations.py`（或 `./gradlew checkMigrations`）校验编号连续、`SCHEMA_VERSION` 一致、已发布迁移未被篡改/复用，**推送前必跑**；CI 已挂载，合流后未后移会被拦下。

**迁移失败处理**：不得手改已发布迁移；新增修正迁移。

---

## 七、资产同步（硬规则）与变更影响矩阵

`app/src/main/assets/prompts/`（AI 提示词）与 `docs-site/docs/`（用户文档）是 AI Agent 的知识来源，必须与代码同步：

- **AI 工作流改动 → 同步 `prompts/`**：工具增删改名、参数签名变化、agent 行为或提示词逻辑调整，都要更新 `prompts/` 下对应文件（自行查找，不存在则新建）。
- **功能或工具变化 → 检查 `docs-site/docs/`** 是否有使用文档要更新。
- **UI 变化（新增页面、改交互、调布局、改文案）→ 必须更新 `docs-site/docs/`**；新增文档页同步加进 `docs-site/.vitepress/config.ts` 侧栏与 `docs-site/docs/guide/overview.md` 索引。
- **用户可见中文文案 → 必须进双语 `strings.xml`**：写入 `values/strings.xml`（中文）与 `values-en/strings.xml`（英文），代码用 `stringResource(R.string.xxx)` 引用。**禁止在 `.kt` 中硬编码中文 UI 文案。**
- **提供商预设只在仓库数据文件维护**：新增或调整内置 AI 供应商，**只改 `data/providers.json`**；**不要**同步改 `app/src/main/assets/providers.json`（内置兜底快照，随发版固化）与 `docs-site/` 文档。
- **容器镜像目录由脚本生成，禁止手改**：`data/container-images.json` 由 CI 跑 `scripts/sync-container-images.py` 生成；`app/src/main/assets/container-images.json` 是内置兜底快照，发版前用同一脚本刷新。增删发行版或改镜像源，改脚本配置或 `sources` 表，不要改生成结果。
- **文档目录约定**：`docs-site/docs/` 是文档唯一事实源，`guide/` 放功能说明、`advanced/` 放环境搭建与进阶教程。构建时由 `syncAiDocs` task 复制到 `assets/docs/`，AI 在容器内看到的是 `~/.aiverse/docs/{guide,advanced}/*.md`。**面向用户书写**：讲清怎么做、会看到什么、出错怎么办；变量名、错误码、内部实现路径属于 `prompts/`，别写进用户文档。

**变更影响矩阵**：

| 改动类型 | 必须检查 |
|---|---|
| Agent 工具增删改 | `ToolRegistry`、权限策略、`assets/prompts/60-tools-and-paths.md`、docs |
| Room schema | `SCHEMA_VERSION`、迁移文件编号连续、`check_migrations.py` |
| UI/交互/文案 | `core/ui` 组件、theme token、双语 strings、`docs-site/docs/` |
| UI 尺寸/间距/圆角 | 每个 dp 统一走 `Spacing` / `Radius` / `UIDensityTokens.scale()`，禁止硬编码 |
| 内置供应商 | 只改 `data/providers.json` |
| 容器镜像 | 改脚本配置，不手改生成 JSON |
| 新依赖 | `app/build.gradle.kts`、许可证/体积/安全评估 |
| 签名/CI/flavor | 先发 RC，PR 流程中必须问用户 |

---

## 八、构建验证、分支、PR、Issue、发版（摘要）

### 8.1 构建与验证

**改完编译型代码（`.kt` / `.gradle.kts` / `AndroidManifest.xml`）→ 提交前必跑冒烟编译；`git push` 前必跑单元测试，并跑 `check_migrations.py` 迁移对账。** 只改文档 / 资源文案 / 纯 `.md` 时这些跳过。

| 用途 | 命令 |
| --- | --- |
| 冒烟编译（日常默认） | `./gradlew :app:assembleUniversalDebug`（容器内用 3GB 堆 + `sh gradlew`） |
| 推送前单测 | `./gradlew :app:testUniversalDebugUnitTest` |
| 推送前迁移对账 | `python3 scripts/check_migrations.py`（或 `./gradlew checkMigrations`） |
| 发版构建 APK / AAB | `./gradlew assembleRelease` / `./gradlew bundleRelease` |

- **别用聚合任务做日常验证**：`assembleDebug` / `assembleRelease` / `test` / `build` 都会跨三个 flavor 全跑，耗时极长。
- 容器内耗时构建必须用 `terminal` + `notify=true`，不要反复读输出、不要轮询。

### 8.2 分支工作流

- **新功能 / 复杂多文件改动 / 架构重构** → 开 `feat/xxx` 或 `refactor/xxx`，动手前先定好分支名，验证通过后合回 `main`。
- **日常 bug 修复 / 补单测 / CI 与构建配置 / 纯文档 / 资源文案** → 直接提交 `main`。
- **预览版热修复** → 从该 RC Tag 拉 `hotfix/xxx`。
- **合并后清理分支**：`git branch --merged main` 确认后 `git branch -d <branch>`；推送过的同步 `git push origin --delete <branch>`。

### 8.3 PR 合并流程

**在本地验证完再推送，不用 `gh pr merge` 在远端直接合。**

1. **摸状态**：`git fetch origin` → `gh pr view <N> --json state,mergeable,mergeStateStatus,headRefName,changedFiles` + `gh pr checks <N>` → `git rev-list --left-right --count origin/main...main`。fork PR 的 Vercel 必报 `Authorization required to deploy` 属正常，只看 `Build & Test` 是否 pass。
2. **拉到本地**：`git fetch origin pull/<N>/head:pr-<N>`。勿用 `gh pr checkout`。
3. **审查**：`gh pr diff <N> > /tmp/pr<N>.diff` 通读。PR 描述与 CodeRabbit 结论都不算证据，逐项落地核实：新依赖、`R.string` 双语存在、组件签名与调用点、是否复用既有实现、安全面、资产同步、迁移对账、每个 dp 是否统一走 token、超范围改动。
4. **预演冲突**：`git merge-tree --write-tree --name-only main pr-<N>`。
5. **临时分支合并**：`git switch -c merge/pr-<N> main` → `git merge --no-ff pr-<N> -m "Merge pull request #<N> from <owner>/<head-branch>" -m "<PR 标题>"`。
6. **冒烟 + 真机验证**：动了 UI 或交互的 PR 必须发 APK 给用户真机验证，通过才继续。
7. **单测 + 推送**：`git switch main && git merge --ff-only merge/pr-<N> && git push origin main`。
8. **清理**：`git branch -d merge/pr-<N> pr-<N>`。不动 stash 与未跟踪文件。

**必须停下来问用户**：本地 `main` 有未推提交／预演有冲突／PR 含数据库迁移或构建、CI、签名改动／审查发现超范围改动或质量问题／编译或单测失败。

### 8.4 Issue 审查

1. 通读 issue，提炼诉求、验收标准、模块、优先级；issue 提案只是建议，不得直接采纳。
2. 阅读相关真实代码，确认描述是否属实、影响面多大；事实以核实过的代码为准。拿不准用 `websearch` / `webfetch` 查官方文档与社区实践。
3. 评估是否合适：是否符合分层、是否重复造轮子、是否触碰硬约束、风险面。
4. 给出至少一个平替方案并对比，推荐结论；需要用户取舍时用选择题征询。
5. 输出报告：结论、理由、代码位置、方案对比、下一步；不落盘除非用户要求。

### 8.5 发版（摘要）

- 版本号唯一事实源是 Git Tag / Commit，无需手写。
- 含新功能/行为变化、构建链路/签名/flavor/CI、容器镜像/PRoot/ABI 改动 → **必须先发 RC**；纯文档/typo/资源文案可直接正式；纯 bug 修复小改可直接正式，触碰启动或容器仍先 RC。
- 打 Tag 前按需执行 `python3 scripts/update-models-dev-assets.py`；失败非零退出则跳过，不重试不手改。
- **严禁在 `feat/*` / `refactor/*` 上打 Tag 发版**，必须先合入 `main`。
- hotfix 从 RC Tag 拉，勿从最新 `main` 拉；hotfix 带迁移时发布即冻结，合回 `main` 先跑 `check_migrations.py`。

---

## 九、安全规则（显性化）

- 禁止外发凭据 / token / 私钥 / 签名文件内容。
- 命令拼接必须转义，禁止把用户输入直接拼进 shell。
- 可疑网络请求、上传、遥测、第三方 SDK 必须核实来源与用途。
- 备份、凭据、注入容器、签名、CI 相关改动需额外审查，并在 PR 中标注。
- PR 审查必须检查安全面；发现凭据外发、命令注入、可疑请求，直接不通过并交用户定夺。

---

## 十、工具边界

- 文件读写用已有文件工具，不用 `cat` / `sed` / `echo >` 代替。
- 短时、一次性查询/检查用 `Bash`；长任务、常驻服务、交互式任务用 `terminal`（`start` + `notify=true`；查看用 `read` / `send`）。
- 搜索优先 `rg`。
- 不反复读同一日志；不循环查进程；不轮询子代理状态。
- 子代理完成后自动通知，主会话收到通知后读一次结果即可。

---

## 十一、优先补强与一句话心智模型

**优先补强三条**：

1. **状态查询禁令 + 允许/禁止动作清单 + 超时只诊断一次**；
2. **子代理 prompt 模板 + 预期产出契约 + 最多 5 个分批调度**；
3. **完成定义 DoD + 变更影响矩阵 + 每个 dp 统一规则**。

**一句话心智模型（合并修订）**：

> **垂直按 feature 切、水平按 DDD 分层；domain 是核心不依赖外层；跨 feature 共享下沉到 core；Agent = prompts 注入 + ToolRegistry 注册工具 + 权限引擎管控 + provider 适配 + Room 持久化；UI 一律复用 core/ui 组件与 theme token，每个 dp 统一走 Spacing / Radius / UIDensityTokens.scale()、禁止硬编码魔数；改动先自检专家模式，无法确认即按非专家只读；再按「探索→编写→解冲突→审核+编译」四阶段派子代理并行推进，子代理全局最多 5 个、超出分批；子代理与编译构建等异步任务完成后会自动通知，禁止用指令轮询 task read / 反复读 terminal 输出，超时只诊断一次；构建覆盖 3GB 堆、OOM 重跑仍失败查聚合任务；迁移失败不手改已发布迁移、新增修正迁移；冲突无法解决停下问用户；审核不通过改完再审直到通过；提交每 issue 一行 Closes；代码参考已有样式求最优解保大局观。任何改动先问「这动了哪个 feature 的哪一层，触发哪些资产同步硬规则」。**