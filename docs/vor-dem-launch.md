# Vor dem Launch (Stand 05.11.2026, Zweig `feat/app-design-review`)

Geprüft am Gesamtstand (bis `ea30681`): Unit-Tests online 589, offline 582, core 210, alle grün; Lint (onlineRelease) 0 Fehler, 28 Warnungen; Release-Build mit R8 baut (APK 18 MB).

## Muss vor dem Upload erledigt sein

1. **Erledigt 05.11.2026 (Entscheidung des Herausgebers):** Nutzung mit Quellenangabe; Quelle steht unter „Über“. Vorher: **Aḏān-Aufnahme ohne Lizenz.** `app/src/main/res/raw/adhan_almakki.ogg` ist der YouTube-Download von ʿAbd al-Karīm al-Makkī. Ohne Erlaubnis des Rechteinhabers nicht veröffentlichen. Ersatz mit geprüfter Lizenz: Liste der Aḏān-Sitzung (`adhan-freie-aufnahmen.md`), z. B. Mardin Sabah-Ezan (CC0), „Beautiful adhan“ (CC0), Asim Javed (CC0). Bei CC BY: Urheber in „Über“ nennen.
2. **Erledigt:** 0.4.0 (24), „Neu in dieser Version“ für 24 in de/en. Vorher: **Versionsnummer.** Der Zweig steht auf 0.2.0 (versionCode 22), im Play Store ist 0.3.0 (23). Play lehnt kleinere oder gleiche Codes ab: mindestens 24.
3. **Erledigt:** `main` eingeführt; der Squash entsprach Commit `5b84011`, alle App-Konflikte zugunsten des Zweigs gelöst. Danach Tests online 589, offline 582, core 210, Wear 38 grün, Lint 0 Fehler, Release-APK und AAB signiert gebaut. Vorher: **Zweig mit `main` zusammenführen.** `main` hat 3 Commits, die hier fehlen, darunter einen Squash eines früheren Stands dieses Zweigs (`eea338b`) und das Release 0.3.0. Konflikte sind wahrscheinlich; danach alle Tests erneut.
4. **Play Console: Vordergrunddienst.** Neu ist `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (Aḏān). Play verlangt dafür eine Erklärung mit Begründung und meist ein kurzes Video der Funktion.
5. **Release-Fassung auf einem echten Telefon.** Bisher nur Debug auf dem Emulator. R8 kann zur Laufzeit etwas entfernen, das gebraucht wird (Glance-Widgets, Widget-Einstellungen, Aḏān-Dienst).

## Sollte vor dem Launch geprüft werden

6. **Aktualisierung von 0.3.0.** Alte Einstellungen müssen sauber übernommen werden: Widget-Schalter „transparent“ wird 45 %, bestehende Widgets ohne eigene Werte bleiben gleich, Ersteinrichtung kommt nicht erneut.
7. **Aḏān auf echtem Telefon:** Wiedergabe bei gesperrtem, dunklem Bildschirm und im Doze-Modus, Stopp per Lautstärketaste (auf dem Emulator nur bei eingeschaltetem Bildschirm), „Nicht stören“, Telefonat, Bluetooth-Kopfhörer, Akku-Optimierung des Herstellers (OnePlus/OPPO beendet Hintergrunddienste gern).
8. **Widgets auf echtem Telefon:** alle Aufbauten in 2×1, 2×2, 4×1, 4×2; Sperrbildschirm-Widget; „Restzeit groß“ zählt in Stufen weiter; zwei Widgets mit verschiedenen Einstellungen.
9. **Kleiner Bildschirm (360×640) und große Schrift (200 %):** „Heute“ mit Bild im Kopf und Unterzeilen wurde nur auf 412×915 angesehen.
10. **TalkBack:** „Heute“ (Gebetszeilen und Unterzeilen antippbar), Widget-Einstellungen, Aḏān-Einstellungen. Die Unterzeilen sind nur 24 dp hoch.
11. **Hell, Dunkel, Hochkontrast** für die neuen Bildschirme (Widget einstellen, Aḏān).
12. **Uhr (Wear) und Desktop** nach dem Zusammenführen kurz prüfen, ob Sync und geteilter Code noch passen.

## Store-Auftritt

13. **Screenshots** zeigen noch das alte „Heute“; neue aufnehmen (Bild im Kopf, Unterzeilen, Widget-Themen).
14. **Beschreibung** um Aḏān, Widget-Themen und Widget-Einstellungen ergänzen; „Neu in dieser Version“ schreiben.
15. **Datenschutz-Formular:** keine neuen Daten erhoben (Aḏān-Datei bleibt lokal). Trotzdem gegen die Liste im Formular halten.
16. **Fotos:** im Code als „Unsplash-Platzhalter“ markiert; entscheiden, ob sie bleiben (Unsplash-Lizenz erlaubt das, Nennung steht in „Über“).

## Bekannte Grenzen (bewusst so)

- Widget-Ecken nicht wählbar (Launcher entscheidet).
- Duʿāʾ nach dem Aḏān nur mit eigener Datei, keine frei lizenzierte Aufnahme gefunden.
- Die Play-Fassung ist auf dem Testtelefon seit 05.11.2026 nicht mehr installiert; Ursache unklar.
