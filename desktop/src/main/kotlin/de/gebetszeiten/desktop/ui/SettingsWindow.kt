package de.gebetszeiten.desktop.ui

import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.core.prayertimes.display.germanName
import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace
import de.gebetszeiten.core.prayertimes.officialtimes.displayName
import de.gebetszeiten.desktop.data.Autostart
import de.gebetszeiten.desktop.data.DesktopSettings
import de.gebetszeiten.desktop.data.NotifyStyle
import de.gebetszeiten.desktop.data.SettingsStore
import de.gebetszeiten.desktop.data.ToneChoice
import de.gebetszeiten.desktop.data.searchPlaces
import de.gebetszeiten.desktop.notify.EzanPlayer
import de.gebetszeiten.desktop.notify.TonePlayer
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JTabbedPane
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileNameExtensionFilter

private val LEAD_CHOICES = listOf(0, 5, 10, 15, 20, 30)
private val END_CHOICES = listOf(0, 10, 15, 20, 30, 45)
private val SAHUR_CHOICES = listOf(0, 30, 45, 60, 90)
private val IFTAR_CHOICES = listOf(0, 5, 10, 15, 30)
private val BANNER_CHOICES = listOf(10, 20, 30, 60, 0)
private val REMINDER_PRAYERS = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

private fun minutes(list: List<Int>, zero: String, fmt: (Int) -> String) =
    list.map { if (it == 0) zero else fmt(it) }.toTypedArray()

/**
 * Einstellungsfenster. Der Konstruktor merkt sich nur die Argumente; alle
 * Swing-Komponenten entstehen beim ersten [show] auf dem EDT, egal von welchem
 * Thread aus aufgerufen wird. Speichern schreibt ueber [store] und meldet das
 * Ergebnis an [onSaved]; das Neuladen der Zeiten ist Sache des Aufrufers.
 * [applyAutostart] setzt den Windows-Autostart (in Handproben ein No-op).
 */
class SettingsWindow(
    private val store: SettingsStore,
    private val places: List<DiyanetPlace>,
    private val onSaved: (DesktopSettings) -> Unit,
    private val applyAutostart: (Boolean) -> Unit = { on -> Autostart.currentExe()?.let { Autostart.set(on, it) } },
) {
    @Volatile private var ui: SettingsUi? = null

    /** Baut das Fenster beim ersten Mal (auf dem EDT) und zeigt es; ein offenes wird nach vorn geholt. */
    fun show() = onEdt(wait = false) { ui().showNow() }

    val isShowing: Boolean get() = ui?.isShowing == true

    fun snapshot(): BufferedImage? = ui?.snapshot()

    internal fun searchAndSelectFirst(text: String): DiyanetPlace? = ui?.searchAndSelectFirst(text)

    internal fun selectTab(index: Int) = ui?.selectTab(index) ?: Unit

    internal fun saveOnEdt() = ui?.saveOnEdt() ?: Unit

    private fun ui(): SettingsUi = ui ?: SettingsUi(store, places, onSaved, applyAutostart).also { ui = it }

    private fun onEdt(wait: Boolean, block: () -> Unit) {
        when {
            SwingUtilities.isEventDispatchThread() -> block()
            wait -> SwingUtilities.invokeAndWait(block)
            else -> SwingUtilities.invokeLater(block)
        }
    }
}

/** Ton-Zeile: Auswahl, eigene Datei, Probe. */
private class ToneRow(val label: String) {
    val combo = JComboBox(ToneChoice.entries.map { it.label }.toTypedArray())
    val file = JTextField()
    val pick = JButton("Datei…")
    val probe = JButton("Probe")

    var choice: ToneChoice
        get() = ToneChoice.entries[combo.selectedIndex.coerceAtLeast(0)]
        set(v) { combo.selectedIndex = v.ordinal }

    fun updateEnabled() {
        val isFile = choice == ToneChoice.FILE
        file.isEnabled = isFile
        pick.isEnabled = isFile
        probe.isEnabled = choice != ToneChoice.OFF && (!isFile || file.text.isNotBlank())
    }
}

/**
 * Der eigentliche Fensteraufbau; nur auf dem EDT erzeugen. Speichern schreibt ueber [store] und meldet das
 * Ergebnis an [onSaved]; das Neuladen der Zeiten ist Sache des Aufrufers.
 * [applyAutostart] setzt den Windows-Autostart (in Handproben ein No-op, damit
 * nie in die echte Registry geschrieben wird).
 *
 * Der Prozess laeuft mit `sun.java2d.uiScale=1`, deshalb ist jedes Mass ueber
 * [scaled] gerechnet; die Schriften skaliert [installScaledDefaults].
 */
internal class SettingsUi(
    private val store: SettingsStore,
    private val places: List<DiyanetPlace>,
    private val onSaved: (DesktopSettings) -> Unit,
    private val applyAutostart: (Boolean) -> Unit = { on -> Autostart.currentExe()?.let { Autostart.set(on, it) } },
) {
    // Zuerst: Farbschema und Schriften muessen stehen, bevor die Felder unten Komponenten erzeugen.
    @Suppress("unused")
    private val themed = installScaledDefaults()
    private val player = EzanPlayer()
    private val tonePlayer = TonePlayer()
    private var current: DesktopSettings = store.load()
    private val palette = Theme.current()

    private val frame = JFrame("Gebetszeiten – Einstellungen")
    private val tabs = JTabbedPane()

    // Ort
    private val searchField = JTextField()
    private val resultModel = DefaultListModel<DiyanetPlace>()
    private val resultList = JList(resultModel)
    private val chosenLabel = JLabel()

    // Erinnerungen
    private val reminderBoxes = REMINDER_PRAYERS.associateWith { JCheckBox(it.germanName()) }
    private val leadCombo = JComboBox(minutes(LEAD_CHOICES, "Zur Gebetszeit") { "$it Min vorher" })
    private val endCombo = JComboBox(minutes(END_CHOICES, "Aus") { "$it Min vor Ende" })
    private val karahaWarning = JCheckBox("Vor Karāha-Zeiten warnen (15 Min vorher)")

    // Hinweise
    private val cumaHints = JCheckBox("Ǧumʿa: Donnerstagabend, Freitagmorgen, vor dem Ǧumʿa-Gebet")
    private val fastingHints = JCheckBox("Sunna-Fasten: Vorabend von Montag, Donnerstag und al-Ayyām al-Bīḍ")
    private val nafileHints = JCheckBox("Freiwillige Gebete: Beginn von Ḍuḥā und Tahaǧǧud")
    private val hijriHints = JCheckBox("Besondere Nächte und Tage (Lailat al-Qadr, ʿĀšūrāʾ, ʿArafa, ʿĪd …)")
    private val ramazanHints = JCheckBox("Ramaḍān: Saḥūr und Ifṭār")
    private val sahurCombo = JComboBox(minutes(SAHUR_CHOICES, "Aus") { "$it Min vor Imsāk" })
    private val iftarCombo = JComboBox(minutes(IFTAR_CHOICES, "Aus") { "$it Min vorher" })
    private val showTexts = JCheckBox("Lesetexte anzeigen (Qurʾān, Ḥadīṯ, Gelehrtenwort mit Quelle)")

    // Toene & Anzeige
    private val styleBanner = JRadioButton("Eigenes Banner (unten rechts)")
    private val styleWindows = JRadioButton("Windows-Benachrichtigung")
    private val bannerCombo = JComboBox(minutes(BANNER_CHOICES, "bleibt bis Klick") { "$it Sekunden" })
    private val toneRows = listOf(
        ToneRow("Gebetszeit"), ToneRow("Vorlauf"), ToneRow("Warnungen"), ToneRow("Hinweise"),
    )
    private val toneVolume = JSlider(0, 100, 60)
    private val ezanField = JTextField()
    private val fajrField = JTextField()
    private val volume = JSlider(0, 100, 70)
    private val probe = JButton("Probe hören")

    // Programm
    private val showKaraha = JCheckBox("Karāha im Feld anzeigen")
    private val exactRemaining = JCheckBox("Restzeit minutengenau anzeigen")
    private val taskbarField = JCheckBox("Feld in der Taskleiste")
    private val autostart = JCheckBox("Mit Windows starten")

    // Der gewaehlte Ort; null = keiner.
    private var chosenName: String? = null
    private var chosenLat: Double? = null
    private var chosenLng: Double? = null
    private var chosenId: Int? = null

    init {
        build()
        load(current)
        frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        frame.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent?) {
                player.stop()
                tonePlayer.stop()
            }
        })
    }

    /** Nur auf dem EDT aufrufen. */
    fun showNow() {
        // Geschlossen und neu geoeffnet: frisch laden, das Tray-Menue kann
        // inzwischen etwas geaendert haben (Feld in der Taskleiste).
        if (!frame.isShowing) {
            current = store.load()
            load(current)
        }
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
        frame.toFront()
    }

    val isShowing: Boolean get() = frame.isShowing

    // ---- Aufbau ----------------------------------------------------------

    /** Ein Reiter: Spalte mit Ueberschriften und Zeilen, oben buendig. */
    private inner class Page {
        val panel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(scaled(10), scaled(16), scaled(10), scaled(16))
        }
        private var row = 0

        fun add(c: Component, top: Int = 4, fill: Int = GridBagConstraints.HORIZONTAL, weighty: Double = 0.0) {
            panel.add(c, GridBagConstraints().apply {
                gridx = 0; gridy = row++; weightx = 1.0; this.weighty = weighty; this.fill = fill
                anchor = GridBagConstraints.NORTHWEST
                insets = Insets(scaled(top), 0, 0, 0)
            })
        }

        fun heading(text: String) = add(JLabel(text).apply {
            font = font.deriveFont(Font.BOLD, font.size2D * 1.1f)
            foreground = palette.accent
        }, top = if (row == 0) 0 else 14)

        fun note(text: String) = add(JLabel("<html><body style='width:${scaled(380)}px'>$text</body></html>").apply {
            foreground = palette.muted
            font = font.deriveFont(font.size2D * 0.92f)
        })

        fun labeled(label: String, c: JComponent) = add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(JLabel(label).apply { preferredSize = Dimension(scaled(150), preferredSize.height) })
            add(c)
        })

        /** Rest nach unten schieben. */
        fun end(): JPanel {
            add(JPanel().apply { isOpaque = false }, weighty = 1.0, fill = GridBagConstraints.BOTH)
            return panel
        }
    }

    private fun build() {
        tabs.addTab("Ort", placePage())
        tabs.addTab("Erinnerungen", reminderPage())
        tabs.addTab("Hinweise", hintPage())
        tabs.addTab("Töne & Anzeige", tonePage())
        tabs.addTab("Programm", programPage())
        tabs.preferredSize = Dimension(scaled(560), scaled(520))

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, scaled(8), scaled(8)))
        val saveButton = JButton("Speichern").apply { font = font.deriveFont(Font.BOLD) }
        val cancelButton = JButton("Abbrechen")
        buttons.add(saveButton)
        buttons.add(cancelButton)

        frame.contentPane.layout = BorderLayout()
        frame.contentPane.add(tabs, BorderLayout.CENTER)
        frame.contentPane.add(buttons, BorderLayout.SOUTH)
        frame.rootPane.defaultButton = saveButton
        runCatching { frame.iconImage = javax.imageio.ImageIO.read(SettingsUi::class.java.getResource("/icon.png")) }

        saveButton.addActionListener { save() }
        cancelButton.addActionListener { frame.dispose() }
    }

    private fun placePage(): JPanel = Page().run {
        heading("Ort")
        note("Ort suchen, z. B. Nürnberg. Es zählen nur amtliche Diyanet-Zeiten.")
        add(searchField)
        resultList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resultList.visibleRowCount = 8
        resultList.cellRenderer = javax.swing.DefaultListCellRenderer().let { base ->
            javax.swing.ListCellRenderer<DiyanetPlace> { list, value, index, sel, focus ->
                base.getListCellRendererComponent(list, describe(value), index, sel, focus)
            }
        }
        add(JScrollPane(resultList).apply { preferredSize = Dimension(scaled(400), scaled(200)) }, fill = GridBagConstraints.BOTH)
        add(chosenLabel.apply { font = font.deriveFont(Font.BOLD) }, top = 8)

        searchField.document.addDocumentListener(onChange { refreshResults() })
        resultList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) resultList.selectedValue?.let { choose(it) }
        }
        end()
    }

    private fun reminderPage(): JPanel = Page().run {
        heading("Gebete")
        add(JPanel(java.awt.GridLayout(1, 0, scaled(4), 0)).apply {
            isOpaque = false
            reminderBoxes.values.forEach { add(it) }
        })
        labeled("Erinnerung", leadCombo)
        heading("Warnungen")
        labeled("Vakit endet", endCombo)
        note("Warnt vor dem Ende jeder gewählten Gebetszeit. Für ʿAṣr gilt der Beginn der Iṣfirār-Karāha als Ende.")
        add(karahaWarning)
        end()
    }

    private fun hintPage(): JPanel = Page().run {
        heading("Aus den Gebetszeiten")
        add(cumaHints)
        add(fastingHints)
        add(nafileHints)
        heading("Aus dem Hiǧra-Kalender")
        add(hijriHints)
        add(ramazanHints)
        labeled("Saḥūr-Erinnerung", sahurCombo)
        labeled("Ifṭār-Vorlauf", iftarCombo)
        note("Das Hiǧra-Datum kommt von der Diyanet-Seite und wird beim Abruf geholt. Fehlt es, gibt es keine Hiǧra-Hinweise statt geratener.")
        heading("Lesetexte")
        add(showTexts)
        note("Auszüge aus Werken der Gelehrten (Shamela), arabisch mit sinngemäßer deutscher Übersetzung und Quelle.")
        end()
    }

    private fun tonePage(): JPanel = Page().run {
        heading("Anzeige")
        ButtonGroup().apply { add(styleBanner); add(styleWindows) }
        add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
            isOpaque = false
            add(styleBanner)
            add(javax.swing.Box.createHorizontalStrut(scaled(16)))
            add(styleWindows)
        })
        labeled("Banner schließt nach", bannerCombo)
        styleBanner.addActionListener { bannerCombo.isEnabled = true }
        styleWindows.addActionListener { bannerCombo.isEnabled = false }

        heading("Töne")
        for (r in toneRows) add(toneRow(r))
        add(JPanel(BorderLayout(scaled(8), 0)).apply {
            isOpaque = false
            add(JLabel("Lautstärke").apply { preferredSize = Dimension(scaled(150), preferredSize.height) }, BorderLayout.WEST)
            add(toneVolume, BorderLayout.CENTER)
        })
        note("Bei „Nicht stören“ und nach „Töne 1 Stunde aus“ (Tray) bleibt es still; die Anzeige kommt trotzdem.")

        heading("Ezan")
        add(fileRow("Ezan-Datei", ezanField))
        add(fileRow("Faǧr eigene Datei", fajrField))
        add(JPanel(BorderLayout(scaled(8), 0)).apply {
            isOpaque = false
            add(JLabel("Lautstärke").apply { preferredSize = Dimension(scaled(150), preferredSize.height) }, BorderLayout.WEST)
            add(volume, BorderLayout.CENTER)
            add(probe, BorderLayout.EAST)
        })
        note("Ist eine Ezan-Datei gewählt, ersetzt der Ezan den Ton zur Gebetszeit.")

        probe.addActionListener { playProbe() }
        val updateProbe = onChange { updateProbeEnabled() }
        ezanField.document.addDocumentListener(updateProbe)
        fajrField.document.addDocumentListener(updateProbe)
        end()
    }

    private fun programPage(): JPanel = Page().run {
        heading("Feld in der Taskleiste")
        add(taskbarField)
        add(showKaraha)
        add(exactRemaining)
        heading("Start")
        add(autostart)
        end()
    }

    private fun toneRow(r: ToneRow): JPanel {
        r.file.isEditable = false
        r.combo.addActionListener { r.updateEnabled() }
        r.file.document.addDocumentListener(onChange { r.updateEnabled() })
        r.pick.addActionListener { chooseAudio(r.file) }
        r.probe.addActionListener { tonePlayer.play(r.choice, r.file.text.takeIf { it.isNotBlank() }, toneVolume.value) }
        return JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(JLabel(r.label).apply { preferredSize = Dimension(scaled(150), preferredSize.height) }, GridBagConstraints().apply { gridx = 0; anchor = GridBagConstraints.WEST })
            add(r.combo, GridBagConstraints().apply { gridx = 1; insets = Insets(0, 0, 0, scaled(6)) })
            add(r.file, GridBagConstraints().apply { gridx = 2; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL })
            add(r.pick, GridBagConstraints().apply { gridx = 3; insets = Insets(0, scaled(6), 0, 0) })
            add(r.probe, GridBagConstraints().apply { gridx = 4; insets = Insets(0, scaled(4), 0, 0) })
        }
    }

    private fun fileRow(label: String, field: JTextField): JPanel {
        field.isEditable = false
        val pick = JButton("Wählen…")
        val clear = JButton("Leeren")
        pick.addActionListener { chooseAudio(field) }
        clear.addActionListener { field.text = "" }
        return JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(JLabel(label).apply { preferredSize = Dimension(scaled(150), preferredSize.height) }, GridBagConstraints().apply { gridx = 0; anchor = GridBagConstraints.WEST })
            add(field, GridBagConstraints().apply { gridx = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL })
            add(pick, GridBagConstraints().apply { gridx = 2; insets = Insets(0, scaled(6), 0, 0) })
            add(clear, GridBagConstraints().apply { gridx = 3; insets = Insets(0, scaled(4), 0, 0) })
        }
    }

    private fun chooseAudio(field: JTextField) {
        val chooser = JFileChooser(field.text.takeIf { it.isNotBlank() }?.let { Path.of(it).parent?.toFile() })
        chooser.fileFilter = FileNameExtensionFilter("Audio (*.mp3, *.wav)", "mp3", "wav")
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            field.text = chooser.selectedFile.absolutePath
        }
    }

    private fun onChange(block: () -> Unit) = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent?) = block()
        override fun removeUpdate(e: DocumentEvent?) = block()
        override fun changedUpdate(e: DocumentEvent?) = block()
    }

    // ---- Zustand ---------------------------------------------------------

    private fun describe(p: DiyanetPlace): String {
        val province = DiyanetPlace(0, p.province, "", p.countryCode, 0.0, 0.0).displayName()
        return "${p.displayName()} · $province · ${p.countryCode}"
    }

    private fun <T> JComboBox<T>.select(choices: List<Int>, value: Int) {
        selectedIndex = choices.indexOf(value).takeIf { it >= 0 } ?: 0
    }

    private fun JComboBox<*>.value(choices: List<Int>): Int = choices[selectedIndex.coerceAtLeast(0)]

    private fun load(s: DesktopSettings) {
        chosenName = s.placeName
        chosenLat = s.latitude
        chosenLng = s.longitude
        chosenId = s.diyanetId
        updateChosenLabel()
        REMINDER_PRAYERS.forEach { reminderBoxes.getValue(it).isSelected = it in s.reminders }
        leadCombo.select(LEAD_CHOICES, s.leadMinutes)
        endCombo.select(END_CHOICES, s.endWarningMinutes)
        karahaWarning.isSelected = s.karahaWarning
        cumaHints.isSelected = s.cumaHints
        fastingHints.isSelected = s.fastingHints
        nafileHints.isSelected = s.nafileHints
        hijriHints.isSelected = s.hijriHints
        ramazanHints.isSelected = s.ramazanHints
        sahurCombo.select(SAHUR_CHOICES, s.sahurMinutes)
        iftarCombo.select(IFTAR_CHOICES, s.iftarLeadMinutes)
        showTexts.isSelected = s.showTexts
        styleBanner.isSelected = s.notifyStyle == NotifyStyle.BANNER
        styleWindows.isSelected = s.notifyStyle == NotifyStyle.WINDOWS
        bannerCombo.select(BANNER_CHOICES, s.bannerSeconds)
        bannerCombo.isEnabled = s.notifyStyle == NotifyStyle.BANNER
        val tones = listOf(s.tonePrayer to s.tonePrayerFile, s.toneLead to s.toneLeadFile, s.toneWarning to s.toneWarningFile, s.toneHint to s.toneHintFile)
        toneRows.zip(tones).forEach { (r, t) ->
            r.choice = t.first
            r.file.text = t.second.orEmpty()
            r.updateEnabled()
        }
        toneVolume.value = s.toneVolume
        showKaraha.isSelected = s.showKaraha
        exactRemaining.isSelected = s.exactRemaining
        ezanField.text = s.ezanFile.orEmpty()
        fajrField.text = s.fajrEzanFile.orEmpty()
        volume.value = s.ezanVolume
        taskbarField.isSelected = s.taskbarField
        autostart.isSelected = s.autostart
        updateProbeEnabled()
    }

    private fun updateChosenLabel() {
        chosenLabel.text = chosenName?.let { "Gewählt: $it" } ?: "Noch kein Ort gewählt"
    }

    private fun refreshResults() {
        val hits = searchPlaces(places, searchField.text)
        resultModel.clear()
        hits.forEach { resultModel.addElement(it) }
    }

    private fun choose(p: DiyanetPlace) {
        chosenName = p.displayName()
        chosenLat = p.latitude
        chosenLng = p.longitude
        chosenId = p.diyanetId
        updateChosenLabel()
    }

    private fun probeFile(): String? = ezanField.text.takeIf { it.isNotBlank() } ?: fajrField.text.takeIf { it.isNotBlank() }

    private fun updateProbeEnabled() {
        probe.isEnabled = probeFile() != null
    }

    private fun playProbe() {
        if (player.isPlaying) {
            player.stop()
            return
        }
        probeFile()?.let { player.play(Path.of(it), volume.value) }
    }

    private fun collect(): DesktopSettings {
        fun file(r: ToneRow) = r.file.text.takeIf { it.isNotBlank() }
        // Eigene Datei ohne Datei waere stumm: dann lieber der eingebaute Klang.
        fun choice(r: ToneRow) = if (r.choice == ToneChoice.FILE && file(r) == null) ToneChoice.SOFT else r.choice
        val (prayer, lead, warning, hint) = toneRows
        return current.copy(
            placeName = chosenName,
            latitude = chosenLat,
            longitude = chosenLng,
            diyanetId = chosenId,
            reminders = REMINDER_PRAYERS.filter { reminderBoxes.getValue(it).isSelected }.toSet(),
            leadMinutes = leadCombo.value(LEAD_CHOICES),
            endWarningMinutes = endCombo.value(END_CHOICES),
            karahaWarning = karahaWarning.isSelected,
            cumaHints = cumaHints.isSelected,
            fastingHints = fastingHints.isSelected,
            nafileHints = nafileHints.isSelected,
            hijriHints = hijriHints.isSelected,
            ramazanHints = ramazanHints.isSelected,
            sahurMinutes = sahurCombo.value(SAHUR_CHOICES),
            iftarLeadMinutes = iftarCombo.value(IFTAR_CHOICES),
            showTexts = showTexts.isSelected,
            notifyStyle = if (styleWindows.isSelected) NotifyStyle.WINDOWS else NotifyStyle.BANNER,
            bannerSeconds = bannerCombo.value(BANNER_CHOICES),
            tonePrayer = choice(prayer),
            toneLead = choice(lead),
            toneWarning = choice(warning),
            toneHint = choice(hint),
            tonePrayerFile = file(prayer),
            toneLeadFile = file(lead),
            toneWarningFile = file(warning),
            toneHintFile = file(hint),
            toneVolume = toneVolume.value,
            showKaraha = showKaraha.isSelected,
            exactRemaining = exactRemaining.isSelected,
            ezanFile = ezanField.text.takeIf { it.isNotBlank() },
            fajrEzanFile = fajrField.text.takeIf { it.isNotBlank() },
            ezanVolume = volume.value,
            taskbarField = taskbarField.isSelected,
            autostart = autostart.isSelected,
        )
    }

    /** Speichert, setzt den Autostart, meldet [onSaved], schliesst das Fenster. */
    internal fun save() {
        val s = collect()
        store.save(s)
        current = s
        applyAutostart(s.autostart)
        onSaved(s)
        frame.dispose()
    }

    // ---- Hilfen fuer die Handprobe --------------------------------------

    /** Tippt [text] ins Suchfeld und waehlt den ersten Treffer; liefert ihn. */
    internal fun searchAndSelectFirst(text: String): DiyanetPlace? {
        var hit: DiyanetPlace? = null
        SwingUtilities.invokeAndWait {
            searchField.text = text
            if (resultModel.size() > 0) {
                resultList.selectedIndex = 0
                hit = resultModel.getElementAt(0)
            }
        }
        return hit
    }

    internal fun selectTab(index: Int) = SwingUtilities.invokeAndWait { tabs.selectedIndex = index }

    /** Fenster als Bild (Handprobe), sonst null. */
    fun snapshot(): BufferedImage? {
        var img: BufferedImage? = null
        runCatching {
            SwingUtilities.invokeAndWait {
                val c = frame.contentPane
                img = BufferedImage(c.width, c.height, BufferedImage.TYPE_INT_ARGB)
                val g = img!!.createGraphics()
                c.paint(g)
                g.dispose()
            }
        }
        return img
    }

    internal fun saveOnEdt() = SwingUtilities.invokeAndWait { save() }
}
