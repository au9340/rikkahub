package me.rerere.rikkahub.data.ai.transformers

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.prompts.renderWorkspacePrompt
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceShellMode
import me.rerere.workspace.WorkspaceShellStatus
import java.io.ByteArrayOutputStream
import java.nio.file.Paths

/**
 * Workspace 系统提示注入转换器
 *
 * 当助手绑定了一个 shell 已就绪的 workspace 时, 把助手自己配置的工作区提示词渲染后追加到系统提示。
 * 提示词正文不再硬编码: 默认值在 data/ai/prompts/Workspace.kt, 用户可以在助手详情里改。
 * 这里只负责采集"事实"(工作区名 / 发行版 / shell / 架构)并做占位符替换。
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

        val mode = workspaceRepository.shellMode(workspaceId)
        val template = when (mode) {
            WorkspaceShellMode.ROOTFS -> ctx.assistant.rootfsWorkspacePrompt
            // 本地 Shell 已无创建入口, 暂时与 Termux 共用一份模板
            WorkspaceShellMode.TERMUX, WorkspaceShellMode.LOCAL -> ctx.assistant.termuxWorkspacePrompt
        }.trim()
        // 模板留空 = 用户显式关闭注入, 不要回落到默认值, 否则"删干净"永远删不掉
        if (template.isEmpty()) return messages

        val rendered = renderWorkspacePrompt(template, workspaceFacts(workspace, mode, workspaceId))
        val unresolved = PLACEHOLDER_REGEX.findAll(rendered)
            .map { it.groupValues[1] }
            .toSet()
        if (unresolved.isNotEmpty()) {
            Log.w(TAG, "Unresolved workspace prompt placeholders: $unresolved")
        }

        val prompt = if (mode == WorkspaceShellMode.ROOTFS) {
            rendered + buildAgentsPrompt(workspaceId, ctx.workspaceCwd)
        } else {
            rendered
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

    /** 采集模板可用的占位符; 只放事实, 不放文案 */
    private suspend fun workspaceFacts(
        workspace: WorkspaceEntity,
        mode: WorkspaceShellMode,
        workspaceId: String,
    ): Map<String, String> = buildMap {
        put("workspace_name", workspace.name)
        when (mode) {
            WorkspaceShellMode.ROOTFS -> {
                put("shell", workspaceRepository.rootfsShell(workspaceId))
                readDistroName(workspaceId)?.let { put("distro", it) }
            }

            WorkspaceShellMode.TERMUX, WorkspaceShellMode.LOCAL -> {
                put("arch", primaryArch())
                // TODO: runner 改成从工作区目录启动后, 这里换成 ~/workspaces
                put("cwd", "~")
            }
        }
    }

    private fun primaryArch(): String =
        Build.SUPPORTED_ABIS.firstOrNull()?.let { abi ->
            when {
                abi.startsWith("arm64") -> "aarch64"
                abi.startsWith("x86_64") -> "x86_64"
                else -> abi
            }
        } ?: "unknown"

    private suspend fun buildAgentsPrompt(workspaceId: String, cwd: String?): String {
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
            Log.d(TAG, "Skipping workspace file: $path", e)
            null
        }

    private companion object {
        const val TAG = "WorkspaceReminder"
        const val MAX_AGENTS_BYTES = 64L * 1024
        const val MAX_OS_RELEASE_BYTES = 16L * 1024
        val OS_RELEASE_PATHS = listOf("/etc/os-release", "/usr/lib/os-release")
        val PLACEHOLDER_REGEX = Regex("""\{\{\s*(\w+)\s*}}""")
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
