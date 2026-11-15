package com.sajith.scrolllock.ui.workout

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * AI Voice Fitness Coach powered by Android TextToSpeech.
 * Announces rep counts, debounced posture corrections, rest periods, and workout completion.
 */
class VoiceCoachManager(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    var isEnabled: Boolean = true
    private var lastCueSpokenMs = 0L
    private var lastSpokenCue = ""

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setSpeechRate(1.03f)
            ready = true
        }
    }

    fun speakRepCount(rep: Int, target: Int) {
        if (!ready || !isEnabled) return
        val phrase = when {
            rep == target -> "$rep! Goal reached! Great work."
            rep == target / 2 && target >= 6 -> "$rep. Halfway there!"
            else -> "$rep"
        }
        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "rep_$rep")
    }

    fun speakPostureCue(cue: String) {
        if (!ready || !isEnabled || cue.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        // Debounce posture cues to at most once every 3.5 seconds so voice stays helpful
        if ((now - lastCueSpokenMs) < 3500L || (cue == lastSpokenCue && (now - lastCueSpokenMs) < 6000L)) {
            return
        }
        lastCueSpokenMs = now
        lastSpokenCue = cue
        tts?.speak(cue, TextToSpeech.QUEUE_ADD, null, "cue_${now}")
    }

    fun speakMessage(message: String) {
        if (!ready || !isEnabled || message.isBlank()) return
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "msg_${SystemClock.elapsedRealtime()}")
    }

    fun shutdown() {
        ready = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
