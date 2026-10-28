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
import de.gebetszeiten.ui.theme.DarkColors
import de.gebetszeiten.ui.theme.LightColors
import de.gebetszeiten.ui.theme.Palette
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Das Widget für Start- und (Android 16 QPR1+) Sperrbildschirm, eine kleine
 * Fassung der Ansicht „Heute“: Name des laufenden Abschnitts, „noch …“ und
 * „dann <Gebet> um HH:mm“; groß zusätzlich das Foto des Abschnitts und die
 * fünf Pflichtgebete. Es wird ereignisgesteuert neu gezeichnet: an jedem
 * Übergang ruft der Alarm-Empfänger [GlanceAppWidget.updateAll], es gibt
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

    private companion object {
        val FULL = DpSize(220.dp, 120.dp)

        /** Ab dieser Breite steht Šurūq als sechster Eintrag in der Zeitzeile;
         *  schmaler bleiben es die fünf Pflichtgebete. */
        val SHURUQ_MIN_WIDTH = 300.dp
        val CORNER = 24.dp
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
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
            val notice = widgetNotice(next, settings.city, settings.canFetchOfficial()) ?: return
            val transparent = settings.widgetTransparent
            provideContent {
                GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors)) {
                    NoTimesContent(notice.headline, transparent)
                }
            }
            return
        }
        // Ein Regler fuer alle Flaechen (AppSettings.countdownMode); das
        // Widget liest denselben OFF/STEPS/EXACT-Wert wie App und
        // Benachrichtigung.
        val showCountdown = settings.countdownMode != de.gebetszeiten.data.AppSettings.COUNTDOWN_OFF
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
        val today = PrayerProvider.daily(context, settings, now.toLocalDate(), zone) ?: return
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

        val transparent = settings.widgetTransparent
        // Kopfzeile im großen Widget: macht einen still falschen Ort sichtbar
        // und ist zugleich die Hijri-Datumszeile.
        val header = "${settings.city} · ${hijriTextShort(now.toLocalDate(), settings.hijriOffsetDays)}"
        val countdown = Countdown(showCountdown, exact, remainingLabel, urgent, chronoBase)

        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors)) {
                if (LocalSize.current.width >= FULL.width && LocalSize.current.height >= FULL.height) {
                    FullContent(context, title, countdown, nextLine, karahaLine, header, photo, dayPlan, transparent)
                } else {
                    CompactContent(context, title, countdown, nextLine, karahaLine, transparent)
                }
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
    )

    @Composable
    private fun widgetBackground(transparent: Boolean) = if (transparent) {
        androidx.glance.color.ColorProvider(
            day = LightColors.surface.copy(alpha = 0.45f),
            night = DarkColors.surface.copy(alpha = 0.45f),
        )
    } else {
        // `surface` ist der Grund (creme bzw. schwarz); `widgetBackground`
        // aus glance-material3 waere dagegen reinweiss bzw. grau.
        GlanceTheme.colors.surface
    }

    /** Klein: Abschnitt, „noch …“, „dann …“ und gegebenenfalls die Karāha-Zeile. */
    @Composable
    private fun CompactContent(
        context: Context,
        title: String,
        countdown: Countdown,
        nextLine: String?,
        karahaLine: de.gebetszeiten.prayer.KarahaLine?,
        transparent: Boolean,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(CORNER)
                .background(widgetBackground(transparent))
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
                fontScale = context.resources.configuration.fontScale,
                countdown = countdown.show,
                karaha = karahaLine != null,
                next = nextLine != null,
            )
            HeroText(
                context, title, countdown,
                nextLine.takeIf { fit.next }, karahaLine.takeIf { fit.karaha },
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
    private fun NoTimesContent(headline: String, transparent: Boolean) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(CORNER)
                .background(widgetBackground(transparent))
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
        transparent: Boolean,
    ) {
        // Durchsichtig eingestellt: kein Foto, sonst wäre nichts durchsichtig.
        val onPhoto = !transparent
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .cornerRadius(CORNER)
                .background(widgetBackground(transparent))
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
