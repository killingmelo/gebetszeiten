package de.gebetszeiten.desktop.ui

import de.gebetszeiten.core.prayertimes.Prayer
import de.gebetszeiten.core.prayertimes.display.germanName
import de.gebetszeiten.core.prayertimes.officialtimes.DiyanetPlace
import de.gebetszeiten.core.prayertimes.officialtimes.displayName
import de.gebetszeiten.desktop.data.Autostart
import de.gebetszeiten.desktop.data.DesktopSettings
import de.gebetszeiten.desktop.data.SettingsStore
import de.gebetszeiten.desktop.data.searchPlaces
import de.gebetszeiten.desktop.notify.EzanPlayer
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileNameExtensionFilter

private val LEAD_CHOICES = listOf(0, 5, 10, 15, 20, 30)
private val REMINDER_PRAYERS = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

/**
 * Einstellungsfenster. Speichern schreibt ueber [store] und meldet das
 * Ergebnis an [onSaved]; das Neuladen der Zeiten ist Sache des Aufrufers.
 * [applyAutostart] setzt den Windows-Autostart (in Handproben ein No-op, damit
 * nie in die echte Registry geschrieben wird).
 *
 * Der Prozess laeuft mit `sun.java2d.uiScale=1`, deshalb ist jedes Mass ueber
 * [scaled] gerechnet; die Schriften skaliert [installScaledDefaults].
 */
class SettingsWindow(
    private val store: SettingsStore,
    private val places: List<DiyanetPlace>,
    private val onSaved: (DesktopSettings) -> Unit,
    private val applyAutostart: (Boolean) -> Unit = { on -> Autostart.currentExe()?.let { Autostart.set(on, it) } },
) {
    private val player = EzanPlayer()
    private var current: DesktopSettings = store.load()

    private val frame = JFrame("Gebetszeiten – Einstellungen")
    private val searchField = JTextField()
    private val resultModel = DefaultListModel<DiyanetPlace>()
    private val resultList = JList(resultModel)
    private val chosenLabel = JLabel()
    private val reminderBoxes = REMINDER_PRAYERS.associateWith { JCheckBox(it.germanName()) }
    private val leadCombo = JComboBox(LEAD_CHOICES.map { if (it == 0) "Zur Gebetszeit" else "$it Min vorher" }.toTypedArray())
    private val showKaraha = JCheckBox("Karaha anzeigen")
    private val karahaWarning = JCheckBox("Vor Karaha warnen")
    private val ezanField = JTextField()
    private val fajrField = JTextField()
    private val volume = JSlider(0, 100, 70)
    private val probe = JButton("Probe hören")
    private val taskbarField = JCheckBox("Feld in der Taskleiste")
    private val autostart = JCheckBox("Mit Windows starten")

    // Der gewaehlte Ort; null = keiner.
    private var chosenName: String? = null
    private var chosenLat: Double? = null
    private var chosenLng: Double? = null
    private var chosenId: Int? = null

    init {
        installScaledDefaults()
        build()
        load(current)
        frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        frame.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent?) = player.stop()
        })
    }

    fun show() {
        SwingUtilities.invokeLater {
            frame.pack()
            frame.setLocationRelativeTo(null)
            frame.isVisible = true
            frame.toFront()
        }
    }

    val isShowing: Boolean get() = frame.isShowing

    // ---- Aufbau ----------------------------------------------------------

    private fun build() {
        val body = JPanel(GridBagLayout())
        body.border = BorderFactory.createEmptyBorder(scaled(12), scaled(14), scaled(8), scaled(14))
        var row = 0

        fun add(c: Component, fill: Int = GridBagConstraints.HORIZONTAL, top: Int = 4) {
            body.add(c, GridBagConstraints().apply {
                gridx = 0; gridy = row++; weightx = 1.0; this.fill = fill
                anchor = GridBagConstraints.WEST
                insets = java.awt.Insets(scaled(top), 0, 0, 0)
            })
        }

        fun heading(text: String) = add(JLabel(text).apply { font = font.deriveFont(java.awt.Font.BOLD) }, top = 12)

        heading("Ort")
        add(searchField)
        searchField.toolTipText = "Ort suchen, z. B. Nürnberg"
        resultList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        resultList.visibleRowCount = 5
        resultList.cellRenderer = javax.swing.DefaultListCellRenderer().let { base ->
            javax.swing.ListCellRenderer<DiyanetPlace> { list, value, index, sel, focus ->
                base.getListCellRendererComponent(list, describe(value), index, sel, focus)
            }
        }
        add(JScrollPane(resultList).apply { preferredSize = Dimension(scaled(400), scaled(110)) }, GridBagConstraints.BOTH)
        add(chosenLabel)

        heading("Erinnerungen")
        add(JPanel(java.awt.GridLayout(1, 0, scaled(4), 0)).apply { reminderBoxes.values.forEach { add(it) } })
        add(JPanel(FlowLayout(FlowLayout.LEFT, scaled(6), 0)).apply {
            add(JLabel("Vorlauf"))
            add(leadCombo)
        })
        add(showKaraha)
        add(karahaWarning)

        heading("Ezan")
        add(fileRow("Ezan-Datei", ezanField))
        add(fileRow("Fajr eigene Datei", fajrField))
        add(JPanel(BorderLayout(scaled(8), 0)).apply {
            add(JLabel("Lautstärke"), BorderLayout.WEST)
            add(volume, BorderLayout.CENTER)
            add(probe, BorderLayout.EAST)
        })

        heading("Programm")
        add(taskbarField)
        add(autostart)

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, scaled(8), scaled(8)))
        val saveButton = JButton("Speichern")
        val cancelButton = JButton("Abbrechen")
        buttons.add(saveButton)
        buttons.add(cancelButton)

        frame.contentPane.layout = BorderLayout()
        frame.contentPane.add(body, BorderLayout.CENTER)
        frame.contentPane.add(buttons, BorderLayout.SOUTH)
        frame.rootPane.defaultButton = saveButton

        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = refreshResults()
            override fun removeUpdate(e: DocumentEvent?) = refreshResults()
            override fun changedUpdate(e: DocumentEvent?) = refreshResults()
        })
        resultList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) resultList.selectedValue?.let { choose(it) }
        }
        probe.addActionListener { playProbe() }
        saveButton.addActionListener { save() }
        cancelButton.addActionListener { frame.dispose() }
        val updateProbe = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateProbeEnabled()
            override fun removeUpdate(e: DocumentEvent?) = updateProbeEnabled()
            override fun changedUpdate(e: DocumentEvent?) = updateProbeEnabled()
        }
        ezanField.document.addDocumentListener(updateProbe)
        fajrField.document.addDocumentListener(updateProbe)
    }

    private fun fileRow(label: String, field: JTextField): JPanel {
        field.isEditable = false
        val pick = JButton("Wählen…")
        val clear = JButton("Leeren")
        pick.addActionListener {
            val chooser = JFileChooser(field.text.takeIf { it.isNotBlank() }?.let { Path.of(it).parent?.toFile() })
            chooser.fileFilter = FileNameExtensionFilter("Audio (*.mp3, *.wav)", "mp3", "wav")
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                field.text = chooser.selectedFile.absolutePath
            }
        }
        clear.addActionListener { field.text = "" }
        return JPanel(GridBagLayout()).apply {
            add(JLabel(label), GridBagConstraints().apply { gridx = 0; insets = java.awt.Insets(0, 0, 0, scaled(8)); anchor = GridBagConstraints.WEST })
            add(field, GridBagConstraints().apply { gridx = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL })
            add(pick, GridBagConstraints().apply { gridx = 2; insets = java.awt.Insets(0, scaled(6), 0, 0) })
            add(clear, GridBagConstraints().apply { gridx = 3; insets = java.awt.Insets(0, scaled(4), 0, 0) })
        }
    }

    // ---- Zustand ---------------------------------------------------------

    private fun describe(p: DiyanetPlace): String {
        val province = DiyanetPlace(0, p.province, "", p.countryCode, 0.0, 0.0).displayName()
        return "${p.displayName()} · $province · ${p.countryCode}"
    }

    private fun load(s: DesktopSettings) {
        chosenName = s.placeName
        chosenLat = s.latitude
        chosenLng = s.longitude
        chosenId = s.diyanetId
        updateChosenLabel()
        REMINDER_PRAYERS.forEach { reminderBoxes.getValue(it).isSelected = it in s.reminders }
        leadCombo.selectedIndex = LEAD_CHOICES.indexOf(s.leadMinutes).takeIf { it >= 0 } ?: 0
        showKaraha.isSelected = s.showKaraha
        karahaWarning.isSelected = s.karahaWarning
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

    private fun collect(): DesktopSettings = current.copy(
        placeName = chosenName,
        latitude = chosenLat,
        longitude = chosenLng,
        diyanetId = chosenId,
        reminders = REMINDER_PRAYERS.filter { reminderBoxes.getValue(it).isSelected }.toSet(),
        leadMinutes = LEAD_CHOICES[leadCombo.selectedIndex.coerceAtLeast(0)],
        karahaWarning = karahaWarning.isSelected,
        showKaraha = showKaraha.isSelected,
        ezanFile = ezanField.text.takeIf { it.isNotBlank() },
        fajrEzanFile = fajrField.text.takeIf { it.isNotBlank() },
        ezanVolume = volume.value,
        taskbarField = taskbarField.isSelected,
        autostart = autostart.isSelected,
    )

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
