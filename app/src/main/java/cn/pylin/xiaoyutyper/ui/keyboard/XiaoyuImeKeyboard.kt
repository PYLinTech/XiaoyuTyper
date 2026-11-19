package cn.pylin.xiaoyutyper.ui.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cn.pylin.xiaoyutyper.ime.ImeController
import cn.pylin.xiaoyutyper.ime.KeyboardScreen
import kotlin.math.roundToInt

internal class FloatingKeyboardState {
    val bounds = Rect()
}

@Composable
internal fun XiaoyuImeKeyboard(controller: ImeController, floatingState: FloatingKeyboardState) {
    XiaoyuTheme {
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            FloatingKeyboard(controller, floatingState)
        } else {
            val density = LocalDensity.current
            val screenHeight =
                (LocalResources.current.displayMetrics.heightPixels / density.density).dp
            val contentHeight = maxOf(315.dp, screenHeight * 0.36f)
            KeyboardContent(
                controller = controller,
                height = contentHeight + imeBottomBarHeight(),
                floating = false,
            )
        }
    }
}

@Composable
private fun FloatingKeyboard(controller: ImeController, state: FloatingKeyboardState) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val context = LocalContext.current
        val prefs = remember(context) {
            context.getSharedPreferences("floating_keyboard", Context.MODE_PRIVATE)
        }
        val navigationBarBottom =
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomInset = maxOf(
            navigationBarBottom,
            KeyboardLayout.WIDE_MIN_NAVIGATION_INSET_DP.dp,
        )
        val maxPanelHeight = minOf(
            KeyboardLayout.WIDE_MAX_HEIGHT_DP.dp,
            maxHeight * KeyboardLayout.WIDE_MAX_HEIGHT_RATIO,
        )
        val panelWidth = minOf(
            maxWidth,
            KeyboardLayout.WIDE_MAX_WIDTH_DP.dp,
            maxPanelHeight * KeyboardLayout.WIDE_ASPECT_RATIO,
        )
        val panelHeight = panelWidth / KeyboardLayout.WIDE_ASPECT_RATIO
        val panelWidthPx = with(density) { panelWidth.toPx() }
        val panelHeightPx = with(density) { panelHeight.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val marginPx = with(density) { KeyboardLayout.WIDE_DRAG_MARGIN_DP.dp.toPx() }
        val bottomGapPx = with(density) {
            (bottomInset + KeyboardLayout.WIDE_BOTTOM_MARGIN_DP.dp).toPx()
        }
        val maxLeft = (widthPx - panelWidthPx - marginPx).coerceAtLeast(marginPx)
        val maxTop = (heightPx - bottomGapPx - panelHeightPx).coerceAtLeast(marginPx)
        var offset by remember(widthPx, heightPx) {
            val savedX = if (prefs.contains("left")) prefs.getInt("left", 0) else null
            val savedY = if (prefs.contains("top")) prefs.getInt("top", 0) else null
            mutableStateOf(
                Offset(
                    savedX?.toFloat()?.coerceIn(marginPx, maxLeft)
                        ?: (widthPx - panelWidthPx) / 2f,
                    savedY?.toFloat()?.coerceIn(marginPx, maxTop)
                        ?: maxTop,
                ),
            )
        }
        val savePosition = {
            prefs.edit()
                .putInt("left", offset.x.roundToInt())
                .putInt("top", offset.y.roundToInt())
                .apply()
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .onGloballyPositioned { coordinates ->
                    val rect = coordinates.boundsInWindow()
                    state.bounds.set(
                        rect.left.roundToInt(),
                        rect.top.roundToInt(),
                        rect.right.roundToInt(),
                        rect.bottom.roundToInt(),
                    )
                }
                .width(panelWidth)
                .height(panelHeight),
        ) {
            KeyboardContent(controller, panelHeight, floating = true)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(KeyboardLayout.WIDE_GRIP_WIDTH_DP.dp)
                    .height(KeyboardLayout.WIDE_HEAD_HEIGHT_DP.dp)
                    .pointerInput(marginPx, maxLeft, maxTop) {
                        detectDragGestures(
                            onDragEnd = { savePosition() },
                            onDragCancel = { savePosition() },
                        ) { change, dragAmount ->
                            change.consume()
                            offset = Offset(
                                (offset.x + dragAmount.x).coerceIn(marginPx, maxLeft),
                                (offset.y + dragAmount.y).coerceIn(marginPx, maxTop),
                            )
                        }
                    },
            )
        }
    }
}

@Composable
private fun KeyboardContent(
    controller: ImeController,
    height: Dp,
    floating: Boolean,
) {
    when (controller.screen) {
        KeyboardScreen.Daily -> DailyKeyboard(controller.daily, height, floating)
        KeyboardScreen.Secure -> SecureKeyboard(controller.secure, height, floating)
    }
}
