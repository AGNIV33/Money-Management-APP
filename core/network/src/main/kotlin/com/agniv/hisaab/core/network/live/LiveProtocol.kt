package com.agniv.hisaab.core.network.live

import com.agniv.hisaab.core.domain.assistant.LiveEvent
import com.agniv.hisaab.core.domain.assistant.ToolAnswer
import com.agniv.hisaab.core.domain.assistant.ToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/**
 * The messages of the Gemini Live WebSocket, as plain JSON text. Kept free of any network code so it can be
 * tested with fixed samples. What the assistant says and may do is locked into the server token (see the
 * `ai-live-token` function), so the setup message only names the model and asks for audio.
 * One small function per message kind keeps each readable, hence the function count.
 */
@Suppress("TooManyFunctions")
internal object LiveProtocol {
    private const val MILLIS_PER_SECOND = 1000.0
    private val json = Json { ignoreUnknownKeys = true }

    fun setupMessage(
        model: String,
        resumeHandle: String?,
    ): String =
        buildJsonObject {
            putJsonObject("setup") {
                put("model", model)
                putJsonObject("generationConfig") { putJsonArray("responseModalities") { add(JsonPrimitive("AUDIO")) } }
                putJsonObject("sessionResumption") { resumeHandle?.let { put("handle", it) } }
                // Keeps a long conversation going instead of ending when the context fills up.
                putJsonObject("contextWindowCompression") { putJsonObject("slidingWindow") {} }
            }
        }.toString()

    fun audioMessage(
        pcm: ByteArray,
        length: Int,
    ): String =
        buildJsonObject {
            putJsonObject("realtimeInput") {
                putJsonObject("audio") {
                    put("data", Base64.getEncoder().encodeToString(pcm.copyOf(length)))
                    put("mimeType", "audio/pcm;rate=16000")
                }
            }
        }.toString()

    fun toolAnswersMessage(answers: List<ToolAnswer>): String =
        buildJsonObject {
            putJsonObject("toolResponse") {
                put(
                    "functionResponses",
                    buildJsonArray {
                        answers.forEach { a ->
                            add(
                                buildJsonObject {
                                    put("id", a.id)
                                    put("name", a.name)
                                    put("response", asObject(a.responseJson))
                                },
                            )
                        }
                    },
                )
            }
        }.toString()

    /** Anything that is not a JSON object becomes a plain error, so a bad answer never breaks the session. */
    private fun asObject(text: String): JsonObject =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse { buildJsonObject { put("error", "bad_answer") } }

    /** One server message can carry several things (audio pieces, then "interrupted", then "turn complete"). */
    fun parse(text: String): List<LiveEvent> {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
        val events = mutableListOf<LiveEvent>()
        if ("setupComplete" in root) events += LiveEvent.Ready
        root["serverContent"].asObjectOrNull()?.let { events += serverContent(it) }
        root["toolCall"].asObjectOrNull()?.let { toolCalls(it)?.let(events::add) }
        root["goAway"].asObjectOrNull()?.let { goAway(it)?.let(events::add) }
        root["sessionResumptionUpdate"].asObjectOrNull()?.let { resume(it)?.let(events::add) }
        return events
    }

    private fun serverContent(content: JsonObject): List<LiveEvent> {
        val events = mutableListOf<LiveEvent>()
        val parts = content["modelTurn"].asObjectOrNull()?.get("parts") as? JsonArray
        parts?.forEach { part ->
            val data =
                part
                    .asObjectOrNull()
                    ?.get("inlineData")
                    .asObjectOrNull()
                    ?.get("data")
                    .asStringOrNull()
            if (!data.isNullOrEmpty()) {
                runCatching { Base64.getDecoder().decode(data) }.getOrNull()?.let { events += LiveEvent.Audio(it) }
            }
        }
        if (content["interrupted"].asBoolean()) events += LiveEvent.Interrupted
        if (content["turnComplete"].asBoolean()) events += LiveEvent.TurnComplete
        return events
    }

    private fun toolCalls(call: JsonObject): LiveEvent.ToolCalls? {
        val list =
            (call["functionCalls"] as? JsonArray)?.mapNotNull { item ->
                val o = item.asObjectOrNull() ?: return@mapNotNull null
                val name = o["name"].asStringOrNull() ?: return@mapNotNull null
                ToolCall(o["id"].asStringOrNull().orEmpty(), name, (o["args"].asObjectOrNull() ?: JsonObject(emptyMap())).toString())
            }
        return list?.takeIf { it.isNotEmpty() }?.let { LiveEvent.ToolCalls(it) }
    }

    /** timeLeft arrives as text like "50s" or "12.5s". */
    private fun goAway(message: JsonObject): LiveEvent.GoAway? {
        val seconds = message["timeLeft"].asStringOrNull()?.removeSuffix("s")?.toDoubleOrNull() ?: return null
        return LiveEvent.GoAway((seconds * MILLIS_PER_SECOND).toLong())
    }

    private fun resume(update: JsonObject): LiveEvent.Resume? {
        val handle = update["newHandle"].asStringOrNull()
        return if (update["resumable"].asBoolean() && !handle.isNullOrEmpty()) LiveEvent.Resume(handle) else null
    }

    private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement?.asStringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonElement?.asBoolean(): Boolean = (this as? JsonPrimitive)?.booleanOrNull == true
}
