package me.rerere.rikkahub.data.ai.transformers

import android.util.Log
import kotlinx.coroutines.CancellationException
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceShellMode
import me.rerere.workspace.WorkspaceShellStatus
import java.io.ByteArrayOutputStream
import java.nio.file.Paths

/**
 * Workspace 系统提示注入转换器
 *
 * 当助手绑定了一个 shell 已就绪的 workspace 时, 在系统提示词中追加一段引导,
 * 让模型了解 workspace 环境与 workspace_* 工具的使用方式。
 * 三种 Shell 模式 (proot / 本地 / Termux) 各自一套提示, 避免模型把 /workspace 或 apt 当成通用的。
 * 同时读取 ~/.agents、/workspace 和会话当前目录中的 AGENTS.md 作为工作区指令。
 */
class WorkspaceReminderTransformer(
    private val workspaceRepository: WorkspaceRepository,
) : InputMessageTransformer {
    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val workspaceId = ctx.assistant.workspaceId?.toString() ?: return messages
        val workspace = workspaceRepository.getById(workspaceId) ?: return messages
        // 与 ChatToolFactory.createWorkspaceToolsIfReady 保持一致: 仅在 shell 就绪时注入
        if (workspace.shellStatus != WorkspaceShellStatus.READY.name) return messages

        val prompt = when (workspaceRepository.shellMode(workspaceId)) {
            WorkspaceShellMode.TERMUX ->
                buildTermuxShellPrompt(workspace = workspace, cwd = ctx.workspaceCwd)

            WorkspaceShellMode.LOCAL ->
                buildLocalShellPrompt(workspace = workspace, cwd = ctx.workspaceCwd)

            WorkspaceShellMode.ROOTFS ->
                buildWorkspacePrompt(
                    workspace = workspace,
                    shell = workspaceRepository.rootfsShell(workspaceId),
                    distro = readDistroName(workspaceId),
                    cwd = ctx.workspaceCwd,
                ) + buildAgentsPrompt(workspaceId, ctx.workspaceCwd)
        }

        // 追加到第一条 system 消息; 若不存在则插入一条
        val systemIndex = messages.indexOfFirst { it.role == MessageRole.SYSTEM }
        return if (systemIndex >= 0) {
            messages.toMutableList().apply {
                this[systemIndex] = this[systemIndex]
                    .appendText("\n\n$prompt")
                    .copy(isSynthetic = true)
            }
        } else {
            listOf(UIMessage.system(prompt).copy(isSynthetic = true)) + messages
        }
    }

    private suspend fun buildAgentsPrompt(workspaceId: String, cwd: String?): String {
        // ProotShellRunner 将 HOME 固定为 /root；相对 PWD 按 /workspace 解析。
        val workingDirectory = Paths.get("/workspace")
            .resolve(cwd?.takeIf { it.isNotBlank() } ?: ".")
            .normalize()
        val paths = linkedSetOf(
            "/root/.agents/AGENTS.md",
            "/workspace/AGENTS.md",
            workingDirectory.resolve("AGENTS.md").toString(),
        )
        val instructions = paths.mapNotNull { path ->
            readRootfsText(workspaceId, path, MAX_AGENTS_BYTES)?.let { path to it }
        }
        if (instructions.isEmpty()) return ""
        return buildString {
            appendLine()
            appendLine()
            appendLine("<workspace_instructions>")
            appendLine("Follow the AGENTS.md instructions below.")
            instructions.forEach { (path, content) ->
                appendLine()
                appendLine("AGENTS.md source: $path")
                appendLine(content)
            }
            append("</workspace_instructions>")
        }
    }

    private suspend fun readDistroName(workspaceId: String): String? =
        OS_RELEASE_PATHS.firstNotNullOfOrNull { path ->
            readRootfsText(workspaceId, path, MAX_OS_RELEASE_BYTES)?.let(::parseOsReleaseName)
        }

    // 文件不存在、过大或不可读时返回 null, 不影响提示词的其余部分
    private suspend fun readRootfsText(workspaceId: String, path: String, maxBytes: Long): String? =
        try {
            val size = workspaceRepository.rootfsFileSize(workspaceId, path)
            require(size <= maxBytes) { "$path exceeds $maxBytes bytes" }
            val content = ByteArrayOutputStream().use { output ->
                workspaceRepository.exportRootfsFile(workspaceId, path, output)
                output.toString(Charsets.UTF_8.name())
            }
            content.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d("WorkspaceReminder", "Skipping workspace file: $path", e)
            null
        }

    private companion object {
        const val MAX_AGENTS_BYTES = 64L * 1024
        const val MAX_OS_RELEASE_BYTES = 16L * 1024
        val OS_RELEASE_PATHS = listOf("/etc/os-release", "/usr/lib/os-release")
    }
}

private const val MAX_DISTRO_NAME_LENGTH = 80

/** 从 os-release 内容中取发行版名称, 优先 PRETTY_NAME, 其次 NAME + VERSION_ID */
internal fun parseOsReleaseName(content: String): String? {
    val values = content.lineSequence()
        .map { it.trim() }
        .filter { !it.startsWith("#") && it.indexOf('=') > 0 }
        .associate { line ->
            line.substringBefore('=') to line.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
        }
    val name = values["PRETTY_NAME"]?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(values["NAME"], values["VERSION_ID"]).joinToString(" ")
    // 内容来自 Rootfs 内的文件, 拼进系统提示前去掉控制字符并限制长度
    return name.filterNot { it.isISOControl() }.trim().take(MAX_DISTRO_NAME_LENGTH).ifBlank { null }
}

private fun buildWorkspacePrompt(
    workspace: WorkspaceEntity,
    shell: String,
    distro: String?,
    cwd: String? = null,
): String = buildString {
    appendLine("<workspace>")
    appendLine("You have access to a persistent Linux workspace named \"${workspace.name}\", running in a sandboxed proot rootfs environment.")
    appendLine("- The workspace files area is mounted at `/workspace`. Use it as your working directory; files written there persist across turns of this conversation.")
    appendLine("- All paths passed to workspace tools must be absolute and inside the Rootfs (for example `/workspace/notes.md`).")
    if (distro != null) {
        appendLine("- The Rootfs distribution is $distro. Use its native package manager when you need to install missing tools.")
    }
    if (shell == "/bin/bash") {
        appendLine("- `workspace_shell` runs commands with `/bin/bash`.")
    } else {
        appendLine("- `workspace_shell` runs commands with `$shell`, a POSIX shell. Bash is not installed, so avoid bash-only syntax such as arrays, here-strings (`<<<`) and brace expansion.")
    }
    appendLine("- Available tools:")
    appendLine("  - `workspace_read_file`: read file contents.")
    appendLine("  - `workspace_write_file` / `workspace_edit_file`: create files, or make precise edits to existing files.")
    appendLine("  - `workspace_shell`: run shell commands (the files area is mounted at /workspace).")
    appendLine("- Prefer `workspace_shell` for tasks that standard Unix tools handle well, and prefer `workspace_edit_file` for targeted edits over rewriting whole files.")
    appendLine("- The skills directory is mounted at `/skills`. Each skill is a subdirectory `/skills/<skill-name>/` containing a `SKILL.md` (with `name` and `description` frontmatter) plus any supporting files. Read a skill's `SKILL.md` before using it, and follow its instructions.")
    appendLine("- Built-in skills shipped with the app are mounted at `/builtin_skills/<skill-name>/` with the same layout. Treat `/builtin_skills` as READ-ONLY: you may read files and run scripts there, but never modify, overwrite, or delete anything. A skill in `/skills` with the same name overrides the built-in one.")
    appendLine("- Files the user uploaded are mounted at `/upload`. Treat `/upload` as READ-ONLY: read uploaded files from `/upload/<file-name>`, but never modify, overwrite, or delete anything there. If you need to change an uploaded file, copy it into `/workspace` first and edit the copy.")
    if (!cwd.isNullOrBlank()) {
        appendLine("- Current working directory: `$cwd`. Use this as the default context for file operations and shell commands.")
    }
    append("</workspace>")
}

/**
 * 本地 Shell 工作区的提示词。
 *
 * 关键差异: 没有 proot 的 /workspace 挂载、没有包管理器、没有 root、不能访问其他应用数据。
 * 文件工具仍用 /workspace/... 路径 (由 WorkspaceManager.resolveRootfsPath 映射到 files 目录),
 * 但 shell 命令里的 /workspace 是真实根目录下的不存在路径, 必须用相对路径或 pwd 得到的绝对路径。
 */
private fun buildLocalShellPrompt(
    workspace: WorkspaceEntity,
    cwd: String? = null,
): String = buildString {
    appendLine("<workspace>")
    appendLine("You have access to a persistent workspace named \"${workspace.name}\" that runs a native Android shell (no proot, no Linux distribution).")
    appendLine("- `workspace_shell` runs `/system/bin/sh` as the Android app user and starts in the workspace files area. Use relative paths, or run `pwd` first; `/workspace` does not exist as a real directory in shell commands.")
    appendLine("- `workspace_read_file`, `workspace_write_file` and `workspace_edit_file` still take `/workspace/...` paths; they map to the same files area the shell starts in.")
    appendLine("- The shell is toybox-based: common tools such as `ls`, `cat`, `grep`, `sed`, `awk`, `find`, `tar` are available, but there is no `apt`, `apk`, `pip`, `npm` or compiler, and no root.")
    appendLine("- Only the app's private storage is reachable; other apps' data and `/sdcard` are not accessible.")
    appendLine("- Never read or modify app configuration, preferences or databases (anything outside the workspace files area).")
    appendLine("- Available tools:")
    appendLine("  - `workspace_read_file`: read file contents.")
    appendLine("  - `workspace_write_file` / `workspace_edit_file`: create files, or make precise edits to existing files.")
    appendLine("  - `workspace_shell`: run shell commands.")
    appendLine("- Prefer `workspace_edit_file` for targeted edits instead of rewriting whole files.")
    if (!cwd.isNullOrBlank()) {
        appendLine("- Current working directory: `$cwd`.")
    }
    append("</workspace>")
}

/**
 * Termux 工作区的提示词。
 *
 * 命令由 Termux 的 bash 直接执行 (与本应用共享 UID), 环境变量由 runner 注入,
 * 所以 pkg/python/git 等都能用; 但工作目录是 Termux 的 HOME, 与 app 工作区目录不是同一个文件系统。
 */
private fun buildTermuxShellPrompt(
    workspace: WorkspaceEntity,
    cwd: String? = null,
): String {
    // 提示词里的 $PREFIX 是给 shell 看的字面量, 不能触发 Kotlin 字符串插值
    val dollar = "$"
    return buildString {
        appendLine("<workspace>")
        appendLine("You have access to a persistent workspace named \"${workspace.name}\" whose shell uses the Termux bash.")
        appendLine("- `workspace_shell` runs `/data/data/com.termux/files/usr/bin/bash -lc <command>` with `PREFIX=/data/data/com.termux/files/usr`, `PATH=${dollar}PREFIX/bin`, `HOME=/data/data/com.termux/files/home`, `TMPDIR=${dollar}PREFIX/tmp`, `SHELL=${dollar}PREFIX/bin/bash`, `TERM=xterm-256color`, `LANG=en_US.UTF-8`, `COLORTERM=truecolor` and termux-exec's `LD_PRELOAD`.")
        appendLine("- Bash, `pkg install`, python, git, node and friends are available; install missing tools with `pkg install <name>`.")
        appendLine("- The shell starts in Termux's HOME, **not** in the workspace files area. `/workspace` does not exist there.")
        appendLine("- `workspace_read_file`, `workspace_write_file` and `workspace_edit_file` use `/workspace/...` paths and read the app's workspace directory, which is a different filesystem from Termux's HOME.")
        appendLine("- Each command runs in a fresh bash, so keep state on disk rather than in shell variables.")
        appendLine("- Never read or modify the RikkaHub app's private data (configuration, preferences, databases).")
        appendLine("- Available tools:")
        appendLine("  - `workspace_read_file`: read workspace file contents.")
        appendLine("  - `workspace_write_file` / `workspace_edit_file`: create or edit workspace files.")
        appendLine("  - `workspace_shell`: run commands with the Termux bash.")
        if (!cwd.isNullOrBlank()) {
            appendLine("- Workspace-relative path requested by the user: `$cwd`.")
        }
        append("</workspace>")
    }
}

private fun UIMessage.appendText(extra: String): UIMessage {
    val updatedParts = parts.toMutableList()
    val firstTextIndex = updatedParts.indexOfFirst { it is UIMessagePart.Text }
    if (firstTextIndex >= 0) {
        val text = updatedParts[firstTextIndex] as UIMessagePart.Text
        updatedParts[firstTextIndex] = text.copy(text = text.text + extra)
    } else {
        updatedParts.add(UIMessagePart.Text(extra))
    }
    return copy(parts = updatedParts)
}
