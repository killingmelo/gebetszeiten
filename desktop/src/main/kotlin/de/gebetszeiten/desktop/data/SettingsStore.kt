package de.gebetszeiten.desktop.data

import de.gebetszeiten.core.prayertimes.Prayer
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

/**
 * `settings.properties` im Datenordner, UTF-8. Unbekannte oder kaputte Werte
 * fallen einzeln auf den Standard zurueck; eine unlesbare Datei ergibt
 * lauter Standardwerte.
 */
class SettingsStore(private val dir: Path) {

    private val file: Path get() = dir.resolve("settings.properties")

    fun load(): DesktopSettings {
        val p = Properties()
        try {
            if (Files.isRegularFile(file)) {
                Files.newBufferedReader(file, StandardCharsets.UTF_8).use { p.load(it) }
            }
        } catch (e: IOException) {
            return DesktopSettings()
        } catch (e: IllegalArgumentException) {
            return DesktopSettings()
        }
        val d = DesktopSettings()
        val lat = p.double("latitude")
        val lng = p.double("longitude")
        // Ein halber Ort ist keiner.
        val hasPlace = lat != null && lng != null
        return DesktopSettings(
            placeName = if (hasPlace) p.text("placeName") else null,
            latitude = if (hasPlace) lat else null,
            longitude = if (hasPlace) lng else null,
            diyanetId = if (hasPlace) p.int("diyanetId") else null,
            reminders = p.reminders() ?: d.reminders,
            leadMinutes = p.int("leadMinutes")?.takeIf { it >= 0 } ?: d.leadMinutes,
            karahaWarning = p.bool("karahaWarning") ?: d.karahaWarning,
            showKaraha = p.bool("showKaraha") ?: d.showKaraha,
            exactRemaining = p.bool("exactRemaining") ?: d.exactRemaining,
            endWarningMinutes = p.int("endWarningMinutes")?.takeIf { it in 0..120 } ?: d.endWarningMinutes,
            cumaHints = p.bool("cumaHints") ?: d.cumaHints,
            fastingHints = p.bool("fastingHints") ?: d.fastingHints,
            nafileHints = p.bool("nafileHints") ?: d.nafileHints,
            hijriHints = p.bool("hijriHints") ?: d.hijriHints,
            ramazanHints = p.bool("ramazanHints") ?: d.ramazanHints,
            sahurMinutes = p.int("sahurMinutes")?.takeIf { it in 0..180 } ?: d.sahurMinutes,
            iftarLeadMinutes = p.int("iftarLeadMinutes")?.takeIf { it in 0..60 } ?: d.iftarLeadMinutes,
            showTexts = p.bool("showTexts") ?: d.showTexts,
            notifyStyle = p.enum<NotifyStyle>("notifyStyle") ?: d.notifyStyle,
            bannerSeconds = p.int("bannerSeconds")?.takeIf { it in 0..600 } ?: d.bannerSeconds,
            tonePrayer = p.enum<ToneChoice>("tonePrayer") ?: d.tonePrayer,
            toneLead = p.enum<ToneChoice>("toneLead") ?: d.toneLead,
            toneWarning = p.enum<ToneChoice>("toneWarning") ?: d.toneWarning,
            toneHint = p.enum<ToneChoice>("toneHint") ?: d.toneHint,
            tonePrayerFile = p.text("tonePrayerFile"),
            toneLeadFile = p.text("toneLeadFile"),
            toneWarningFile = p.text("toneWarningFile"),
            toneHintFile = p.text("toneHintFile"),
            toneVolume = p.int("toneVolume")?.takeIf { it in 0..100 } ?: d.toneVolume,
            ezanFile = p.text("ezanFile"),
            fajrEzanFile = p.text("fajrEzanFile"),
            ezanVolume = p.int("ezanVolume")?.takeIf { it in 0..100 } ?: d.ezanVolume,
            taskbarField = p.bool("taskbarField") ?: d.taskbarField,
            autostart = p.bool("autostart") ?: d.autostart,
        )
    }

    fun save(s: DesktopSettings) {
        val p = Properties()
        fun put(key: String, value: Any?) {
            if (value != null) p.setProperty(key, value.toString())
        }
        put("placeName", s.placeName)
        put("latitude", s.latitude)
        put("longitude", s.longitude)
        put("diyanetId", s.diyanetId)
        p.setProperty("reminders", s.reminders.joinToString(",") { it.name })
        put("leadMinutes", s.leadMinutes)
        put("karahaWarning", s.karahaWarning)
        put("showKaraha", s.showKaraha)
        put("exactRemaining", s.exactRemaining)
        put("endWarningMinutes", s.endWarningMinutes)
        put("cumaHints", s.cumaHints)
        put("fastingHints", s.fastingHints)
        put("nafileHints", s.nafileHints)
        put("hijriHints", s.hijriHints)
        put("ramazanHints", s.ramazanHints)
        put("sahurMinutes", s.sahurMinutes)
        put("iftarLeadMinutes", s.iftarLeadMinutes)
        put("showTexts", s.showTexts)
        put("notifyStyle", s.notifyStyle.name)
        put("bannerSeconds", s.bannerSeconds)
        put("tonePrayer", s.tonePrayer.name)
        put("toneLead", s.toneLead.name)
        put("toneWarning", s.toneWarning.name)
        put("toneHint", s.toneHint.name)
        put("tonePrayerFile", s.tonePrayerFile)
        put("toneLeadFile", s.toneLeadFile)
        put("toneWarningFile", s.toneWarningFile)
        put("toneHintFile", s.toneHintFile)
        put("toneVolume", s.toneVolume)
        put("ezanFile", s.ezanFile)
        put("fajrEzanFile", s.fajrEzanFile)
        put("ezanVolume", s.ezanVolume)
        put("taskbarField", s.taskbarField)
        put("autostart", s.autostart)

        Files.createDirectories(dir)
        val tmp = Files.createTempFile(dir, "settings", ".tmp")
        try {
            Files.newBufferedWriter(tmp, StandardCharsets.UTF_8).use { p.store(it, null) }
            atomicReplace(tmp, file)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private fun Properties.text(key: String): String? = getProperty(key)?.takeIf { it.isNotBlank() }

    private inline fun <reified E : Enum<E>> Properties.enum(key: String): E? =
        getProperty(key)?.trim()?.let { v -> enumValues<E>().firstOrNull { it.name == v } }

    private fun Properties.int(key: String): Int? = getProperty(key)?.trim()?.toIntOrNull()

    private fun Properties.double(key: String): Double? =
        getProperty(key)?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun Properties.bool(key: String): Boolean? = when (getProperty(key)?.trim()?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }

    /** `null`, wenn der Schluessel fehlt; unbekannte Namen werden uebersprungen.
     *  Ein leerer Wert heisst bewusst: keine Erinnerung. */
    private fun Properties.reminders(): Set<Prayer>? {
        val raw = getProperty("reminders") ?: return null
        return raw.split(',').mapNotNull { name ->
            Prayer.entries.firstOrNull { it.name == name.trim() }
        }.toSet()
    }
}

/** Temp-Datei atomar an ihren Platz schieben (Rueckfall ohne ATOMIC_MOVE). */
internal fun atomicReplace(tmp: Path, target: Path) {
    try {
        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (e: AtomicMoveNotSupportedException) {
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
    }
}
