package com.asim.aigrammarkeyboard

import android.inputmethodservice.InputMethodService
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AiKeyboardService : InputMethodService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var aiRepository: AiRepository
    private lateinit var messageBox: EditText
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private val buttonsToDisable = mutableListOf<Button>()

    override fun onCreate() {
        super.onCreate()
        aiRepository = AiRepository()
    }

    override fun onCreateInputView(): View {
        buttonsToDisable.clear()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.WHITE)
        }

        messageBox = EditText(this).apply {
            hint = getString(R.string.message_hint)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2
            maxLines = 4
            setSingleLine(false)
            textSize = 16f
            setTextColor(Color.parseColor("#111827"))
            setHintTextColor(Color.parseColor("#6B7280"))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedDrawable(Color.WHITE, Color.parseColor("#D1D5DB"), dp(10))
            // The IME cannot open another soft keyboard, so this field is filled by our own keys.
            showSoftInputOnFocus = false
        }
        root.addView(
            messageBox,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(createStatusRow())
        root.addView(createActionRow(
            createActionButton(getString(R.string.button_fix_grammar)) {
                improveText(AiRepository.PROMPT_FIX_GRAMMAR)
            },
            createActionButton(getString(R.string.button_make_professional)) {
                improveText(AiRepository.PROMPT_MAKE_PROFESSIONAL)
            }
        ))
        root.addView(createActionRow(
            createActionButton(getString(R.string.button_make_simple)) {
                improveText(AiRepository.PROMPT_MAKE_SIMPLE)
            },
            createActionButton(getString(R.string.button_send_to_app)) {
                sendToCurrentApp()
            }
        ))

        addKeyboardRows(root)
        setReadyStatus()
        messageBox.requestFocus()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::messageBox.isInitialized) {
            messageBox.requestFocus()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createStatusRow(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(4))
            gravity = android.view.Gravity.CENTER_VERTICAL

            progressBar = ProgressBar(this@AiKeyboardService).apply {
                isIndeterminate = true
                visibility = View.GONE
            }
            addView(progressBar, LinearLayout.LayoutParams(dp(24), dp(24)))

            statusText = TextView(this@AiKeyboardService).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#374151"))
                setPadding(dp(8), 0, 0, 0)
            }
            addView(
                statusText,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
    }

    private fun createActionRow(left: Button, right: Button): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, weightedButtonParams())
            addView(right, weightedButtonParams())
        }
    }

    private fun createActionButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { onClick() }
            buttonsToDisable.add(this)
        }
    }

    private fun addKeyboardRows(root: LinearLayout) {
        addKeyRow(root, listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"))
        addKeyRow(root, listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"))
        addKeyRow(root, listOf("z", "x", "c", "v", "b", "n", "m"))

        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(createKeyButton(",", ","), weightedKeyParams(0.8f))
            addView(createKeyButton("Space", " "), weightedKeyParams(2.2f))
            addView(createKeyButton(".", "."), weightedKeyParams(0.8f))
            addView(createUtilityButton("Back") { deleteFromMessage() }, weightedKeyParams(1.2f))
            addView(createUtilityButton("Clear") {
                messageBox.text?.clear()
                setReadyStatus()
            }, weightedKeyParams(1.2f))
        }
        root.addView(bottomRow)
    }

    private fun addKeyRow(root: LinearLayout, labels: List<String>) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            labels.forEach { label ->
                addView(createKeyButton(label, label), weightedKeyParams())
            }
        }
        root.addView(row)
    }

    private fun createKeyButton(label: String, value: String): Button {
        return createUtilityButton(label) {
            appendToMessage(value)
        }
    }

    private fun createUtilityButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            setOnClickListener { onClick() }
            buttonsToDisable.add(this)
        }
    }

    private fun improveText(prompt: String) {
        val roughText = messageBox.text?.toString()?.trim().orEmpty()
        if (roughText.isBlank()) {
            setStatus(getString(R.string.error_empty_text), isError = true)
            return
        }

        setLoading(true)
        setStatus(getString(R.string.status_loading), isError = false)

        serviceScope.launch {
            runCatching {
                aiRepository.rewrite(roughText, prompt)
            }.onSuccess { correctedText ->
                messageBox.setText(correctedText)
                messageBox.setSelection(correctedText.length)
                setStatus(getString(R.string.status_done), isError = false)
            }.onFailure { error ->
                setStatus(friendlyError(error), isError = true)
            }
            setLoading(false)
        }
    }

    private fun sendToCurrentApp() {
        val textToSend = messageBox.text?.toString()?.trim().orEmpty()
        if (textToSend.isBlank()) {
            setStatus(getString(R.string.error_empty_text), isError = true)
            return
        }

        currentInputConnection?.commitText(textToSend, 1)
        messageBox.text?.clear()
        setStatus(getString(R.string.status_sent), isError = false)
    }

    private fun appendToMessage(value: String) {
        val editable = messageBox.text ?: return
        val start = messageBox.selectionStart.coerceAtLeast(0)
        val end = messageBox.selectionEnd.coerceAtLeast(0)
        val replaceStart = minOf(start, end)
        val replaceEnd = maxOf(start, end)
        editable.replace(replaceStart, replaceEnd, value)
        messageBox.setSelection(replaceStart + value.length)
    }

    private fun deleteFromMessage() {
        val editable = messageBox.text ?: return
        val start = messageBox.selectionStart.coerceAtLeast(0)
        val end = messageBox.selectionEnd.coerceAtLeast(0)
        val deleteStart = minOf(start, end)
        val deleteEnd = maxOf(start, end)

        when {
            deleteStart != deleteEnd -> editable.delete(deleteStart, deleteEnd)
            deleteStart > 0 -> editable.delete(deleteStart - 1, deleteStart)
        }
    }

    private fun setReadyStatus() {
        setStatus(getString(R.string.status_ready), isError = false)
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        buttonsToDisable.forEach { it.isEnabled = !isLoading }
    }

    private fun setStatus(message: String, isError: Boolean) {
        statusText.text = message
        statusText.setTextColor(
            if (isError) Color.parseColor("#B91C1C") else Color.parseColor("#374151")
        )
    }

    private fun friendlyError(error: Throwable): String {
        return when (error) {
            is UnknownHostException -> getString(R.string.error_no_internet)
            is SocketTimeoutException -> getString(R.string.error_timeout)
            else -> error.message ?: getString(R.string.error_unknown)
        }
    }

    private fun weightedButtonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
    }

    private fun weightedKeyParams(weight: Float = 1f): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(38), weight).apply {
            setMargins(dp(1), dp(1), dp(1), dp(1))
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
}


