package cn.pylin.xiaoyutyper

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import cn.pylin.xiaoyutyper.ime.ImeController
import cn.pylin.xiaoyutyper.ime.ImeInput
import cn.pylin.xiaoyutyper.pinyin.PinyinRepository
import cn.pylin.xiaoyutyper.ui.keyboard.FloatingKeyboardState
import cn.pylin.xiaoyutyper.ui.keyboard.XiaoyuImeKeyboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class XiaoyuInputMethodService :
    InputMethodService(),
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner,
    ImeInput {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore
        get() = store

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val repository by lazy { PinyinRepository.get(this) }

    private val controller by lazy {
        ImeController(this, repository, serviceScope)
    }

    private val floatingState = FloatingKeyboardState()

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onCreateInputView(): View {
        window?.window?.let { imeWindow ->
            val decorView = imeWindow.decorView
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            imeWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            imeWindow.setDimAmount(0f)
            configureNavigationBar()
        }
        return ComposeView(this).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindow,
            )
            setContent {
                XiaoyuImeKeyboard(controller, floatingState)
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        controller.startSession(isPasswordField(info), isEnglishSubtype())
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        controller.finishSession()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onFinishInputView(finishingInput)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
            val attributes = win.attributes
            attributes.gravity = Gravity.BOTTOM
            attributes.x = 0
            attributes.y = 0
            win.attributes = attributes
            return
        }
        val attributes = win.attributes
        attributes.gravity = Gravity.BOTTOM
        attributes.x = 0
        attributes.y = 0
        attributes.width = WindowManager.LayoutParams.MATCH_PARENT
        attributes.height = WindowManager.LayoutParams.MATCH_PARENT
        win.attributes = attributes
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val win = window?.window ?: return
        onConfigureWindow(win, false, false)
        configureNavigationBar()
        win.decorView.requestLayout()
    }

    @Suppress("DEPRECATION")
    private fun configureNavigationBar() {
        val imeWindow = window?.window ?: return
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val lightNavigationBars =
            landscape &&
                resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK !=
                Configuration.UI_MODE_NIGHT_YES
        imeWindow.navigationBarColor = if (landscape) Color.TRANSPARENT else Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            imeWindow.isNavigationBarContrastEnforced = !landscape
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            imeWindow.insetsController?.setSystemBarsAppearance(
                if (lightNavigationBars) {
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                } else {
                    0
                },
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        } else {
            val flags = imeWindow.decorView.systemUiVisibility
            imeWindow.decorView.systemUiVisibility = if (lightNavigationBars) {
                flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            } else {
                flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
        }
    }

    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) return
        val decor = window?.window?.decorView ?: return
        outInsets.contentTopInsets = decor.height
        outInsets.visibleTopInsets = decor.height
        val bounds = floatingState.bounds
        outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
        if (bounds.isEmpty) {
            outInsets.touchableRegion.setEmpty()
        } else {
            outInsets.touchableRegion.set(bounds)
        }
    }

    override fun onDestroy() {
        controller.finishSession()
        serviceScope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDestroy()
    }

    override fun commitText(text: String) {
        if (text.isEmpty()) return
        currentInputConnection?.commitText(text, 1)
    }

    override fun deleteBackward() {
        val connection = currentInputConnection ?: return
        if (!connection.getSelectedText(0).isNullOrEmpty()) {
            connection.commitText("", 1)
            return
        }
        if (!connection.deleteSurroundingTextInCodePoints(1, 0)) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    override fun isMultiline(): Boolean {
        val inputType = currentInputEditorInfo?.inputType ?: return false
        return inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
            inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
    }

    override fun performEditorAction(): Boolean {
        val action = currentInputEditorInfo?.actionId ?: return false
        if (action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
            return false
        }
        val connection = currentInputConnection ?: return false
        connection.performEditorAction(action)
        return true
    }

    override fun isNextAction(): Boolean =
        currentInputEditorInfo?.actionId == EditorInfo.IME_ACTION_NEXT

    override fun hideKeyboard() {
        requestHideSelf(0)
    }

    private fun isPasswordField(info: EditorInfo?): Boolean {
        val inputType = info?.inputType ?: return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD

            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun isEnglishSubtype(): Boolean =
        getSystemService(InputMethodManager::class.java)
            ?.currentInputMethodSubtype
            ?.languageTag
            ?.startsWith("en") == true
}
