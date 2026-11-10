package com.agniv.hisaab.core.network.live

import com.agniv.hisaab.core.domain.assistant.LiveEvent
import com.agniv.hisaab.core.domain.assistant.ToolAnswer
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.util.Base64

class LiveProtocolTest {
    private fun parse(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `setup asks for audio only and carries no text or secrets`() {
        val setup = parse(LiveProtocol.setupMessage("models/gemini-3.8-live", null))["setup"]!!.jsonObject
        assertThat(setup["model"]!!.jsonPrimitive.content).isEqualTo("models/gemini-3.8-live")
        assertThat(setup.toString()).contains("\"AUDIO\"")
        assertThat(setup.toString()).doesNotContain("TEXT")
        assertThat(setup["sessionResumption"]!!.jsonObject).isEmpty()
    }

    @Test
    fun `setup passes the resume handle`() {
        val setup = parse(LiveProtocol.setupMessage("models/m", "abc123"))["setup"]!!.jsonObject
        assertThat(setup["sessionResumption"]!!.jsonObject["handle"]!!.jsonPrimitive.content).isEqualTo("abc123")
    }

    @Test
    fun `audio is sent as base64 pcm at 16 kHz and only the filled part`() {
        val pcm = byteArrayOf(1, 2, 3, 4, 99, 99)
        val audio = parse(LiveProtocol.audioMessage(pcm, 4))["realtimeInput"]!!.jsonObject["audio"]!!.jsonObject
        assertThat(audio["mimeType"]!!.jsonPrimitive.content).isEqualTo("audio/pcm;rate=16000")
        assertThat(Base64.getDecoder().decode(audio["data"]!!.jsonPrimitive.content)).isEqualTo(byteArrayOf(1, 2, 3, 4))
    }

    @Test
    fun `tool answers are wrapped and a broken answer becomes a plain error`() {
        val message =
            LiveProtocol.toolAnswersMessage(
                listOf(ToolAnswer("1", "finance_snapshot", """{"spent":10}"""), ToolAnswer("2", "loans_summary", "not json")),
            )
        val answers = parse(message)["toolResponse"]!!.jsonObject["functionResponses"].toString()
        assertThat(answers).contains("\"spent\":10")
        assertThat(answers).contains("bad_answer")
    }

    @Test
    fun `setup complete becomes ready`() {
        assertThat(LiveProtocol.parse("""{"setupComplete":{}}""")).containsExactly(LiveEvent.Ready)
    }

    @Test
    fun `audio parts are decoded in order, then interrupted and turn complete`() {
        val a = Base64.getEncoder().encodeToString(byteArrayOf(5, 6))
        val b = Base64.getEncoder().encodeToString(byteArrayOf(7))
        val part1 = """{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$a"}}"""
        val part2 = """{"inlineData":{"data":"$b"}}"""
        val text = """{"serverContent":{"modelTurn":{"parts":[$part1,$part2]},"interrupted":true,"turnComplete":true}}"""
        val events = LiveProtocol.parse(text)
        assertThat(events).hasSize(4)
        assertThat((events[0] as LiveEvent.Audio).pcm).isEqualTo(byteArrayOf(5, 6))
        assertThat((events[1] as LiveEvent.Audio).pcm).isEqualTo(byteArrayOf(7))
        assertThat(events[2]).isEqualTo(LiveEvent.Interrupted)
        assertThat(events[3]).isEqualTo(LiveEvent.TurnComplete)
    }

    @Test
    fun `tool calls keep id name and args`() {
        val events =
            LiveProtocol.parse(
                """{"toolCall":{"functionCalls":[""" +
                    """{"id":"x1","name":"emi_options","args":{"price_rupees":150000}},""" +
                    """{"id":"x2","name":"loans_summary"}]}}""",
            )
        val calls = (events.single() as LiveEvent.ToolCalls).calls
        assertThat(calls.map { it.name }).containsExactly("emi_options", "loans_summary").inOrder()
        assertThat(calls[0].id).isEqualTo("x1")
        assertThat(calls[0].argsJson).contains("150000")
        assertThat(calls[1].argsJson).isEqualTo("{}")
    }

    @Test
    fun `go away time and resume handle are read`() {
        assertThat(LiveProtocol.parse("""{"goAway":{"timeLeft":"12.5s"}}""")).containsExactly(LiveEvent.GoAway(12_500))
        assertThat(LiveProtocol.parse("""{"sessionResumptionUpdate":{"newHandle":"h1","resumable":true}}"""))
            .containsExactly(LiveEvent.Resume("h1"))
        assertThat(LiveProtocol.parse("""{"sessionResumptionUpdate":{"newHandle":"h1","resumable":false}}""")).isEmpty()
    }

    @Test
    fun `garbage and unknown messages are ignored`() {
        assertThat(LiveProtocol.parse("not json")).isEmpty()
        assertThat(LiveProtocol.parse("""{"usageMetadata":{"totalTokenCount":5}}""")).isEmpty()
        assertThat(LiveProtocol.parse("""{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"data":"@@@"}}]}}}""")).isEmpty()
    }
}
