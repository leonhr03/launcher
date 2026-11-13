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

## Dein bisheriges Layout übernehmen

Xiaomi speichert die Anordnung des MIUI/HyperOS-Launchers in einem geschützten Bereich, den andere Apps ohne Root nicht lesen dürfen. Ein automatischer Import ist daher nicht möglich.
So baust du es schnell nach:
1. **Einstellungen → Raster:** Spalten/Zeilen wie bei MIUI (meist 4 × 6 oder 5 × 6), Symbolgröße angleichen.
2. **Widgets:** Bearbeitungsmodus → **+** → dieselben Widgets deiner Apps wählen (z. B. Xiaomi-Uhr, Wetter, Kalender). Größe über „Größe ändern“ anpassen.
3. **Apps & Ordner** per Drag & Drop an dieselben Plätze ziehen.
4. **Hintergrund:** Einstellungen → Hintergrundbild → „Aktuelles Hintergrundbild übernehmen“.

> Hinweis: Die speziellen MIUI-„Widget-Galerie“-Widgets (MAML) funktionieren nur im Xiaomi-Launcher. Normale Android-Widgets funktionieren alle.

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
