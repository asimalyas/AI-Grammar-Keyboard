package com.asim.aigrammarkeyboard

import android.app.Activity
import android.graphics.Color
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
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 24f
            setTextColor(Color.parseColor("#111827"))
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.settings_summary)
            textSize = 15f
            setTextColor(Color.parseColor("#4B5563"))
            setPadding(0, dp(8), 0, dp(16))
        })

        root.addView(sectionLabel(getString(R.string.api_key_status)))
        root.addView(keyStatusLine("Gemini", BuildConfig.GEMINI_API_KEY.isNotBlank()))

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

    private fun sectionLabel(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.parseColor("#111827"))
            setPadding(0, dp(24), 0, dp(8))
        }
    }

    private fun keyStatusLine(providerName: String, configured: Boolean): TextView {
        val status = if (configured) getString(R.string.key_configured) else getString(R.string.key_missing)
        val color = if (configured) Color.parseColor("#047857") else Color.parseColor("#B91C1C")
        return TextView(this).apply {
            text = "$providerName: $status"
            textSize = 15f
            setTextColor(color)
            setPadding(0, dp(4), 0, dp(4))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
