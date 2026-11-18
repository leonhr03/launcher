# Theme „Clean iOS“ für Xiaomi / HyperOS

Datei: `theme/CleanIOS.mtz` (neu bauen mit `python3 theme/build_theme.py`)

**Inhalt:** iOS-Symbolform (Squircle) für alle Apps, zwei eigene Farbverlauf-Hintergründe (Startbildschirm + Sperrbildschirm).

## Installieren

1. `CleanIOS.mtz` aufs Handy laden (z. B. in *Downloads*).
2. **Designs**-App (Themes) öffnen → unten **Profil/Ich** → **Designs** → oben rechts **Importieren** → `CleanIOS.mtz` wählen → **Anwenden**.
   - Fehlt „Importieren“: In den Einstellungen → *Zusätzliche Einstellungen* → *Region* kurz auf **Indien** stellen, Designs-App neu öffnen, importieren, danach Region zurückstellen.
   - Setzt HyperOS das Theme nach ein paar Minuten zurück, ist das eine Xiaomi-Sperre für Themes außerhalb des Theme-Stores. Dann zumindest die Bilder `hintergrund-home.jpg` / `hintergrund-sperrbildschirm.jpg` normal als Hintergrund setzen.
3. Vorher den **Xiaomi-Launcher** wieder als Standard wählen (Einstellungen → Apps → Standard-Apps → Startbildschirm), dann gehen auch die echten Wischgesten.

## Den Rest iOS-ähnlich machen (in HyperOS eingebaut, kein Theme nötig)

| iOS-Funktion | Wo bei Xiaomi |
|---|---|
| Kontrollzentrum rechts / Mitteilungen links | Einstellungen → *Mitteilungen & Kontrollzentrum* → *Stil des Kontrollzentrums* → **Neue Version** |
| Große Uhr auf dem Sperrbildschirm | Sperrbildschirm lange drücken → **Anpassen** → Uhrstil wählen |
| App-Mediathek statt Seiten | Startbildschirm-Einstellungen → **Startbildschirm** → *Mit App-Schublade* |
| Symbolgröße / Raster 4×6 | Startbildschirm-Einstellungen → **Symbolgröße**, **Layout** |
| Wischgesten | Einstellungen → *Startbildschirm* → *Systemnavigation* → **Gesten** |

Nicht per Theme möglich: Dynamic Island, eigene Gestenleiste, Clean-Launcher-Kontrollzentrum (dafür bleibt die App).
