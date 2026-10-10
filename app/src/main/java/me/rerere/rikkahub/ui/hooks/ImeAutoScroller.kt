package me.rerere.rikkahub.ui.hooks

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity

/**
 * 键盘弹出时让消息列表跟随上移，保证最新消息始终露在输入框上方。
 *
 * 只有列表本来就停在底部（用户正在看最新消息）时才跟随；如果用户已经往上翻看历史消息，
 * 键盘弹出不应该把正在看的内容顶走，此时保持原地不动。
 */
@Composable
fun ImeLazyListAutoScroller(
    lazyListState: LazyListState,
) {
    val ime = WindowInsets.ime
    val localDensity = LocalDensity.current
    var imeHeight by remember(lazyListState) { mutableIntStateOf(0) }
    // 键盘收起期间持续跟踪列表是否停在底部；键盘弹出期间冻结该状态，
    // 因为列表被顶起后 canScrollForward 会一直为 true，用它即时判断会失效。
    var isAtBottom by remember(lazyListState) { mutableStateOf(true) }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            ime.getBottom(localDensity) to lazyListState.canScrollForward
        }.collect { (keyboardHeight, canScrollForward) ->
            if (keyboardHeight == 0) {
                // 键盘收起：记录此刻是否贴底，作为下次弹出时的判断依据
                isAtBottom = !canScrollForward
            } else {
                // 键盘弹出：只有弹起前就贴着底部才跟随滚动
                val delta = keyboardHeight - imeHeight
                if (isAtBottom && delta > 0) {
                    lazyListState.scrollBy(delta.toFloat())
                }
            }
            imeHeight = keyboardHeight
        }
    }
}
