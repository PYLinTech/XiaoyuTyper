package cn.pylin.xiaoyutyper.ime

import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.pylin.xiaoyutyper.R
import cn.pylin.xiaoyutyper.pinyin.DictionaryState
import cn.pylin.xiaoyutyper.pinyin.PinyinCandidate
import cn.pylin.xiaoyutyper.pinyin.PinyinRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class DailyKeyboardController(
    input: ImeInput,
    private val repository: PinyinRepository,
    private val scope: CoroutineScope,
) : KeyboardController(input, scope) {
    var mode by mutableStateOf(PinyinMode.Chinese)
        private set

    var composing by mutableStateOf("")
        private set
    var candidates by mutableStateOf<List<PinyinCandidate>>(emptyList())
        private set
    private var candidatesComposition: String? = null
    private var dictionaryState by mutableStateOf(DictionaryState.Loading)

    private var querySequence = 0
    private var pendingSpace = false
    private var queryActive by mutableStateOf(false)
    private var queryJob: Job? = null

    @get:StringRes
    val hintRes: Int?
        get() = when {
            dictionaryState == DictionaryState.Loading -> R.string.ime_hint_dictionary_loading
            dictionaryState == DictionaryState.Error -> R.string.ime_hint_dictionary_unavailable
            queryActive -> null
            composing.isNotEmpty() && candidates.isEmpty() -> R.string.ime_hint_no_candidates
            else -> null
        }

    init {
        scope.launch {
            repository.state.collect { dictionaryState = it }
        }
    }

    fun onLetter(letter: String) {
        if (mode == PinyinMode.Chinese) {
            composing += letter
            refreshCandidates()
            return
        }
        input.commitText(if (shift != ShiftMode.Off) letter.uppercase() else letter)
        consumeShiftOnce()
    }

    fun onSymbol(symbol: String) {
        if (composing.isNotEmpty()) {
            input.commitText(activeFirstCandidate()?.word ?: composing)
            clearComposition()
        }
        input.commitText(symbol)
    }

    fun onSpace() {
        if (mode != PinyinMode.Chinese) {
            input.commitText(" ")
            return
        }
        when {
            composing.isEmpty() -> input.commitText(" ")
            queryActive -> pendingSpace = true
            else -> {
                val first = activeFirstCandidate()
                if (first != null) commitCandidate(first) else commitRaw()
            }
        }
    }

    fun onEnter() {
        if (composing.isNotEmpty()) {
            commitRaw()
            return
        }
        if (input.isMultiline()) {
            input.commitText("\n")
            return
        }
        if (!input.performEditorAction()) input.hideKeyboard()
    }

    fun toggleMode() {
        if (composing.isNotEmpty()) commitRaw()
        mode = if (mode == PinyinMode.Chinese) PinyinMode.English else PinyinMode.Chinese
        clearSymbolsAndShift()
    }

    fun selectCandidate(candidate: PinyinCandidate) {
        if (queryActive || candidatesComposition != composing) return
        val current = candidates.firstOrNull { it.word == candidate.word } ?: return
        commitCandidate(current)
    }

    override fun hide() {
        close()
        input.hideKeyboard()
    }

    fun resetSession() {
        close()
        resetModifiers()
    }

    fun applyDefaultMode(english: Boolean) {
        mode = if (english) PinyinMode.English else PinyinMode.Chinese
    }

    fun close() {
        stopBackspace()
        clearComposition()
    }

    protected override fun deleteBackward() {
        backspace()
    }

    private fun backspace() {
        if (composing.isNotEmpty()) {
            composing = composing.dropLast(1)
            refreshCandidates()
            return
        }
        input.deleteBackward()
    }

    private fun commitCandidate(candidate: PinyinCandidate) {
        input.commitText(candidate.word)
        val matched = candidate.matchedLength.coerceAtMost(composing.length)
        if (matched in 1 until composing.length) {
            composing = composing.substring(matched)
            refreshCandidates()
        } else {
            clearComposition()
        }
    }

    private fun activeFirstCandidate(): PinyinCandidate? =
        if (!queryActive && candidatesComposition == composing) candidates.firstOrNull() else null

    private fun commitRaw() {
        if (composing.isEmpty()) return
        input.commitText(composing)
        clearComposition()
    }

    private fun clearComposition() {
        composing = ""
        pendingSpace = false
        querySequence++
        clearCandidates()
    }

    private fun clearCandidates() {
        candidates = emptyList()
        candidatesComposition = null
        queryActive = false
        queryJob?.cancel()
        queryJob = null
    }

    private fun refreshCandidates() {
        pendingSpace = false
        if (mode != PinyinMode.Chinese || composing.isEmpty()) {
            clearCandidates()
            return
        }

        val value = composing
        val id = ++querySequence
        queryActive = true
        queryJob?.cancel()
        queryJob = scope.launch {
            val engine = repository.engine()
            val result = if (engine == null) {
                emptyList()
            } else {
                try {
                    engine.getCandidates(value)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    emptyList()
                }
            }
            if (id != querySequence || composing != value) return@launch
            queryActive = false
            queryJob = null
            candidates = result
            candidatesComposition = value
            if (pendingSpace) {
                pendingSpace = false
                val first = result.firstOrNull()
                if (first != null) commitCandidate(first) else commitRaw()
            }
        }
    }
}
