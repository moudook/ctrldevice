package com.ctrldevice.features.agent_engine.intelligence

import com.ctrldevice.agent.tools.ToolRegistry
import com.ctrldevice.features.agent_engine.coordination.TaskNode
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

/**
 * An AgentBrain that uses a Remote LLM (e.g., OpenAI, Anthropic, Local Inference) via HTTP.
 * Optimized for latency (async I/O) and reliability (retries, validation).
 */
class LLMBrain(
    private val apiKey: String,
    private val endpoint: String = "https://api.openai.com/v1/chat/completions",
    private val model: String = "gpt-4-turbo"
) : AgentBrain {

    companion object {
        // Optimization: Shared client
        private val client = OkHttpClient()
        private val gson = Gson()
        private val JSON = "application/json; charset=utf-8".toMediaType()

        // Optimization: LRU Cache
        private const val CACHE_SIZE = 50
        private val responseCache = java.util.Collections.synchronizedMap(
            object : java.util.LinkedHashMap<String, BrainThought>(CACHE_SIZE, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BrainThought>?): Boolean {
                    return size > CACHE_SIZE
                }
            }
        )
    }

    override suspend fun proposeNextStep(
        task: TaskNode.AtomicTask,
        screenContext: String,
        previousActions: List<String>,
        screenshotBase64: String?,
        inputData: Map<String, Any>
    ): BrainThought {
        // Optimization: Fail fast if context is dangerously large (though Driver should prune)
        val safeContext = if (screenContext.length > 50_000) {
            screenContext.take(50_000) + "\n...[TRUNCATED]..."
        } else {
            screenContext
        }

        return withContext(Dispatchers.IO) {
            try {
                val prompt = constructPrompt(task, safeContext, previousActions, screenshotBase64, inputData)

                // Optimization: Better Cache Key
                // Hash task + last action + screen subset to hit cache more often
                val lastAction = previousActions.lastOrNull() ?: "start"
                val contextHash = safeContext.hashCode()
                val cacheKey = "${task.description}_${lastAction}_$contextHash"

                val cached = responseCache[cacheKey]
                if (cached != null) {
                    return@withContext cached
                }

                // Optimization: Retry logic with backoff
                val response = callLlmApiWithRetry(prompt)
                var thought = parseResponse(response)

                // Optimization: Tool Validation
                // If LLM hallucinates a tool, correct it to "unknown" or specific fallback
                if (thought.toolName != "unknown" && ToolRegistry.getTool(thought.toolName) == null) {
                    // Try to correct common mistakes
                    val correction = ToolRegistry.findToolForTask(thought.toolName)
                    thought = if (correction != null) {
                        thought.copy(toolName = correction.name)
                    } else {
                        thought.copy(toolName = "unknown", reasoning = "Hallucinated tool: ${thought.toolName}. " + thought.reasoning)
                    }
                }

                // Update Cache
                responseCache[cacheKey] = thought

                thought
            } catch (e: Exception) {
                e.printStackTrace()
                BrainThought(
                    reasoning = "LLM Error: ${e.message}. Falling back to safety.",
                    toolName = "unknown",
                    toolParams = "{}"
                )
            }
        }
    }

    suspend fun planTask(userGoal: String): TaskNode? {
        return withContext(Dispatchers.IO) {
            try {
                val prompt = constructPlanningPrompt(userGoal)
                val response = callLlmApiWithRetry(prompt)
                parsePlanningResponse(userGoal, response)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    // --- Private Helpers ---

    private suspend fun callLlmApiWithRetry(jsonBody: String): String {
        var attempt = 0
        val maxRetries = 3
        var lastException: Exception? = null

        while (attempt < maxRetries) {
            try {
                return callLlmApiAsync(jsonBody)
            } catch (e: IOException) {
                lastException = e
                // Network error? Retry.
            } catch (e: ApiException) {
                lastException = e
                // 429 (Rate Limit) or 5xx (Server Error)? Retry.
                if (e.code == 429 || e.code in 500..599) {
                     val delayMs = (1000L * (1 shl attempt)) // Exponential backoff: 1s, 2s, 4s
                     delay(delayMs)
                } else {
                    throw e // 400/401/403 -> Don't retry
                }
            }
            attempt++
        }
        throw lastException ?: Exception("Failed after $maxRetries attempts")
    }

    // Optimization: Non-blocking I/O using Coroutines + OkHttp
    private suspend fun callLlmApiAsync(jsonBody: String): String = suspendCancellableCoroutine { continuation ->
        val body = jsonBody.toRequestBody(JSON)
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(ApiException(response.code, "HTTP ${response.code}"))
                        }
                        return
                    }
                    val responseBody = response.body?.string() ?: ""
                    if (continuation.isActive) {
                        continuation.resume(responseBody)
                    }
                }
            }
        })
    }

    private fun parseResponse(jsonResponse: String): BrainThought {
        val responseObj = gson.fromJson(jsonResponse, LlmResponse::class.java)
        val content = responseObj.choices?.firstOrNull()?.message?.content
            ?: throw Exception("Empty LLM response")

        // Optimization: Strip Markdown code blocks if present (Common LLM quirk)
        val cleanJson = if (content.trim().startsWith("```")) {
            content.substringAfter("json").substringAfter("\n").substringBeforeLast("```")
        } else {
            content
        }

        val thoughtData = gson.fromJson(cleanJson, LlmThought::class.java)

        return BrainThought(
            reasoning = thoughtData.reasoning ?: "No reasoning provided",
            toolName = thoughtData.tool_name ?: "unknown",
            toolParams = thoughtData.tool_params ?: "{}"
        )
    }

    private fun constructPlanningPrompt(goal: String): String {
        return """
            You are a Senior Task Architect for an Android Agent.
            User Goal: "$goal"

            Break this goal down into a logical structure of sub-tasks.

            Available Agent Types:
            - SYSTEM: For clicking, typing, scrolling, settings, navigation.
            - RESEARCH: For searching the web (Chrome), reading content.

            Output a JSON object representing the plan:
            {
              "type": "sequence", // or "parallel"
              "description": "Plan description",
              "tasks": [
                {
                  "type": "atomic",
                  "description": "Step 1 description (e.g. Open Chrome)",
                  "agent": "SYSTEM" // or RESEARCH
                },
                 {
                  "type": "atomic",
                  "description": "Step 2 description",
                  "agent": "RESEARCH"
                }
              ]
            }
        """.trimIndent()
    }

    private fun parsePlanningResponse(originalGoal: String, jsonResponse: String): TaskNode? {
         try {
            val responseObj = gson.fromJson(jsonResponse, LlmResponse::class.java)
            val content = responseObj.choices?.firstOrNull()?.message?.content ?: return null

            // Strip markdown here too
            val cleanJson = if (content.trim().startsWith("```")) {
                content.substringAfter("json").substringAfter("\n").substringBeforeLast("```")
            } else {
                content
            }

            val planData = gson.fromJson(cleanJson, JsonPlan::class.java)

            val id = "planned_${System.currentTimeMillis()}"

            return if (planData.type == "sequence") {
                 val subTasks = planData.tasks?.mapIndexed { index, t ->
                    TaskNode.AtomicTask(
                        id = "${id}_$index",
                        priority = 10,
                        description = t.description ?: "Unknown Step",
                        assignedTo = if (t.agent == "RESEARCH") com.ctrldevice.features.agent_engine.coordination.AgentType.RESEARCH else com.ctrldevice.features.agent_engine.coordination.AgentType.SYSTEM,
                        estimatedDuration = 10.seconds // Default
                    )
                 } ?: emptyList()

                 TaskNode.SequentialGroup(
                    id = id,
                    priority = 10,
                    description = planData.description ?: originalGoal,
                    tasks = subTasks
                 )
            } else {
                // Default to atomic if simple
                 TaskNode.AtomicTask(
                    id = id,
                    priority = 10,
                    description = originalGoal,
                    assignedTo = com.ctrldevice.features.agent_engine.coordination.AgentType.SYSTEM,
                    estimatedDuration = 10.seconds
                 )
            }
         } catch (e: Exception) {
             e.printStackTrace()
             return null
         }
    }

    private fun constructPrompt(
        task: TaskNode.AtomicTask,
        screenContext: String,
        previousActions: List<String>,
        screenshotBase64: String?,
        inputData: Map<String, Any>
    ): String {
        // Mode detection
        val mode = if (screenshotBase64 != null) "MULTIMODAL (Text + Vision)" else "TEXT_ONLY"

        // Serialize input data for context
        val inputContext = if (inputData.isNotEmpty()) {
            "Input Data from Previous Tasks:\n${inputData.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }}"
        } else {
            "Input Data: None"
        }

        // Simple JSON prompt construction
        val systemPrompt = """
            You are an Agent controlling an Android device.
            Your Goal: ${task.description}
            Current Mode: $mode
            $inputContext

            POLICY:
            1. You generally speak and reason in ENGLISH.
            2. If the user asks you to type text in a specific language, you may do so.
            3. Your internal "reasoning" must always be in ENGLISH.
            4. USE THE SCREEN CONTEXT. The text below represents the UI tree.
               - Items marked "(Clickable)" can be clicked.
               - Indentation represents hierarchy.
               - If you can't see the element, try scrolling.

            Available Tools:
            - click_element(text_or_id): Click a UI element.
            - input_text(text into field): Type text.
            - scroll(direction): Scroll up/down/left/right.
            - go_home(): Go to home screen.
            - go_back(): Go back.
            - open_chrome(query): Search/Open URL.
            - launch_app(name): Open an app.
            - read_screen(): Refresh screen content.
            - find_element(query): Get details (bounds/id) of a specific element.
            - wait_for_element(query|timeout): Wait for element to appear.
            - gesture(swipe ... / tap ...): Advanced interaction.

            Output strictly valid JSON:
            {
              "reasoning": "thought process",
              "tool_name": "tool_name",
              "tool_params": "parameters"
            }
        """.trimIndent()

        // Truncate history if too long to save context window
        val recentHistory = previousActions.takeLast(10)

        val textContent = """
            Screen Context (Accessibility Node Tree):
            $screenContext

            History (Last ${recentHistory.size} actions):
            ${recentHistory.joinToString("\n")}
        """.trimIndent()

        // Logic Compression: Use Data Classes for Request Structure
        val userContentList = mutableListOf<ContentPart>(
            ContentPart(type = "text", text = textContent)
        )

        if (!screenshotBase64.isNullOrEmpty()) {
            userContentList.add(
                ContentPart(
                    type = "image_url",
                    image_url = ImageUrl("data:image/jpeg;base64,$screenshotBase64")
                )
            )
        }

        val request = OpenAiRequest(
            model = model,
            messages = listOf(
                OpenAiMessage("system", systemPrompt),
                OpenAiMessage("user", userContentList)
            ),
            response_format = ResponseFormat("json_object"),
            max_tokens = 300
        )

        return gson.toJson(request)
    }

    // Data classes for JSON parsing
    private data class LlmResponse(val choices: List<Choice>?)
    private data class Choice(val message: Message?)
    private data class Message(val content: String?)
    private data class LlmThought(val reasoning: String?, val tool_name: String?, val tool_params: String?)
    private data class JsonPlan(val type: String?, val description: String?, val tasks: List<JsonTask>?)
    private data class JsonTask(val type: String?, val description: String?, val agent: String?)

    // Logic Compression: Request Data Classes
    private data class OpenAiRequest(
        val model: String,
        val messages: List<OpenAiMessage>,
        val response_format: ResponseFormat,
        val max_tokens: Int
    )
    private data class OpenAiMessage(
        val role: String,
        val content: Any // String or List<ContentPart>
    )
    private data class ContentPart(
        val type: String,
        val text: String? = null,
        val image_url: ImageUrl? = null
    )
    private data class ImageUrl(val url: String)
    private data class ResponseFormat(val type: String)

    class ApiException(val code: Int, message: String) : IOException(message)
}
