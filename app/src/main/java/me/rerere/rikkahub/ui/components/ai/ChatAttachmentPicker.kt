package me.rerere.rikkahub.ui.components.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.FileProvider
import com.dokar.sonner.ToastType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionCamera
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.rikkahub.ui.pages.photo.PhotoPickerPage
import me.rerere.rikkahub.utils.isAllowedFileType
import me.rerere.ui.sketch.SketchDialog
import me.rerere.ui.sketch.SketchResult
import org.koin.compose.koinInject
import java.io.File
import kotlin.uuid.Uuid

/**
 * 输入框附件入口。
 *
 * 相比上游做了三处收敛：
 * 1. 相册不再用系统的 GetMultipleContents，改成自建相册页（PhotoPickerPage）
 * 2. 去掉视频 / 音频入口
 * 3. 去掉强制裁剪：拍完 / 选完直接进输入框，想改再点缩略图（走画板）
 */
internal data class ChatAttachmentPickerActions(
    val onTakePicture: () -> Unit,
    val onPickImage: () -> Unit,
    val onPickFile: () -> Unit,
    val onSketch: () -> Unit,
)

@Composable
internal fun rememberChatAttachmentPickerActions(
    inputState: ChatInputState,
    setting: Settings,
    onAttachmentAdded: () -> Unit,
): ChatAttachmentPickerActions {
    val context = LocalContext.current
    val resources = LocalResources.current
    val toaster = LocalToaster.current
    val filesManager: FilesManager = koinInject()
    val cameraPermission = rememberPermissionState(PermissionCamera)
    PermissionManager(permissionState = cameraPermission)

    // 自建相册：加号直接打开它，选完把 uri 按选择顺序交给文件管理落库
    var showPhotoPicker by remember { mutableStateOf(false) }
    if (showPhotoPicker) {
        PhotoPickerPage(
            onDismiss = { showPhotoPicker = false },
            onConfirm = { uris ->
                showPhotoPicker = false
                if (uris.isNotEmpty()) {
                    inputState.addImages(filesManager.createChatFilesByContents(uris))
                    onAttachmentAdded()
                }
            },
        )
    }

    var cameraOutputUri by remember { mutableStateOf<Uri?>(null) }
    var cameraOutputFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captureSuccessful ->
            val uri = cameraOutputUri
            if (captureSuccessful && uri != null) {
                // createChatFilesByContents 会把内容拷贝进应用目录，拷完就能把临时文件删了
                inputState.addImages(filesManager.createChatFilesByContents(listOf(uri)))
                onAttachmentAdded()
            }
            cameraOutputFile?.delete()
            cameraOutputFile = null
            cameraOutputUri = null
        }
    val onTakePicture: () -> Unit = {
        if (cameraPermission.allRequiredPermissionsGranted) {
            cameraOutputFile = context.cacheDir.resolve("camera_${Uuid.random()}.jpg")
            cameraOutputUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", cameraOutputFile!!
            )
            cameraLauncher.launch(cameraOutputUri!!)
        } else {
            cameraPermission.requestPermissions()
        }
    }

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                val documents = uris.mapNotNull { uri ->
                    val fileName = filesManager.getFileNameFromUri(uri) ?: "file"
                    val mime = filesManager.getFileMimeType(uri) ?: "text/plain"
                    if (isAllowedFileType(fileName, mime)) {
                        val localUri = filesManager.createChatFilesByContents(listOf(uri)).firstOrNull()
                            ?: run {
                                toaster.show(
                                    resources.getString(R.string.chat_input_file_read_failed, fileName),
                                    type = ToastType.Error
                                )
                                return@mapNotNull null
                            }
                        UIMessagePart.Document(url = localUri.toString(), fileName = fileName, mime = mime)
                    } else {
                        toaster.show(
                            resources.getString(R.string.chat_input_unsupported_file_type, fileName),
                            type = ToastType.Error
                        )
                        null
                    }
                }
                if (documents.isNotEmpty()) {
                    inputState.addFiles(documents)
                    onAttachmentAdded()
                }
            }
        }

    val scope = rememberCoroutineScope()
    var showSketch by remember { mutableStateOf(false) }
    if (showSketch) {
        SketchDialog(
            onDismiss = { showSketch = false },
            onConfirm = { result ->
                showSketch = false
                scope.launch {
                    inputState.addImages(listOfNotNull(filesManager.createChatFileBySketch(result)))
                    onAttachmentAdded()
                }
            },
        )
    }

    return ChatAttachmentPickerActions(
        onTakePicture = onTakePicture,
        onPickImage = { showPhotoPicker = true },
        onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
        onSketch = { showSketch = true },
    )
}

/** 把画板的结果存成聊天附件。 */
internal suspend fun FilesManager.createChatFileBySketch(result: SketchResult): Uri? = withContext(Dispatchers.IO) {
    createChatFilesByByteArrays(
        byteArrays = listOf(result.encode()),
        displayName = "sketch.${result.extension}",
        mimeType = result.mimeType,
    ).firstOrNull()
}
