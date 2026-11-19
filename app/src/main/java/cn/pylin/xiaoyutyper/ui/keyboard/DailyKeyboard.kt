package cn.pylin.xiaoyutyper.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.pylin.xiaoyutyper.R
import cn.pylin.xiaoyutyper.ime.DailyKeyboardController
import cn.pylin.xiaoyutyper.ime.KeyboardLayer
import cn.pylin.xiaoyutyper.ime.PinyinMode

@Composable
fun DailyKeyboard(
    controller: DailyKeyboardController,
    height: Dp,
    floating: Boolean,
) {
    KeyboardScaffold(
        height = height,
        floating = floating,
        head = { KeyboardHead(controller.composing, floating) },
        bar = { DailyBar(controller) },
    ) {
        if (controller.layer == KeyboardLayer.Letters) {
            LetterKeyRows(
                shift = controller.shift,
                onShift = controller::onShift,
                onLetter = controller::onLetter,
                onBackspaceDown = controller::onBackspaceDown,
                onBackspaceReleased = controller::onBackspaceReleased,
            )
        } else {
            SymbolKeyRows(
                rows = dailySymbolRows(controller.mode, controller.moreSymbols),
                moreSymbols = controller.moreSymbols,
                onToggleMore = controller::toggleSymbolPage,
                onSymbol = controller::onSymbol,
                onBackspaceDown = controller::onBackspaceDown,
                onBackspaceReleased = controller::onBackspaceReleased,
            )
        }
        DailyUtilRow(controller)
    }
}

@Composable
private fun DailyBar(controller: DailyKeyboardController) {
    if (controller.composing.isEmpty()) {
        val chinese = controller.mode == PinyinMode.Chinese
        KeyboardStatusBar(
            iconRes = if (chinese) R.drawable.ic_ime_pinyin else R.drawable.ic_ime_english,
            label = stringResource(
                if (chinese) R.string.ime_keyboard_pinyin else R.string.ime_keyboard_english,
            ),
            onHide = controller::hide,
        )
        return
    }

    CandidateRow(
        candidates = controller.candidates,
        hintRes = controller.hintRes,
        onSelect = controller::selectCandidate,
    )
}

@Composable
private fun ColumnScope.DailyUtilRow(controller: DailyKeyboardController) {
    val chinese = controller.mode == PinyinMode.Chinese
    val comma = if (chinese) "，" else ","
    val period = if (chinese) "。" else "."

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SymbolToggleKey(
            layer = controller.layer,
            backLabelRes = R.string.ime_back_to_pinyin,
            onToggle = controller::toggleLayer,
        )
        KeyboardKey(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            kind = KeyboardKeyKind.Function,
            semanticsLabel = stringResource(
                if (chinese) R.string.ime_switch_to_english else R.string.ime_switch_to_chinese,
            ),
            onPress = controller::toggleMode,
        ) { color ->
            KeyIcon(
                iconRes = if (chinese) R.drawable.ic_ime_mode_cn else R.drawable.ic_ime_mode_en,
                color = color,
                size = 26.dp,
            )
        }
        KeyboardKey(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            semanticsLabel = comma,
            onPress = { controller.onSymbol(comma) },
        ) { color ->
            KeyLabel(text = comma, color = color)
        }
        SpaceKey(weight = 3f, onSpace = controller::onSpace)
        KeyboardKey(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            semanticsLabel = period,
            onPress = { controller.onSymbol(period) },
        ) { color ->
            KeyLabel(text = period, color = color)
        }
        KeyboardKey(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight(),
            kind = KeyboardKeyKind.Function,
            semanticsLabel = stringResource(R.string.ime_key_enter),
            onPress = controller::onEnter,
        ) { color ->
            KeyIcon(R.drawable.ic_ime_enter, color)
        }
    }
}
