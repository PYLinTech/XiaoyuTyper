package cn.pylin.xiaoyutyper.ime

enum class PinyinMode { Chinese, English }

enum class KeyboardLayer { Letters, Symbols }

enum class ShiftMode { Off, Once, Locked }

enum class KeyboardScreen { Daily, Secure }

interface ImeInput {
    fun commitText(text: String)

    fun deleteBackward()

    fun isMultiline(): Boolean

    fun performEditorAction(): Boolean

    fun isNextAction(): Boolean

    fun hideKeyboard()
}
