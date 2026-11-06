package com.artemis.agent.ai

import com.artemis.agent.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiVlmClient(private val apiKey: String) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey"

    suspend fun decideNextAction(
        goal: String,
        step: Int,
        maxSteps: Int,
        history: List<String>,
        uiNodes: List<UiNode>,
        screenshotBase64: String?,
        isAdvancedMode: Boolean
    ): Pair<String, AgentAction> = withContext(Dispatchers.IO) {
        val systemPrompt = buildSystemPrompt(isAdvancedMode)
        val userPrompt = buildUserPrompt(goal, step, maxSteps, history, uiNodes)

        val requestJson = buildRequestBody(systemPrompt, userPrompt, screenshotBase64)
        val body = requestJson.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .post(body)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw IllegalStateException("Empty response from Gemini API")

        if (!response.isSuccessful) {
            throw IllegalStateException("Gemini API error (${response.code}): $responseBody")
        }

        parseModelResponse(responseBody)
    }

    private fun buildSystemPrompt(isAdvancedMode: Boolean): String = buildString {
        append("You are Artemis, an autonomous AI Agent operating an Android smartphone.\n")
        append("Your job is to accomplish the user's task step by step by observing the screen and UI elements.\n\n")
        append("Available actions:\n")
        append("- tap: {\"x\": float, \"y\": float, \"note\": string}\n")
        append("- click_node: {\"node_index\": int, \"note\": string}\n")
        append("- swipe: {\"startX\": float, \"startY\": float, \"endX\": float, \"endY\": float, \"durationMs\": int}\n")
        append("- scroll: {\"direction\": \"UP\"|\"DOWN\"|\"LEFT\"|\"RIGHT\"}\n")
        append("- type: {\"text\": string}\n")
        append("- global: {\"type\": \"BACK\"|\"HOME\"|\"RECENTS\"|\"NOTIFICATIONS\"}\n")
        append("- launch_app: {\"package_name\": string}\n")
        append("- open_url: {\"url\": string}\n")
        append("- wait: {\"seconds\": int}\n")
        append("- finish: {\"summary\": string, \"success\": boolean}\n")

        if (isAdvancedMode) {
            append("\n[Advanced Mode Activated - ADB Powers Enabled]:\n")
            append("- adb_shell: {\"command\": string} (Execute shell commands directly)\n")
            append("- force_stop: {\"package_name\": string} (Force stop background apps)\n")
            append("- read_logcat: {\"filter\": string, \"lines\": int} (Inspect system logs & crashes)\n")
        }

        append("\nRespond STRICTLY with a single JSON object (no markdown, no extra explanation):\n")
        append("{\n")
        append("  \"thought\": \"brief reasoning in 1-2 sentences\",\n")
        append("  \"action\": \"tap\" | \"click_node\" | \"swipe\" | \"scroll\" | \"type\" | \"global\" | \"launch_app\" | \"open_url\" | \"wait\" | \"adb_shell\" | \"force_stop\" | \"read_logcat\" | \"finish\",\n")
        append("  \"params\": { ... }\n")
        append("}\n")
    }

    private fun buildUserPrompt(
        goal: String,
        step: Int,
        maxSteps: Int,
        history: List<String>,
        uiNodes: List<UiNode>
    ): String = buildString {
        append("USER GOAL: $goal\n")
        append("CURRENT STEP: $step of $maxSteps\n\n")

        if (history.isNotEmpty()) {
            append("PREVIOUS ACTIONS:\n")
            history.takeLast(5).forEach { append("- $it\n") }
            append("\n")
        }

        append("CURRENT UI HIERARCHY (Visible Elements):\n")
        if (uiNodes.isEmpty()) {
            append("(No accessibility nodes available, use visual coordinates)\n")
        } else {
            uiNodes.take(80).forEach { append(it.toCompactString()).append("\n") }
        }
    }

    private fun buildRequestBody(systemPrompt: String, userPrompt: String, screenshotBase64: String?): String {
        val root = JsonObject()

        // System Instruction
        val sysInstruction = JsonObject()
        val sysParts = com.google.gson.JsonArray()
        val sysText = JsonObject()
        sysText.addProperty("text", systemPrompt)
        sysParts.add(sysText)
        sysInstruction.add("parts", sysParts)
        root.add("system_instruction", sysInstruction)

        // Contents
        val contents = com.google.gson.JsonArray()
        val content = JsonObject()
        val parts = com.google.gson.JsonArray()

        // Add Screenshot Image if available
        if (!screenshotBase64.isNullOrBlank()) {
            val imagePart = JsonObject()
            val inlineData = JsonObject()
            inlineData.addProperty("mime_type", "image/jpeg")
            inlineData.addProperty("data", screenshotBase64)
            imagePart.add("inline_data", inlineData)
            parts.add(imagePart)
        }

        // Add Text Prompt
        val textPart = JsonObject()
        textPart.addProperty("text", userPrompt)
        parts.add(textPart)

        content.add("parts", parts)
        contents.add(content)
        root.add("contents", contents)

        // Generation Config
        val genConfig = JsonObject()
        genConfig.addProperty("temperature", 0.2)
        genConfig.addProperty("response_mime_type", "application/json")
        root.add("generationConfig", genConfig)

        return gson.toJson(root)
    }

    private fun parseModelResponse(jsonResponse: String): Pair<String, AgentAction> {
        val root = gson.fromJson(jsonResponse, JsonObject::class.java)
        val candidates = root.getAsJsonArray("candidates")
        val content = candidates[0].asJsonObject.getAsJsonObject("content")
        val parts = content.getAsJsonArray("parts")
        val text = parts[0].asJsonObject.get("text").asString.trim()

        val cleanJson = text
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val parsed = gson.fromJson(cleanJson, JsonObject::class.java)
        val thought = parsed.get("thought")?.asString ?: "Executing next step..."
        val actionType = parsed.get("action")?.asString?.lowercase() ?: "wait"
        val params = parsed.getAsJsonObject("params") ?: JsonObject()

        val action = when (actionType) {
            "tap" -> {
                val x = params.get("x").asFloat
                val y = params.get("y").asFloat
                val note = params.get("note")?.asString ?: ""
                AgentAction.Tap(x, y, note)
            }
            "click_node" -> {
                val index = params.get("node_index").asInt
                val note = params.get("note")?.asString ?: ""
                AgentAction.ClickNode(index, note)
            }
            "swipe" -> {
                val startX = params.get("startX").asFloat
                val startY = params.get("startY").asFloat
                val endX = params.get("endX").asFloat
                val endY = params.get("endY").asFloat
                val duration = params.get("durationMs")?.asLong ?: 300L
                AgentAction.Swipe(startX, startY, endX, endY, duration)
            }
            "scroll" -> {
                val dir = params.get("direction")?.asString?.uppercase() ?: "DOWN"
                val scrollDir = runCatching { ScrollDirection.valueOf(dir) }.getOrDefault(ScrollDirection.DOWN)
                AgentAction.Scroll(scrollDir)
            }
            "type" -> {
                val input = params.get("text")?.asString ?: ""
                AgentAction.TypeText(input)
            }
            "global" -> {
                val typeStr = params.get("type")?.asString?.uppercase() ?: "BACK"
                val globalType = runCatching { GlobalActionType.valueOf(typeStr) }.getOrDefault(GlobalActionType.BACK)
                AgentAction.Global(globalType)
            }
            "launch_app" -> {
                val pkg = params.get("package_name")?.asString ?: ""
                AgentAction.LaunchApp(pkg)
            }
            "open_url" -> {
                val url = params.get("url")?.asString ?: ""
                AgentAction.OpenUrl(url)
            }
            "adb_shell" -> {
                val cmd = params.get("command")?.asString ?: ""
                AgentAction.AdbShell(cmd)
            }
            "force_stop" -> {
                val pkg = params.get("package_name")?.asString ?: ""
                AgentAction.ForceStop(pkg)
            }
            "read_logcat" -> {
                val filter = params.get("filter")?.asString ?: ""
                val lines = params.get("lines")?.asInt ?: 50
                AgentAction.ReadLogcat(filter, lines)
            }
            "finish" -> {
                val summary = params.get("summary")?.asString ?: "Task completed."
                val success = params.get("success")?.asBoolean ?: true
                AgentAction.Finish(summary, success)
            }
            else -> AgentAction.Wait(2)
        }

        return Pair(thought, action)
    }
}
