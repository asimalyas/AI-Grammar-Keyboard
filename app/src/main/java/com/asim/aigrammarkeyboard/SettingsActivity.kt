package com.asim.aigrammarkeyboard

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(Color.WHITE)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.owner_name)
            textSize = 25f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 18f
            setTextColor(Color.parseColor("#374151"))
            setPadding(0, dp(4), 0, dp(10))
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.settings_summary)
            textSize = 15f
            setTextColor(Color.parseColor("#4B5563"))
            setPadding(0, 0, 0, dp(14))
        })

        root.addView(sectionLabel(getString(R.string.provider_status)))
        root.addView(keyStatusLine("Active provider", providerDisplayName(), true))
        root.addView(keyStatusLine("Groq", BuildConfig.GROQ_API_KEY.isNotBlank()))
        root.addView(keyStatusLine("Gemini", BuildConfig.GEMINI_API_KEY.isNotBlank()))

        root.addView(sectionLabel(getString(R.string.how_to_use_title)))
        root.addView(instructionText(getString(R.string.how_to_use_steps)))

        root.addView(sectionLabel(getString(R.string.language_support_title)))
        root.addView(instructionText(getString(R.string.language_support_summary)))

        val scrollView = ScrollView(this).apply {
            addView(
                root,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        setContentView(scrollView)
    }

    private fun providerDisplayName(): String {
        return if (AiRepository.activeProvider() == AiRepository.AI_PROVIDER_GEMINI) "Gemini" else "Groq"
    }

    private fun sectionLabel(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#111827"))
            setPadding(0, dp(22), 0, dp(8))
        }
    }

    private fun instructionText(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 15f
            setTextColor(Color.parseColor("#4B5563"))
            setLineSpacing(dp(2).toFloat(), 1.0f)
        }
    }

    private fun keyStatusLine(providerName: String, configured: Boolean): TextView {
        val status = if (configured) getString(R.string.key_configured) else getString(R.string.key_missing)
        return keyStatusLine(providerName, status, configured)
    }

    private fun keyStatusLine(label: String, status: String, good: Boolean): TextView {
        val color = if (good) Color.parseColor("#047857") else Color.parseColor("#B91C1C")
        return TextView(this).apply {
            text = "$label: $status"
            textSize = 15f
            setTextColor(color)
            setPadding(0, dp(4), 0, dp(4))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}