package me.rerere.rikkahub.data.ai.prompts

/**
 * 工作区 Shell 提示词的默认值。
 *
 * 这里只放"最基础的兜底": 告诉模型工作目录在哪、文件工具和 shell 的路径怎么对应、有没有 root。
 * 环境变量由 runner 自动注入, 不写进提示词。
 * 用户可以在「助手 - 提示词 - 工作区提示词」里改, 改坏了点重置回到这里。
 */
const val DEFAULT_ROOTFS_WORKSPACE_PROMPT = """
<workspace name="{{workspace_name}}">
You have a persistent Linux workspace named "{{workspace_name}}", running in a proot rootfs. The workspace files area is mounted at `/workspace`: use absolute paths such as `/workspace/notes.md` for all workspace tools, and `{{shell}}` as the shell.
</workspace>
"""

const val DEFAULT_TERMUX_WORKSPACE_PROMPT = """
<workspace name="{{workspace_name}}">
You are running inside Termux on an Android device ({{arch}}). The shell starts in `{{cwd}}`, which is the same directory the file tools call `/workspace`. There is no root.
</workspace>
"""

/**
 * 渲染模板里的 {{name}} 占位符。
 *
 * 纯字符串替换, 不引模板引擎 —— 模板是用户输入, 模板引擎能跑表达式反而是攻击面。
 * 未知占位符原样保留, 由调用方记录日志, 避免"写错了却静默消失"。
 */
fun renderWorkspacePrompt(template: String, facts: Map<String, String>): String =
    facts.entries.fold(template) { text, (key, value) -> text.replace("{{" + key + "}}", value) }
