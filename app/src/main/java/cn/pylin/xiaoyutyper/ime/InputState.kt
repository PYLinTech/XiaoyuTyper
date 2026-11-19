package cn.pylin.xiaoyutyper.ime

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class KeyboardModifiers {
    var layer by mutableStateOf(KeyboardLayer.Letters)
        private set
    var moreSymbols by mutableStateOf(false)
        private set
    var shift by mutableStateOf(ShiftMode.Off)
        private set

    private val shiftTap = ShiftTap()

    fun onShift() {
        shift = shiftTap.next(shift)
    }

    fun toggleLayer() {
        layer = if (layer == KeyboardLayer.Letters) KeyboardLayer.Symbols else KeyboardLayer.Letters
        moreSymbols = false
        if (shift == ShiftMode.Once) shift = ShiftMode.Off
    }

    fun toggleSymbolPage() {
        moreSymbols = !moreSymbols
    }

    fun consumeShiftOnce() {
        if (shift == ShiftMode.Once) shift = ShiftMode.Off
    }

    fun clearSymbolsAndShift() {
        moreSymbols = false
        shift = ShiftMode.Off
    }

    fun reset() {
        layer = KeyboardLayer.Letters
        moreSymbols = false
        shift = ShiftMode.Off
        shiftTap.reset()
    }
}

internal class BackspaceRepeater(
    private val scope: CoroutineScope,
    private val onDelete: () -> Unit,
) {
    private var job: Job? = null

    fun start() {
        onDelete()
        stop()
        job = scope.launch {
            delay(REPEAT_DELAY_MS)
            while (isActive) {
                onDelete()
                delay(REPEAT_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

private class ShiftTap {
    private var lastTap = 0L

    fun next(current: ShiftMode): ShiftMode {
        val now = SystemClock.uptimeMillis()
        if (now - lastTap <= DOUBLE_TAP_MS) {
            lastTap = 0L
            return ShiftMode.Locked
        }
        lastTap = now
        return if (current == ShiftMode.Off) ShiftMode.Once else ShiftMode.Off
    }

    fun reset() {
        lastTap = 0L
    }
}

private const val DOUBLE_TAP_MS = 350L
private const val REPEAT_DELAY_MS = 380L
private const val REPEAT_INTERVAL_MS = 60L
