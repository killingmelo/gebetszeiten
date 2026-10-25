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
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10

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
        val name = file.fileName.toString().lowercase()
        runCatching {
            when {
                name.endsWith(".mp3") -> playMp3(file, volumePercent)
                name.endsWith(".wav") -> playWav(file, volumePercent)
            }
        }.onFailure { stop() }
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
        val player = AdvancedPlayer(input, GainDevice(gainDb(volumePercent)))
        val gen = synchronized(lock) { mp3 = player; generation }
        Thread({
            runCatching { player.play() }
            runCatching { input.close() }
            synchronized(lock) { if (generation == gen && mp3 === player) mp3 = null }
        }, "ezan-mp3").apply { isDaemon = true }.start()
    }

    private fun playWav(file: Path, volumePercent: Int) {
        val stream = AudioSystem.getAudioInputStream(file.toFile())
        val c = AudioSystem.getClip()
        c.open(stream)
        runCatching {
            val gain = c.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            gain.value = gainDb(volumePercent).coerceIn(gain.minimum, gain.maximum)
        }
        val gen = synchronized(lock) { clip = c; generation }
        c.addLineListener { e ->
            if (e.type == LineEvent.Type.STOP) {
                synchronized(lock) { if (generation == gen && clip === c) clip = null }
                runCatching { c.close() }
            }
        }
        c.start()
    }

    /** JLayer-Ausgabe mit Lautstaerke: setzt MASTER_GAIN, sobald die Leitung offen ist. */
    private class GainDevice(private val db: Float) : JavaSoundAudioDevice() {
        override fun createSource() {
            super.createSource()
            // JLayer 1.0.1.4 hat keinen Lautstaerke-Zugriff; die offene Leitung steckt im privaten Feld.
            runCatching {
                val field = JavaSoundAudioDevice::class.java.getDeclaredField("source").apply { isAccessible = true }
                val line = field.get(this) as? SourceDataLine ?: return@runCatching
                val gain = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                gain.value = db.coerceIn(gain.minimum, gain.maximum)
            }
        }
    }

    private companion object {
        /** 100 % = unveraendert (0 dB), darunter logarithmisch bis -60 dB bei 0 %. */
        fun gainDb(percent: Int): Float {
            val p = percent.coerceIn(0, 100)
            return if (p == 0) -60f else (20 * log10(p / 100.0)).toFloat().coerceAtLeast(-60f)
        }
    }
}
