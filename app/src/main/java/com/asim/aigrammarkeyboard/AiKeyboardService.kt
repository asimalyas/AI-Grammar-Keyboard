package com.asim.aigrammarkeyboard

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

class AiKeyboardService : InputMethodService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val allButtons = mutableListOf<Button>()
    private val letterButtons = mutableListOf<Button>()
    private lateinit var aiRepository: AiRepository
    private lateinit var statusRow: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private var isShiftOn = false
    private var currentImeOptions = 0

    override fun onCreate() {
        super.onCreate()
        aiRepository = AiRepository()
    }

    override fun onCreateInputView(): View {
        allButtons.clear()
        letterButtons.clear()

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(4))
            setBackgroundColor(DARK_BACKGROUND)
            addView(createAiToolbar())
            addView(createStatusRow())
            addKeyboardRows(this)
            setReadyStatus()
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        currentImeOptions = attribute?.imeOptions ?: 0
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createAiToolbar(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(1))

            addView(createToolbarIcon("☺") {
                showKeyboardPicker()
            }, toolbarIconParams())
            addView(createToolbarIcon("⚙") {
                openSettings()
            }, toolbarIconParams())
            addView(createToolbarButton(getString(R.string.button_fix_short)) {
                improveCurrentDraft(AiRepository.PROMPT_FIX_GRAMMAR)
            }, toolbarParams(1.05f))
            addView(createToolbarButton(getString(R.string.button_pro_short)) {
                improveCurrentDraft(AiRepository.PROMPT_MAKE_PROFESSIONAL)
            }, toolbarParams(1.05f))
            addView(createToolbarButton(getString(R.string.button_simple_short)) {
                improveCurrentDraft(AiRepository.PROMPT_MAKE_SIMPLE)
            }, toolbarParams(1.25f))
            addView(createToolbarIcon("⇄") {
                showKeyboardPicker()
            }, toolbarIconParams())
            addView(createToolbarIcon("⋯") {
                openSettings()
            }, toolbarIconParams())
        }
    }

    private fun createStatusRow(): View {
        statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(2), dp(1), dp(2), dp(2))

            progressBar = ProgressBar(this@AiKeyboardService).apply {
                isIndeterminate = true
                visibility = View.GONE
            }
            addView(progressBar, LinearLayout.LayoutParams(dp(16), dp(16)))

            statusText = TextView(this@AiKeyboardService).apply {
                textSize = 10f
                setTextColor(STATUS_TEXT)
                setSingleLine(true)
                setPadding(dp(6), 0, 0, 0)
            }
            addView(statusText, LinearLayout.LayoutParams(0, dp(18), 1f))
        }
        return statusRow
    }

    private fun addKeyboardRows(root: LinearLayout) {
        addKeyRow(root, listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"))
        addKeyRow(root, listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"))
        addKeyRow(root, listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"), leftInsetWeight = 0.48f, rightInsetWeight = 0.48f)
        addSpecialLetterRow(root)
        addBottomRow(root)
    }

    private fun addKeyRow(
        root: LinearLayout,
        labels: List<String>,
        leftInsetWeight: Float = 0f,
        rightInsetWeight: Float = 0f
    ) {
        val row = keyboardRow()
        if (leftInsetWeight > 0f) {
            row.addView(spacer(), rowParams(leftInsetWeight))
        }
        labels.forEach { label ->
            row.addView(createLetterKey(label), rowParams())
        }
        if (rightInsetWeight > 0f) {
            row.addView(spacer(), rowParams(rightInsetWeight))
        }
        root.addView(row)
    }

    private fun addSpecialLetterRow(root: LinearLayout) {
        val row = keyboardRow()
        row.addView(createSpecialKey(getString(R.string.key_shift)) {
            isShiftOn = !isShiftOn
            refreshLetterLabels()
            hideStatus()
        }, rowParams(1.15f))

        listOf("z", "x", "c", "v", "b", "n", "m").forEach { label ->
            row.addView(createLetterKey(label), rowParams())
        }

        row.addView(createSpecialKey(getString(R.string.key_backspace)) {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }, rowParams(1.15f))
        root.addView(row)
    }

    private fun addBottomRow(root: LinearLayout) {
        val row = keyboardRow()
        row.addView(createSpecialKey("!#1") {
            setStatus(getString(R.string.status_symbols_later), false)
        }, rowParams(1.15f))
        row.addView(createSpecialKey(",") {
            commitText(",")
        }, rowParams(0.85f))
        row.addView(createSpecialKey(getString(R.string.key_language)) {
            commitText(" ")
        }, rowParams(3.7f))
        row.addView(createSpecialKey(".") {
            commitText(".")
        }, rowParams(0.85f))
        row.addView(createSendKey(), rowParams(1.25f))
        root.addView(row)
    }

    private fun createToolbarButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setIncludeFontPadding(false)
            background = stateDrawable(TOOLBAR_KEY, TOOLBAR_KEY_PRESSED, dp(15))
            backgroundTintList = null
            setOnClickListener {
                keyFeedback(this)
                onClick()
            }
            allButtons.add(this)
        }
    }

    private fun createToolbarIcon(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            typeface = Typeface.DEFAULT
            setTextColor(TOOLBAR_ICON_TEXT)
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setIncludeFontPadding(false)
            background = stateDrawable(TOOLBAR_ICON_KEY, TOOLBAR_KEY_PRESSED, dp(15))
            backgroundTintList = null
            setOnClickListener {
                keyFeedback(this)
                onClick()
            }
            allButtons.add(this)
        }
    }

    private fun createLetterKey(label: String): Button {
        return createBaseKey(label).apply {
            tag = label
            setOnClickListener {
                keyFeedback(this)
                val text = if (isShiftOn) label.uppercase(Locale.US) else label
                commitText(text)
                if (isShiftOn) {
                    isShiftOn = false
                    refreshLetterLabels()
                    setReadyStatus()
                }
            }
            letterButtons.add(this)
            allButtons.add(this)
        }
    }

    private fun createSpecialKey(label: String, onClick: () -> Unit): Button {
        return createBaseKey(label).apply {
            textSize = 13f
            background = stateDrawable(SPECIAL_KEY, SPECIAL_KEY_PRESSED, dp(4))
            backgroundTintList = null
            setOnClickListener {
                keyFeedback(this)
                onClick()
            }
            allButtons.add(this)
        }
    }

    private fun createSendKey(): Button {
        return createSpecialKey(getString(R.string.key_send)) {
            val action = currentImeOptions and EditorInfo.IME_MASK_ACTION
            if (action == EditorInfo.IME_ACTION_SEND ||
                action == EditorInfo.IME_ACTION_DONE ||
                action == EditorInfo.IME_ACTION_GO ||
                action == EditorInfo.IME_ACTION_SEARCH
            ) {
                currentInputConnection?.performEditorAction(action)
            } else {
                commitText("\n")
            }
        }.apply {
            setTextColor(SEND_TEXT)
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun createBaseKey(label: String): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 19f
            typeface = Typeface.DEFAULT
            setTextColor(KEY_TEXT)
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setIncludeFontPadding(false)
            background = stateDrawable(LETTER_KEY, LETTER_KEY_PRESSED, dp(4))
            backgroundTintList = null
        }
    }

    private fun improveCurrentDraft(prompt: String) {
        val inputConnection = currentInputConnection ?: run {
            setStatus(getString(R.string.error_no_input_connection), true)
            return
        }

        val selectedText = inputConnection.getSelectedText(0)?.toString().orEmpty()
        val beforeCursor = inputConnection.getTextBeforeCursor(MAX_DRAFT_CHARS, 0)?.toString().orEmpty()
        val textToImprove = selectedText.ifBlank { beforeCursor }.trim()

        if (textToImprove.isBlank()) {
            setStatus(getString(R.string.error_empty_text), true)
            return
        }

        setLoading(true)
        setStatus(getString(R.string.status_loading), false)

        serviceScope.launch {
            runCatching {
                aiRepository.rewrite(textToImprove, prompt)
            }.onSuccess { correctedText ->
                replaceDraft(inputConnection, selectedText, beforeCursor, correctedText)
                hideStatus()
            }.onFailure { error ->
                setStatus(friendlyError(error), true)
            }
            setLoading(false)
        }
    }

    private fun replaceDraft(
        inputConnection: InputConnection,
        selectedText: String,
        beforeCursor: String,
        correctedText: String
    ) {
        if (selectedText.isNotBlank()) {
            inputConnection.commitText(correctedText, 1)
            return
        }

        if (beforeCursor.isNotEmpty()) {
            inputConnection.deleteSurroundingText(beforeCursor.length, 0)
        }
        inputConnection.commitText(correctedText, 1)
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun keyFeedback(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        view.playSoundEffect(SoundEffectConstants.CLICK)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun showKeyboardPicker() {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.showInputMethodPicker()
    }

    private fun refreshLetterLabels() {
        letterButtons.forEach { button ->
            val label = button.tag as? String ?: return@forEach
            button.text = if (isShiftOn) label.uppercase(Locale.US) else label
        }
    }

    private fun setReadyStatus() {
        hideStatus()
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        if (isLoading) {
            statusRow.visibility = View.VISIBLE
        }
        allButtons.forEach { it.isEnabled = !isLoading }
    }

    private fun setStatus(message: String, isError: Boolean) {
        statusText.text = message
        statusText.setTextColor(if (isError) ERROR_TEXT else STATUS_TEXT)
        statusRow.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
    }

    private fun hideStatus() {
        progressBar.visibility = View.GONE
        statusText.text = ""
        statusRow.visibility = View.GONE
    }

    private fun friendlyError(error: Throwable): String {
        return when (error) {
            is UnknownHostException -> getString(R.string.error_no_internet)
            is SocketTimeoutException -> getString(R.string.error_timeout)
            is IOException -> getString(R.string.error_api_short)
            else -> error.message ?: getString(R.string.error_unknown)
        }
    }

    private fun keyboardRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
    }

    private fun spacer(): View {
        return View(this)
    }

    private fun toolbarParams(weight: Float): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(30), weight).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
    }

    private fun toolbarIconParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(30), 0.68f).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
    }

    private fun rowParams(weight: Float = 1f): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(35), weight).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
    }

    private fun stateDrawable(normalColor: Int, pressedColor: Int, radius: Int): StateListDrawable {
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), roundedDrawable(pressedColor, pressedColor, radius))
            addState(intArrayOf(android.R.attr.state_enabled), roundedDrawable(normalColor, normalColor, radius))
            addState(intArrayOf(), roundedDrawable(DISABLED_KEY, DISABLED_KEY, radius))
        }
    }

    private fun roundedDrawable(fillColor: Int, strokeColor: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            cornerRadius = radius.toFloat()
            setStroke(dp(1), strokeColor)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val MAX_DRAFT_CHARS = 1000
        private val DARK_BACKGROUND = Color.parseColor("#050505")
        private val LETTER_KEY = Color.parseColor("#2A2A2A")
        private val LETTER_KEY_PRESSED = Color.parseColor("#4A4A4A")
        private val SPECIAL_KEY = Color.parseColor("#171717")
        private val SPECIAL_KEY_PRESSED = Color.parseColor("#3A3A3A")
        private val TOOLBAR_KEY = Color.parseColor("#252525")
        private val TOOLBAR_ICON_KEY = Color.parseColor("#1F1F1F")
        private val TOOLBAR_KEY_PRESSED = Color.parseColor("#3F3F3F")
        private val DISABLED_KEY = Color.parseColor("#1B1B1B")
        private val KEY_TEXT = Color.parseColor("#F2F2F2")
        private val TOOLBAR_ICON_TEXT = Color.parseColor("#CFCFCF")
        private val STATUS_TEXT = Color.parseColor("#BDBDBD")
        private val ERROR_TEXT = Color.parseColor("#FF8A80")
        private val SEND_TEXT = Color.parseColor("#4EA3FF")
    }
}