#!/bin/bash
# Installiert den Launcher im Emulator und macht Screenshots.
set -x
OUT=screenshots
rm -rf $OUT; mkdir -p $OUT
PKG=com.cleanlauncher
APK=$(ls app/build/outputs/apk/release/*.apk | head -1)
adb root || true
sleep 3
adb install -r "$APK"
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
# Rechte, die man auf dem Handy per Einstellungen gibt (für Glas-Effekt + Badges)
adb shell appops set $PKG MANAGE_EXTERNAL_STORAGE allow
adb shell cmd notification allow_listener $PKG/$PKG.system.NotificationService
adb shell cmd package set-home-activity $PKG/.MainActivity
sleep 2
shot() { sleep "${2:-3}"; adb exec-out screencap -p > "$OUT/$1.png"; }

adb shell input keyevent KEYCODE_HOME
sleep 8
# Testmitteilungen (für Badges & Sperrbildschirm)
adb shell cmd notification post -S bigtext -t "Lisa" chat1 "Kommst du heute noch vorbei?"
adb shell cmd notification post -S bigtext -t "Mama" chat2 "Essen ist um 19 Uhr fertig"
adb shell am force-stop $PKG
adb shell input keyevent KEYCODE_HOME
shot 01-startbildschirm 8

# Ordner erstellen: App auf andere App ziehen
adb shell input draganddrop 159 860 413 860 1500
sleep 2
adb shell input tap 540 120
adb shell input keyevent KEYCODE_BACK
shot 02-ordner-erstellt 2
adb shell input tap 413 840
shot 03-ordner-offen 2
adb shell input keyevent KEYCODE_BACK
sleep 1

# Kontextmenü
adb shell input swipe 667 840 667 840 1000
shot 04-app-menue 2
adb shell input keyevent KEYCODE_BACK
sleep 1

# App-Mediathek
adb shell input swipe 540 1500 540 500 200
shot 05-app-mediathek 3
adb shell input keyevent KEYCODE_BACK
sleep 1

# Bearbeitungsmodus (auf Seite 2 lange auf freie Fläche drücken)
adb shell input swipe 950 1300 100 1300 250
sleep 2
adb shell input swipe 540 1650 540 1650 1000
shot 06-bearbeiten 2
# Widget-Galerie
adb shell input tap 89 126
shot 07-widgets 3
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_HOME
sleep 1
adb shell input keyevent KEYCODE_HOME
sleep 2

# Sperrbildschirm
adb shell am start -n $PKG/.lock.LockScreenActivity
shot 08-sperrbildschirm 4
adb shell input keyevent KEYCODE_HOME
sleep 2

# Einstellungen
adb shell am start -n $PKG/.SettingsActivity
shot 09-einstellungen 4
adb shell input swipe 540 1700 540 500 400
shot 10-einstellungen-2 2
ls -la $OUT
