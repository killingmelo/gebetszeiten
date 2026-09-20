package de.gebetszeiten.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.glance.appwidget.updateAll
import de.gebetszeiten.alarm.PrayerAlarmScheduler
import de.gebetszeiten.data.AppSettings
import de.gebetszeiten.data.SettingsRepository
import de.gebetszeiten.notify.PrayerNotifier
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.widget.NextPrayerWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
            repository.save(value)
            reschedule(value)
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
        viewModelScope.launch { reschedule(repository.current(), force = true) }
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
            city = value.city,
        )
        PrayerNotifier.updatePauseNotice(app, hasTimes = next != null)
        PrayerAlarmScheduler.scheduleNext(app, value)
        NextPrayerWidget().updateAll(app)
        // Zaehler hier, nicht in refreshOfficialNow: JEDER abgeschlossene
        // Durchlauf soll die Statuszeile neu lesen lassen — auch der nach
        // einem Ortswechsel (save() -> reschedule()), sonst bleibt dort
        // "noch kein Versuch" stehen, nachdem der Abruf laengst durch ist.
        _officialRefreshes.value += 1
    }
}
