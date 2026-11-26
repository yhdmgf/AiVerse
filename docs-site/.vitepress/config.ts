import { defineConfig } from 'vitepress'

const siteData = {
  "lang": "en-US",
  "dir": "ltr",
  "title": "AiVerse",
  "titleTemplate": ":title | AiVerse - 手机端 AI 编程工具与终端",
  "description": "AiVerse 是一款开源的 Android 端 AI 编程与移动工作站工具。内置 Linux 容器与终端环境，AI Agent 可自主读写文件、执行 Shell 命令与运行构建，深度集成 MCP 协议、Git 版本控制与远程 SSH 开发。",
  "base": "/",
  "head": [],
  "router": {
    "prefetchLinks": true
  },
  "appearance": true,
  "themeConfig": {
    "nav": [
      {
        "text": "使用手册",
        "link": "/guide/quick-start",
        "activeMatch": "/guide/"
      },
      {
        "text": "进阶教程",
        "link": "/advanced/build-android-app",
        "activeMatch": "/advanced/"
      },
      {
        "text": "下载",
        "link": "https://aivapp.cn/download"
      },
      {
        "text": "用户协议",
        "link": "/terms"
      },
      {
        "text": "隐私政策",
        "link": "/privacy"
      }
    ],
    "sidebar": {
      "/guide/": [
        {
          "text": "入门",
          "items": [
            {
              "text": "快速上手",
              "link": "/guide/quick-start"
            },
            {
              "text": "功能总览",
              "link": "/guide/overview"
            },
            {
              "text": "免费供应商",
              "link": "/guide/free-providers"
            }
          ]
        },
        {
          "text": "核心工作流",
          "items": [
            {
              "text": "聊天界面导览",
              "link": "/guide/chat"
            },
            {
              "text": "会话管理",
              "link": "/guide/sessions"
            },
            {
              "text": "角色系统",
              "link": "/guide/roles"
            },
            {
              "text": "四种模式（Build / Plan / Auto / Expert）",
              "link": "/guide/modes"
            },
            {
              "text": "会话目标（Goal）",
              "link": "/guide/goal"
            },
            {
              "text": "检查点与撤销",
              "link": "/guide/checkpoint"
            },
            {
              "text": "终端",
              "link": "/guide/terminal"
            },
            {
              "text": "内置浏览器",
              "link": "/guide/browser"
            },
            {
              "text": "文件浏览与代码编辑",
              "link": "/guide/files"
            },
            {
              "text": "Git 版本管理",
              "link": "/guide/git"
            },
            {
              "text": "平板与大屏适配",
              "link": "/guide/tablet"
            }
          ]
        },
        {
          "text": "模型与用量",
          "items": [
            {
              "text": "AI 供应商与模型",
              "link": "/guide/providers"
            },
            {
              "text": "默认与专用模型",
              "link": "/guide/default-models"
            },
            {
              "text": "Token 统计与费用",
              "link": "/guide/token-stats"
            }
          ]
        },
        {
          "text": "执行环境",
          "items": [
            {
              "text": "容器与镜像",
              "link": "/guide/container"
            },
            {
              "text": "远程 SSH 模式",
              "link": "/guide/remote-ssh"
            },
            {
              "text": "工作区同步",
              "link": "/guide/sync"
            },
            {
              "text": "网络代理",
              "link": "/guide/proxy"
            }
          ]
        },
        {
          "text": "扩展能力",
          "items": [
            {
              "text": "MCP 服务器",
              "link": "/guide/mcp"
            },
            {
              "text": "技能",
              "link": "/guide/skills"
            },
            {
              "text": "子代理",
              "link": "/guide/subagent"
            },
            {
              "text": "Shizuku 工具",
              "link": "/guide/shizuku"
            },
            {
              "text": "自定义提示词",
              "link": "/guide/custom-prompts"
            },
            {
              "text": "记忆与项目规则",
              "link": "/guide/memory"
            },
            {
              "text": "插件安装与使用",
              "link": "/guide/scripts"
            }
          ]
        },
        {
          "text": "设置与维护",
          "items": [
            {
              "text": "偏好设置",
              "link": "/guide/general"
            },
            {
              "text": "外观与语言",
              "link": "/guide/appearance"
            },
            {
              "text": "语音朗读",
              "link": "/guide/tts"
            },
            {
              "text": "工具授权",
              "link": "/guide/permissions"
            },
            {
              "text": "软件权限",
              "link": "/guide/app-permissions"
            },
            {
              "text": "后台运行",
              "link": "/guide/background-run"
            },
            {
              "text": "日志与故障排查",
              "link": "/guide/logs"
            },
            {
              "text": "常见错误提示",
              "link": "/guide/error-codes"
            },
            {
              "text": "存储空间",
              "link": "/guide/storage"
            },
            {
              "text": "备份与还原",
              "link": "/guide/backup"
            },
            {
              "text": "关于与更新",
              "link": "/guide/about"
            },
            {
              "text": "用户协议",
              "link": "/terms"
            },
            {
              "text": "隐私政策",
              "link": "/privacy"
            }
          ]
        }
      ],
      "/advanced/": [
        {
          "text": "环境搭建",
          "items": [
            {
              "text": "在容器中编译 Android 应用",
              "link": "/advanced/build-android-app"
            },
            {
              "text": "在容器中编译 Flutter 应用",
              "link": "/advanced/build-flutter-app"
            },
            {
              "text": "在容器中构建 Flutter release APK",
              "link": "/advanced/build-flutter-release"
            },
            {
              "text": "安装 Playwright 浏览器自动化",
              "link": "/advanced/playwright-mcp"
            }
          ]
        },
        {
          "text": "设备调试",
          "items": [
            {
              "text": "用 adb 无线调试其他设备",
              "link": "/advanced/adb-wireless-debug"
            }
          ]
        },
        {
          "text": "模型与推理",
          "items": [
            {
              "text": "思考强度与关闭思考",
              "link": "/advanced/reasoning-modes"
            }
          ]
        },
        {
          "text": "扩展开发",
          "items": [
            {
              "text": "自定义面板",
              "link": "/advanced/dashboard-cards"
            },
            {
              "text": "插件开发指南",
              "link": "/advanced/plugin-dev"
            }
          ]
        }
      ],
      "/en/guide/": [
        {
          "text": "Getting Started",
          "items": [
            {
              "text": "Quick Start",
              "link": "/en/guide/quick-start"
            },
            {
              "text": "Feature Overview",
              "link": "/en/guide/overview"
            },
            {
              "text": "Free Vendors",
              "link": "/en/guide/free-providers"
            }
          ]
        },
        {
          "text": "Core Workflows",
          "items": [
            {
              "text": "Chat Interface Tour",
              "link": "/en/guide/chat"
            },
            {
              "text": "Session Management",
              "link": "/en/guide/sessions"
            },
            {
              "text": "Roles",
              "link": "/en/guide/roles"
            },
            {
              "text": "Four Modes (Build / Plan / Auto / Expert)",
              "link": "/en/guide/modes"
            },
            {
              "text": "Session Goal",
              "link": "/en/guide/goal"
            },
            {
              "text": "Checkpoints & Undo",
              "link": "/en/guide/checkpoint"
            },
            {
              "text": "Terminal",
              "link": "/en/guide/terminal"
            },
            {
              "text": "Built-in Browser",
              "link": "/en/guide/browser"
            },
            {
              "text": "File Browsing & Code Editing",
              "link": "/en/guide/files"
            },
            {
              "text": "Git Version Control",
              "link": "/en/guide/git"
            },
            {
              "text": "Tablet & Large Screen",
              "link": "/en/guide/tablet"
            }
          ]
        },
        {
          "text": "Models & Usage",
          "items": [
            {
              "text": "AI Vendors & Models",
              "link": "/en/guide/providers"
            },
            {
              "text": "Default & Dedicated Models",
              "link": "/en/guide/default-models"
            },
            {
              "text": "Token Stats & Cost",
              "link": "/en/guide/token-stats"
            }
          ]
        },
        {
          "text": "Runtime Environment",
          "items": [
            {
              "text": "Container & Images",
              "link": "/en/guide/container"
            },
            {
              "text": "Remote SSH Mode",
              "link": "/en/guide/remote-ssh"
            },
            {
              "text": "Workspace Sync",
              "link": "/en/guide/sync"
            },
            {
              "text": "Network Proxy",
              "link": "/en/guide/proxy"
            }
          ]
        },
        {
          "text": "Extensions",
          "items": [
            {
              "text": "MCP Servers",
              "link": "/en/guide/mcp"
            },
            {
              "text": "Skills",
              "link": "/en/guide/skills"
            },
            {
              "text": "Subagents",
              "link": "/en/guide/subagent"
            },
            {
              "text": "Shizuku Tools",
              "link": "/en/guide/shizuku"
            },
            {
              "text": "Custom Prompts",
              "link": "/en/guide/custom-prompts"
            },
            {
              "text": "Memory & Project Rules",
              "link": "/en/guide/memory"
            },
            {
              "text": "Plugin Install & Use",
              "link": "/en/guide/scripts"
            }
          ]
        },
        {
          "text": "Settings & Maintenance",
          "items": [
            {
              "text": "General Settings",
              "link": "/en/guide/general"
            },
            {
              "text": "Appearance & Language",
              "link": "/en/guide/appearance"
            },
            {
              "text": "Text-to-Speech",
              "link": "/en/guide/tts"
            },
            {
              "text": "Tool Authorization",
              "link": "/en/guide/permissions"
            },
            {
              "text": "System Permissions",
              "link": "/en/guide/app-permissions"
            },
            {
              "text": "Background Run",
              "link": "/en/guide/background-run"
            },
            {
              "text": "Logs & Troubleshooting",
              "link": "/en/guide/logs"
            },
            {
              "text": "Common Error Messages",
              "link": "/en/guide/error-codes"
            },
            {
              "text": "Storage",
              "link": "/en/guide/storage"
            },
            {
              "text": "Backup & Restore",
              "link": "/en/guide/backup"
            },
            {
              "text": "About & Updates",
              "link": "/en/guide/about"
            },
            {
              "text": "User Agreement",
              "link": "/en/terms"
            },
            {
              "text": "Privacy Policy",
              "link": "/en/privacy"
            }
          ]
        }
      ],
      "/en/advanced/": [
        {
          "text": "Environment Setup",
          "items": [
            {
              "text": "Build Android Apps in the Container",
              "link": "/en/advanced/build-android-app"
            },
            {
              "text": "Build Flutter Apps in the Container",
              "link": "/en/advanced/build-flutter-app"
            },
            {
              "text": "Build a Flutter Release APK in the Container",
              "link": "/en/advanced/build-flutter-release"
            },
            {
              "text": "Install Playwright Browser Automation",
              "link": "/en/advanced/playwright-mcp"
            }
          ]
        },
        {
          "text": "Device Debugging",
          "items": [
            {
              "text": "Debug Other Devices over Wireless adb",
              "link": "/en/advanced/adb-wireless-debug"
            }
          ]
        },
        {
          "text": "Models & Reasoning",
          "items": [
            {
              "text": "Thinking Effort & Disabling Thinking",
              "link": "/en/advanced/reasoning-modes"
            }
          ]
        },
        {
          "text": "Extension Development",
          "items": [
            {
              "text": "Custom Dashboard Cards",
              "link": "/en/advanced/dashboard-cards"
            },
            {
              "text": "Plugin Development Guide",
              "link": "/en/advanced/plugin-dev"
            }
          ]
        }
      ]
    },
    "socialLinks": [
      {
        "icon": "github",
        "link": "https://github.com/yhdmgf/AiVerse"
      }
    ],
    "search": {
      "provider": "local"
    },
    "outline": {
      "label": "页面导航",
      "level": [
        2,
        3
      ]
    },
    "docFooter": {
      "prev": "上一页",
      "next": "下一页"
    },
    "lastUpdatedText": "最后更新于",
    "darkModeSwitchLabel": "主题",
    "lightModeSwitchTitle": "切换到浅色模式",
    "darkModeSwitchTitle": "切换到深色模式",
    "sidebarMenuLabel": "菜单",
    "returnToTopLabel": "回到顶部",
    "footer": {
      "message": "基于 GPL-3.0 协议开源",
      "copyright": "Copyright © 2026-至今 AiVerse"
    }
  },
  "locales": {
    "root": {
      "label": "简体中文",
      "lang": "zh-Hans"
    },
    "en": {
      "label": "English",
      "lang": "en",
      "title": "AiVerse",
      "titleTemplate": ":title | AiVerse - AI Coding Tool & Terminal on Android",
      "description": "AiVerse is an open-source AI coding and mobile workstation tool for Android. It ships with a built-in Linux container and terminal, letting the AI Agent read and write files, run shell commands and builds on its own — with deep integration of the MCP protocol, Git version control and remote SSH development.",
      "head": [
        [
          "meta",
          {
            "name": "keywords",
            "content": "AiVerse, Android AI coding, mobile development, Linux terminal, PRoot, Termux, AI Agent, MCP, code editor, Git client, mobile workstation, remote SSH"
          }
        ],
        [
          "meta",
          {
            "property": "og:title",
            "content": "AiVerse - AI Coding Tool & Linux Terminal on Android"
          }
        ],
        [
          "meta",
          {
            "property": "og:description",
            "content": "An open-source AI coding tool for Android with a built-in Linux container and terminal. AI Agent edits code, runs commands and builds; supports MCP tools, Git and remote SSH."
          }
        ],
        [
          "meta",
          {
            "name": "twitter:title",
            "content": "AiVerse - AI Coding Tool & Linux Terminal on Android"
          }
        ],
        [
          "meta",
          {
            "name": "twitter:description",
            "content": "An open-source AI coding tool for Android with a built-in Linux container and terminal. AI Agent edits code, runs commands and builds; supports MCP tools, Git and remote SSH."
          }
        ]
      ],
      "themeConfig": {
        "nav": [
          {
            "text": "Guide",
            "link": "/en/guide/quick-start",
            "activeMatch": "/en/guide/"
          },
          {
            "text": "Advanced",
            "link": "/en/advanced/build-android-app",
            "activeMatch": "/en/advanced/"
          },
          {
            "text": "Download",
            "link": "https://aivapp.cn/en/download"
          },
          {
            "text": "User Agreement",
            "link": "/en/terms"
          },
          {
            "text": "Privacy",
            "link": "/en/privacy"
          }
        ],
        "sidebar": {
          "/en/guide/": [
            {
              "text": "Getting Started",
              "items": [
                {
                  "text": "Quick Start",
                  "link": "/en/guide/quick-start"
                },
                {
                  "text": "Feature Overview",
                  "link": "/en/guide/overview"
                },
                {
                  "text": "Free Vendors",
                  "link": "/en/guide/free-providers"
                }
              ]
            },
            {
              "text": "Core Workflows",
              "items": [
                {
                  "text": "Chat Interface Tour",
                  "link": "/en/guide/chat"
                },
                {
                  "text": "Session Management",
                  "link": "/en/guide/sessions"
                },
                {
                  "text": "Roles",
                  "link": "/en/guide/roles"
                },
                {
                  "text": "Four Modes (Build / Plan / Auto / Expert)",
                  "link": "/en/guide/modes"
                },
                {
                  "text": "Session Goal",
                  "link": "/en/guide/goal"
                },
                {
                  "text": "Checkpoints & Undo",
                  "link": "/en/guide/checkpoint"
                },
                {
                  "text": "Terminal",
                  "link": "/en/guide/terminal"
                },
                {
                  "text": "Built-in Browser",
                  "link": "/en/guide/browser"
                },
                {
                  "text": "File Browsing & Code Editing",
                  "link": "/en/guide/files"
                },
                {
                  "text": "Git Version Control",
                  "link": "/en/guide/git"
                },
                {
                  "text": "Tablet & Large Screen",
                  "link": "/en/guide/tablet"
                }
              ]
            },
            {
              "text": "Models & Usage",
              "items": [
                {
                  "text": "AI Vendors & Models",
                  "link": "/en/guide/providers"
                },
                {
                  "text": "Default & Dedicated Models",
                  "link": "/en/guide/default-models"
                },
                {
                  "text": "Token Stats & Cost",
                  "link": "/en/guide/token-stats"
                }
              ]
            },
            {
              "text": "Runtime Environment",
              "items": [
                {
                  "text": "Container & Images",
                  "link": "/en/guide/container"
                },
                {
                  "text": "Remote SSH Mode",
                  "link": "/en/guide/remote-ssh"
                },
                {
                  "text": "Workspace Sync",
                  "link": "/en/guide/sync"
                },
                {
                  "text": "Network Proxy",
                  "link": "/en/guide/proxy"
                }
              ]
            },
            {
              "text": "Extensions",
              "items": [
                {
                  "text": "MCP Servers",
                  "link": "/en/guide/mcp"
                },
                {
                  "text": "Skills",
                  "link": "/en/guide/skills"
                },
                {
                  "text": "Subagents",
                  "link": "/en/guide/subagent"
                },
                {
                  "text": "Shizuku Tools",
                  "link": "/en/guide/shizuku"
                },
                {
                  "text": "Custom Prompts",
                  "link": "/en/guide/custom-prompts"
                },
                {
                  "text": "Memory & Project Rules",
                  "link": "/en/guide/memory"
                },
                {
                  "text": "Plugin Install & Use",
                  "link": "/en/guide/scripts"
                }
              ]
            },
            {
              "text": "Settings & Maintenance",
              "items": [
                {
                  "text": "General Settings",
                  "link": "/en/guide/general"
                },
                {
                  "text": "Appearance & Language",
                  "link": "/en/guide/appearance"
                },
                {
                  "text": "Text-to-Speech",
                  "link": "/en/guide/tts"
                },
                {
                  "text": "Tool Authorization",
                  "link": "/en/guide/permissions"
                },
                {
                  "text": "System Permissions",
                  "link": "/en/guide/app-permissions"
                },
                {
                  "text": "Background Run",
                  "link": "/en/guide/background-run"
                },
                {
                  "text": "Logs & Troubleshooting",
                  "link": "/en/guide/logs"
                },
                {
                  "text": "Common Error Messages",
                  "link": "/en/guide/error-codes"
                },
                {
                  "text": "Storage",
                  "link": "/en/guide/storage"
                },
                {
                  "text": "Backup & Restore",
                  "link": "/en/guide/backup"
                },
                {
                  "text": "About & Updates",
                  "link": "/en/guide/about"
                },
                {
                  "text": "User Agreement",
                  "link": "/en/terms"
                },
                {
                  "text": "Privacy Policy",
                  "link": "/en/privacy"
                }
              ]
            }
          ],
          "/en/advanced/": [
            {
              "text": "Environment Setup",
              "items": [
                {
                  "text": "Build Android Apps in the Container",
                  "link": "/en/advanced/build-android-app"
                },
                {
                  "text": "Build Flutter Apps in the Container",
                  "link": "/en/advanced/build-flutter-app"
                },
                {
                  "text": "Build a Flutter Release APK in the Container",
                  "link": "/en/advanced/build-flutter-release"
                },
                {
                  "text": "Install Playwright Browser Automation",
                  "link": "/en/advanced/playwright-mcp"
                }
              ]
            },
            {
              "text": "Device Debugging",
              "items": [
                {
                  "text": "Debug Other Devices over Wireless adb",
                  "link": "/en/advanced/adb-wireless-debug"
                }
              ]
            },
            {
              "text": "Models & Reasoning",
              "items": [
                {
                  "text": "Thinking Effort & Disabling Thinking",
                  "link": "/en/advanced/reasoning-modes"
                }
              ]
            },
            {
              "text": "Extension Development",
              "items": [
                {
                  "text": "Custom Dashboard Cards",
                  "link": "/en/advanced/dashboard-cards"
                },
                {
                  "text": "Plugin Development Guide",
                  "link": "/en/advanced/plugin-dev"
                }
              ]
            }
          ]
        },
        "outline": {
          "label": "On this page",
          "level": [
            2,
            3
          ]
        },
        "docFooter": {
          "prev": "Previous",
          "next": "Next"
        },
        "lastUpdatedText": "Last updated",
        "darkModeSwitchLabel": "Theme",
        "lightModeSwitchTitle": "Switch to light mode",
        "darkModeSwitchTitle": "Switch to dark mode",
        "sidebarMenuLabel": "Menu",
        "returnToTopLabel": "Back to top",
        "footer": {
          "message": "Open source under GPL-3.0",
          "copyright": "Copyright © 2026-present AiVerse"
        }
      }
    }
  },
  "scrollOffset": 134,
  "cleanUrls": true
} as const

export default defineConfig({
  srcDir: './docs',
  lang: siteData.lang,
  title: siteData.title,
  titleTemplate: siteData.titleTemplate,
  description: siteData.description,
  cleanUrls: true,
  lastUpdated: true,
  sitemap: { hostname: 'https://aivapp.cn' },
  head: [
    ['link', { rel: 'icon', href: '/logo.png', type: 'image/png' }],
    ['link', { rel: 'stylesheet', href: '/custom.css?v=9' }],
    ['script', { src: '/site-enhance.js?v=12' }]
  ],
  locales: siteData.locales,
  themeConfig: siteData.themeConfig
})
