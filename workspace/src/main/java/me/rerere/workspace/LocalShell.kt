package me.rerere.workspace

import java.io.File

/** 本地 Shell 标记: 存在时使用宿主 /system/bin/sh, 不依赖 Rootfs */
const val LOCAL_SHELL_MARKER = ".local_shell"

/** Termux 标记: 存在时通过 RUN_COMMAND 在 Termux 里执行命令 */
const val TERMUX_SHELL_MARKER = ".termux_shell"

enum class WorkspaceShellMode { ROOTFS, LOCAL, TERMUX }

/** [filesDir] 是 workspace 下的 files 目录, 标记文件在它的上一级 */
fun workspaceUsesLocalShell(filesDir: File): Boolean =
    File(filesDir.parentFile, LOCAL_SHELL_MARKER).exists()

fun workspaceUsesTermux(filesDir: File): Boolean =
    File(filesDir.parentFile, TERMUX_SHELL_MARKER).exists()

fun workspaceShellMode(filesDir: File): WorkspaceShellMode = when {
    workspaceUsesTermux(filesDir) -> WorkspaceShellMode.TERMUX
    workspaceUsesLocalShell(filesDir) -> WorkspaceShellMode.LOCAL
    else -> WorkspaceShellMode.ROOTFS
}

fun setWorkspaceShellMode(filesDir: File, mode: WorkspaceShellMode) {
    val parent = filesDir.parentFile
    parent?.mkdirs()
    File(parent, LOCAL_SHELL_MARKER).let { marker ->
        if (mode == WorkspaceShellMode.LOCAL) marker.writeText("1") else marker.delete()
    }
    File(parent, TERMUX_SHELL_MARKER).let { marker ->
        if (mode == WorkspaceShellMode.TERMUX) marker.writeText("1") else marker.delete()
    }
}

fun setWorkspaceLocalShell(filesDir: File, enabled: Boolean) {
    setWorkspaceShellMode(filesDir, if (enabled) WorkspaceShellMode.LOCAL else WorkspaceShellMode.ROOTFS)
}
