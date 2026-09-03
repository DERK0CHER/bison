package net.bison.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * A short sound on each answer.
 *
 * ToneGenerator's stock tones were the telephone keypad's: a hard square-wave beep that starts
 * and stops at full volume, which is exactly what makes a device sound cheap. These are sine
 * waves rendered here instead - a fifth rising for right, a third falling for wrong - each note
 * eased in over a few milliseconds and left to decay, so nothing clicks and nothing buzzes.
 *
 * Still no assets, no permission and no playback library: a couple of hundred milliseconds of
 * samples is a few kilobytes of arithmetic.
 */
class Feedback {
    private var yes: AudioTrack? = null
    private var no: AudioTrack? = null

    fun play(correct: Boolean) {
        runCatching {
            val track =
                if (correct) {
                    yes ?: build(RIGHT).also { yes = it }
                } else {
                    no ?: build(WRONG).also { no = it }
                }
            track?.let {
                // a static track keeps its samples; it only has to be stopped and rewound
                if (it.playState != AudioTrack.PLAYSTATE_STOPPED) it.stop()
                it.reloadStaticData()
                it.play()
            }
        }.onFailure { Log.w(TAG, "could not play the sound", it) }
    }

    /** Frees the audio resources; they are rebuilt on the next sound if needed */
    fun release() {
        for (track in listOfNotNull(yes, no)) {
            runCatching {
                if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
                track.release()
            }
        }
        yes = null
        no = null
    }

    /** One note of the little two note figure */
    private data class Note(
        val hz: Double,
        val ms: Int,
    )

    private fun build(notes: List<Note>): AudioTrack? =
        runCatching {
            val data = render(notes)
            val track =
                AudioTrack
                    .Builder()
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            // USAGE_MEDIA, not ASSISTANCE_SONIFICATION: sonification plays on
                            // the system stream, which the volume keys do not reach while an app
                            // is in front, so the tones could not be turned down at all.
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    ).setAudioFormat(
                        AudioFormat
                            .Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    ).setBufferSizeInBytes(data.size * Short.SIZE_BYTES)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
            track.write(data, 0, data.size)
            track
        }.onFailure { Log.w(TAG, "no audio track available", it) }
            .getOrNull()

    /**
     * Turns the notes into 16 bit samples.
     *
     * The envelope is the whole point. Each note fades in over [ATTACK_MS] so it does not click,
     * decays away over its length so it does not sit flat, and is faded to silence over the last
     * [TAIL_MS] so the buffer ends at zero rather than mid wave.
     */
    private fun render(notes: List<Note>): ShortArray {
        val samples = ShortArray(notes.sumOf { it.ms } * SAMPLE_RATE / 1000)
        var written = 0
        for (note in notes) {
            val length = note.ms * SAMPLE_RATE / 1000
            val attack = ATTACK_MS * SAMPLE_RATE / 1000.0
            val tail = TAIL_MS * SAMPLE_RATE / 1000.0
            for (i in 0 until length) {
                val envelope =
                    AMPLITUDE *
                        min(1.0, i / attack) *
                        min(1.0, (length - i) / tail) *
                        exp(-DECAY * i / length)
                val wave = sin(2 * PI * note.hz * i / SAMPLE_RATE)
                samples[written + i] = (wave * envelope * Short.MAX_VALUE).toInt().toShort()
            }
            written += length
        }
        return samples
    }

    private companion object {
        const val TAG = "Feedback"
        const val SAMPLE_RATE = 44100

        /** Quiet enough to live with for a hundred questions in a row */
        const val AMPLITUDE = 0.25

        const val ATTACK_MS = 5
        const val TAIL_MS = 12

        /** How sharply a note falls away over its own length */
        const val DECAY = 2.6

        /** G5 up to D6: a rising fifth, which is about as plainly "yes" as two notes get */
        val RIGHT = listOf(Note(783.99, 70), Note(1174.66, 130))

        /** E flat 4 down to B flat 3: a falling third, dull rather than scolding */
        val WRONG = listOf(Note(311.13, 90), Note(233.08, 160))
    }
}
