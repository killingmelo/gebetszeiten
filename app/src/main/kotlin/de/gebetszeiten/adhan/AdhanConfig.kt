package de.gebetszeiten.adhan

/**
 * Die Aḏān-Einstellungen und alle Entscheidungen, die daraus folgen, ohne
 * `Context`: welche Aufnahme bei welchem Gebet läuft, ob die Duʿāʾ folgt,
 * auf welchem Audiokanal, ob Nicht-stören die Wiedergabe verbietet und ob
 * die normale Meldung dann leise bleibt. Der Dienst ([AdhanService]) führt
 * nur noch aus, was hier entschieden ist; so ist die Logik im JVM-Test
 * prüfbar.
 *
 * Werkseinstellung: aus. Wer nichts wählt, hört nichts Neues.
 */
data class AdhanConfig(
    /** Hauptschalter. */
    val enabled: Boolean = false,
    /** Gebete (`Prayer.name`), zu denen der Aḏān läuft. Šurūq hat keinen. */
    val prayers: Set<String> = ALL_PRAYERS,
    /** Aufnahme für alle Gebete: [VOICE_BUNDLED] oder [VOICE_CUSTOM]. */
    val voice: String = VOICE_BUNDLED,
    /** Eigene Aufnahme (Content-URI), nur mit [VOICE_CUSTOM] wirksam. */
    val customUri: String? = null,
    /** Eigener Faǧr-Aḏān (Content-URI); `null` = dieselbe Aufnahme wie sonst. */
    val fajrUri: String? = null,
    /** Duʿāʾ nach dem Aḏān abspielen. */
    val duaEnabled: Boolean = false,
    /** Aufnahme der Duʿāʾ (Content-URI). Ohne sie bleibt es beim Aḏān. */
    val duaUri: String? = null,
    /** Audiokanal: [STREAM_ALARM] oder [STREAM_NOTIFICATION]. */
    val stream: String = STREAM_ALARM,
    /** Lautstärke der Wiedergabe in Prozent der Kanallautstärke, 10 bis 100. */
    val volumePercent: Int = 100,
) {
    companion object {
        val ALL_PRAYERS = setOf("FAJR", "DHUHR", "ASR", "MAGHRIB", "ISHA")
        const val VOICE_BUNDLED = "BUNDLED"
        const val VOICE_CUSTOM = "CUSTOM"
        const val STREAM_ALARM = "ALARM"
        const val STREAM_NOTIFICATION = "NOTIFICATION"
        val VOLUME_STEPS = listOf(40, 70, 100)
    }
}

/** Woher ein Stück kommt: die mitgelieferte Aufnahme oder eine Datei des Nutzers. */
sealed interface AdhanSource {
    data object Bundled : AdhanSource
    data class Custom(val uri: String) : AdhanSource
}

/** Was zu einem Gebet abgespielt wird, der Reihe nach, und wie. */
data class AdhanPlan(
    val tracks: List<AdhanSource>,
    val stream: String,
    val volume: Float,
)

/**
 * Der Plan für [prayer] (`Prayer.name`), oder `null`, wenn nichts läuft:
 * Hauptschalter aus, Gebet nicht gewählt, Šurūq, oder eine eigene Aufnahme
 * gewählt, aber keine Datei hinterlegt.
 */
fun adhanPlan(config: AdhanConfig, prayer: String): AdhanPlan? {
    if (!config.enabled || prayer !in AdhanConfig.ALL_PRAYERS || prayer !in config.prayers) return null
    val main: AdhanSource = when {
        prayer == "FAJR" && !config.fajrUri.isNullOrBlank() -> AdhanSource.Custom(config.fajrUri)
        config.voice == AdhanConfig.VOICE_CUSTOM ->
            config.customUri?.takeIf { it.isNotBlank() }?.let { AdhanSource.Custom(it) } ?: return null
        else -> AdhanSource.Bundled
    }
    val dua = config.duaUri?.takeIf { config.duaEnabled && it.isNotBlank() }?.let { AdhanSource.Custom(it) }
    return AdhanPlan(
        tracks = listOfNotNull(main, dua),
        stream = normalizedStream(config.stream),
        volume = normalizedVolume(config.volumePercent) / 100f,
    )
}

/** Spätestens so lange nach Gebetsbeginn darf der Aḏān noch erklingen. */
const val ADHAN_LATE_LIMIT_MILLIS = 5 * 60_000L

/**
 * Ob der Wecker rechtzeitig kam. Ein verspäteter Wecker (Gerät war aus,
 * Energiesparen hat ihn verschoben) soll keinen Aḏān mitten ins Gebet oder
 * eine halbe Stunde zu spät rufen; die Meldung kommt trotzdem.
 */
fun adhanDue(prayerStartMillis: Long, nowMillis: Long): Boolean =
    nowMillis - prayerStartMillis in -60_000L..ADHAN_LATE_LIMIT_MILLIS

/** Unbekannte gespeicherte Werte fallen auf den Wecker-Kanal zurück. */
fun normalizedStream(stored: String?): String =
    if (stored == AdhanConfig.STREAM_NOTIFICATION) AdhanConfig.STREAM_NOTIFICATION else AdhanConfig.STREAM_ALARM

/** Nie stumm (das wäre ein unsichtbarer Ausfall), nie über 100 %. */
fun normalizedVolume(stored: Int?): Int = (stored ?: 100).coerceIn(10, 100)

/**
 * Darf der Aḏān unter dem aktuellen Nicht-stören-Filter laufen?
 *
 * [interruptionFilterAll] ist `NotificationManager.currentInterruptionFilter
 * == INTERRUPTION_FILTER_ALL`, [alarmsAllowed] ob Wecker trotz Nicht-stören
 * durchkommen (Filter „nur Wecker“ oder „Priorität“ mit erlaubten Weckern).
 * Auf dem Benachrichtigungs-Kanal gilt Nicht-stören immer; auf dem
 * Wecker-Kanal nur, wenn auch Wecker schweigen sollen.
 */
fun adhanAllowedUnderDnd(stream: String, interruptionFilterAll: Boolean, alarmsAllowed: Boolean): Boolean =
    interruptionFilterAll || (normalizedStream(stream) == AdhanConfig.STREAM_ALARM && alarmsAllowed)

/**
 * Der Stil der Eintritts-Meldung, wenn zugleich der Aḏān läuft: dann still,
 * damit Ton oder Vibration der Meldung nicht über den Aḏān fallen. Die
 * Meldung selbst bleibt, mit Gebet und Uhrzeit.
 */
fun entryStyleWithAdhan(style: String, adhanPlays: Boolean): String =
    if (adhanPlays) de.gebetszeiten.data.AppSettings.STYLE_SILENT else style
