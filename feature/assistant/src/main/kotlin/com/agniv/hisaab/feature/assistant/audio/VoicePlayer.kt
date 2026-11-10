package com.agniv.hisaab.feature.assistant.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Plays the assistant's voice (16-bit mono PCM, 24 kHz) in the order it arrives. [flush] drops everything not yet
 * played, which is how interruptions work: the moment the user speaks, the assistant goes quiet.
 */
class VoicePlayer(
    private val onSpeakingChanged: (Boolean) -> Unit,
) {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val pending = AtomicInteger(0)
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile private var running = false

    @Volatile private var speaking = false

    fun start() {
        if (running) return
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val audio =
            AudioTrack
                .Builder()
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                ).setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                ).setBufferSizeInBytes(maxOf(minBuffer, SAMPLE_RATE))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        track = audio
        running = true
        audio.play()
        thread =
            Thread({ loop(audio) }, "kinly-voice").also {
                it.isDaemon = true
                it.start()
            }
    }

    fun enqueue(pcm: ByteArray) {
        if (!running || pcm.isEmpty()) return
        pending.incrementAndGet()
        setSpeaking(true)
        queue.put(pcm)
    }

    /** Throws away what has not been played yet. */
    fun flush() {
        queue.clear()
        pending.set(0)
        track?.let {
            runCatching {
                it.pause()
                it.flush()
                it.play()
            }
            // Devices differ on whether flush() resets the play position, so measure from here on.
            synchronized(lock) {
                headBase = it.playbackHeadPosition.toLong()
                framesWritten = 0
            }
        }
        setSpeaking(false)
    }

    private fun loop(audio: AudioTrack) {
        while (running) {
            val piece = queue.poll(POLL_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (piece == null) {
                if (speaking && pending.get() <= 0 && isDrained(audio)) setSpeaking(false)
                continue
            }
            synchronized(lock) { framesWritten += piece.size / BYTES_PER_FRAME }
            audio.write(piece, 0, piece.size)
            pending.decrementAndGet()
        }
    }

    private val lock = Any()
    private var framesWritten = 0L
    private var headBase = 0L

    private fun isDrained(audio: AudioTrack): Boolean =
        synchronized(lock) { audio.playbackHeadPosition.toLong() - headBase >= framesWritten }

    private fun setSpeaking(value: Boolean) {
        if (speaking == value) return
        speaking = value
        onSpeakingChanged(value)
    }

    fun stop() {
        running = false
        queue.clear()
        thread?.join(JOIN_MS)
        thread = null
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
        synchronized(lock) {
            framesWritten = 0
            headBase = 0
        }
        setSpeaking(false)
    }

    private companion object {
        const val SAMPLE_RATE = 24_000
        const val BYTES_PER_FRAME = 2
        const val POLL_MS = 80L
        const val JOIN_MS = 300L
    }
}
