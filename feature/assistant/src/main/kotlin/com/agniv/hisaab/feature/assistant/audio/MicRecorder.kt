package com.agniv.hisaab.feature.assistant.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import kotlin.math.abs

/**
 * Reads the microphone as 16-bit mono PCM at 16 kHz in pieces of about 100 ms, which is what the live service
 * expects. Uses the voice-call source so the phone's own echo cancelling and noise removal can run, which stops
 * the assistant from hearing itself through the speaker.
 */
class MicRecorder(
    private val onChunk: (buffer: ByteArray, length: Int, loud: Boolean) -> Unit,
) {
    @Volatile private var running = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null

    /** Returns false when the microphone could not be opened (permission missing or in use). */
    @SuppressLint("MissingPermission") // the sheet asks for RECORD_AUDIO right before calling start()
    fun start(): Boolean {
        if (!running) openRecorder()?.let(::begin)
        return running
    }

    private val chunkBytes = SAMPLE_RATE * BYTES_PER_SAMPLE * CHUNK_MS / MILLIS_PER_SECOND

    @SuppressLint("MissingPermission") // see start()
    private fun openRecorder(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder =
            if (minBuffer > 0) {
                runCatching {
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(minBuffer, chunkBytes * 2),
                    )
                }.getOrNull()
            } else {
                null
            }
        if (recorder != null && recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }
        return recorder
    }

    private fun begin(recorder: AudioRecord) {
        attachEffects(recorder.audioSessionId)
        record = recorder
        running = true
        recorder.startRecording()
        thread =
            Thread({ loop(recorder, chunkBytes) }, "kinly-mic").also {
                it.isDaemon = true
                it.start()
            }
    }

    private fun loop(
        recorder: AudioRecord,
        chunkBytes: Int,
    ) {
        val buffer = ByteArray(chunkBytes)
        while (running) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read > 0) onChunk(buffer, read, isLoud(buffer, read))
            if (read < 0) break
        }
    }

    /** Rough "is someone talking" test, used only to stop the session after a long silence. */
    private fun isLoud(
        buffer: ByteArray,
        length: Int,
    ): Boolean {
        var total = 0L
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val sample = (buffer[i + 1].toInt() shl BYTE_BITS) or (buffer[i].toInt() and BYTE_MASK)
            total += abs(sample)
            count++
            i += BYTES_PER_SAMPLE
        }
        return count > 0 && total / count > LOUD_LEVEL
    }

    private fun attachEffects(sessionId: Int) {
        if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(sessionId)?.enabled = true }
        if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(sessionId)?.enabled = true }
        if (AutomaticGainControl.isAvailable()) runCatching { AutomaticGainControl.create(sessionId)?.enabled = true }
    }

    fun isRunning(): Boolean = running

    fun stop() {
        running = false
        thread?.join(JOIN_MS)
        thread = null
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val BYTES_PER_SAMPLE = 2
        const val CHUNK_MS = 100
        const val MILLIS_PER_SECOND = 1000
        const val BYTE_BITS = 8
        const val BYTE_MASK = 0xFF
        const val LOUD_LEVEL = 600
        const val JOIN_MS = 300L
    }
}
