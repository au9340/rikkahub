package me.rerere.rikkahub.data.workspace

import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceShellContext
import me.rerere.workspace.WorkspaceShellRunner
import me.rerere.workspace.readResult
import java.io.File
import java.io.IOException

/**
 * Termux 模式: 直接以 Termux 的 bash 启动命令, 并注入 Termux 的环境变量。
 */
class TermuxShellRunner : WorkspaceShellRunner {
    override fun execute(context: WorkspaceShellContext): WorkspaceCommandResult {
        if (!File(TERMUX_BASH).exists()) {
            return failure("找不到 $TERMUX_BASH，请确认已安装 Termux")
        }
        val workingDir = File(TERMUX_HOME).takeIf { it.isDirectory } ?: context.filesDir
        val builder = ProcessBuilder(TERMUX_BASH, "-lc", context.command)
            .directory(workingDir)
            .redirectErrorStream(false)
        builder.environment().apply {
            put("PREFIX", TERMUX_PREFIX)
            put("LD_PRELOAD", TERMUX_EXEC_PRELOAD)
            put("PATH", "$TERMUX_PREFIX/bin")
            put("HOME", TERMUX_HOME)
            put("TMPDIR", "$TERMUX_PREFIX/tmp")
            put("SHELL", TERMUX_BASH)
            put("TERM", "xterm-256color")
            put("LANG", "en_US.UTF-8")
            put("COLORTERM", "truecolor")
        }
        return try {
            builder.start().readResult(context.timeoutMillis, context.stdin)
        } catch (error: IOException) {
            failure("无法启动 Termux bash: ${error.message}")
        }
    }

    private fun failure(message: String) = WorkspaceCommandResult(
        exitCode = 126,
        stdout = "",
        stderr = message,
    )

    private companion object {
        const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
        const val TERMUX_HOME = "/data/data/com.termux/files/home"
        const val TERMUX_BASH = "$TERMUX_PREFIX/bin/bash"
        const val TERMUX_EXEC_PRELOAD = "$TERMUX_PREFIX/lib/libtermux-exec-ld-preload.so"
    }
}
