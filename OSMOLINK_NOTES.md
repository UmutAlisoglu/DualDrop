# OsmoLink – Notizen für später (Bauen, Updaten, Veröffentlichen)

Stand: 2026-11-07. Diese Datei ist für Druomi und für jeden Claude, der hier weitermacht.

## Was wo liegt

| Was | Wo |
|---|---|
| Quellcode (git, Branch `osmolink`) | `Desktop\Projects\osmo-action4-research\OsmoLink` |
| Upstream (Osmosis) | git-Remote `upstream` = https://github.com/KonradIT/Osmosis |
| OsmoLink-eigener Code | `app/src/main/java/de/druomi/osmolink/` (+ `res/values*/osmolink_strings.xml`, `res/drawable/ol_*`) |
| **Signatur-Schlüssel (sichern!)** | `Desktop\Projects\osmo-action4-research\osmolink-release.jks` |
| **Passwörter zum Schlüssel (sichern!)** | `OsmoLink\keystore.properties` (nicht in git) |
| Fertige APK | `OsmoLink\app\build\outputs\apk\release\app-release.apk` |
| Forschung, Bericht | https://claude.ai/artifact/5HCyjri1o1M6xv4eX4Mtip |

Ohne `osmolink-release.jks` + Passwörter kann niemand ein Update bauen, das sich über die installierte App installieren lässt. Beides z. B. in einen Passwort-Manager oder auf einen USB-Stick kopieren.

## App neu bauen

Voraussetzungen auf dem PC (alles schon installiert): JDK 17 (`C:\Program Files\Eclipse Adoptium\jdk-17*`), Android SDK unter `%LOCALAPPDATA%\Android\Sdk` (platforms 35 + 36, build-tools 35/36), adb.

```bash
cd ~/Desktop/Projects/osmo-action4-research/OsmoLink
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.21.9-hotspot"
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Vor jedem Update in `app/build.gradle` `versionCode` um 1 erhöhen und `versionName` anpassen.

## Updates von Osmosis übernehmen

```bash
git fetch upstream
git merge upstream/main      # Konflikte meist nur in MainActivity.kt, activity_main.xml, build.gradle, AndroidManifest.xml
```

Unsere Änderungen an Osmosis-Dateien sind klein und mit `OsmoLink:` kommentiert (Suche danach).

## Wie „Heim-WLAN behalten" funktioniert

- Befehl: `cmd wifi force-overlay-config-value bool config_wifiMultiStaLocalOnlyConcurrencyEnabled enabled true` (aus: `... enabled false`). Hält bis zum Neustart.
- Die App koppelt sich einmalig mit dem eigenen „Wireless-Debugging" (Bibliothek libadb-android), gibt sich `WRITE_SECURE_SETTINGS` und schaltet danach Wireless-Debugging selbst an/aus.
- Nach jedem Neustart: `BootReceiver` → `EnsureWorker` (WorkManager, wartet auf WLAN) → Flag setzen. Gemessen 15–45 s nach dem Entsperren.
- Kompatibilitäts-Check: `cmd wifi query-interface 2000 com.android.shell STA -new` muss „can be created without destroying" melden.
- Achtung: `WifiManager.isStaConcurrencyForLocalOnlyConnectionsSupported` ist bei Samsung gecacht, deshalb merkt sich die App den Boot, in dem das Flag bestätigt wurde.

## Rechtliche Einschätzung zum Verbreiten (keine Rechtsberatung)

**Lizenzen – erlaubt, mit Pflichten:**
- Osmosis: MIT. Weitergeben und ändern erlaubt, der MIT-Hinweis (LICENSE.txt, Copyright Konrad Iturbe) muss mit ausgeliefert werden. Ist im Repo.
- libadb-android: wahlweise Apache-2.0 oder GPL-3.0 → wir nutzen Apache-2.0 (Lizenztext/NOTICE beilegen).
- spake2-java (von libadb mitgezogen): LGPL-3.0. Erlaubt, solange der Quellcode bzw. Link dazu und der LGPL-Text mitgeliefert werden; bei einer Open-Source-App unproblematisch.
- Conscrypt, AndroidX, Material, Kotlin: Apache-2.0. Bouncy Castle: MIT-artig. Alles erlaubt.
- → Vor Veröffentlichung: Datei `THIRD_PARTY_NOTICES.md` mit allen Lizenzen beilegen und in der App verlinken.

**Marke „Osmo"/„DJI":**
- „OSMO" ist eine eingetragene Marke von DJI (u. a. USPTO Nr. 86700744, „OSMO ACTION" 88379274). Ein App-Name, der „Osmo" enthält (OsmoLink), kann als Markenverletzung oder Verwechslungsgefahr gewertet werden, vor allem im Play Store.
- Beschreibende Nennung ist erlaubt („funktioniert mit DJI Osmo Action 4"), solange kein DJI-Logo verwendet und keine Zugehörigkeit vorgetäuscht wird. Hinweis „Nicht mit DJI verbunden" ist in der App.
- → Empfehlung: vor einer öffentlichen Veröffentlichung umbenennen (Name ohne „Osmo"/„DJI").

**Reverse Engineering / Protokoll:**
- Das Kameraprotokoll wurde von Osmosis durch Mitschneiden der eigenen Kamera-Kommunikation dokumentiert. Wir kopieren keinen DJI-Code und keine DJI-Firmware. In der EU erlaubt die Software-Richtlinie (2009/24/EG, Art. 6) Reverse Engineering zur Herstellung von Interoperabilität; nach EuGH (SAS Institute, C-406/10) sind Funktionalität und Datenformate eines Programms nicht urheberrechtlich geschützt, was für ein Kommunikationsprotokoll sinngemäß gilt.
- Risiko: DJI könnte trotzdem Druck machen (z. B. Abmahnung, Takedown-Anfrage bei GitHub). Bisher ist nichts gegen Osmosis bekannt (seit 2026 öffentlich, F-Droid).

**Google Play / Verteilung:**
- GitHub-Release (APK) und F-Droid: möglich. Ab 2026/2027 verlangt Google auch für Apps außerhalb des Play Stores eine Entwickler-Verifizierung (zuerst in BR/ID/SG/TH ab Sept. 2026, weltweit 2027+). Für Hobby-Entwickler soll es ein kostenloses Konto mit begrenzter Verteilung geben.
- Play Store: Apps, die Wireless-Debugging nutzen (z. B. Shizuku, LADB), gibt es dort. Für die Freigabe muss die Nutzung von `WRITE_SECURE_SETTINGS` und des Foreground-Service „specialUse" begründet werden. Als Privatperson mit einmaligem Entwicklerkonto (25 USD) möglich; bei kostenpflichtigen Apps wird die Adresse öffentlich angezeigt.
- Technisches Risiko: Google diskutiert (2026), On-Device-ADB einzuschränken. Dann würde die Selbst-Einrichtung ggf. nicht mehr funktionieren; der Kamera-Teil wäre nicht betroffen.

**Haftung:** In README und App klar sagen: inoffiziell, ohne Gewähr, Nutzung auf eigenes Risiko. Die App ändert nur einen Laufzeit-Schalter (bis zum Neustart) und keine Firmware.

## Veröffentlichen (wenn Druomi zustimmt)

1. Name final festlegen (siehe Marke), ggf. `app_name`, `applicationId` (vor erster Veröffentlichung!) und README anpassen.
2. `THIRD_PARTY_NOTICES.md` ergänzen.
3. Repo auf GitHub (Account `Druomiq`) anlegen, `osmolink`-Branch pushen, als Fork-Hinweis Osmosis verlinken.
4. Release mit `app-release.apk` anhängen.
