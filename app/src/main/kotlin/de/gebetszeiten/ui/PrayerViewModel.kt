package de.gebetszeiten.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.gebetszeiten.alarm.PrayerAlarmScheduler
import de.gebetszeiten.core.prayertimes.officialtimes.displayName
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.data.SettingsRepository
import de.gebetszeiten.notify.PrayerNotifier
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.widget.NextPrayerWidget
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PrayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings.DEFAULT,
    )

    private val _officialRefreshes = MutableStateFlow(0)

    /** Zaehlt ABGESCHLOSSENE Abrufe (Erfolg wie Fehlschlag). Die Statuszeile
     *  nutzt ihn als produceState-Key und liest damit genau dann neu, wenn es
     *  etwas Neues geben KANN — kein Pollen, keine Wartezeit bei fruehem
     *  Ruecksprung (Cache schon frisch). */
    val officialRefreshes: StateFlow<Int> = _officialRefreshes.asStateFlow()

    fun save(value: AppSettings) {
        viewModelScope.launch {
            val before = repository.settings.first().timeOffsets
            repository.save(value)
            reschedule(value)
            // Die Uhr zeigt dieselben verschobenen Zeiten wie das Telefon.
            if (value.timeOffsets != before) {
                de.gebetszeiten.official.OfficialTimesProvider.syncOffsetsToWear(getApplication(), value.timeOffsets)
            }
        }
    }

    /**
     * Der Abschluss der Ersteinrichtung — speichern, merken, planen.
     *
     * Alle drei gehoeren zusammen. Fehlte [SettingsRepository.markOnboardingDone],
     * kaeme die Ersteinrichtung beim naechsten Start wieder; fehlte das
     * Neuplanen in [save], erschiene die gerade gewaehlte Anzeige erst zum
     * naechsten Gebet — derselbe stille Ausfall wie beim frueher leeren
     * Berechtigungs-Rueckruf. `OnboardingWiringTest` haelt das fest.
     */
    fun completeOnboarding(value: AppSettings) {
        viewModelScope.launch {
            repository.save(value)
            repository.markOnboardingDone()
            reschedule(value)
        }
    }

    /** Make sure the channel, alarm chain and widget are live (called on launch). */
    fun ensureScheduled() {
        viewModelScope.launch {
            reschedule(repository.current())
        }
    }

    /** Manueller Abruf aus den Einstellungen — schreibt Zeitstempel und
     *  Fehlergrund in den Cache, den die Statuszeile liest. `force = true`
     *  gilt NUR fuer diesen einen Aufruf (Default-Parameter von [reschedule]):
     *  App-Start ([ensureScheduled]) und Einstellungsaenderung ([save]) rufen
     *  [reschedule] ohne das Argument und erzwingen damit weiterhin nichts. */
    fun refreshOfficialNow() {
        // Ein zweiter Tipp waehrend des Abrufs startet keinen zweiten.
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            try {
                val value = repository.current()
                reschedule(value, force = true)
                _fetchOutcomes.emit(outcomeFor(value))
            } finally {
                _refreshing.value = false
            }
        }
    }

    private val _refreshing = MutableStateFlow(false)

    /** Laeuft gerade ein Abruf von Hand? Der Knopf zeigt dann, dass er arbeitet. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _fetchOutcomes = MutableSharedFlow<FetchOutcome>(extraBufferCapacity = 1)

    /** Ergebnis jedes Abrufs von Hand, einmal je Abruf (fuer die Meldung). */
    val fetchOutcomes: SharedFlow<FetchOutcome> = _fetchOutcomes.asSharedFlow()

    private suspend fun outcomeFor(value: AppSettings): FetchOutcome {
        val app = getApplication<Application>()
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val hasTimes = PrayerProvider.daily(app, value, today, zone) != null
        val place = de.gebetszeiten.official.DiyanetPlaceIndex
            .nearest(app, value.latitude, value.longitude)
            ?.displayName()
            ?: value.city
        val error = de.gebetszeiten.official.OfficialTimesCache(app)
            .status(value.latitude, value.longitude).lastError
        return fetchOutcome(hasTimes, place, error)
    }

    private suspend fun reschedule(value: AppSettings, force: Boolean = false) {
        val app = getApplication<Application>()
        // Online flavor: refresh the official-times cache (no-op offline).
        PrayerProvider.refreshOfficial(app, value, force = force)
        PrayerNotifier.ensureChannel(app)
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        val active = PrayerProvider.currentlyActive(app, value, zone, now)
            ?.takeIf { it.prayer != de.gebetszeiten.core.prayertimes.Prayer.SUNRISE }
        val next = PrayerProvider.nextPrayer(app, value, zone, now)
        val photo = de.gebetszeiten.notify.ongoingPhotoAt(app, value, zone, now)
        // Die Daueranzeige dekodiert ein Foto; das gehoert nicht auf den
        // Hauptthread, auf dem `viewModelScope` laeuft.
        withContext(Dispatchers.Default) {
            PrayerNotifier.updateOngoing(
                app,
                next,
                value.persistentNotification,
                value.countdownMode != de.gebetszeiten.data.AppSettings.COUNTDOWN_OFF,
                active,
                replacesEntry = value.reminderStyle == de.gebetszeiten.data.AppSettings.STYLE_SILENT,
                activeUntil = PrayerProvider.activeUntil(app, value, zone, now, active),
                exact = value.countdownMode == de.gebetszeiten.data.AppSettings.PRECISION_EXACT,
                karahaLine = de.gebetszeiten.prayer.KarahaDisplay.line(app, value, zone, now),
                // Der aktive Ort aus den gerade gespeicherten Einstellungen —
                // nach einem Ortswechsel also sofort der neue, passend zu den
                // Zeiten daneben.
                photo = photo,
                offsets = value.timeOffsets,
                look = value.liveLook(),
            )
        }
        PrayerNotifier.updatePauseNotice(app, hasTimes = next != null)
        PrayerAlarmScheduler.scheduleNext(app, value)
        NextPrayerWidget.refreshAll(app)
        // Zaehler hier, nicht in refreshOfficialNow: JEDER abgeschlossene
        // Durchlauf soll die Statuszeile neu lesen lassen — auch der nach
        // einem Ortswechsel (save() -> reschedule()), sonst bleibt dort
        // "noch kein Versuch" stehen, nachdem der Abruf laengst durch ist.
        _officialRefreshes.value += 1
    }
}
