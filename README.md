# Clean Launcher

Ein aufgeräumter, iOS-ähnlicher Android-Launcher (gemacht für Xiaomi / MIUI / HyperOS, läuft aber auf jedem Android 8+).
Die Symbolgröße ist frei einstellbar, damit sie zu deinem bisherigen Launcher passt – und auch sonst lässt sich fast alles einstellen.

## Installieren

1. Auf GitHub im Tab **Actions** den letzten erfolgreichen Lauf von „APK bauen“ öffnen.
2. Unten bei **Artifacts** `CleanLauncher-apk` herunterladen, entpacken und die `app-release.apk` aufs Handy kopieren.
3. APK öffnen und installieren (Installation aus unbekannten Quellen erlauben).
4. Home-Taste drücken → „Clean Launcher“ wählen, oder in den Einstellungen:
   **Einstellungen → Apps → Apps verwalten → Standard-Apps → Startbildschirm** (bei HyperOS: *Standard-Apps → Launcher*).

> Tipp Xiaomi: Damit Gesten-Navigation mit einem fremden Launcher funktioniert, brauchst du eine aktuelle MIUI-/HyperOS-Version. Falls die Wischgesten nicht gehen, kurz die Navigationsleiste aktivieren.

## Bedienung

| Aktion | Was passiert |
|---|---|
| App antippen | App öffnen (Zoom-Animation) |
| Lange auf App drücken | iOS-Menü: App-Verknüpfungen, Home-Bildschirm bearbeiten, Umbenennen, Dock, App-Info, App entfernen |
| Lange drücken **und ziehen** | App/Ordner/Widget verschieben – freie Plätze sind erlaubt (Lücken wie bei MIUI) |
| App auf andere App ziehen | Ordner erstellen (Ordnertitel antippen zum Umbenennen) |
| App auf Ordner ziehen | In den Ordner legen |
| Beim Ziehen an den Rand | Seite wechseln |
| Lange auf freie Fläche | Bearbeitungsmodus (Symbole wackeln) – oben links **+** für Widgets |
| Nach oben wischen | App-Mediathek (Kategorien wie iOS, Suche) |
| Nach unten wischen / „Suchen“ | Suche |
| Home-Taste | zurück zur ersten Seite |

## Dein Xiaomi-Layout übernehmen (Seiten, Dock, Ordner, Widgets)

1. Auf dem Xiaomi: **Einstellungen → Über das Telefon → Sichern & wiederherstellen → Mobilgerät / Lokale Sicherung → Sichern**.
   Nur **Startbildschirm** (bzw. *Systemanwendungen → Startbildschirm*) auswählen und **ohne Passwort** sichern.
2. Im Launcher: **Launcher-Einstellungen → Xiaomi-Layout übernehmen → Sicherung automatisch suchen** (braucht „Zugriff auf alle Dateien“) oder die `.bak`-Datei selbst wählen.
3. **Übernehmen** tippen. Seiten, Raster, Dock, Ordner und Widgets landen an derselben Stelle. Für jedes Widget fragt Android eventuell einmal nach Erlaubnis.

Nicht übernehmbar: Xiaomi-eigene Spezial-Widgets (MAML/„Gadgets“) und Einstellungen *innerhalb* von Widgets (z. B. die Stadt beim Wetter) – die einmal neu wählen.

## Wischgesten statt 3 Tasten (Xiaomi)

MIUI/HyperOS schaltet die Vollbild-Gesten ab, sobald ein anderer Launcher aktiv ist. Einmalig per PC freischalten:

```
adb shell pm grant com.cleanlauncher android.permission.WRITE_SECURE_SETTINGS
adb shell settings put global force_fsg_nav_bar 1
```

(USB-Debugging in den Entwickleroptionen einschalten, Android Platform-Tools auf dem PC.) Danach in den Launcher-Einstellungen unter „Wischgesten“ den Schalter nutzen bzw. in den Systemeinstellungen „Gesten“ wählen.

## iOS-Sperrbildschirm

Einstellungen → **Sperrbildschirm (iOS-Stil)** einschalten und die angezeigten Berechtigungen erteilen (Über anderen Apps anzeigen, bei Xiaomi: „Auf Sperrbildschirm anzeigen“, „Pop-up-Fenster im Hintergrund“, Autostart, Akku „Keine Einschränkungen“).
Er zeigt große Uhr, Datum, Mitteilungen, Taschenlampe und Kamera. Deine echte Sperre (PIN/Fingerabdruck) bleibt aktiv – nach dem Hochwischen fragt das System wie gewohnt.

## Einstellungen

- **Aussehen:** iOS-Schrift (Inter), Glas-Effekt, „Suchen“-Taste, rote Badges, Widget-Stil (hell/dunkel/Glas), Widget-Ecken
- **Hintergrundbild:** aus der Galerie (für Home/Sperrbildschirm/beide) oder das aktuelle übernehmen
- **Sperrbildschirm:** an/aus, Mitteilungen, Inhalte verbergen bis entsperrt, Taschenlampe/Kamera, Uhrfarbe, Abdunkeln, Vorschau
- **Raster:** Spalten, Zeilen, Seitenrand, Abstand oben
- **App-Symbole:** Größe (32–100 dp), Form (Original, iOS-Squircle, abgerundet, Kreis, Quadrat, Tropfen), Eckenradius, Schatten, Animationen
- **Beschriftung:** an/aus, Größe, Farbe, Schatten, fett, im Dock
- **Dock:** an/aus, Anzahl, Hintergrund, Deckkraft, Farbe, Eckenradius, Abstand
- **Startbildschirm:** Seitenpunkte, Abdunkeln, Statusleiste, Hochformat, neue Apps automatisch hinzufügen
- **Gesten:** wischen hoch/runter, doppelt tippen, lange drücken – frei belegbar
- **App-Mediathek:** iOS-Kategorien, Liste oder Raster
- **Apps:** ausgeblendete und umbenannte Apps, Mitteilungszugriff
- **Zurücksetzen:** nur Design oder alles

Schrift: [Inter](https://github.com/rsms/inter) (SIL Open Font License, siehe `FONT-LICENSE-Inter.txt`).

## Selbst bauen

```
./gradlew assembleRelease
```
Benötigt JDK 17 und das Android SDK (compileSdk 35).
