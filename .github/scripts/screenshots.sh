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

# Kontextmenü
adb shell input swipe 667 880 667 880 1000
shot 02-app-menue 2
adb shell input keyevent KEYCODE_BACK
sleep 1

# App-Mediathek
adb shell input swipe 540 1500 540 500 200
shot 03-app-mediathek 3
adb shell input keyevent KEYCODE_BACK
sleep 1

# Bearbeitungsmodus (lange auf freie Fläche drücken)
adb shell input swipe 920 1800 920 1800 1000
shot 04-bearbeiten 2

# Ordner erstellen: Calendar auf Clock ziehen (echte Finger-Bewegung)
adb shell input motionevent DOWN 159 880
sleep 0.3
for x in 180 220 260 300 340 380 412; do adb shell input motionevent MOVE $x 880; done
sleep 1
adb shell input motionevent UP 412 880
shot 05a-ordner-erstellt 2
# Ziehen auf freien Platz: Gmail (Reihe 3, Spalte 2) ganz nach unten rechts
adb logcat -c
adb shell input motionevent DOWN 412 1185
sleep 0.3
for i in 1 2 3 4 5 6 7 8 9 10; do adb shell input motionevent MOVE $((412 + i * 50)) $((1185 + i * 60)); done
sleep 1
shot 05b-beim-ziehen 0
adb shell input motionevent UP 912 1785
shot 05-ordner-und-verschoben 2

# Widget-Galerie
adb shell input tap 88 70
shot 06-widgets 3
adb shell input keyevent KEYCODE_BACK
sleep 1
# Fertig
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input tap 412 880
shot 07-ordner-offen 3
adb shell input keyevent KEYCODE_BACK
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
adb logcat -d -s CleanLauncher > $OUT/log.txt
ls -la $OUT
