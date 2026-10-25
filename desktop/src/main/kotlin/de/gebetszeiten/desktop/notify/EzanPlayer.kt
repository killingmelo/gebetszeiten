package de.gebetszeiten.desktop.notify

import javazoom.jl.player.JavaSoundAudioDevice
import javazoom.jl.player.advanced.AdvancedPlayer
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import javax.sound.sampled.LineEvent
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * Spielt den Ezan: `.mp3` ueber JLayer auf eigenem Thread, `.wav` ueber einen
 * `Clip`. Fehlt die Datei oder ist sie kaputt, bleibt es still; ein neues
 * [play] stoppt das laufende.
 */
class EzanPlayer {
    private val lock = Any()
    private var mp3: AdvancedPlayer? = null
    private var clip: Clip? = null
    private var generation = 0

    val isPlaying: Boolean
        get() = synchronized(lock) { mp3 != null || clip != null }

    fun play(file: Path, volumePercent: Int) {
        stop()
        if (!Files.isRegularFile(file)) return
        if (volumePercent <= 0) return // 0 % = stumm, nichts abspielen
        val name = file.fileName.toString().lowercase()
        runCatching {
            when {
                name.endsWith(".mp3") -> playMp3(file, volumePercent)
                name.endsWith(".wav") -> playWav(file, volumePercent)
            }
        }.onFailure { logAudio("Wiedergabe von $name", it); stop() }
    }

    fun stop() {
        val (p, c) = synchronized(lock) {
            generation++
            val pair = mp3 to clip
            mp3 = null
            clip = null
            pair
        }
        runCatching { p?.close() }
        runCatching { c?.stop(); c?.close() }
    }

    private fun playMp3(file: Path, volumePercent: Int) {
        val input = BufferedInputStream(Files.newInputStream(file))
        val player = AdvancedPlayer(input, GainDevice(volumeFactor(volumePercent)))
        val gen = synchronized(lock) { mp3 = player; generation }
        Thread({
            runCatching { player.play() }.onFailure { logAudio("MP3", it) }
            runCatching { input.close() }
            synchronized(lock) { if (generation == gen && mp3 === player) mp3 = null }
        }, "ezan-mp3").apply { isDaemon = true }.start()
    }

    private fun playWav(file: Path, volumePercent: Int) {
        val stream = AudioSystem.getAudioInputStream(file.toFile())
        val c = AudioSystem.getClip()
        c.open(stream)
        // Ohne Lautstaerkeregelung lieber still als mit 100 %: Fehler -> play() faengt, loggt, stoppt.
        val gain = c.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        gain.value = gainDb(volumePercent).coerceIn(gain.minimum, gain.maximum)
        val gen = synchronized(lock) { clip = c; generation }
        c.addLineListener { e ->
            if (e.type == LineEvent.Type.STOP) {
                synchronized(lock) { if (generation == gen && clip === c) clip = null }
                runCatching { c.close() }
            }
        }
        c.start()
    }

    /**
     * JLayer-Ausgabe mit Lautstaerke: skaliert die dekodierten PCM-Samples vor
     * dem Schreiben auf die Leitung (kein Zugriff auf JLayer-Interna noetig).
     */
    private class GainDevice(private val factor: Double) : JavaSoundAudioDevice() {
        override fun writeImpl(samples: ShortArray, offs: Int, len: Int) {
            scaleSamples(samples, offs, len, factor)
            super.writeImpl(samples, offs, len)
        }
    }
}

/**
 * Lautstaerke 0..100 % als linearer Faktor auf die Amplitude: quadratisch
 * ((p/100)^2), das kommt der empfundenen Lautstaerke naeher als linear.
 * 0 % = 0.0 (still), 100 % = 1.0 (unveraendert), 20 % = 0.04 (-28 dB).
 */
fun volumeFactor(percent: Int): Double {
    val p = percent.coerceIn(0, 100) / 100.0
    return p * p
}

/** Skaliert [len] Samples ab [offs] in place, begrenzt auf den Short-Bereich. */
fun scaleSamples(samples: ShortArray, offs: Int, len: Int, factor: Double) {
    for (i in offs until offs + len) {
        samples[i] = (samples[i] * factor).roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}

private fun logAudio(what: String, e: Throwable) {
    System.err.println("Ezan: $what: ${e.javaClass.simpleName}: ${e.message}")
}

/** dB fuer MASTER_GAIN (WAV), aus demselben [volumeFactor]; min. -60 dB. */
private fun gainDb(percent: Int): Float = (20 * log10(volumeFactor(percent))).toFloat().coerceAtLeast(-60f)
