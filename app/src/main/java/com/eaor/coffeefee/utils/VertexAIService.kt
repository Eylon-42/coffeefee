package com.eaor.coffeefee.utils

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.vertexai.vertexAI
import com.google.firebase.vertexai.type.content
import com.eaor.coffeefee.models.CoffeeShop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import org.json.JSONArray
import org.json.JSONObject

class VertexAIService private constructor() {
    
    companion object {
        private const val TAG = "VertexAIService"
        private const val MODEL_NAME = "gemini-pro"
        
        @Volatile
        private var instance: VertexAIService? = null
        
        fun getInstance(): VertexAIService {
            return instance ?: synchronized(this) {
                instance ?: VertexAIService().also { instance = it }
            }
        }
    }
    
    // The Firebase VertexAI client
    private val vertexAI = Firebase.vertexAI
    
    /**
     * Generate coffee shop suggestions based on user preferences
     * @param userTags The user's preference tags
     * @param coffeeShops All available coffee shops
     * @return Result containing either success with suggested coffee shops and reasons or failure with exception
     */
    suspend fun generateCoffeeShopSuggestions(
        userTags: List<String>,
        coffeeShops: List<CoffeeShop>
    ): Result<List<Pair<CoffeeShop, List<String>>>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating coffee shop suggestions for user tags: $userTags")
            
            // Build the prompt for VertexAI
            val prompt = buildSuggestionPrompt(userTags, coffeeShops)
            
            // Create a generative model
            val generativeModel = vertexAI.generativeModel(MODEL_NAME)
            
            // Create content from the prompt
            val contentObject = content { text(prompt) }
            
            // Generate content
            val response = generativeModel.generateContent(contentObject)
            
            if (response.text.isNullOrEmpty()) {
                Log.e(TAG, "Empty response from VertexAI")
                Result.failure(Exception("Empty response from AI"))
            } else {
                Log.d(TAG, "Successfully generated suggestions: ${response.text}")
                
                // Parse the response
                val suggestions = parseSuggestionResponse(response.text!!, coffeeShops, userTags)
                Result.success(suggestions)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during suggestion generation", e)
            Result.failure(e)
        }
    }
    
    /**
     * Build a prompt for the VertexAI model to suggest coffee shops
     */
    private fun buildSuggestionPrompt(userTags: List<String>, coffeeShops: List<CoffeeShop>): String {
        Log.d(TAG, "Building suggestion prompt with ${userTags.size} user tags and ${coffeeShops.size} coffee shops")
        
        // Filter out coffee shops without tags to avoid unnecessary processing
        val shopsWithTags = coffeeShops.filter { !it.tags.isNullOrEmpty() }
        
        val coffeeShopsData = shopsWithTags.mapIndexed { index, shop ->
            """
            Coffee Shop #${index + 1}:
            Name: ${shop.name}
            Tags: ${shop.tags?.joinToString(", ") ?: "none"}
            Description: ${shop.caption}
            """
        }.joinToString("\n\n")
        
        return """
            Given the following user preferences and coffee shop information, suggest the most suitable coffee shops for the user. 
            Provide your response in JSON format.
            
            User Preferences (tags): ${userTags.joinToString(", ")}
            
            Available Coffee Shops:
            $coffeeShopsData
            
            Instructions:
            1. Analyze the user's preferences and the coffee shop tags.
            2. Select up to 5 coffee shops that best match the user's preferences.
            3. For each suggested coffee shop, provide specific reasons why it's a good match for this user.
            4. Return the results in the following JSON format:
            {
              "suggestions": [
                {
                  "shopIndex": [index of the coffee shop, starting from 1],
                  "reasons": ["reason 1", "reason 2", ...]
                }
              ]
            }
            
            Only return the JSON output and nothing else.
            Always provide at least one reason for each suggested coffee shop.
            Always return up to 5 suggestions, even if fewer are available.
            Always return at least one suggestion, even if no matches are found.
        """.trimIndent()
    }
    
    /**
     * Parse the response from VertexAI to extract coffee shop suggestions
     */
    private fun parseSuggestionResponse(
        response: String, 
        coffeeShops: List<CoffeeShop>,
        userTags: List<String>
    ): List<Pair<CoffeeShop, List<String>>> {
        try {
            Log.d(TAG, "Parsing AI response: $response")
            
            // Extract JSON from the response if it's not a pure JSON response
            val jsonText = extractJson(response)
            Log.d(TAG, "Extracted JSON: $jsonText")
            
            val jsonObject = JSONObject(jsonText)
            val suggestionsArray = jsonObject.getJSONArray("suggestions")
            
            val result = mutableListOf<Pair<CoffeeShop, List<String>>>()
            
            for (i in 0 until suggestionsArray.length()) {
                val suggestionObject = suggestionsArray.getJSONObject(i)
                val shopIndex = suggestionObject.getInt("shopIndex") - 1 // Convert to 0-based index
                
                // Ensure the index is valid
                if (shopIndex >= 0 && shopIndex < coffeeShops.size) {
                    val coffeeShop = coffeeShops[shopIndex]
                    Log.d(TAG, "Found match for shop: ${coffeeShop.name}")
                    
                    val reasonsArray = suggestionObject.getJSONArray("reasons")
                    val reasons = mutableListOf<String>()
                    
                    for (j in 0 until reasonsArray.length()) {
                        reasons.add(reasonsArray.getString(j))
                    }
                    
                    result.add(Pair(coffeeShop, reasons))
                } else {
                    Log.w(TAG, "Invalid shop index: $shopIndex")
                }
            }
            
            if (result.isEmpty()) {
                Log.d(TAG, "No matches found in AI response, falling back to tag matching")
                return fallbackToTagMatching(coffeeShops, userTags)
            }
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing suggestion response: ${e.message}", e)
            
            // Fallback to a simpler approach if JSON parsing fails
            return fallbackToTagMatching(coffeeShops, userTags)
        }
    }
    
    /**
     * Fallback method to match coffee shops based on tags when AI parsing fails
     */
    private fun fallbackToTagMatching(
        coffeeShops: List<CoffeeShop>,
        userTags: List<String>
    ): List<Pair<CoffeeShop, List<String>>> {
        Log.d(TAG, "Using fallback tag matching with user tags: $userTags")
        
        return coffeeShops
            .filter { shop -> 
                val shopTags = shop.tags
                Log.d(TAG, "Shop ${shop.name} has tags: $shopTags")
                shopTags?.any { tag -> userTags.contains(tag) } == true 
            }
            .map { shop ->
                val matchingTags = shop.tags?.filter { userTags.contains(it) } ?: emptyList()
                Pair(shop, matchingTags)
            }
            .sortedByDescending { it.second.size }
            .take(5)
            .also { 
                Log.d(TAG, "Fallback found ${it.size} matches") 
            }
    }
    
    /**
     * Extract JSON from a text response that might contain additional text
     */
    private fun extractJson(text: String): String {
        val jsonStart = text.indexOf("{")
        val jsonEnd = text.lastIndexOf("}") + 1
        
        return if (jsonStart >= 0 && jsonEnd > jsonStart) {
            text.substring(jsonStart, jsonEnd)
        } else {
            throw IllegalArgumentException("No valid JSON found in response")
        }
    }
    
    /**
     * Generate a coffee experience description based on user input
     * @param prompt The user input or description
     * @param location The location related to the coffee experience
     * @param tags Optional list of tags to include in the prompt
     * @return Result containing either success with text or failure with exception
     */
    suspend fun generateCoffeeExperience(
        prompt: String, 
        location: String, 
        tags: List<String> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating content with prompt: $prompt, location: $location, tags: $tags")
            
            // Build the complete prompt with user input, location and tags
            val fullPrompt = buildPrompt(prompt, location, tags)
            
            // Create a generative model
            val generativeModel = vertexAI.generativeModel(MODEL_NAME)
            
            // Create content from the prompt
            val contentObject = content { text(fullPrompt) }
            
            // Generate content
            val response = generativeModel.generateContent(contentObject)
            
            if (response.text.isNullOrEmpty()) {
                Log.e(TAG, "Empty response from VertexAI")
                Result.failure(Exception("Empty response from AI"))
            } else {
                Log.d(TAG, "Successfully generated content: ${response.text}")
                Result.success(response.text!!)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during content generation", e)
            Result.failure(e)
        }
    }
    
    /**
     * Build a prompt for the VertexAI model
     */
    private fun buildPrompt(prompt: String, location: String, tags: List<String>): String {
        val tagString = if (tags.isNotEmpty()) {
            "Consider these tags: ${tags.joinToString(", ")}"
        } else {
            ""
        }
        
        return """
            Write a brief, engaging coffee experience description based on the following:
            
            User Description: $prompt
            Location: $location
            $tagString
            
            Keep it conversational and authentic. The description should be 2-3 sentences max,
            highlight sensory details, and capture the essence of the coffee experience.
            Don't use hashtags or emojis.
        """.trimIndent()
    }
    
    /**
     * Build a prompt specifically for generating tags based on user preferences
     */
    fun buildTagGenerationPrompt(
        favoriteDrink: String,
        dietaryNeeds: String,
        atmosphere: String,
        location: String,
        existingTags: List<String> = emptyList()
    ): String {
        val existingTagsString = if (existingTags.isNotEmpty()) {
            "Consider these existing tags: ${existingTags.joinToString(", ")}"
        } else {
            ""
        }

        return """
            Based on the following user preferences, generate 5-8 tags that would help match this user with appropriate coffee shops:
            
            Favorite coffee drink: $favoriteDrink
            Dietary preferences: ${if (dietaryNeeds.isEmpty()) "None specified" else dietaryNeeds}
            Preferred atmosphere: $atmosphere
            Preferred location: $location
            $existingTagsString
            
            The tags should be relevant to coffee shop attributes, ambiance, offerings, and location preferences.
            Only output the tags as a comma-separated list. For example: "artisanal, quiet, vegan-friendly, downtown"
        """.trimIndent()
    }
} 