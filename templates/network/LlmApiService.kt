package com.ctrldevice.agent.llm

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import kotlinx.coroutines.flow.Flow

/**
 * Interface for the AI Brain (LLM) running over the Internet.
 * Supports OpenAI-compatible APIs (HuggingFace, Ollama, etc.).
 */
interface LlmApiService {

    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun generateResponse(@Body request: LlmRequest): LlmResponse

    // Optional: Streaming for faster feedback
    // suspend fun streamResponse(@Body request: LlmRequest): Flow<LlmResponseChunk>
}

data class LlmRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 1000
)

data class Message(
    val role: String, // "system", "user", "assistant"
    val content: String
)

data class LlmResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)
