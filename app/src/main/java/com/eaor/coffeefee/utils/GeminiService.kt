package com.eaor.coffeefee.utils

import android.util.Log
import com.eaor.coffeefee.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GeminiRequest(val prompt: String, val maxTokens: Int = 100)
data class GeminiResponse(val choices: List<Choice>)
data class Choice(val text: String)

class GeminiService private constructor() {
    val model = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    suspend fun generateCoffeeExperience(
        prompt: String,
        location: String,
        tags: List<String> = listOf()
    ): Result<String> {
        return try {
            withContext(Dispatchers.IO) {
                try {
                    val fullPrompt = buildPrompt(prompt, location, tags)
                    Log.d(TAG, "Sending prompt to Gemini: $fullPrompt")

                    val response = model.generateContent(fullPrompt)
                    Log.d(TAG, "response prompt to Gemini: $response")

                    if (response.text.isNullOrEmpty()) {
                        Result.failure(Exception("Empty response from Gemini"))
                    } else {
                        Result.success(response.text!!)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error generating content", e)
                    Result.failure(e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in coroutine context", e)
            Result.failure(e)
        }
    }

    private fun buildPrompt(prompt: String, location: String, tags: List<String>): String {
        return """
            Location: $location
            Tags: ${tags.joinToString(", ")}
            User Input: $prompt
            
            Please generate a detailed and engaging coffee experience description that:
            Given the following description and location, generate 3-5 general and broad tags that
            describe the content. One of the tags must be the city from the location.
            Avoid specific, detailed, or niche tags. Think in terms of high-level categories or themes.
            Description: "Beautiful sunset over the Mediterranean Sea with gentle waves and golden sky.
            Location: "Tel Aviv Beach, Israel"
            Return only the tags, in a simple comma-separated format.
            
            Format the response as a single, well-structured paragraph suitable for social media.
        """.trimIndent()
    }

    companion object {
        private const val TAG = "GeminiService"
        
        @Volatile
        private var instance: GeminiService? = null

        fun getInstance(): GeminiService {
            return instance ?: synchronized(this) {
                instance ?: GeminiService().also { instance = it }
            }
        }
    }
} 