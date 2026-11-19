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
import androidx.compose.ui.unit.sp
import cn.pylin.xiaoyutyper.R
import cn.pylin.xiaoyutyper.ime.KeyboardLayer
import cn.pylin.xiaoyutyper.ime.SecureKeyboardController

@Composable
fun SecureKeyboard(
    controller: SecureKeyboardController,
    height: Dp,
    floating: Boolean,
) {
    KeyboardScaffold(
        height = height,
        floating = floating,
        head = { KeyboardHead("", floating) },
        bar = { SecureBar(controller) },
    ) {
        if (controller.layer == KeyboardLayer.Letters) {
            LetterKeyRows(
                shift = controller.shift,
                onShift = controller::onShift,
                onLetter = controller::onChar,
                onBackspaceDown = controller::onBackspaceDown,
                onBackspaceReleased = controller::onBackspaceReleased,
            )
        } else {
            SymbolKeyRows(
                rows = secureSymbolRows(controller.moreSymbols),
                moreSymbols = controller.moreSymbols,
                onToggleMore = controller::toggleSymbolPage,
                onSymbol = controller::onChar,
                onBackspaceDown = controller::onBackspaceDown,
                onBackspaceReleased = controller::onBackspaceReleased,
            )
        }
        SecureUtilRow(controller)
    }
}

@Composable
private fun SecureBar(controller: SecureKeyboardController) {
    KeyboardStatusBar(
        iconRes = R.drawable.ic_ime_shield,
        label = stringResource(R.string.ime_keyboard_secure),
        onHide = controller::hide,
    )
}

@Composable
private fun ColumnScope.SecureUtilRow(controller: SecureKeyboardController) {
    val nextLabel = stringResource(controller.nextLabelRes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        SymbolToggleKey(
            layer = controller.layer,
            backLabelRes = R.string.ime_back_to_english,
            onToggle = controller::toggleLayer,
        )
        SpaceKey(weight = 6f, onSpace = controller::onSpace)
        KeyboardKey(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight(),
            kind = KeyboardKeyKind.Function,
            semanticsLabel = nextLabel,
            onPress = controller::onNext,
        ) { color ->
            KeyLabel(text = nextLabel, color = color, fontSize = 12.sp)
        }
    }
}
