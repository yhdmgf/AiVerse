package com.aiverse.core.ui

/**
 * 全屏查看器的打开来源。
 *
 * 刻意只放「文件树」这一档：只有从侧边栏文件树打开时，顶部栏才换成「返回箭头（左）+ 横向三点（右）」
 * 并提供文件操作入口；聊天附件 / Markdown 图片走默认 `null`，完全保持旧行为（仅右上 × 关闭、无菜单）。
 *
 * 请求上的 [origin] 带默认值 `null`，所以既有调用点（聊天 / Markdown / Git 渲染）一行不改即向后兼容。
 */
enum class ViewerOrigin {
    /** 从侧边栏文件树打开：显示返回箭头 + 三点菜单入口，动作由宿主（feature 层）注入。 */
    FILE_TREE
}
