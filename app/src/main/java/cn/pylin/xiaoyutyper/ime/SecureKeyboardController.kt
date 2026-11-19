package cn.pylin.xiaoyutyper.ime

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import cn.pylin.xiaoyutyper.R
import kotlinx.coroutines.CoroutineScope

class SecureKeyboardController(
    input: ImeInput,
    scope: CoroutineScope,
) : KeyboardController(input, scope) {
    @get:StringRes
    var nextLabelRes by mutableIntStateOf(R.string.ime_secure_done)
        private set

    fun onChar(char: String) {
        val shifted = layer == KeyboardLayer.Letters && shift != ShiftMode.Off
        input.commitText(if (shifted) char.uppercase() else char)
        if (shifted) consumeShiftOnce()
    }

    fun onSpace() {
        input.commitText(" ")
    }

    fun onNext() {
        if (!input.performEditorAction()) input.hideKeyboard()
    }

    fun resetSession() {
        stopBackspace()
        resetModifiers()
        nextLabelRes = if (input.isNextAction()) R.string.ime_secure_next else R.string.ime_secure_done
    }

    protected override fun deleteBackward() {
        input.deleteBackward()
    }
}
