# AiVerse 官网 · 提示词库 · 插件商店审核后台

本仓库为 **AiVerse（安卓端 AI 编程工具）** 的官网与配套服务部署目录，包含三部分：

1. **官网与文档站**（VitePress 打包成品）
2. **提示词库**（Prompt 库，20 篇）
3. **插件商店审核后台**（前端 + 后端 API）

线上站点：**https://www.aivapp.cn**

---

## 一、目录结构

```text
/var/www/html/aiverse/
├── index.html              # 官网首页
├── guide/                  # 快速上手文档
├── advanced/               # 进阶文档（含插件开发 plugin/ 15 篇）
├── en/                     # 英文版文档
├── md/                     # 提示词库（20 篇 + index.html 浏览页）
├── assets/                 # VitePress 打包的 JS/CSS
├── custom.css              # 全站样式增强（卡片动效、渐变标题等）
├── site-enhance.js         # 全站脚本增强
├── search.html             # 搜索页
├── logo.png / logo.webp    # 品牌 Logo
├── favicon.ico / .svg      # 站点图标
├── store/                  # 插件商店审核后台【前端】
│   ├── index.html          # 审核页结构（登录 + 流程说明 + 审核列表）
│   ├── style.css           # 样式（琥珀橙品牌色）
│   ├── review.js           # 审核交互逻辑
│   └── logo.png            # 后台品牌 Logo
└── api/                    # 插件商店【后端 API】（见下方说明）
```

---

## 二、官网与文档站

| 路径 | 作用 |
|------|------|
| `/` | 官网首页：产品介绍、下载入口、特性展示 |
| `/guide/` | 快速上手：安装、首次配置、基础功能 |
| `/advanced/` | 进阶指南：高级功能、插件开发 |
| `/advanced/plugin/` | 插件开发文档（15 篇：快速入门、项目结构、权限、上架等） |
| `/en/` | 英文版文档（8 页核心内容） |
| `/logo.png` | 品牌 Logo（原子轨道图案） |

官网由 **VitePress** 构建为纯静态文件，由 **Nginx** 直接提供服务，无需后端进程。

---

## 三、提示词库（Prompt 库）

路径：`/md/`

- 共 **20 篇**高质量提示词，覆盖：角色设定、编程开发、写作创作、学习效率、商业职场等。
- 每篇为一个独立 HTML 文件（如 `deepseek.html`、`role.html`、`coding.html`）。
- `md/index.html` 提供统一浏览页：分类、搜索、一键复制、移动端适配。

特点：
- 纯静态，零依赖；
- 深色/浅色自动适配；
- 移动端友好；
- 支持中英双语（部分）。

---

## 四、插件商店审核后台

插件商店审核后台用于**人工审核第三方上传的插件包**，通过后自动发布到 GitHub 仓库供用户安装。

### 4.1 架构

| 组件 | 地址 | 技术 | 说明 |
|------|------|------|------|
| 前端 | https://www.aivapp.cn/store/ | 静态 HTML/CSS/JS | Nginx 直接服务 `/var/www/html/aiverse/store/` |
| 后端 API | https://www.aivapp.cn/api/* | Go | Nginx 反代 `/api/*` → `127.0.0.1:8088` |

### 4.2 前端（`/store/`）

- 登录页：用户名 + 密码 + **6 位 TOTP 动态码** + **算术人机验证**（四重要素，表单提交，Session Cookie 鉴权）；
- 流程说明：**上传插件 → 人工审核 → 发布上架**；
- 审核列表：待审核 / 已处理两个 Tab，展示插件名、版本、作者、大小、时间；
- 操作：查看清单、下载包、通过、拒绝（需填原因）；
- 品牌色：聊蓝 `#5468ff`；
- 错误提示：醒目红框。

### 4.3 后端 API（`/api/*`）

| 接口 | 方法 | 说明 | 鉴权 |
|------|------|------|------|
| `/api/upload` | POST | 上传插件包（multipart，字段 `file` + `uploader`，上限 10MB） | IP 限流 + 所有者校验 |
| `/api/owner` | GET | 查询插件所有者（`?id=<pluginId>`） | 无（公开） |
| `/api/owner/change` | POST | 修改插件所有者（body：`{id, new_owner}`） | 需要（管理员） |
| `/api/captcha/challenge` | GET | 下发算术题挑战 `{id,question,totp_required}` | 无 |
| `/api/captcha/verify` | POST | 校验算术题答案，签发一次性 token | 无 |
| `/api/login` | POST | 管理员登录（需 `captcha_token` + `totp`），返回 Session Cookie | 算术验证 + TOTP + IP/账号双维度限流 |
| `/api/queue` | GET | 审核队列（待审核 + 已处理） | 需要 |
| `/api/approve` | POST | 通过并发布到 GitHub 仓库 | 需要 |
| `/api/reject` | POST | 拒绝（需填原因） | 需要 |
| `/api/logout` | POST | 退出登录 | 需要 |

后端为 **Go 单二进制**（前端通过 `//go:embed` 内嵌，亦可独立部署）：
- 二进制路径：`/opt/aiverse-store/store-backend`
- Systemd 服务：`store-backend.service`（监听 `127.0.0.1:8088`）
- 数据目录：`/opt/aiverse-store/data/`（上传包暂存 `inbox/`，队列元数据 `queue/*.json`，会话 `sessions/`，IP 锁 `ipban.json`，账号锁 `accountban.json`，上传限流 `upload_ratelimit.json`）
- 凭据：`/etc/nginx/secrets/store_backend.env`（600 权限，含管理员用户名/密码哈希、GitHub Token、TOTP 密钥 `STORE_ADMIN_TOTP_SECRET`、`STORE_TRUST_PROXY`）

### 4.4 插件上架流程

```text
开发者上传 plugin.zip + 填写作者名（uploader）  →  POST /api/upload
        ↓
后端校验：uploader 必填 + 所有者匹配（非管理员）
        ↓
暂存 inbox/（不公开）
        ↓
管理员在 /store/ 审核（查看清单 / 下载）
        ↓
通过 → POST /api/approve → 自动发布到 GitHub 仓库 aiverseRules（index.json + plugin.txt）
        ↓
用户在 AiVerse APP 插件商店看到并安装
```

> **所有者校验规则**：首次上传时记录 uploader 为插件所有者，后续更新必须匹配（防冒名）；管理员不受限制。可在后台「ID 管理」功能里查询/修改所有者。

### 4.5 登录安全（三层防护）

审核后台登录在三重验证之上，全部本地实现、不依赖第三方/Cloudflare：

1. **算术人机验证**：登录前先完成算术题（`/api/captcha/challenge` → `/api/captcha/verify`），通过后获得 60s 一次性 token，防脚本撞库；
2. **TOTP 动态码**：配置 `STORE_ADMIN_TOTP_SECRET` 后登录必须附 6 位动态码（RFC 6238，步长 60s，±1 时间窗容错；未配密钥时自动降级跳过）。用认证器 App 扫后台二维码绑定；
3. **失败锁定（IP + 账号 双维度）**：1 分钟内登录失败 3 次，对来源 IP（`ipban.json`）和尝试的账号名（`accountban.json`）各锁 30 分钟；**输对密码+TOTP 同时清零两维度并解锁**。nginx 以覆盖式 `$remote_addr` 传真实 IP，后端凭 `STORE_TRUST_PROXY=1` 采信，防 XFF 伪造绕过。

> 算术答案、锁定响应（429 + `locked`/`locked_until`）、session 均绑定安全属性（HttpOnly/Secure/SameSite=Strict）。

---

## 五、源码仓库

| 内容 | 仓库 |
|------|------|
| 官网源码（VitePress） | AiCode `docs-site/` |
| 提示词库 | jieapi/ai-prompts + jieapi/prompt-for-AiVerse（20 篇） |
| 插件商店审核后台（admin） | **https://github.com/yhdmgf/aiverserules**（`site/` 目录：Go 后端 + `web/` 前端） |
| 插件仓库（上架目标） | https://github.com/yhdmgf/aiverserules |

插件商店审核后台源码托管在 **github.com/yhdmgf/aiverserules**，其中：
- `site/`：Go 后端（`main.go`、`admin.go`、`upload.go`、`store.go`、`github.go`、`security.go`、`web.go`）；
- `web/`：审核后台前端（`index.html`、`style.css`、`review.js`、`logo.png`）。

---

## 六、本地预览

本目录为纯静态（除 `/api/`），可直接用任意静态服务器预览官网与提示词：

```bash
python3 -m http.server 8080
# 访问 http://localhost:8080
```

> 注：插件商店审核后台 `/store/` 需配合后端 Go 服务（`127.0.0.1:8088`）与 Nginx 反代 `/api/` 才能完整运行。

---

**AiVerse** — 手机端 AI 编程工具与内置 Linux 终端
官网：https://www.aivapp.cn
