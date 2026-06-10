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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PrayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)

    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings.DEFAULT,
    )

    fun save(value: AppSettings) {
        viewModelScope.launch {
            repository.save(value)
            reschedule(value)
        }
    }

    /** Make sure the channel, alarm chain and widget are live (called on launch). */
    fun ensureScheduled() {
        viewModelScope.launch {
            reschedule(repository.current())
        }
    }

    private suspend fun reschedule(value: AppSettings) {
        val app = getApplication<Application>()
        // Online flavor: refresh the official-times cache (no-op offline).
        PrayerProvider.refreshOfficial(app, value)
        PrayerNotifier.ensureChannel(app)
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        PrayerNotifier.updateOngoing(
            app,
            PrayerProvider.nextPrayer(app, value, zone, now),
            value.persistentNotification,
            value.showCountdown,
            PrayerProvider.currentlyActive(app, value, zone, now)
                ?.takeIf { it.prayer != de.gebetszeiten.core.prayertimes.Prayer.SUNRISE },
        )
        PrayerAlarmScheduler.scheduleNext(app, value)
        NextPrayerWidget().updateAll(app)
    }
}
