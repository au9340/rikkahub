package me.rerere.rikkahub.ui.components.ui.permission

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R

/**
 * 权限信息数据类
 * @param permission Android权限字符串 (如 android.permission.CAMERA)
 * @param usage 权限使用说明的Composable内容
 * @param required 是否为必需权限
 */
data class PermissionInfo(
    val permission: String,
    val displayName: @Composable () -> Unit,
    val usage: @Composable () -> Unit,
    val required: Boolean = false
)

/**
 * 权限状态枚举
 */
enum class PermissionStatus {
    /** 未请求 */
    NotRequested,
    /** 已授权 */
    Granted,
    /** 被拒绝但可以再次请求 */
    Denied,
    /** 被拒绝且用户选择"不再询问" */
    DeniedPermanently
}

/**
 * 权限请求结果
 */
data class PermissionResult(
    val permission: String,
    val status: PermissionStatus,
    val isGranted: Boolean = status == PermissionStatus.Granted
)

/**
 * 多个权限的请求结果
 */
data class MultiplePermissionResult(
    val results: Map<String, PermissionResult>,
    val allGranted: Boolean = results.values.all { it.isGranted },
    val allRequiredGranted: Boolean
)

val PermissionCamera = PermissionInfo(
    permission = Manifest.permission.CAMERA,
    displayName = { Text(stringResource(R.string.permission_camera)) },
    usage = { Text(stringResource(R.string.permission_camera_desc)) },
    required = true
)

val PermissionRecordAudio = PermissionInfo(
    permission = Manifest.permission.RECORD_AUDIO,
    displayName = { Text(stringResource(R.string.permission_microphone)) },
    usage = { Text(stringResource(R.string.permission_microphone_desc)) },
    required = true
)

/**
 * 自建相册读图权限。
 *
 * 这里刻意用 READ_EXTERNAL_STORAGE 而不是 READ_MEDIA_IMAGES：应用 targetSdk = 28，
 * 在 Android 13/14 上走的是 legacy 分支，这一个权限就能拿到媒体库的全量读权限，
 * 用户也不会被问「允许全部 / 选择照片」，因此 READ_MEDIA_VISUAL_USER_SELECTED 那套
 * 部分授权的复杂度整个不需要。
 *
 * 文案暂时写中文字面量，跟 ChatInput 里「模型 / 搜索 / MCP / 文件」的做法一致，
 * 之后交给 locale-tui 统一补翻译。
 */
@Suppress("DEPRECATION")
val PermissionMediaImages = PermissionInfo(
    permission = Manifest.permission.READ_EXTERNAL_STORAGE,
    displayName = { Text("相册") },
    usage = { Text("需要读取相册的权限，才能在这个页面里浏览并选择照片") },
    required = true
)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
val PermissionNotification = PermissionInfo(
    permission = Manifest.permission.POST_NOTIFICATIONS,
    displayName = { Text(stringResource(R.string.permission_notification)) },
    usage = { Text(stringResource(R.string.permission_notification_desc)) },
    required = true
)

@RequiresApi(37)
val PermissionLocalNetwork = PermissionInfo(
    permission = Manifest.permission.ACCESS_LOCAL_NETWORK,
    displayName = { Text(stringResource(R.string.permission_local_network)) },
    usage = { Text(stringResource(R.string.permission_local_network_desc)) },
    required = true
)
