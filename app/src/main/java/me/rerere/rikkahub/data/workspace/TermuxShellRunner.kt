package me.rerere.rikkahub.data.workspace

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceShellContext
import me.rerere.workspace.WorkspaceShellRunner
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 通过 Termux 的 RUN_COMMAND service 在 Termux 环境里执行命令。
 *
 * 不能直接 exec /data/data/com.termux/.../bash: 那是另一个应用的私有目录, 非 root 设备上
 * 读取和执行都会被 UID/SELinux 拦住。RUN_COMMAND 是 Termux 官方提供的跳板, 命令以
 * Termux 自己的 UID 运行, 因此天然带着 Termux 的 PREFIX/PATH/LD_PRELOAD 等环境。
 *
 * 前置条件:
 * 1. 安装 Termux
 * 2. Termux 里 ~/.termux/termux.properties 设置 allow-external-apps=true
 * 3. 系统设置里给本应用授予「在 Termux 环境中运行命令」权限
 */
class TermuxShellRunner(context: Context) : WorkspaceShellRunner {
    private val appContext = context.applicationContext
    private val nextRequestCode = AtomicInteger(1)

    override fun execute(context: WorkspaceShellContext): WorkspaceCommandResult {
        if (!isTermuxInstalled()) {
            return failure("未安装 Termux（com.termux）")
        }
        if (!hasRunCommandPermission()) {
            return failure("未授予 Termux 运行命令权限：系统设置 → 应用 → RikkaHub → 权限 → 其他权限 → 允许在 Termux 环境中运行命令")
        }

        val resultQueue = LinkedBlockingQueue<Bundle>(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                resultQueue.offer(intent?.getBundleExtra(EXTRA_RESULT_BUNDLE) ?: Bundle())
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(RESULT_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        return try {
            val requestCode = nextRequestCode.getAndIncrement()
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(
                appContext,
                requestCode,
                Intent(RESULT_ACTION).setPackage(appContext.packageName),
                flags,
            )

            val intent = Intent().apply {
                component = ComponentName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
                action = ACTION_RUN_COMMAND
                putExtra(EXTRA_COMMAND_PATH, "$TERMUX_PREFIX/bin/bash")
                putExtra(EXTRA_ARGUMENTS, arrayOf("-lc", buildScript(context.command)))
                putExtra(EXTRA_WORKDIR, TERMUX_HOME)
                putExtra(EXTRA_BACKGROUND, true)
                putExtra(EXTRA_SESSION_ACTION, "0")
                putExtra(EXTRA_COMMAND_LABEL, "RikkaHub workspace")
                putExtra(EXTRA_PENDING_INTENT, pendingIntent)
            }
            ContextCompat.startForegroundService(appContext, intent)

            val bundle = resultQueue.poll(context.timeoutMillis + TERMUX_STARTUP_GRACE_MS, TimeUnit.MILLISECONDS)
                ?: return WorkspaceCommandResult(
                    exitCode = -1,
                    stdout = "",
                    stderr = "Termux 执行超时（${context.timeoutMillis + TERMUX_STARTUP_GRACE_MS}ms）",
                    timedOut = true,
                )

            val errorCode = bundle.getInt(RESULT_ERR, Activity.RESULT_OK)
            val errorMessage = bundle.getString(RESULT_ERRMSG).orEmpty()
            if (errorCode != Activity.RESULT_OK) {
                return failure(errorMessage.ifBlank { "Termux 执行失败（code=$errorCode）" })
            }
            WorkspaceCommandResult(
                exitCode = bundle.getInt(RESULT_EXIT_CODE, -1),
                stdout = bundle.getString(RESULT_STDOUT).orEmpty(),
                stderr = bundle.getString(RESULT_STDERR).orEmpty(),
                timedOut = false,
                truncated = false,
            )
        } catch (error: Exception) {
            failure(error.message ?: "无法调用 Termux")
        } finally {
            runCatching { appContext.unregisterReceiver(receiver) }
        }
    }

    /** 注入 Termux 的环境变量后再执行命令; 工作目录固定为 Termux 的 HOME */
    private fun buildScript(command: String): String = buildString {
        append("export PREFIX=").append(TERMUX_PREFIX).append('\n')
        append("export PATH=").append(TERMUX_PREFIX).append("/bin\n")
        append("export HOME=").append(TERMUX_HOME).append('\n')
        append("export TMPDIR=").append(TERMUX_PREFIX).append("/tmp\n")
        append("export SHELL=").append(TERMUX_PREFIX).append("/bin/bash\n")
        append("export TERM=xterm-256color\n")
        append("export LANG=en_US.UTF-8\n")
        append("export COLORTERM=truecolor\n")
        append("if [ -f ${'$'}PREFIX/lib/libtermux-exec-ld-preload.so ]; then export LD_PRELOAD=${'$'}PREFIX/lib/libtermux-exec-ld-preload.so; fi\n")
        append("cd \"${'$'}HOME\" 2>/dev/null || cd /\n")
        append(command)
    }

    private fun failure(message: String) = WorkspaceCommandResult(
        exitCode = 126,
        stdout = "",
        stderr = message,
    )

    private fun isTermuxInstalled(): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    }.getOrDefault(false)

    private fun hasRunCommandPermission(): Boolean = runCatching {
        appContext.checkSelfPermission(TERMUX_RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
        const val TERMUX_HOME = "/data/data/com.termux/files/home"
        const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
        const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
        const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
        const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
        const val EXTRA_RESULT_BUNDLE = "com.termux.RUN_COMMAND_RESULT_BUNDLE"
        const val RESULT_STDOUT = "stdout"
        const val RESULT_STDERR = "stderr"
        const val RESULT_EXIT_CODE = "exitCode"
        const val RESULT_ERR = "err"
        const val RESULT_ERRMSG = "errmsg"
        const val RESULT_ACTION = "me.rerere.rikkahub.action.TERMUX_RESULT"
        const val TERMUX_STARTUP_GRACE_MS = 15_000L
    }
}
