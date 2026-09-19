# Datenschutzerklärung / Privacy Policy

**Gebetszeiten** (de.gebetszeiten)

## Deutsch

Diese App erhebt, speichert und teilt **keine personenbezogenen Daten** und
enthält **keine Werbung, kein Tracking, keine Analyse-Dienste, keine Konten**.

- **Kein GPS:** Der Gebetsort wird manuell aus einer eingebauten Städteliste
  gewählt und nur lokal gespeichert.
- **Internet nur für amtliche Zeiten:** Die App lädt die offiziellen
  Diyanet-Gebetszeiten für den gewählten Ort — primär direkt von
  `namazvakitleri.diyanet.gov.tr` (Jahres-Tabelle, typischerweise ein Abruf
  pro Jahr und Ort), ersatzweise vom Community-Proxy
  `prayertimes.api.abdus.dev`. Übermittelt wird dabei ausschließlich die
  **Diyanet-Standort-Kennung** des gewählten Ortes (bei erstmaliger Auswahl
  eines Ortes außerhalb Deutschlands einmalig der **Städtename** zur
  Auflösung). Keine Koordinaten, keine Geräte-Kennungen — technisch bedingt
  sieht der jeweilige Server dabei die IP-Adresse.
- **Online-Ortssuche (Fallback):** Findet die eingebaute Städteliste einen
  gesuchten Ort nicht, fragt die App (nur bei aktiviertem Online-Abgleich)
  den Geocoding-Dienst `geocoding-api.open-meteo.com` an. Übermittelt wird
  dabei ausschließlich der **getippte Ortsname** — keine Koordinaten, keine
  Geräte-Kennungen; technisch bedingt sieht der Server die IP-Adresse.
- **Abschaltbar:** In den Einstellungen lässt sich der Online-Abgleich
  deaktivieren (deaktiviert auch die Online-Ortssuche); für Deutschland sind
  amtliche Tabellen offline gebündelt. Für andere Orte zeigt die App ohne
  amtliche Zeiten **keine Zeiten mehr an**, sondern einen Hinweis — es sei
  denn, in den Einstellungen ist zusätzlich „Berechnung als Notausgang"
  aktiviert; nur dann rechnet die App lokal weiter.
- **Wear OS:** Die Watch-App ruft die amtlichen Zeiten für den gewählten Ort
  seit dieser Version selbst ab — über dieselben Endpunkte und mit denselben
  übertragenen Daten wie das Telefon (Diyanet-Standort-Kennung, typischerweise
  ein Abruf pro Jahr und Ort). Sie erhält die Zeiten außerdem weiterhin vom
  gekoppelten Handy (Play-Services-Gerätesync). Anders als am Telefon gibt es
  auf der Uhr **keinen eigenen Schalter**, der diesen Abruf abstellt — der
  oben beschriebene Online-Abgleich-Schalter wirkt nur auf dem Telefon, und
  auch der Notausgang („Berechnung als Notausgang") ändert daran nichts: er
  bestimmt nur, was ohne amtliche Zeiten angezeigt wird, nicht, ob die Uhr
  abruft. Ohne eigenen Abruf, ohne Handy-Sync und ohne eingebaute amtliche
  Tabellen zeigt sie — wie das Telefon — keine Zeiten, es sei denn, der
  Notausgang ist aktiviert. Wer den Netzzugriff der Uhr ausschließen will,
  muss die Offline-Variante der Watch-App installieren (siehe unten).

**Offline-Variante:** Aus dem Quellcode ist weiterhin eine Variante ohne
jede Internet-Berechtigung baubar (`de.gebetszeiten.offline`).

## English

This app does **not collect, store, or share any personal data** and contains
**no ads, no tracking, no analytics, and no accounts**.

- **No GPS:** The prayer location is chosen manually from a bundled city list
  and stored only locally on the device.
- **Internet only for official times:** The app downloads the official
  Diyanet prayer times for the chosen location — primarily directly from
  `namazvakitleri.diyanet.gov.tr` (full-year table, typically one request per
  year and location), with the community proxy `prayertimes.api.abdus.dev`
  as fallback. The only data transmitted is the **Diyanet location id** of
  the chosen place (plus, once, the **city name** when a location outside
  Germany is first selected, to resolve its id). No coordinates, no device
  identifiers — for technical reasons the respective server does see the IP
  address.
- **Online place search (fallback):** If the bundled city list has no match
  for a searched place, the app (only with online sync enabled) queries the
  geocoding service `geocoding-api.open-meteo.com`. The only data transmitted
  is the **typed place name** — no coordinates, no device identifiers; for
  technical reasons the server does see the IP address.
- **Can be turned off:** The online sync can be disabled in the settings
  (this also disables the online place search); official tables for Germany
  are bundled offline. For other places, without official times the app
  **shows no times at all**, only a notice — unless the "Calculation as a
  fallback" setting is additionally enabled, in which case it calculates
  locally.
- **Wear OS:** As of this version, the watch app fetches the official times
  for the chosen place itself — via the same endpoints and with the same
  data transmitted as the phone (Diyanet location id, typically one request
  per year and location). It also still receives times from the paired phone
  (Play Services device sync). Unlike the phone, the watch has **no switch of
  its own** to turn this off — the online-sync switch described above only
  affects the phone, and the fallback-calculation setting doesn't change it
  either: that setting only decides what is shown when no official times are
  available, not whether the watch fetches. Without its own fetch, without
  phone sync, and without bundled official tables, it shows — like the
  phone — no times, unless the fallback calculation is enabled. Anyone who
  wants to rule out the watch's network access entirely has to install the
  offline variant of the watch app (see below).

**Offline variant:** A variant without any Internet permission can still be
built from source (`de.gebetszeiten.offline`).

---

Kontakt / Contact: h.richtersohn@gmail.com
Quellcode / Source code: https://github.com/killingmelo/gebetszeiten
