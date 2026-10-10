package me.rerere.rikkahub.ui.pages.photo

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import me.rerere.rikkahub.data.media.PhotoItem
import me.rerere.rikkahub.data.media.PhotoRepository
import me.rerere.rikkahub.ui.components.ui.permission.PermissionManager
import me.rerere.rikkahub.ui.components.ui.permission.PermissionMediaImages
import me.rerere.rikkahub.ui.components.ui.permission.rememberPermissionState

/** 一次从 MediaStore 拉多少张 */
private const val PHOTO_PAGE_SIZE = 60

/** 距离列表底部还剩多少张时提前加载下一页 */
private const val PHOTO_LOAD_AHEAD = 16

/**
 * 自建相册选择器：直接读手机相册，一行 4 张，从上到下按时间倒序。
 *
 * 交互：点缩略图 = 选中 / 取消，选中的图右上角带 ①②③ 顺序徽标（跟豆包一致）；
 * 点「完成」把选中的 uri 按选择顺序交回去。顶栏故意只留这两个东西，
 * 不做相册分桶切换、不做预览条。
 *
 * 没界面层的 ViewModel：状态就几项，直接用 remember 管，省掉一层 Koin 注册。
 */
@Composable
internal fun PhotoPickerPage(
    onDismiss: () -> Unit,
    onConfirm: (List<Uri>) -> Unit,
) {
    val context = LocalContext.current
    val permission = rememberPermissionState(PermissionMediaImages)
    // 未授权时 PermissionManager 会弹 rationale，用户授权后下面会自动加载
    PermissionManager(permissionState = permission)

    val repository = remember(context) { PhotoRepository(context) }
    var photos by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    // 用 List 而不是 Set：选的顺序就是 ①②③ 的编号顺序
    var selected by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var reachedEnd by remember { mutableStateOf(false) }

    val granted = permission.allRequiredPermissionsGranted

    // 首屏
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        loading = true
        reachedEnd = false
        val first = repository.loadPhotos(offset = 0, limit = PHOTO_PAGE_SIZE)
        photos = first
        reachedEnd = first.size < PHOTO_PAGE_SIZE
        loading = false
    }

    val gridState = rememberLazyGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            !loading && !reachedEnd && photos.isNotEmpty() &&
                last >= photos.size - PHOTO_LOAD_AHEAD
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (!shouldLoadMore) return@LaunchedEffect
        loading = true
        val more = repository.loadPhotos(offset = photos.size, limit = PHOTO_PAGE_SIZE)
        // DateAdded 相同时分页可能重复返回同一张，按 id 去个重
        val known = photos.mapTo(mutableSetOf()) { it.id }
        photos = photos + more.filterNot { it.id in known }
        reachedEnd = more.size < PHOTO_PAGE_SIZE
        loading = false
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "全部照片",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { onConfirm(selected.map { it.uri }) },
                        enabled = selected.isNotEmpty(),
                    ) {
                        Text(
                            text = if (selected.isEmpty()) "完成" else "完成(${selected.size})",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }

                when {
                    !granted -> PermissionHint(onRequest = { permission.requestPermissions() })

                    photos.isEmpty() && !loading -> CenteredText("相册里还没有照片")

                    else -> LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items = photos, key = { it.id }) { photo ->
                            val order = selected.indexOfFirst { it.id == photo.id }
                            PhotoCell(
                                photo = photo,
                                order = if (order >= 0) order + 1 else null,
                                onClick = {
                                    selected = if (order >= 0) {
                                        // 取消中间一张时，后面的编号自然往前补
                                        selected.filterNot { it.id == photo.id }
                                    } else {
                                        selected + photo
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCell(
    photo: PhotoItem,
    order: Int?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(1.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (order != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = order.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun PermissionHint(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "需要相册权限才能浏览照片",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRequest) {
            Text("授权")
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
