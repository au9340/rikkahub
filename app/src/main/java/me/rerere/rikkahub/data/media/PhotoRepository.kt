package me.rerere.rikkahub.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 相册里的一张照片 */
data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val dateAdded: Long,
)

/**
 * 直接读 MediaStore 的图片集合，给自建相册页用。
 *
 * 两个刻意的选择：
 * 1. 分页用 Bundle 形式的 queryArgs，而不是把 "LIMIT 60 OFFSET 60" 拼进 sortOrder。
 *    Android 11 起 contentProvider 会过滤 sortOrder 里的这类关键字，拼字符串会直接抛异常。
 * 2. 查询整包在 runCatching 里：没权限 / 相册被清空 / 查询参数不被支持时返回空列表，
 *    而不是把异常丢到 UI 上。
 *
 * 这里不走 Paging3：现在每个格子都是固定尺寸的缩略图，加载攒到滚动快接近底部就行，
 * 少了 PagingSource 那套样板。之后要换成 Paging3 也不会影响调用方。
 */
class PhotoRepository(private val context: Context) {

    suspend fun loadPhotos(offset: Int, limit: Int): List<PhotoItem> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val queryArgs = Bundle().apply {
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.Images.Media.DATE_ADDED),
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING,
            )
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }

        val photos = mutableListOf<PhotoItem>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                queryArgs,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    photos += PhotoItem(
                        id = id,
                        uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id,
                        ),
                        dateAdded = cursor.getLong(dateColumn),
                    )
                }
            }
        }
        photos
    }
}
