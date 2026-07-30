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
- **Abschaltbar:** In den Einstellungen lässt sich der Online-Abgleich
  deaktivieren; für Deutschland sind amtliche Tabellen offline gebündelt,
  sonst rechnet die App lokal.
- **Wear OS:** Die Watch-App bleibt vollständig offline.

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
- **Can be turned off:** The online sync can be disabled in the settings;
  official tables for Germany are bundled offline, elsewhere the app
  calculates locally.
- **Wear OS:** The watch app remains fully offline.

**Offline variant:** A variant without any Internet permission can still be
built from source (`de.gebetszeiten.offline`).

---

Kontakt / Contact: h.richtersohn@gmail.com
Quellcode / Source code: https://github.com/killingmelo/gebetszeiten
