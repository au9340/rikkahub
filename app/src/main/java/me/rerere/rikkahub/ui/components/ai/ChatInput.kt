package me.rerere.rikkahub.ui.components.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.dokar.sonner.ToastType
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.Material3
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.ui.UIMessagePart
import me.rerere.asr.ASRStatus
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.ArrowUp02
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.Fullscreen
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getQuickMessagesOfAssistant
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.QuickMessage
import me.rerere.rikkahub.service.MessageQueueState
import me.rerere.rikkahub.service.QueuedMessage
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionContext
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionItem
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionList
import me.rerere.rikkahub.ui.components.ai.completion.ChatCompletionProvider
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.KeepScreenOn
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionRecordAudio
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalASRState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.utils.SoundEffectPlayer
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds
import me.rerere.rikkahub.ui.pages.chat.VoicePhase
import me.rerere.rikkahub.ui.pages.chat.VoiceSessionState
import kotlin.uuid.Uuid

/** 聊天输入框最多显示的行数：超过之后框内滚动，并出现全屏编辑入口 */
private const val CHAT_INPUT_MAX_LINES = 3

@Composable
fun ChatInput(
    state: ChatInputState,
    loading: Boolean,
    settings: Settings,
    // 会话视角下的助手和模型：会话开始后以会话上固定的配置为准
    assistant: Assistant,
    chatModel: Model?,
    hazeState: HazeState,
    onUpdateSearchMode: (SearchMode) -> Unit,
    modifier: Modifier = Modifier,
    completionProviders: List<ChatCompletionProvider> = emptyList(),
    onUpdateChatModel: (Model) -> Unit,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    onMoreClick: () -> Unit,
    onCancelClick: () -> Unit,
    onSendClick: () -> Unit,
    onLongSendClick: () -> Unit,
    // 空输入时右侧按钮变成加号，点一下直接开自建相册；不传就退回原来的灰色禁用箭头
    onPickImage: (() -> Unit)? = null,
    messageQueue: MessageQueueState = MessageQueueState(),
    onRemoveQueuedMessage: (Uuid) -> Unit = {},
    onBeginEditQueuedMessage: (Uuid) -> QueuedMessage? = { null },
    onFinishEditQueuedMessage: (Uuid, List<UIMessagePart>?) -> Unit = { _, _ -> },
    onResumeMessageQueue: () -> Unit = {},
    onStartVoiceMode: (() -> Unit)? = null,
    voiceState: VoiceSessionState = VoiceSessionState(),
    onStopVoiceMode: () -> Unit = {},
) {
    val toaster = LocalToaster.current
    val hazeTintColor = MaterialTheme.colorScheme.surfaceContainerLow
    val inputHazeStyle = HazeBlurStyle.Material3 {
        blurRadius(12.dp)
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    val themeShape = MaterialTheme.shapes.largeIncreased
    val containerShape = RoundedCornerShape(
        topStart = themeShape.topStart,
        topEnd = themeShape.topEnd,
        bottomEnd = themeShape.bottomEnd,
        bottomStart = themeShape.bottomStart,
    )
    val modelListState = rememberModelListState(
        modelId = chatModel?.id,
        providers = settings.providers,
        type = ModelType.CHAT,
    )

    fun sendMessage() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading && state.isEmpty()) onCancelClick() else onSendClick()
    }

    fun sendMessageWithoutAnswer() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (loading && state.isEmpty()) onCancelClick() else onLongSendClick()
    }

    val asr = LocalASRState.current
    val asrState by asr.state.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val soundEffectPlayer: SoundEffectPlayer = koinInject()
    LaunchedEffect(Unit) {
        soundEffectPlayer.preload(R.raw.asr_start, R.raw.asr_stop)
    }
    val asrPermission = rememberPermissionState(PermissionRecordAudio)
    PermissionManager(permissionState = asrPermission)
    var asrBaseText by remember { mutableStateOf("") }
    LaunchedEffect(asrState.status) {
        when (asrState.status) {
            ASRStatus.Listening -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                soundEffectPlayer.play(R.raw.asr_start)
            }

            ASRStatus.Stopping -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                soundEffectPlayer.play(R.raw.asr_stop)
            }

            else -> {}
        }
    }
    LaunchedEffect(asrState.errorMessage) {
        asrState.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            toaster.show(message = message, type = ToastType.Error)
        }
    }

    // 是否已经有内容：只留文字和发送箭头，其余图标收起来
    val hasContent = !state.isEmpty()
    var showMcpSheet by remember { mutableStateOf(false) }
    val quickMessages = remember(settings.quickMessages, assistant.quickMessageIds) {
        settings.getQuickMessagesOfAssistant(assistant)
    }

    // 底部渐变遮罩：从输入区顶部渐变到输入框底边，再往下铺纯色。
    // 输入框底边以下还有 8dp 内边距和系统导航栏（键盘弹出时导航栏不可见，inset 归 0），
    // 渐变必须在这条线之前就完全不透明，否则滚到那里的消息仍会透出来（导航栏透字）
    val scrimColor = MaterialTheme.colorScheme.background
    val imeInsets = WindowInsets.ime
    val navigationBarsInsets = WindowInsets.navigationBars

    // 键盘是否处于展开状态。这里有两个坑：
    // 1) 不能用输入框焦点：输入法自带的「收起键盘」不会清掉焦点，键盘收了功能栏也回不来
    // 2) 不能等 inset 归零：那要等键盘完全收完（~250ms）功能栏才出现，会有明显延迟。
    //    所以看趋势：一开始往下收就立刻让功能栏回来；再用峰值做门槛，
    //    免得键盘自己换高度（候选栏、表情行）时被误判成收起
    var keyboardExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(imeInsets) {
        var last = imeInsets.getBottom(density)
        var peak = last
        snapshotFlow { imeInsets.getBottom(density) }.collect { height ->
            if (height > peak) peak = height
            val closing = height < last && height < peak * 0.75f
            last = height
            if (height == 0) peak = 0
            keyboardExpanded = height > 0 && !closing
        }
    }

    Surface(
        color = Color.Transparent,
        modifier = Modifier.drawWithCache {
            val bottomGap = navigationBarsInsets.getBottom(this) + 8.dp.toPx()
            val solidStartY = (size.height - imeInsets.getBottom(this) - bottomGap).coerceAtLeast(1f)
            val brush = Brush.verticalGradient(
                colors = listOf(scrimColor.copy(alpha = 0f), scrimColor),
                startY = 0f,
                endY = solidStartY,
            )
            onDrawBehind {
                drawRect(brush)
                // 输入框下方到屏幕底部（键盘弹出时到键盘上沿）之间补一层纯色，彻底盖住底下的消息
                drawRect(
                    color = scrimColor,
                    topLeft = Offset(0f, solidStartY),
                    size = Size(size.width, (size.height - solidStartY).coerceAtLeast(0f)),
                )
            }
        },
    ) {
        Column(
            modifier = modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MessageQueuePanel(
                state = messageQueue,
                onRemove = onRemoveQueuedMessage,
                onBeginEdit = onBeginEditQueuedMessage,
                onFinishEdit = onFinishEditQueuedMessage,
                onResume = onResumeMessageQueue,
            )

            // 功能栏和输入行必须贴在一起：两者之间如果留成透明缝，滚动的消息就会从缝里透出来
            Column {
                // 功能栏：自下往上第二行。键盘弹起时整行收起，只留最底部的输入行。
                // 它自带一层不透明底色（底部渐变在这里还没到全不透明，光靠渐变挡不住消息）
                AnimatedVisibility(
                    visible = !keyboardExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Surface(
                        color = scrimColor,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // 这里留出的 8dp 是功能栏与输入框之间的视觉留白，也铺在不透明底色之内
                        Column(modifier = Modifier.padding(bottom = 8.dp)) {
                            InputFunctionBar(
                                state = state,
                                modelListState = modelListState,
                                settings = settings,
                                assistant = assistant,
                                chatModel = chatModel,
                                onUpdateSearchMode = onUpdateSearchMode,
                                onUpdateSearchService = onUpdateSearchService,
                                onOpenMcp = { showMcpSheet = true },
                                onOpenFiles = onMoreClick,
                            )
                        }
                    }
                }

                // 输入行：永远在最底部
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(containerShape)
                        .then(
                            if (settings.displaySetting.enableBlurEffect) {
                                when (settings.displaySetting.backgroundEffectType) {
                                    BackgroundEffectType.BLUR -> Modifier.hazeBlur(
                                        input = HazeInput.Sources(hazeState),
                                        style = inputHazeStyle,
                                    )
                                    BackgroundEffectType.GLASS -> Modifier.hazeGlass(
                                        input = HazeInput.Sources(hazeState),
                                        style = GlassStyle.Material3(
                                            containerColor = hazeTintColor,
                                            tint = hazeTintColor.copy(alpha = 0.3f),
                                        ) {
                                            // Keep background text from competing with the input text.
                                            optics(GlassDefaults.optics.copy(
                                                blurRadius = OpticalSizeValue.Fixed(16.dp),
                                                depth = OpticalSizeValue.Fixed(0.5f),
                                            ))
                                            shape(containerShape)
                                        },
                                    )
                                }
                            } else Modifier
                        ),
                    shape = containerShape,
                    tonalElevation = 0.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    color = if (settings.displaySetting.enableBlurEffect) Color.Transparent else hazeTintColor,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (voiceState.phase != VoicePhase.Off) {
                            VoiceModeRow(
                                state = voiceState,
                                onStop = onStopVoiceMode,
                                onRetry = { onStartVoiceMode?.invoke() },
                            )
                            androidx.compose.material3.HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        if (state.messageContent.isNotEmpty()) {
                            MediaFileInputRow(state = state)
                        }

                        TextInputRow(
                            state = state,
                            assistant = assistant,
                            completionProviders = completionProviders,
                            onSendMessage = { sendMessage() },
                            // 快捷消息：输入内容之后就收起来（图片/文件的入口都在功能栏里）
                            leadingActions = if (!hasContent && quickMessages.isNotEmpty()) {
                                {
                                    QuickMessageButton(
                                        quickMessages = quickMessages,
                                        state = state,
                                    )
                                }
                            } else null,
                            trailingActions = {
                                // 语音输入：输入内容之后就收起来
                                if (!hasContent && !voiceState.isActive && (asrState.isAvailable || asrState.isRecording)) {
                                    AsrButton(
                                        state = asrState,
                                        onClick = {
                                            when (asrState.status) {
                                                ASRStatus.Listening -> asr.stop()
                                                ASRStatus.Idle, ASRStatus.Error -> {
                                                    if (!asrPermission.allRequiredPermissionsGranted) {
                                                        asrPermission.requestPermissions()
                                                    } else {
                                                        asrBaseText = state.textContent.text.toString()
                                                        asr.start { transcript ->
                                                            val spacer =
                                                                if (asrBaseText.isBlank() || transcript.isBlank()) "" else " "
                                                            state.setMessageText(asrBaseText + spacer + transcript)
                                                        }
                                                    }
                                                }

                                                ASRStatus.Connecting, ASRStatus.Stopping -> {}
                                            }
                                        }
                                    )
                                }

                                if (loading) {
                                    KeepScreenOn()
                                }

                                AnimatedVisibility(
                                    visible = !asrState.isRecording,
                                    enter = fadeIn() + scaleIn(),
                                    exit = fadeOut() + scaleOut(),
                                ) {
                                    SendButton(
                                        loading = loading,
                                        empty = state.isEmpty(),
                                        onClick = { sendMessage() },
                                        onLongClick = { sendMessageWithoutAnswer() },
                                        onAttachClick = onPickImage,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showMcpSheet) {
        McpPickerSheet(
            settings = settings,
            assistant = assistant,
            onUpdateAssistant = onUpdateAssistant,
            onDismiss = { showMcpSheet = false },
        )
    }

    ModelListSheet(
        state = modelListState,
        onSelect = onUpdateChatModel,
    )
}

// 功能栏：自下往上第二行，模型 / 搜索 / MCP / 文件 / 更多
@Composable
private fun InputFunctionBar(
    state: ChatInputState,
    modelListState: ModelListState,
    settings: Settings,
    assistant: Assistant,
    chatModel: Model?,
    onUpdateSearchMode: (SearchMode) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    onOpenMcp: () -> Unit,
    onOpenFiles: () -> Unit,
) {
    val currentModelId = modelListState.currentModel?.modelId
    val context = LocalContext.current
    val filesManager: FilesManager = koinInject()
    var showMore by remember { mutableStateOf(false) }

    // 「更多」里的视频 / 音频：系统选择器按 MIME 过滤，选完直接作为 Video/Audio 附件进输入框
    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val localUris = filesManager.createChatFilesByContents(uris)
        val parts = localUris.mapIndexedNotNull { index, localUri ->
            val mime = filesManager.getFileMimeType(uris.getOrNull(index) ?: localUri)
            when {
                mime?.startsWith("video/") == true -> UIMessagePart.Video(url = localUri.toString())
                mime?.startsWith("audio/") == true -> UIMessagePart.Audio(url = localUri.toString())
                else -> null
            }
        }
        if (parts.isNotEmpty()) {
            state.messageContent = state.messageContent + parts
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 模型：只按厂商图标区分（DeepSeek 是鲸鱼那种），文字固定两个字，和其它项对齐
        InputFunctionChip(
            label = "模型",
            onClick = modelListState::open,
        ) {
            if (currentModelId != null) {
                AutoAIIcon(
                    name = currentModelId,
                    modifier = Modifier.size(20.dp),
                    color = Color.Transparent,
                )
            } else {
                Icon(
                    imageVector = HugeIcons.Brain02,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        SearchPickerButton(
            enableSearch = assistant.enableWebSearch,
            settings = settings,
            onUpdateSearchMode = onUpdateSearchMode,
            onUpdateSearchService = onUpdateSearchService,
            model = chatModel,
            label = "搜索",
        )

        // MCP：启用至少一个就亮蓝底（原来徽标的底色），不显示数量
        InputFunctionChip(
            label = "MCP",
            checked = assistant.mcpServers.isNotEmpty(),
            onClick = onOpenMcp,
        ) {
            Icon(
                imageVector = HugeIcons.McpServer,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }

        // 文件：打开附件/文件面板（工作区、扩展、压缩上下文也在里面）
        InputFunctionChip(
            label = "文件",
            onClick = onOpenFiles,
        ) {
            Icon(
                imageVector = HugeIcons.Files02,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }

        // 更多：视频 / 音频（这些入口之前被从文件面板里拿掉了，这里单独收回来）
        Box {
            InputFunctionChip(
                label = "更多",
                onClick = { showMore = true },
            ) {
                Icon(
                    imageVector = HugeIcons.MoreVertical,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = showMore,
                onDismissRequest = { showMore = false },
            ) {
                DropdownMenuItem(
                    text = { Text("视频") },
                    onClick = {
                        showMore = false
                        mediaPicker.launch(arrayOf("video/*"))
                    },
                )
                DropdownMenuItem(
                    text = { Text("音频") },
                    onClick = {
                        showMore = false
                        mediaPicker.launch(arrayOf("audio/*"))
                    },
                )
            }
        }
    }
}

// 功能栏 chip：视觉上就是「小图标 + 小字」的小胶囊，但整格点击区撑到 48dp 高，
// 手指不用瞄准（两者靠外层 Box 的点击区和内层 Surface 的视觉分离）
@Composable
private fun InputFunctionChip(
    label: String,
    onClick: () -> Unit,
    checked: Boolean = false,
    leading: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (checked) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
            contentColor = if (checked) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    leading()
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun McpPickerSheet(
    settings: Settings,
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.mcp_picker_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            McpPicker(
                assistant = assistant,
                servers = settings.mcpServers,
                onUpdateAssistant = onUpdateAssistant,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

// 右侧按钮三态：生成中且空输入是「停止」，空输入是「加号」（开自建相册），有内容就是「发送」
@Composable
private fun SendButton(
    loading: Boolean,
    empty: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAttachClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val showStop = loading && empty
    // 没回调时退回原来的灰色禁用箭头，不要给出一个点不动的加号
    val showAttach = !showStop && empty && onAttachClick != null
    val containerColor = when {
        showStop -> MaterialTheme.colorScheme.errorContainer
        empty -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when {
        showStop -> MaterialTheme.colorScheme.onErrorContainer
        showAttach -> MaterialTheme.colorScheme.onSurface
        empty -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onPrimary
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(30.dp)
            .testTag("chat_send_button")
            .clip(CircleShape)
            .combinedClickable(
                enabled = showStop || showAttach || !empty,
                onClick = { if (showAttach) onAttachClick?.invoke() else onClick() },
                onLongClick = onLongClick,
            )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = containerColor,
            content = {},
        )
        Icon(
            imageVector = when {
                showStop -> HugeIcons.Cancel01
                showAttach -> HugeIcons.Add01
                else -> HugeIcons.ArrowUp02
            },
            contentDescription = stringResource(
                when {
                    showStop -> R.string.stop
                    showAttach -> R.string.photo
                    else -> R.string.send
                }
            ),
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TextInputRow(
    state: ChatInputState,
    assistant: Assistant,
    completionProviders: List<ChatCompletionProvider>,
    onSendMessage: () -> Unit,
    trailingActions: @Composable () -> Unit,
    leadingActions: (@Composable () -> Unit)? = null,
) {
    val settings = LocalSettings.current
    val filesManager: FilesManager = koinInject()
    val quickMessages = remember(settings.quickMessages, assistant.quickMessageIds) {
        settings.getQuickMessagesOfAssistant(assistant)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (state.isEditing()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.editing))
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = HugeIcons.Cancel01,
                        contentDescription = stringResource(R.string.cancel_edit),
                        modifier = Modifier.clickable { state.clearInput() }
                    )
                }
            }
        }

        var isFocused by remember { mutableStateOf(false) }
        var isFullScreen by remember { mutableStateOf(false) }
        var completionList by remember { mutableStateOf<ChatCompletionList?>(null) }

        // 输入框实际宽度，用来判断文本是否已经超过 CHAT_INPUT_MAX_LINES 行
        var inputWidthPx by remember { mutableStateOf(0) }
        val textMeasurer = rememberTextMeasurer()
        val inputText = state.textContent.text
        val inputTextStyle = LocalTextStyle.current
        // 估算文本可用宽度：扣掉 M3 输入框左右内边距，以及左侧快捷消息按钮的占位
        val textAreaWidthPx = with(LocalDensity.current) {
            inputWidthPx - 32.dp.roundToPx() - if (quickMessages.isNotEmpty()) 44.dp.roundToPx() else 0
        }
        val inputLineCount = if (inputText.isEmpty() || textAreaWidthPx <= 0) {
            1
        } else {
            textMeasurer.measure(
                text = AnnotatedString(inputText.toString()),
                style = inputTextStyle,
                constraints = Constraints(maxWidth = textAreaWidthPx),
            ).lineCount
        }
        // 达到 CHAT_INPUT_MAX_LINES 行就给全屏编辑入口（跟豆包一样，到三行才出现放大框）
        val showFullScreenButton = inputLineCount >= CHAT_INPUT_MAX_LINES
        val receiveContentListener = remember(
            settings.displaySetting.pasteLongTextAsFile, settings.displaySetting.pasteLongTextThreshold
        ) {
            ReceiveContentListener { transferableContent ->
                when {
                    transferableContent.hasMediaType(MediaType.Image) -> {
                        transferableContent.consume { item ->
                            val uri = item.uri
                            if (uri != null) {
                                state.addImages(
                                    filesManager.createChatFilesByContents(
                                        listOf(uri)
                                    )
                                )
                            }
                            uri != null
                        }
                    }

                    settings.displaySetting.pasteLongTextAsFile && transferableContent.hasMediaType(MediaType.Text) -> {
                        transferableContent.consume { item ->
                            val text = item.text?.toString()
                            if (text != null && text.length > settings.displaySetting.pasteLongTextThreshold) {
                                val document = filesManager.createChatTextFile(text)
                                state.addFiles(listOf(document))
                                true
                            } else {
                                false
                            }
                        }
                    }

                    else -> transferableContent
                }
            }
        }

        LaunchedEffect(completionProviders, isFocused) {
            if (!isFocused || completionProviders.isEmpty()) {
                completionList = null
                return@LaunchedEffect
            }

            snapshotFlow {
                ChatCompletionContext(
                    text = state.textContent.text.toString(),
                    selection = state.textContent.selection,
                )
            }.collectLatest { context ->
                val lists = completionProviders.mapNotNull { provider ->
                    try {
                        provider.complete(context)
                            ?.takeIf { it.items.isNotEmpty() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
                val primary = lists.firstOrNull()
                completionList = primary?.let { list ->
                    val mergedItems = lists
                        .filter { it.replacementRange == list.replacementRange }
                        .flatMap { it.items }
                        .distinctBy { it.label to it.insertText }
                        .sortedWith(
                            compareByDescending<ChatCompletionItem> { it.sortScore }
                                .thenBy { it.label.length }
                                .thenBy { it.label.lowercase() }
                        )
                        .take(8)
                    list.copy(items = mergedItems)
                }
            }
        }

        completionList?.takeIf { it.items.isNotEmpty() }?.let { list ->
            CompletionPopup(
                completionList = list,
                onItemClick = { item ->
                    state.applyCompletion(list.replacementRange, item)
                    completionList = null
                },
            )
        }

        TextField(
            state = state.textContent,
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { inputWidthPx = it.width }
                .testTag("chat_input")
                .contentReceiver(receiveContentListener)
                .onFocusChanged {
                    isFocused = it.isFocused
                },
            shape = MaterialTheme.shapes.largeIncreased,
            placeholder = {
                Text(stringResource(R.string.chat_input_placeholder))
            },
            // 输入框最高 CHAT_INPUT_MAX_LINES 行，超过后内部滚动（写长文用右上角全屏编辑）
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = CHAT_INPUT_MAX_LINES),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = if (settings.displaySetting.sendOnEnter) ImeAction.Send else ImeAction.Default
            ),
            onKeyboardAction = {
                if (settings.displaySetting.sendOnEnter && !state.isEmpty()) {
                    onSendMessage()
                }
            },
            colors = TextFieldDefaults.colors().copy(
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            // 左侧：快捷消息；右侧：放大在上、发送在下（跟豆包一样竖排，不挤占文字宽度）
            leadingIcon = leadingActions,
            trailingIcon = {
                Column(horizontalAlignment = Alignment.End) {
                    if (showFullScreenButton) {
                        IconButton(
                            onClick = {
                                isFullScreen = !isFullScreen
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = HugeIcons.Fullscreen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        trailingActions()
                    }
                }
            },
        )
        if (isFullScreen) {
            FullScreenEditor(state = state) {
                isFullScreen = false
            }
        }
    }
}

@Composable
private fun CompletionPopup(
    completionList: ChatCompletionList,
    onItemClick: (ChatCompletionItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            items(
                items = completionList.items,
                key = { item -> "${item.label}:${item.insertText}" },
            ) { item ->
                Surface(
                    onClick = { onItemClick(item) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item.icon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.detail?.let { detail ->
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ChatInputState.applyCompletion(
    replacementRange: TextRange,
    item: ChatCompletionItem,
) {
    val textLength = textContent.text.length
    val start = replacementRange.min.coerceIn(0, textLength)
    val end = replacementRange.max.coerceIn(start, textLength)
    textContent.edit {
        replace(start, end, item.insertText)
        selection = TextRange(start + item.insertText.length)
    }
}

@Composable
private fun QuickMessageButton(
    quickMessages: List<QuickMessage>,
    state: ChatInputState,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = {
            expanded = !expanded
        }) {
        Icon(HugeIcons.Zap, null)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 200.dp, max = 360.dp)
        ) {
            quickMessages.forEach { quickMessage ->
                Surface(
                    onClick = {
                        state.appendText(quickMessage.content)
                        expanded = false
                    },
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = quickMessage.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = quickMessage.content,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullScreenEditor(
    state: ChatInputState, onDone: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = {
            onDone()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding(),
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row {
                        TextButton(
                            onClick = {
                                onDone()
                            }) {
                            Text(stringResource(R.string.chat_page_save))
                        }
                    }
                    TextField(
                        state = state.textContent,
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .fillMaxSize(),
                        shape = RoundedCornerShape(32.dp),
                        placeholder = {
                            Text(stringResource(R.string.chat_input_placeholder))
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        colors = TextFieldDefaults.colors().copy(
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}
