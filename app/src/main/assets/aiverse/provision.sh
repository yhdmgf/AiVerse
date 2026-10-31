#!/bin/sh
# 容器环境脚本（通用）：一个脚本，两个入口。
#   1) 首次进入终端：在 PTY 上弹出初始化菜单（自动安装 / 环境安装 / 手动安装 / 退出），用户完成
#      后写 /.provisioned 标记，之后进入终端不再弹。
#   2) 容器内执行命令 `aiverse`（等价于 `provision.sh --env`）：直接打开「环境安装」菜单，按场景
#      安装开发环境，可反复使用，不受初始化标记影响。
# 两个入口共用同一套逻辑：按容器内包管理器（apk/apt-get/dnf/yum/pacman）安装运行时，并可换国内镜像源。
# 由 App 启动时提取到 ~/.aiverse/provision.sh（容器内 /root/.aiverse/provision.sh，经 -b 绑定可见）；
# 公共能力（包管理器探测/包名映射/版本探测/换源/场景）在 lib/env-common.sh，与本脚本一并提取。
# 修改包清单/安装逻辑/镜像源后，需同步在 LinuxContainerEngine.PROVISION_VERSION 上 +1 触发存量设备重跑。
# 注意：apk 源分支 v3.21 需与 assets 内 alpine-rootfs 的 Alpine 分支一致（见 ContainerInstaller.ROOTFS_ASSET_VERSION）。
# 不用 ContainerInstaller.INSTALL_VERSION 对照：那是「是否需要重装」的标记，刻意与 assets 的 Alpine 版本解耦。

PROVISION_VERSION="provision-script-v11"
PROVISION_SKIPPED="provision-script-skipped"
MARKER="/.provisioned"

# ── 载入公共库（包名映射/换源/场景等）。按脚本自身所在目录定位：生产脚本在 /root/.aiverse/provision.sh、
# 库在 /root/.aiverse/lib/；工作区草稿副本（aiverse-env/）结构相同，故同一份脚本两处都能跑。
# 缺失时不阻塞：提示重启 App 重新提取后重进终端。──
SELF_DIR=$(dirname "$0")
[ "${SELF_DIR#/}" = "$SELF_DIR" ] && SELF_DIR="$(pwd)/$SELF_DIR"
AIVERSE_LIB_DIR="$SELF_DIR/lib"
LIB_FILE="$AIVERSE_LIB_DIR/env-common.sh"
if [ ! -f "$LIB_FILE" ]; then
    AIVERSE_LIB_DIR="${HOME:-/root}/.aiverse/lib"
    LIB_FILE="$AIVERSE_LIB_DIR/env-common.sh"
fi
if [ ! -f "$LIB_FILE" ]; then
    echo "环境工具未就绪，请重启 App 后重新进入终端" >&2
    exit 0
fi
. "$LIB_FILE"

detect_pmgr

# ── 已安装运行时一览（环境安装菜单用）──
list_installed() {
    echo ""
    echo "已安装运行时："
    for c in "node:Node.js" "python3:Python 3" "java:Java" "go:Go" "rustc:Rust" "php:PHP" "git:Git" "rg:ripgrep"; do
        cmd=${c%%:*}
        name=${c#*:}
        if command -v "$cmd" >/dev/null 2>&1; then
            ver=$("$cmd" --version 2>&1 | head -1)
            echo "  · $name：$ver"
        else
            echo "  · $name：（未安装）"
        fi
    done
    [ -x "$HOME/android/sdk/cmdline-tools/latest/bin/sdkmanager" ] && echo "  · Android SDK：$HOME/android/sdk"
    [ -x "$HOME/.local/flutter-$FLUTTER_VERSION/bin/flutter" ] && echo "  · Flutter SDK：$HOME/.local/flutter-$FLUTTER_VERSION"
}

# ── 环境安装菜单（场景化）：初始化菜单的「环境安装」与 `aiverse` 命令共用 ──
env_install_menu() {
    TOTAL=$(echo $SCENARIO_IDS | wc -w)
    CUSTOM_IDX=$((TOTAL + 1))
    MIRROR_IDX=$((TOTAL + 2))
    LIST_IDX=$((TOTAL + 3))
    EXIT_IDX=$((TOTAL + 4))
    while :; do
        echo ""
        echo "${C_CYAN}环境安装${C_RESET} — 选择要安装的开发环境"
        echo "${C_YELLOW}──────────────────────────────${C_RESET}"
        i=1
        for id in $SCENARIO_IDS; do
            printf "  ${C_GREEN}%d.${C_RESET} %s\n" "$i" "$(scenario_name "$id")"
            i=$((i + 1))
        done
        printf "  ${C_GREEN}%d.${C_RESET} 自定义安装（逐项勾选）\n" "$CUSTOM_IDX"
        printf "  ${C_GREEN}%d.${C_RESET} 仅换软件源\n" "$MIRROR_IDX"
        printf "  ${C_GREEN}%d.${C_RESET} 查看已安装运行时\n" "$LIST_IDX"
        printf "  ${C_DIM}%d. 退出${C_RESET}\n" "$EXIT_IDX"
        echo "${C_YELLOW}──────────────────────────────${C_RESET}"
        printf "请选择: "
        if ! read choice; then
            echo ""
            break
        fi
        case "$choice" in
            ""|*[!0-9]*)
                echo "${C_RED}无效输入，请重新选择。${C_RESET}"
                continue
                ;;
        esac
        if [ "$choice" -ge 1 ] && [ "$choice" -le "$TOTAL" ]; then
            id=$(echo $SCENARIO_IDS | awk -v i="$choice" '{print $i}')
            plog "环境安装：场景 $id"
            install_scenario "$id"
            rc=$?
            case "$rc" in
                0) echo ""; echo "${C_GREEN}安装完成。${C_RESET}" ;;
                2) echo ""; echo "已取消，返回菜单。" ;;
                *) echo ""; echo "${C_RED}安装失败，可重试或换一个镜像源。${C_RESET}" ;;
            esac
        elif [ "$choice" -eq "$CUSTOM_IDX" ]; then
            plog "环境安装：自定义安装"
            install_custom
            rc=$?
            case "$rc" in
                0) echo ""; echo "${C_GREEN}安装完成。${C_RESET}" ;;
                2) echo ""; echo "已取消，返回菜单。" ;;
                *) echo ""; echo "${C_RED}安装失败，可重试或换一个镜像源。${C_RESET}" ;;
            esac
        elif [ "$choice" -eq "$MIRROR_IDX" ]; then
            plog "环境安装：仅换源"
            ask_mirror
            echo "软件源已更新。"
        elif [ "$choice" -eq "$LIST_IDX" ]; then
            list_installed
        elif [ "$choice" -eq "$EXIT_IDX" ]; then
            plog "环境安装：退出"
            break
        else
            echo "${C_RED}无效输入，请重新选择。${C_RESET}"
        fi
    done
}

# ══ 入口 2：`aiverse`（provision.sh --env）→ 直接打开环境安装菜单，不受初始化标记影响 ══
if [ "$1" = "--env" ]; then
    plog "aiverse 打开环境安装菜单：PMGR=${PMGR:-未识别}"
    if [ -z "$PMGR" ]; then
        echo "${C_RED}未识别的包管理器（apk/apt-get/dnf/yum/pacman），无法安装依赖。${C_RESET}"
        exit 1
    fi
    env_install_menu
    exit 0
fi

# ══ 入口 1：首次进入终端的初始化流程 ══
plog "provision.sh 启动：version=$PROVISION_VERSION HOME=$HOME uid=$(id -u 2>/dev/null) PATH=$PATH"

# ── 宿主 supplementary gid 修复：proot 会把宿主进程的补充组（Android AID 3003 inet、
# 9997 everybody、App 自身 uid 派生 gid 等）透传进容器，/etc/group 查不到名字会让
# groups 等命令报警（cannot find name for group ID xxx）。幂等补行：gid_ 命名空间先清再补
# 防历史残留；位于 MARKER 检查之前，每次进终端都执行（已配置设备同样生效），gid 变化自动跟上。
# 全部静默失败，不阻塞进入 shell。
sed -i '/^gid_/d' /etc/group 2>/dev/null
for g in $(id -G 2>/dev/null); do
    grep -q ":x:$g:" /etc/group 2>/dev/null || echo "gid_$g:x:$g:" >> /etc/group 2>/dev/null
done

# 已按当前版本完成或用户选择手动安装（跳过）则直接退出
if [ -f "$MARKER" ]; then
    state=$(cat "$MARKER" 2>/dev/null)
    if [ "$state" = "$PROVISION_VERSION" ]; then plog "标记已是 $PROVISION_VERSION，跳过菜单"; exit 0; fi
    if [ "$state" = "$PROVISION_SKIPPED" ]; then plog "用户曾选择手动安装，跳过菜单"; exit 0; fi
fi

# 首次进入终端：自动配置环境（自动安装常见依赖 common），不弹菜单，用户无需手动选择。
# 安装完成后列出已安装/未安装运行时，未安装的可输入 aiverse 打开环境安装菜单按场景补装。
plog "首次进入终端：自动安装常见环境（common）"
echo ""
echo "${C_YELLOW}自动配置环境${C_RESET}：正在安装常见依赖（bash/curl/ripgrep/git 等），"
echo "  请保持 App 在前台（建议开启「后台保活」），安装完成后会列出未安装的运行时供补装。"
echo ""
install_scenario common
rc=$?
if [ "$rc" = "0" ]; then
    echo "$PROVISION_VERSION" > "$MARKER"
    plog "自动安装完成，写入标记 $PROVISION_VERSION"
    echo ""
    echo "${C_GREEN}基础依赖安装完成。${C_RESET}"
    echo ""
    echo "已安装/未安装运行时（未安装的可在终端输入 ${C_BOLD}aiverse${C_RESET} 打开环境安装菜单，按序号补装）："
    list_installed
elif [ "$rc" = "2" ]; then
    # 用户在安装清单确认时输入非 y（返回菜单）：不是安装失败，不写标记、不报错，直接进入 shell。
    plog "首次自动配置被用户取消（清单确认返回菜单）"
    echo ""
    echo "${C_DIM}已取消自动配置，未安装任何环境。可在终端输入 ${C_BOLD}aiverse${C_RESET} 打开环境安装菜单按需安装。${C_RESET}"
else
    echo ""
    echo "${C_RED}安装失败。可在 AI 对话中让 AI 读取本终端内容进行诊断修复，或重新进入终端重试。${C_RESET}"
fi
git_config