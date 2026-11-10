package com.agniv.hisaab.feature.expenses.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Why listening ended without text. Mapped to a friendly message on screen. */
enum class SpeechFailure { NO_SPEECH, NETWORK, BUSY, OTHER }

/**
 * Thin wrapper over Android's [SpeechRecognizer] (PRD F13: en-IN and hi-IN). Must be used from the main
 * thread. Nothing is recorded by us: Android's speech service turns the voice into text and hands back text.
 */
internal class SpeechController(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onFailure: (SpeechFailure) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(languageTag: String) {
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(listener)
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            },
        )
    }

    /** Ends listening and frees the recognizer. Safe to call twice. */
    fun stop() {
        recognizer?.apply {
            setRecognitionListener(null)
            cancel()
            destroy()
        }
        recognizer = null
    }

    private fun Bundle?.firstText(): String? = this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private val listener =
        object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.firstText()?.let(onPartial)
            }

            override fun onResults(results: Bundle?) {
                val text = results.firstText()
                if (text.isNullOrBlank()) onFailure(SpeechFailure.NO_SPEECH) else onFinal(text)
            }

            override fun onError(error: Int) {
                onFailure(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechFailure.NO_SPEECH
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                            SpeechFailure.NETWORK
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechFailure.BUSY
                        else -> SpeechFailure.OTHER
                    },
                )
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onEvent(
                eventType: Int,
                params: Bundle?,
            ) = Unit
        }
}
