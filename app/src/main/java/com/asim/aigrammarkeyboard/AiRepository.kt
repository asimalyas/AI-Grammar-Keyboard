package com.asim.aigrammarkeyboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AiRepository {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()

    suspend fun rewrite(inputText: String, prompt: String): String {
        return callGemini(inputText, prompt)
    }

    private suspend fun callGemini(inputText: String, prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Gemini API key is missing. Add GEMINI_API_KEY to local.properties.")
        }

        val userText = "$prompt\n\nMessage:\n$inputText"
        val parts = JSONArray().put(JSONObject().put("text", userText))
        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", parts))

        val requestBody = JSONObject()
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.2))
            .toString()
            .toRequestBody(jsonMediaType)

        val url = GEMINI_URL.toHttpUrl()
            .newBuilder()
            .addQueryParameter("key", apiKey)
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        executeRequest(request) { json ->
            json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }
    }

    private fun executeRequest(request: Request, parse: (JSONObject) -> String): String {
        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val details = responseText.take(220).ifBlank { response.message }
                throw IOException("API request failed (${response.code}): $details")
            }

            val parsedText = try {
                parse(JSONObject(responseText)).trim()
            } catch (error: JSONException) {
                throw IOException("Could not read AI response.", error)
            }

            if (parsedText.isBlank()) {
                throw IOException("AI returned an empty response.")
            }
            return parsedText
        }
    }

    companion object {
        const val PROMPT_FIX_GRAMMAR =
            "Correct spelling, grammar, punctuation, and sentence structure. Keep the same meaning. Make the message natural and human. Return only corrected text."
        const val PROMPT_MAKE_PROFESSIONAL =
            "Rewrite this message in professional English. Keep the same meaning. Return only the improved message."
        const val PROMPT_MAKE_SIMPLE =
            "Rewrite this message in simple natural English. Keep the same meaning. Return only the improved message."

        private const val GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
    }
}