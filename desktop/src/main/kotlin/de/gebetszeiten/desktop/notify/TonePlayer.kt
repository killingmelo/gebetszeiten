package de.gebetszeiten.desktop.notify

import de.gebetszeiten.desktop.data.ToneChoice
import java.nio.file.Path
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

private const val RATE = 44_100f

/**
 * Ein Partialton: Frequenz, Anteil, Abklingzeit (s), Einsatz (s).
 * Glockenklaenge sind Summen solcher Teile.
 */
private data class Partial(val hz: Double, val amp: Double, val decay: Double, val start: Double = 0.0)

private fun partials(choice: ToneChoice): List<Partial> = when (choice) {
    // Warm, einmal angeschlagen: Grundton + leise Oktave.
    ToneChoice.SOFT -> listOf(Partial(587.3, 0.7, 0.9), Partial(1174.7, 0.18, 0.5))
    // Kleine Glocke: unharmonische Teiltoene wie bei einer echten Glocke.
    ToneChoice.BELL -> listOf(
        Partial(523.3, 0.55, 1.8), Partial(1046.5, 0.25, 1.2), Partial(1444.0, 0.18, 0.9),
        Partial(2826.0, 0.08, 0.4), Partial(261.6, 0.12, 2.2),
    )
    // Zwei Toene abwaerts, deutlicher fuer Warnungen.
    ToneChoice.DOUBLE -> listOf(
        Partial(880.0, 0.6, 0.35), Partial(1760.0, 0.12, 0.2),
        Partial(659.3, 0.6, 0.6, start = 0.28), Partial(1318.5, 0.12, 0.3, start = 0.28),
    )
    ToneChoice.OFF, ToneChoice.FILE -> emptyList()
}

/**
 * 16-bit-PCM, mono, 44,1 kHz fuer einen eingebauten Klang, bereits mit
 * [volumeFactor] skaliert. Leer bei [ToneChoice.OFF]/[ToneChoice.FILE].
 * Kurzer Einschwinger (5 ms) gegen Knacken.
 */
fun synthesize(choice: ToneChoice, volumePercent: Int): ShortArray {
    val parts = partials(choice)
    if (parts.isEmpty()) return ShortArray(0)
    val seconds = parts.maxOf { it.start + it.decay * 4.5 }
    val n = (seconds * RATE).roundToInt()
    val gain = volumeFactor(volumePercent)
    val attack = 0.005
    val out = ShortArray(n)
    for (i in 0 until n) {
        val t = i / RATE.toDouble()
        var v = 0.0
        for (p in parts) {
            val local = t - p.start
            if (local < 0) continue
            val env = exp(-local / p.decay) * (local / attack).coerceAtMost(1.0)
            v += p.amp * env * sin(2 * PI * p.hz * local)
        }
        out[i] = (v * gain * 0.8 * Short.MAX_VALUE).roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    return out
}

/**
 * Spielt Erinnerungstoene: eingebaute ueber einen `Clip`, eigene Dateien
 * ueber einen eigenen [EzanPlayer] (laeuft unabhaengig vom Ezan). Ein neuer
 * Ton beendet den vorigen.
 */
class TonePlayer {
    private val files = EzanPlayer()
    private var clip: Clip? = null

    @Synchronized
    fun play(choice: ToneChoice, file: String?, volumePercent: Int) {
        stop()
        if (volumePercent <= 0) return
        when (choice) {
            ToneChoice.OFF -> Unit
            ToneChoice.FILE -> file?.let { files.play(Path.of(it), volumePercent) }
            else -> runCatching { playPcm(synthesize(choice, volumePercent)) }
                .onFailure { System.err.println("Ton: ${it.javaClass.simpleName}: ${it.message}") }
        }
    }

    @Synchronized
    fun stop() {
        files.stop()
        clip?.let { runCatching { it.stop(); it.close() } }
        clip = null
    }

    private fun playPcm(samples: ShortArray) {
        if (samples.isEmpty()) return
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = samples[i].toInt()
            bytes[2 * i] = (s and 0xFF).toByte()
            bytes[2 * i + 1] = (s shr 8 and 0xFF).toByte()
        }
        val c = AudioSystem.getClip()
        c.open(AudioFormat(RATE, 16, 1, true, false), bytes, 0, bytes.size)
        c.addLineListener { e -> if (e.type == LineEvent.Type.STOP) runCatching { c.close() } }
        clip = c
        c.start()
    }
}
