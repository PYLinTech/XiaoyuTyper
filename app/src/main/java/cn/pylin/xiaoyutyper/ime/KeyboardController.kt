package cn.pylin.xiaoyutyper.ime

import kotlinx.coroutines.CoroutineScope

abstract class KeyboardController(
    protected val input: ImeInput,
    scope: CoroutineScope,
) {
    private val modifiers = KeyboardModifiers()
    private val backspaceRepeater = BackspaceRepeater(scope) { deleteBackward() }

    val layer: KeyboardLayer
        get() = modifiers.layer
    val moreSymbols: Boolean
        get() = modifiers.moreSymbols
    val shift: ShiftMode
        get() = modifiers.shift

    fun onBackspaceDown() {
        backspaceRepeater.start()
    }

    fun onBackspaceReleased() {
        backspaceRepeater.stop()
    }

    fun onShift() {
        modifiers.onShift()
    }

    fun toggleLayer() {
        modifiers.toggleLayer()
    }

    fun toggleSymbolPage() {
        modifiers.toggleSymbolPage()
    }

    protected fun consumeShiftOnce() {
        modifiers.consumeShiftOnce()
    }

    protected fun clearSymbolsAndShift() {
        modifiers.clearSymbolsAndShift()
    }

    protected fun resetModifiers() {
        modifiers.reset()
    }

    protected fun stopBackspace() {
        backspaceRepeater.stop()
    }

    open fun hide() {
        stopBackspace()
        input.hideKeyboard()
    }

    protected abstract fun deleteBackward()
}
