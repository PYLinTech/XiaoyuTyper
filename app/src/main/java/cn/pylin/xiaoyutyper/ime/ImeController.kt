package cn.pylin.xiaoyutyper.ime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.pylin.xiaoyutyper.pinyin.PinyinRepository
import kotlinx.coroutines.CoroutineScope

class ImeController(
    input: ImeInput,
    repository: PinyinRepository,
    scope: CoroutineScope,
) {
    val daily = DailyKeyboardController(input, repository, scope)
    val secure = SecureKeyboardController(input, scope)

    var screen by mutableStateOf(KeyboardScreen.Daily)
        private set

    private var subtypeEnglish: Boolean? = null

    fun startSession(passwordField: Boolean, englishSubtype: Boolean) {
        daily.resetSession()
        secure.resetSession()
        screen = if (passwordField) KeyboardScreen.Secure else KeyboardScreen.Daily
        if (!passwordField && subtypeEnglish != englishSubtype) {
            daily.applyDefaultMode(englishSubtype)
            subtypeEnglish = englishSubtype
        }
    }

    fun finishSession() {
        daily.close()
        secure.resetSession()
    }
}
