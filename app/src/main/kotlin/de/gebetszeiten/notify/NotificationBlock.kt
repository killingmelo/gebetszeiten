package de.gebetszeiten.notify

/**
 * Warum von dieser App gerade nichts ankommt — oder dass alles in Ordnung ist.
 *
 * Der Anlass: `canPost` fragte nur `checkSelfPermission`. Das reicht nicht.
 * Ein Nutzer kann die Benachrichtigungen der App im System abschalten oder
 * einen einzelnen Kanal auf „Keine" stellen, ohne die Berechtigung zu
 * entziehen — dann ist `checkSelfPermission` weiter erteilt, `notify()`
 * verpufft lautlos, und die App merkt nichts davon. Schlimmer noch: der
 * Merker `pause_notice_shown` behauptet danach „gemeldet", obwohl niemand
 * etwas gesehen hat, und die Meldung kommt nie wieder.
 *
 * Die Reihenfolge der Faelle ist die BEHEBUNGS-Reihenfolge, nicht die
 * technische: ohne Erlaubnis nuetzt der schoenste Kanal nichts. Wer zwei
 * Huerden gleichzeitig hat, bekommt die genannt, die er zuerst wegraeumen
 * muss.
 */
enum class NotificationBlock {
    /** Alles in Ordnung — es kommt an. */
    NONE,

    /** Ab Android 13: die Laufzeit-Erlaubnis fehlt. */
    NO_PERMISSION,

    /** Benachrichtigungen der ganzen App sind im System abgeschaltet. */
    APP_DISABLED,

    /** Ein einzelner Kanal steht auf „Keine" (`IMPORTANCE_NONE`). */
    CHANNEL_DISABLED,
}

/**
 * `IMPORTANCE_NONE` aus `NotificationManager`, hier als Zahl, damit diese
 * Datei ohne Android-Import auskommt und im reinen JVM-Test laeuft.
 * `IMPORTANCE_UNSPECIFIED` (-1000) kommt vor, wenn ein Kanal noch gar nicht
 * angelegt ist — das ist KEIN Ausfall, sondern der Zustand vor dem ersten
 * `createNotificationChannel`.
 */
private const val IMPORTANCE_NONE = 0

/**
 * [hasPermission] ist vor Android 13 immer `true` — dort gab es die
 * Laufzeit-Erlaubnis noch nicht. [appEnabled] entspricht
 * `areNotificationsEnabled()`, die Wichtigkeiten den beiden Kanaelen, auf
 * denen diese App ueberhaupt etwas postet: die Dauerzeile und die
 * Eintritts-/Vorlauf-Meldungen.
 */
fun notificationBlock(
    hasPermission: Boolean,
    appEnabled: Boolean,
    ongoingChannelImportance: Int,
    entryChannelImportance: Int,
): NotificationBlock = when {
    !hasPermission -> NotificationBlock.NO_PERMISSION
    !appEnabled -> NotificationBlock.APP_DISABLED
    // Beide Kanaele zaehlen: ein abgeschalteter Eintritts-Kanal nimmt dem
    // Nutzer seine Gebets-Meldungen, auch wenn die Dauerzeile weiter steht.
    ongoingChannelImportance == IMPORTANCE_NONE -> NotificationBlock.CHANNEL_DISABLED
    entryChannelImportance == IMPORTANCE_NONE -> NotificationBlock.CHANNEL_DISABLED
    else -> NotificationBlock.NONE
}

/**
 * Der Satz, den der Nutzer liest — `null`, wenn es nichts zu sagen gibt.
 *
 * Deutscher Text in Kotlin statt in `strings.xml`, weil er zusammen mit
 * seiner Entscheidung geprueft wird: ein Zweig ohne Satz waere ein stiller
 * Ausfall, und genau das soll dieser Typ verhindern. Dasselbe Muster wie
 * `prayer/NoTimesNotice.kt`.
 */
fun notificationBlockText(block: NotificationBlock): String? = when (block) {
    NotificationBlock.NONE -> null
    NotificationBlock.NO_PERMISSION ->
        "Ohne Benachrichtigungen kann die App weder an die Gebetszeiten erinnern " +
            "noch die Restzeit auf dem Sperrbildschirm zeigen."
    NotificationBlock.APP_DISABLED ->
        "Die Benachrichtigungen dieser App sind in den Systemeinstellungen " +
            "abgeschaltet. Erinnerungen und Sperrbildschirm-Anzeige bleiben aus."
    NotificationBlock.CHANNEL_DISABLED ->
        "Eine Benachrichtigungs-Kategorie dieser App steht in den " +
            "Systemeinstellungen auf „Keine\". Ein Teil der Meldungen kommt nicht an."
}

/** Die Beschriftung des Knopfes daneben — `null` ohne Hinderungsgrund. */
fun notificationBlockAction(block: NotificationBlock): String? = when (block) {
    NotificationBlock.NONE -> null
    // Nur hier hilft der System-Dialog; in den anderen Faellen ist die
    // Erlaubnis erteilt und der Weg fuehrt in die Systemeinstellungen.
    NotificationBlock.NO_PERMISSION -> "Erlauben"
    NotificationBlock.APP_DISABLED, NotificationBlock.CHANNEL_DISABLED -> "Einstellungen öffnen"
}
