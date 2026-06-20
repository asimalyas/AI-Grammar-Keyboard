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
import java.util.Locale
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
        return when (activeProvider()) {
            AI_PROVIDER_GEMINI -> callGemini(inputText, prompt)
            else -> callGroqWithGeminiFallback(inputText, prompt)
        }
    }

    private suspend fun callGroqWithGeminiFallback(inputText: String, prompt: String): String {
        val groqKey = BuildConfig.GROQ_API_KEY.trim()
        if (groqKey.isNotBlank()) {
            return callGroq(inputText, prompt, groqKey)
        }

        if (BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            return callGemini(inputText, prompt)
        }

        throw IllegalStateException("Groq API key is missing. Add GROQ_API_KEY to local.properties or GitHub secrets.")
    }

    private suspend fun callGroq(inputText: String, prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", prompt))
            .put(JSONObject().put("role", "user").put("content", inputText))

        val requestJson = JSONObject()
            .put("model", GROQ_MODEL)
            .put("temperature", 0.2)
            .put("messages", messages)
            .toString()

        val request = Request.Builder()
            .url(GROQ_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toRequestBody(jsonMediaType))
            .build()

        executeRequest(request) { json ->
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }

    private suspend fun callGemini(inputText: String, prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Gemini API key is missing. Add GEMINI_API_KEY to local.properties or GitHub secrets.")
        }

        val requestJson = createGeminiRequestBody(inputText, prompt)
        var lastModelError: ApiException? = null

        for (model in GEMINI_MODELS) {
            val request = createGeminiRequest(apiKey, model, requestJson)
            try {
                return@withContext executeRequest(request) { json ->
                    json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                }
            } catch (error: ApiException) {
                lastModelError = error
                if (error.statusCode != 404) {
                    throw error
                }
            }
        }

        throw lastModelError ?: IOException("No Gemini Flash model was available for this API key.")
    }

    private fun createGeminiRequestBody(inputText: String, prompt: String): String {
        val userText = "$prompt\n\nMessage:\n$inputText"
        val parts = JSONArray().put(JSONObject().put("text", userText))
        val contents = JSONArray().put(JSONObject().put("role", "user").put("parts", parts))

        return JSONObject()
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.2))
            .toString()
    }

    private fun createGeminiRequest(apiKey: String, model: String, requestJson: String): Request {
        val url = "$GEMINI_BASE_URL/$model:generateContent".toHttpUrl()
            .newBuilder()
            .addQueryParameter("key", apiKey)
            .build()

        return Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toRequestBody(jsonMediaType))
            .build()
    }

    private fun executeRequest(request: Request, parse: (JSONObject) -> String): String {
        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val details = responseText.take(220).ifBlank { response.message }
                throw ApiException(response.code, "API request failed (${response.code}): $details")
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
        private const val LANGUAGE_RULE =
            "Detect whether the message is English, Urdu script, or Roman Urdu. Preserve the same language and script. Do not translate Roman Urdu into English or Urdu script. Return only the final message."

        const val PROMPT_FIX_GRAMMAR =
            "$LANGUAGE_RULE Correct spelling, grammar, punctuation, and sentence structure. Keep the same meaning. Make the message natural and human."
        const val PROMPT_MAKE_PROFESSIONAL =
            "$LANGUAGE_RULE Rewrite this message in a professional, respectful style. Keep the same meaning."
        const val PROMPT_MAKE_SIMPLE =
            "$LANGUAGE_RULE Rewrite this message in simple, natural language. Keep the same meaning."

        const val AI_PROVIDER_GROQ = "groq"
        const val AI_PROVIDER_GEMINI = "gemini"

        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val GROQ_MODEL = "llama-3.1-8b-instant"
        private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val GEMINI_MODELS = listOf(
            "gemini-flash-latest",
            "gemini-3.5-flash",
            "gemini-2.5-flash"
        )

        fun activeProvider(): String {
            val provider = BuildConfig.AI_PROVIDER.trim().lowercase(Locale.US)
            return if (provider == AI_PROVIDER_GEMINI) AI_PROVIDER_GEMINI else AI_PROVIDER_GROQ
        }
    }
}

private class ApiException(
    val statusCode: Int,
    message: String
) : IOException(message)