package me.rerere.workspace

import java.io.File

/**
 * 本地 Shell 标记文件。
 *
 * 存在于某个 workspace 根目录时, 该工作区不依赖 Rootfs, 直接使用宿主的 /system/bin/sh。
 * 用文件而不是数据库字段保存, 这样切换模式不需要 Room migration, 也不会因为
 * 备份/恢复顺序不同导致状态不一致。
 */
const val LOCAL_SHELL_MARKER = ".local_shell"

/** [filesDir] 是 workspace 下的 files 目录, 标记文件放在它的上一级 */
fun workspaceUsesLocalShell(filesDir: File): Boolean =
    File(filesDir.parentFile, LOCAL_SHELL_MARKER).exists()

fun setWorkspaceLocalShell(filesDir: File, enabled: Boolean) {
    val marker = File(filesDir.parentFile, LOCAL_SHELL_MARKER)
    if (enabled) {
        filesDir.parentFile?.mkdirs()
        marker.writeText("1")
    } else if (marker.exists()) {
        marker.delete()
    }
}
