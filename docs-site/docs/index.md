---
layout: home
title: AiVerse 官网 - 手机端 AI 编程工具与内置 Linux 终端
titleTemplate: false
description: AiVerse 是一款开源的 Android 端 AI 编程工具，无需电脑，在手机上即可完成完整开发流程。内置 Linux 容器与终端，AI Agent 可直接读写代码并执行构建；支持 Git 版本控制、MCP 协议与远程 SSH 开发。
head:
  - - meta
    - name: keywords
      content: AiVerse, Android AI 编程, 手机编程, 移动端开发, Linux 终端, PRoot, Termux, AI Agent, MCP 协议, 手机代码编辑器, Git 客户端, 移动工作站, 手机跑终端, 远程 SSH 开发

hero:
  name: AiVerse
  text: 手机上的 AI 编程工具与移动工作站
  tagline: 无需电脑，在手机上即可完成从写代码、调试到提交的完整开发流程。内置 Linux 容器与终端，AI Agent 可自主读写文件、执行 Shell 命令与运行构建，还支持 Git、MCP、远程 SSH 与完整模型生态。
  image:
    src: /logo.png
    alt: AiVerse
  actions:
    - theme: brand
      text: 快速上手
      link: /guide/quick-start
    - theme: alt
      text: 下载 APK
      link: https://aivapp.cn/download
    - theme: alt
      text: GitHub
      link: https://github.com/yhdmgf/AiVerse
    - theme: alt
      text: GitCode
      link: https://gitcode.com/aiverse/aiverse/

features:
  - title: AI Agent
    details: 支持 OpenAI / Anthropic / Gemini 兼容协议，多供应商随时切换；内置文件读写、Shell 执行、后台终端、搜索与图片识别等工具，流式输出，长对话自动压缩上下文。
  - title: 内置终端与 Linux 容器
    details: 基于 Termux 组件与 PRoot 的本地容器，内置 Alpine 镜像，可导入自定义 rootfs、挂载宿主目录，终端支持后台常驻。
  - title: 远程 SSH 模式
    details: 以远程服务器作为执行后端，命令走 exec channel、文件走 SFTP、终端走 shell channel，在手机上直接操作远程项目。AI 内置 sshExec、sshRead、sshList、sshTail、sshDownload、sshUpload、sshRm、sshMv 等工具，可直接在远程服务器上执行命令、读写文件、查看日志与传输文件；支持 sudo 提权与高频命令自动映射。
  - title: 检查点与撤销
    details: AI 改代码前自动记录文件快照，对话中可一键回滚，支持只恢复代码、只恢复对话，或两者同时恢复。
  - title: MCP、技能与记忆
    details: 可连接本地 stdio 与远程 HTTP 的 MCP 服务器动态扩展工具；支持全局与项目级技能和长期记忆，让 AI 跨会话复用经验。
  - title: Git 与工作区同步
    details: 内置状态、分支、提交、差异与标签管理；支持 Issue 与 PR 浏览、创建、合并、CI 状态与 AI 审查；支持 SFTP / FTP 同步，并自带 FTP 服务端方便电脑侧管理文件。
  - title: 插件系统
    details: 安装第三方插件为 AI 扩展工具、技能、子代理、供应商与界面入口，内置插件商店，支持应用内登录获取 Cookie，插件在容器内独立进程运行。
---

::: warning 重要说明
AiVerse 是一个 **开源项目**，本身 **不提供任何大模型服务**。模型能力需你 **自行填入自己的 API Key**（或自行部署的本地模型）后方可使用。本项目仅用于学习与开发；**禁止滥用** 其内置的 SSH、Shell（终端/命令执行）能力进行任何违法违规操作，由此产生的一切后果由使用者自行承担。
:::
