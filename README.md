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
| App antippen | App öffnen (mit Zoom-Animation) |
| Lange auf App drücken | Menü: App-Verknüpfungen, Umbenennen, Dock, Entfernen, Ausblenden, App-Info, Deinstallieren |
| Lange auf freie Fläche | Bearbeitungsmodus (Symbole wackeln) |
| Im Bearbeitungsmodus | App antippen, dann eine zweite → Plätze tauschen. „–“ entfernt vom Home-Bildschirm. „+“-Platz = dorthin verschieben |
| Nach oben wischen | App-Mediathek (alphabetisch, mit Suche) |
| Nach unten wischen | Suche |
| Home-Taste | zurück zur ersten Seite |

Alle Gesten sind in den Einstellungen änderbar (auch Doppeltippen zum Sperren → dafür die Bedienungshilfe „Clean Launcher – Gesten“ aktivieren).

## Einstellungen

Erreichbar über das Symbol „Launcher-Einstellungen“ oder im Bearbeitungsmodus über „Einstellungen“.

- **Raster:** Spalten, Zeilen, Seitenrand, Abstand oben
- **App-Symbole:** Größe (32–100 dp), Form (Original, iOS-Squircle, abgerundet, Kreis, Quadrat, Tropfen), Eckenradius, Schatten, Animationen
- **Beschriftung:** an/aus, Größe, Farbe, Schatten, fett, im Dock
- **Dock:** an/aus, Anzahl, Hintergrund, Deckkraft, Farbe, Eckenradius, Abstand
- **Startbildschirm:** Uhr-Widget, Seitenpunkte, Hintergrund abdunkeln, Statusleiste ausblenden, dunkle Statussymbole, Hochformat, neue Apps automatisch hinzufügen
- **Gesten:** wischen hoch/runter, doppelt tippen, lange drücken – jeweils frei belegbar
- **App-Mediathek:** Liste oder Raster, Deckkraft, Unschärfe, Tastatur automatisch, Websuche
- **Apps:** ausgeblendete und umbenannte Apps verwalten
- **Zurücksetzen:** nur Design oder alles

## Selbst bauen

```
./gradlew assembleRelease
```
Benötigt JDK 17 und das Android SDK (compileSdk 35).
