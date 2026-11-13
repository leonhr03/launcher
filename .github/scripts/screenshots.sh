#!/bin/bash
# Installiert den Launcher im Emulator und macht Screenshots.
set -x
OUT=screenshots
mkdir -p $OUT
APK=$(ls app/build/outputs/apk/release/*.apk | head -1)
adb install -r "$APK"
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell cmd package set-home-activity com.cleanlauncher/.MainActivity
sleep 2
shot() { sleep "${2:-4}"; adb exec-out screencap -p > "$OUT/$1.png"; }

# Startbildschirm
adb shell input keyevent KEYCODE_HOME
shot 1-startbildschirm 8
# Zweite Seite
adb shell input swipe 900 1200 150 1200 250
shot 2-seite-2 3
adb shell input keyevent KEYCODE_HOME
sleep 2
# App-Mediathek (nach oben wischen)
adb shell input swipe 540 1500 540 600 200
shot 3-app-mediathek 3
adb shell input keyevent KEYCODE_BACK
sleep 2
# Kontextmenü (lange auf erstes Symbol drücken)
adb shell input swipe 160 330 160 330 900
shot 4-app-menue 3
adb shell input keyevent KEYCODE_BACK
sleep 2
# Bearbeitungsmodus (lange auf freie Fläche im Dock-Bereich drücken)
adb shell input swipe 540 1650 540 1650 900
shot 5-bearbeiten 3
adb shell input keyevent KEYCODE_BACK
sleep 1
# Einstellungen
adb shell am start -n com.cleanlauncher/.SettingsActivity
shot 6-einstellungen 5
adb shell input swipe 540 1700 540 500 400
shot 7-einstellungen-2 3
adb shell input swipe 540 1700 540 400 400
shot 8-einstellungen-3 3
ls -la $OUT
