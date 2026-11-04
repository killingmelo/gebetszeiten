package de.gebetszeiten.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.gebetszeiten.R
import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.data.SettingsRepository
import de.gebetszeiten.prayer.DayRow
import de.gebetszeiten.prayer.IslamicWindows
import de.gebetszeiten.prayer.PrayerProvider
import de.gebetszeiten.prayer.dayRows
import de.gebetszeiten.prayer.heroState
import de.gebetszeiten.prayer.hijriTextShort
import de.gebetszeiten.prayer.labelRes
import de.gebetszeiten.prayer.remainingStepLabel
import de.gebetszeiten.prayer.widgetNotice
import de.gebetszeiten.ui.momentLabelRes
import de.gebetszeiten.ui.photoDescriptionRes
import de.gebetszeiten.ui.photoRes
import de.gebetszeiten.ui.theme.widgetScheme
import de.gebetszeiten.ui.theme.Palette
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Das Widget für Start- und (Android 16 QPR1+) Sperrbildschirm, eine kleine
 * Fassung der Ansicht „Heute“: Name des laufenden Abschnitts, „noch …“ und
 * „dann <Gebet> um HH:mm“; groß zusätzlich das Foto des Abschnitts und die
 * fünf Pflichtgebete. Es wird ereignisgesteuert neu gezeichnet: an jedem
 * Übergang ruft der Alarm-Empfänger [refreshAll], es gibt
 * keine regelmäßige Auffrischung.
 */
class NextPrayerWidget : GlanceAppWidget() {

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    // Zwei Stufen: klein nur der Abschnitt, groß (ab [FULL], wenn der Nutzer
    // das Widget so weit aufzieht) mit Foto und Tagesreihe. `Exact` statt
    // `Responsive`, weil das kleine Widget seine echte Höhe kennen muss, um
    // bei großer Schrift eine Zeile wegzulassen (compactLines); bei
    // `Responsive` lieferte LocalSize nur die Stufe (40 dp). Glance zeichnet
    // dafür bei einer Größenänderung neu; sonst nur bei den ohnehin
    // vorhandenen Ereignissen.
    override val sizeMode: SizeMode = SizeMode.Exact

    internal companion object {
        val FULL = DpSize(220.dp, 120.dp)

        /** Ab dieser Breite steht Šurūq als sechster Eintrag in der Zeitzeile;
         *  schmaler bleiben es die fünf Pflichtgebete. */
        val SHURUQ_MIN_WIDTH = 300.dp
        val CORNER = 24.dp

        private val photoPixelCache = java.util.concurrent.ConcurrentHashMap<Int, IntArray>()

        /** Zähler im Widget-Zustand; jede Erhöhung lässt die Komposition neu rechnen. */
        val REFRESH = longPreferencesKey("refresh")

        /**
         * Alle Widgets neu zeichnen, mit frisch gelesenen Einstellungen und
         * Zeiten, auch wenn ihre Glance-Sitzung noch läuft. Ersetzt
         * `NextPrayerWidget().updateAll(context)` an allen Aufrufstellen.
         */
        suspend fun refreshAll(context: Context) {
            val widget = NextPrayerWidget()
            GlanceAppWidgetManager(context).getGlanceIds(NextPrayerWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { prefs ->
                    prefs[REFRESH] = (prefs[REFRESH] ?: 0L) + 1
                }
                widget.update(context, id)
            }
        }
    }

    /** Trägt nur den Auffrischungszähler [REFRESH], siehe [refreshAll]. */
    override val stateDefinition = PreferencesGlanceStateDefinition

    /**
     * Glance hält nach jedem Aufbau eine Sitzung offen (etwa 45 s) und ruft
     * `provideGlance` in der Zeit nicht erneut auf. Was hier VOR
     * `provideContent` gelesen würde, bliebe in der Zeit stehen: eine zweite
     * Änderung (anderes Thema, anderer Ort, die nächste Restzeit-Stufe) kam
     * dann nicht mehr an. Deshalb rechnet die Komposition selbst neu, sobald
     * [refreshAll] den Zähler erhöht.
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = buildModel(context)
        provideContent {
            val token = currentState<Preferences>()[REFRESH] ?: 0L
            val model by produceState(first, token) { value = buildModel(context) }
            Render(context, model)
        }
    }

    @Composable
    private fun Render(context: Context, model: Model?) {
        when (model) {
            is Model.NoTimes -> GlanceTheme(colors = model.look.colors) {
                NoTimesContent(model.headline, model.look)
            }
            is Model.Times -> GlanceTheme(colors = model.look.colors) {
                when (model.format) {
                    WidgetFormat.COUNTDOWN -> CountdownContent(context, model)
                    WidgetFormat.LIST -> ListContent(context, model)
                    WidgetFormat.SLIM -> SlimContent(context, model)
                    WidgetFormat.STANDARD -> StandardContent(context, model)
                }
            }
            // Kein Tagesplan trotz nächstem Gebet: sollte nicht vorkommen;
            // dann eine leere Fläche statt eines Absturzes.
            null -> Box(GlanceModifier.fillMaxSize()) {}
        }
    }

    /** Der bisherige Aufbau: klein Abschnitt und Restzeit, groß mit Foto und Tagesreihe. */
    @Composable
    private fun StandardContent(context: Context, model: Model.Times) {
        if (LocalSize.current.width >= FULL.width && LocalSize.current.height >= FULL.height) {
            FullContent(
                context, model.title, model.countdown, model.nextLine, model.karahaLine,
                model.header, model.photo, model.dayPlan, model.look,
            )
        } else {
            CompactContent(
                context, model.title, model.countdown, model.nextLine, model.nextShort,
                model.karahaLine, model.look,
            )
        }
    }

    /** Gemeinsamer Rahmen: Grund mit Deckkraft, Ecken, Tipp öffnet die App. */
    private fun frame(look: WidgetLook) = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .cornerRadius(CORNER)
        .background(look.background)
        .clickable(actionStartActivity<de.gebetszeiten.ui.MainActivity>())

    /** „Restzeit groß“: Abschnitt klein, die Restzeit groß, darunter das nächste Gebet. */
    @Composable
    private fun CountdownContent(context: Context, model: Model.Times) {
        val fontScale = context.resources.configuration.fontScale
        val next = if (model.nextLine != null && model.nextShort != null) {
            compactNextLine(model.nextLine, model.nextShort, LocalSize.current.width.value, fontScale)
        } else {
            model.nextLine
        }
        Column(
            modifier = frame(model.look).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val countdown = model.countdown
            // „noch“ steht klein beim Abschnitt, groß nur die Zahl: sonst
            // passte sie im 2×1-Widget nicht und wurde gekürzt.
            val lead = context.getString(R.string.hero_remaining_lead)
            Text(
                text = if (countdown.exact) model.title else "${model.title} $lead",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            if (countdown.exact) {
                RemainingLine(context, countdown, onPhoto = false)
            } else {
                Text(
                    text = countdown.short,
                    style = TextStyle(
                        color = if (countdown.urgent) GlanceTheme.colors.error else GlanceTheme.colors.primary,
                        fontSize = bigCountdownSp(countdown.short, LocalSize.current.width.value, fontScale).sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
            if (next != null) {
                Text(
                    text = next,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
    }

    /** „Tagesliste“: Abschnitt und Restzeit, darunter alle Gebete des Tages untereinander. */
    @Composable
    private fun ListContent(context: Context, model: Model.Times) {
        Column(modifier = frame(model.look).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.title,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (model.countdown.show && !model.countdown.exact) {
                    Text(
                        text = model.countdown.label,
                        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
            }
            Spacer(GlanceModifier.height(4.dp))
            // Passt nicht alles in die Höhe, ab dem laufenden Gebet: die
            // kommenden sind wichtiger als die vergangenen.
            val fits = listRowsThatFit(LocalSize.current.height.value, context.resources.configuration.fontScale)
            val window = listWindow(model.dayPlan.size, model.dayPlan.indexOfFirst { it.current }, fits)
            model.dayPlan.slice(window).forEach { entry ->
                val color = when {
                    entry.current -> GlanceTheme.colors.primary
                    entry.faded -> GlanceTheme.colors.onSurfaceVariant
                    else -> GlanceTheme.colors.onSurface
                }
                val weight = if (entry.current) FontWeight.Bold else FontWeight.Normal
                val spoken = "${entry.label} ${entry.time}" +
                    if (entry.current) context.getString(R.string.status_current) else ""
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)
                        .semantics { contentDescription = spoken },
                ) {
                    Text(
                        text = entry.label,
                        style = TextStyle(color = color, fontSize = 13.sp, fontWeight = weight),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Text(text = entry.time, style = TextStyle(color = color, fontSize = 13.sp, fontWeight = weight))
                }
            }
        }
    }

    /** „Eine Zeile“: Abschnitt, Restzeit, rechts das nächste Gebet. */
    @Composable
    private fun SlimContent(context: Context, model: Model.Times) {
        Row(
            modifier = frame(model.look).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = model.title,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (model.countdown.show && !model.countdown.exact) {
                Text(
                    text = "  " + model.countdown.label,
                    style = TextStyle(
                        color = if (model.countdown.urgent) GlanceTheme.colors.error else GlanceTheme.colors.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            model.nextShort?.let {
                Text(
                    text = it,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
    }

    /** Alles, was das Widget zeigt, aus Einstellungen und Uhrzeit von jetzt. */
    private sealed interface Model {
        data class NoTimes(val headline: String, val look: WidgetLook) : Model
        data class Times(
            val title: String,
            val countdown: Countdown,
            val nextLine: String?,
            val nextShort: String?,
            val karahaLine: de.gebetszeiten.prayer.KarahaLine?,
            val header: String,
            val photo: Photo,
            val dayPlan: List<DayEntry>,
            val look: WidgetLook,
            val format: WidgetFormat,
        ) : Model
    }

    private suspend fun buildModel(context: Context): Model? {
        val settings = SettingsRepository(context).current()
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        // Keine Zeiten unter den aktuellen Einstellungen: statt auf dem
        // letzten (echten, aber veralteten) Stand einzufrieren, sagt das
        // Widget das jetzt selbst - derselbe Wortlaut wie Heute- und
        // Monatsansicht (Aufgabe 10/11), hier nur die Kurzform (`headline`,
        // kein `detail` - dafuer ist auf einem Widget kein Platz). WELCHER
        // Zustand gezeigt wird und mit welchem `onlineEnabled`-Wahrheitswert:
        // das entscheidet `widgetNotice` (reine Funktion, `NoTimesNotice.kt`,
        // mit Tests) - hier steht nur noch der Aufruf und die Auswahl von
        // `.headline`. Verzweigt bewusst auf `next == null` (nicht auf
        // `notice == null`), damit `next` fuer den Rest der Funktion vom
        // Compiler als nicht-nullbar erkannt wird - ohne zweite Deklaration
        // und ohne `!!`.
        val next = PrayerProvider.next(context, settings, zone, now)
        if (next == null) {
            // `widgetNotice(null, ...)` liefert laut eigenem Vertrag immer
            // ein Ergebnis - `?: return` statt `!!`, derselbe defensive
            // Rueckgabepfad wie beim Tagesplan weiter unten, falls sich das
            // je auseinanderentwickelt.
            val notice = widgetNotice(next, settings.city, settings.canFetchOfficial()) ?: return null
            return Model.NoTimes(notice.headline, WidgetLook.of(settings))
        }
        // Ein Regler fuer alle Flaechen (AppSettings.countdownMode); das
        // Widget liest denselben OFF/STEPS/EXACT-Wert wie App und
        // Benachrichtigung.
        val format = WidgetFormat.of(settings.widgetFormat)
        // „Restzeit groß“ zeigt die Restzeit auch bei Countdown aus (dann in
        // Stufen, die Weckkette dafür: AppSettings.widgetNeedsStepAlarms).
        val showCountdown = settings.countdownMode != de.gebetszeiten.data.AppSettings.COUNTDOWN_OFF ||
            format == WidgetFormat.COUNTDOWN
        val exact = settings.countdownMode == de.gebetszeiten.data.AppSettings.PRECISION_EXACT
        val karahaLine = de.gebetszeiten.prayer.KarahaDisplay.line(context, settings, zone, now)
        // Restzeit bis zum naechsten Uebergang (`next`, Sonnenaufgang
        // eingeschlossen): genau das Ziel, gegen das der Alarm-Planer die
        // Stufengrenzen weckt. Es faellt in jedem Abschnitt mit dem Ende
        // zusammen, das `heroState` fuer „noch …“ nimmt.
        val remaining = java.time.Duration.between(now, next.time)
        val remainingLabel = remainingStepLabel(remaining)
        val urgent = de.gebetszeiten.prayer.isUrgent(remaining)
        // elapsedRealtime base for the system-rendered countdown (EXACT mode).
        val chronoBase = android.os.SystemClock.elapsedRealtime() +
            (next.time.toInstant().toEpochMilli() - System.currentTimeMillis())

        // Titel, Foto und „dann …“ aus derselben Logik wie „Heute“.
        // `next` ist bereits non-null, also war `daily(heute)` intern
        // erfolgreich — dennoch kein `!!`, sondern derselbe frueh-Rueckgabe-
        // Pfad wie oben, falls sich das je auseinanderentwickelt.
        val today = PrayerProvider.daily(context, settings, now.toLocalDate(), zone) ?: return null
        val nextFajr = PrayerProvider.daily(context, settings, now.toLocalDate().plusDays(1), zone)?.fajr
        val karaha = IslamicWindows.karaha(today)
        val nafl = IslamicWindows.nafl(today, nextFajr)
        val hero = heroState(today, karaha, nafl, nextFajr, now, isToday = true)
        val title = context.getString(momentLabelRes(hero.moment))
        val nextLine = hero.next?.let {
            context.getString(R.string.hero_next_lead) + " " + context.getString(
                R.string.hero_next_at,
                context.getString(it.prayer.labelRes()),
                it.at.format(timeFormat),
            )
        }
        // Kurzform für schmale Widgets: Name und Uhrzeit, ohne „dann … um“.
        val nextShort = hero.next?.let {
            context.getString(it.prayer.labelRes()) + " " + it.at.format(timeFormat)
        }
        // Die fünf Pflichtgebete des Tages; verblasst ist nur, was vorbei
        // und nicht mehr laufend ist (wie in der Tagesliste von „Heute“).
        val dayPlan = dayRows(
            today = today,
            karaha = karaha,
            nafl = nafl,
            nextFajr = nextFajr,
            now = now,
            isToday = true,
            showKaraha = false,
            showCemaat = false,
            cemaatOffsetMinutes = 0,
        )
            .filterIsInstance<DayRow.PrayerRow>()
            .filter { !it.nextDay }
            .map {
                if (it.prayer == Prayer.SUNRISE) {
                    // Šurūq ist kein Gebet: nie „aktuell“, nur verblassen wie die anderen.
                    DayEntry(
                        label = context.getString(R.string.moment_shuruq),
                        time = it.at.format(timeFormat),
                        current = false,
                        faded = it.past,
                        optional = true,
                    )
                } else {
                    DayEntry(
                        label = context.getString(it.prayer.labelRes()),
                        time = it.at.format(timeFormat),
                        current = it.current,
                        faded = it.past && !it.current,
                    )
                }
            }
        val photo = Photo(photoRes(hero.photo), context.getString(photoDescriptionRes(hero.photo)))

        val look = WidgetLook.of(settings, photoPixels(context, settings, photo.res))
        // Kopfzeile im großen Widget: macht einen still falschen Ort sichtbar
        // und ist zugleich die Hijri-Datumszeile.
        val header = "${settings.city} · ${hijriTextShort(now.toLocalDate(), settings.hijriOffsetDays)}"
        // Ohne „noch“ für die große Anzeige; in der letzten Minute leer, dann die lange Form.
        val shortLabel = de.gebetszeiten.prayer.remainingStepShort(remaining).ifEmpty { remainingLabel }
        val countdown = Countdown(showCountdown, exact, remainingLabel, urgent, chronoBase, shortLabel)

        return Model.Times(title, countdown, nextLine, nextShort, karahaLine, header, photo, dayPlan, look, format)
    }

    /** Bildpunkte des Fotos, nur für das Thema „Wie das Tagesbild“; verkleinert
     *  gelesen und je Foto gemerkt (die Fotos liegen fest im APK). */
    private fun photoPixels(context: Context, settings: de.gebetszeiten.data.AppSettings, res: Int): IntArray? {
        if (WidgetTheme.of(settings.widgetTheme) != WidgetTheme.PHOTO) return null
        return photoPixelCache.getOrPut(res) {
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 16 }
            val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, res, opts)
                ?: return@getOrPut IntArray(0)
            IntArray(bmp.width * bmp.height).also {
                bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                bmp.recycle()
            }
        }
    }

    private data class DayEntry(
        val label: String,
        val time: String,
        val current: Boolean,
        val faded: Boolean,
        /** Nur bei genug Breite sichtbar (Šurūq). */
        val optional: Boolean = false,
    )

    private data class Photo(val res: Int, val description: String)

    private data class Countdown(
        val show: Boolean,
        val exact: Boolean,
        val label: String,
        val urgent: Boolean,
        val chronoBase: Long,
        /** „10+ Min“ ohne „noch“, für „Restzeit groß“. */
        val short: String = label,
    )

    /** Thema und Deckkraft aus den Einstellungen, einmal je Aufbau gelesen. */
    private class WidgetLook(theme: WidgetTheme, val opacity: Int, photo: de.gebetszeiten.ui.theme.Tokens? = null) {
        private val light = photo ?: theme.tokens.first
        private val dark = photo ?: theme.tokens.second
        val colors = ColorProviders(
            light = widgetScheme(light, dark = false),
            dark = widgetScheme(dark, dark = true),
        )

        /** Der Grund (Creme, Schwarz, Grün oder Sand) mit der gewählten
         *  Deckkraft; `widgetBackground` aus glance-material3 wäre dagegen
         *  reinweiß bzw. grau. */
        val background = androidx.glance.color.ColorProvider(
            day = light.canvas.copy(alpha = opacity / 100f),
            night = dark.canvas.copy(alpha = opacity / 100f),
        )

        /** Das Foto nur bei voller Deckkraft: sonst wäre nichts durchsichtig. */
        val photo: Boolean get() = opacity >= 100

        companion object {
            fun of(settings: de.gebetszeiten.data.AppSettings, photoPixels: IntArray? = null) =
                WidgetLook(
                    WidgetTheme.of(settings.widgetTheme),
                    settings.widgetOpacity.coerceIn(0, 100),
                    photoPixels?.takeIf { it.isNotEmpty() }?.let { photoTokens(it) },
                )
        }
    }

    /** Klein: Abschnitt, „noch …“, „dann …“ und gegebenenfalls die Karāha-Zeile. */
    @Composable
    private fun CompactContent(
        context: Context,
        title: String,
        countdown: Countdown,
        nextLine: String?,
        nextShort: String?,
        karahaLine: de.gebetszeiten.prayer.KarahaLine?,
        look: WidgetLook,
    ) {
        val fontScale = context.resources.configuration.fontScale
        val next = if (nextLine != null && nextShort != null) {
            compactNextLine(nextLine, nextShort, LocalSize.current.width.value, fontScale)
        } else {
            nextLine
        }
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(CORNER)
                .background(look.background)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                // The whole widget opens the app — expected tap behaviour.
                .clickable(actionStartActivity<de.gebetszeiten.ui.MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            // Bei großer Schrift passt nicht alles in 2×1: lieber eine Zeile
            // weglassen als die letzte abschneiden (siehe compactLines).
            val fit = compactLines(
                heightDp = LocalSize.current.height.value,
                fontScale = fontScale,
                countdown = countdown.show,
                karaha = karahaLine != null,
                next = next != null,
            )
            HeroText(
                context, title, countdown,
                next.takeIf { fit.next }, karahaLine.takeIf { fit.karaha },
                onPhoto = false,
            )
        }
    }

    /** Titel und Zeilen darunter, auf Fläche oder hell auf dem abgedunkelten Foto. */
    @Composable
    private fun HeroText(
        context: Context,
        title: String,
        countdown: Countdown,
        nextLine: String?,
        karahaLine: de.gebetszeiten.prayer.KarahaLine?,
        onPhoto: Boolean,
    ) {
        val light = ColorProvider(WidgetPhotoStyle.Text)
        Text(
            text = title,
            style = TextStyle(
                color = if (onPhoto) light else GlanceTheme.colors.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        if (countdown.show) RemainingLine(context, countdown, onPhoto)
        if (nextLine != null) {
            Text(
                text = nextLine,
                style = TextStyle(
                    color = if (onPhoto) light else GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                // Eine Zeile, wie compactLines rechnet; ein Umbruch schnitte
                // die Uhrzeit unten ab.
                maxLines = 1,
            )
        }
        karahaLine?.let { KarahaText(it, fontSize = 13, onPhoto = onPhoto) }
    }

    /** „noch …“: Stufen als Text, „Genau“ als Systemzähler ohne App-Weckvorgänge. */
    @Composable
    private fun RemainingLine(context: Context, countdown: Countdown, onPhoto: Boolean) {
        // Auf dem Foto trägt Rot keine 4,5:1; dort steht die Restzeit in den
        // letzten Minuten hell auf einem roten Feld.
        val urgentOnPhoto = onPhoto && countdown.urgent
        val color = when {
            onPhoto -> ColorProvider(WidgetPhotoStyle.Text)
            // Final minutes: urgency colour.
            countdown.urgent -> GlanceTheme.colors.error
            else -> GlanceTheme.colors.primary
        }
        val style = TextStyle(color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        var modifier = GlanceModifier.padding(top = 1.dp, bottom = 1.dp)
        if (urgentOnPhoto) {
            modifier = GlanceModifier
                .cornerRadius(8.dp)
                .background(ColorProvider(Palette.Day.error))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        }
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            if (countdown.exact) {
                Text(text = context.getString(R.string.hero_remaining_lead) + " ", style = style)
                // Live countdown, drawn by the system — no app wake-ups.
                val rv = android.widget.RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
                    setChronometerCountDown(R.id.widget_chrono, true)
                    setChronometer(R.id.widget_chrono, countdown.chronoBase, null, true)
                    when {
                        onPhoto -> setTextColor(R.id.widget_chrono, WidgetPhotoStyle.Text.toArgb())
                        // Final minutes: same urgency cue the STEPS countdown gets
                        // (sonst Akzent aus @color/widget_chrono_text).
                        countdown.urgent -> setTextColor(R.id.widget_chrono, context.getColor(R.color.widget_urgent))
                    }
                }
                AndroidRemoteViews(rv)
            } else {
                Text(text = countdown.label, style = style)
            }
        }
    }

    /** Leer-Darstellung (Aufgabe 12): keine amtlichen Zeiten unter den
     *  aktuellen Einstellungen. Ersetzt die sonstige Widget-Anzeige komplett
     *  (nicht nur eine Zeile darin) — es gibt weder ein "naechstes Gebet"
     *  noch einen Tagesplan, die daneben noch Sinn ergeben wuerden. Bleibt
     *  wie gewohnt antippbar zur App: derselbe Weg fuehrt dort zur
     *  Heute-Ansicht mit demselben Hinweis samt Handlungsmoeglichkeit
     *  (Abrufen/Notausgang einschalten).
     */
    @Composable
    private fun NoTimesContent(headline: String, look: WidgetLook) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(CORNER)
                .background(look.background)
                .padding(14.dp)
                .clickable(actionStartActivity<de.gebetszeiten.ui.MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = headline,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }

    /** "⚠️ Karāha bis/ab HH:MM" — error colour inside a window, dimmed before.
     *  Auf dem Foto hell, im laufenden Fenster auf rotem Feld. */
    @Composable
    private fun KarahaText(line: de.gebetszeiten.prayer.KarahaLine, fontSize: Int, onPhoto: Boolean) {
        Spacer(GlanceModifier.height(2.dp))
        val color = when {
            onPhoto -> ColorProvider(WidgetPhotoStyle.Text)
            line.active -> GlanceTheme.colors.error
            else -> GlanceTheme.colors.onSurfaceVariant
        }
        val modifier = if (onPhoto && line.active) {
            GlanceModifier
                .cornerRadius(8.dp)
                .background(ColorProvider(Palette.Day.error))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        } else {
            GlanceModifier
        }
        Text(
            text = line.text,
            modifier = modifier,
            style = TextStyle(color = color, fontSize = fontSize.sp, fontWeight = FontWeight.Medium),
        )
    }

    /** Groß: oben das Foto des Abschnitts mit dem Titel, darunter auf der
     *  Fläche die fünf Pflichtgebete; das laufende ist hervorgehoben. */
    @Composable
    private fun FullContent(
        context: Context,
        title: String,
        countdown: Countdown,
        nextLine: String?,
        karahaLine: de.gebetszeiten.prayer.KarahaLine?,
        header: String,
        photo: Photo,
        entries: List<DayEntry>,
        look: WidgetLook,
    ) {
        val onPhoto = look.photo
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(CORNER)
                .background(look.background)
                .clickable(actionStartActivity<de.gebetszeiten.ui.MainActivity>()),
        ) {
            Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                if (onPhoto) {
                    Image(
                        provider = ImageProvider(photo.res),
                        contentDescription = photo.description,
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier.fillMaxSize(),
                    )
                    Box(modifier = GlanceModifier.fillMaxSize().background(ColorProvider(WidgetPhotoStyle.Scrim))) {}
                }
                Column(
                    modifier = GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = header,
                        style = TextStyle(
                            color = if (onPhoto) ColorProvider(WidgetPhotoStyle.Text) else GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    HeroText(context, title, countdown, nextLine, karahaLine, onPhoto)
                }
            }
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val showShuruq = LocalSize.current.width >= SHURUQ_MIN_WIDTH
                entries.filter { showShuruq || !it.optional }.forEach { entry ->
                    val color = when {
                        entry.current -> GlanceTheme.colors.primary
                        entry.faded -> GlanceTheme.colors.onSurfaceVariant
                        else -> GlanceTheme.colors.onSurface
                    }
                    // TalkBack: das laufende Gebet wird als solches angesagt.
                    val spoken = "${entry.label} ${entry.time}" +
                        if (entry.current) context.getString(R.string.status_current) else ""
                    Column(
                        modifier = GlanceModifier.defaultWeight().semantics { contentDescription = spoken },
                    ) {
                        Text(
                            text = entry.label,
                            style = TextStyle(
                                color = color,
                                fontSize = 11.sp,
                                fontWeight = if (entry.current) FontWeight.Bold else FontWeight.Medium,
                            ),
                        )
                        Text(
                            text = entry.time,
                            style = TextStyle(
                                color = color,
                                fontSize = 14.sp,
                                fontWeight = if (entry.current) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                    }
                }
            }
        }
    }
}
