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
# ---------- Xiaomi-Layout-Import ----------
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > /tmp/ui.xml
  python3 - "$1" <<'PY'
import re, sys, subprocess
xml = open('/tmp/ui.xml', encoding='utf-8', errors='ignore').read()
m = re.search(r'text="' + re.escape(sys.argv[1]) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
if m:
    x1, y1, x2, y2 = map(int, m.groups())
    subprocess.run(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)])
    print("tapped", sys.argv[1])
else:
    print("not found", sys.argv[1])
PY
}
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
  | grep -oE '[a-z][a-z0-9_.]+/[A-Za-z0-9_.$]+' | sort -u > /tmp/comps.txt
adb shell dumpsys appwidget > /tmp/aw.txt
grep -oE 'ComponentInfo\{[^}]+\}' /tmp/aw.txt | sed 's/ComponentInfo{//; s/}//' | grep -v cleanlauncher | sort -u > /tmp/providers.txt
cat /tmp/providers.txt
(grep -i -m1 'digital\|clock\|calendar' /tmp/providers.txt || head -1 /tmp/providers.txt) > /tmp/provider.txt
cat /tmp/provider.txt
python3 .github/scripts/make_miui_backup.py /tmp/comps.txt /tmp/provider.txt /tmp/home.bak
adb shell mkdir -p /sdcard/MIUI/backup/AllBackup/20261116_120000
adb push /tmp/home.bak "/sdcard/MIUI/backup/AllBackup/20261116_120000/Startbildschirm(com.miui.home).bak"
adb shell appwidget grantbind --package $PKG --user 0
adb shell am start -S -W -n $PKG/.SettingsActivity --ez auto_import true
shot 11-import-gefunden 6
tap_text "Übernehmen"
sleep 6
adb shell input keyevent KEYCODE_HOME
shot 12-import-seite-1 4
adb shell input swipe 950 1300 100 1300 250
shot 13-import-seite-2 3
adb shell input swipe 950 1300 100 1300 250
shot 14-import-seite-3 3

# ---------- iOS überall: Gestenleiste, Dynamic Island, Kontrollzentrum ----------
adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS || true
adb shell appops set $PKG WRITE_SETTINGS allow
adb shell cmd notification allow_dnd $PKG || true
adb shell am start -S -W -n $PKG/.SettingsActivity
sleep 3
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.system.LauncherAccessibilityService
adb shell settings put secure accessibility_enabled 1
sleep 4
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell am start -W -n $PKG/.SettingsActivity
sleep 3
shot 15-einstellungen-ios-ueberall 1
tap_text "Gestenleiste statt 3 Tasten"
sleep 1
tap_text "Kontrollzentrum &amp; Mitteilungen"
sleep 1
tap_text "Dynamic Island"
sleep 2
shot 15b-einstellungen-an 1
adb shell input keyevent KEYCODE_HOME
shot 16-island-und-gestenleiste 4
adb shell cmd notification post -t "Lisa" island1 "Kommst du heute noch vorbei?"
shot 17-island-mitteilung 1
sleep 4
adb shell input swipe 250 15 250 1000 300
shot 18-mitteilungszentrale 1
adb shell input keyevent KEYCODE_BACK
sleep 2
adb shell input swipe 900 15 900 1000 300
shot 19-kontrollzentrum 3
adb shell input keyevent KEYCODE_BACK
sleep 2
adb shell am start -W -a android.settings.SETTINGS
shot 20-in-anderer-app 3
# Gestenleiste: hochwischen = Home
adb shell input swipe 540 2390 540 1900 200
shot 21-nach-home-geste 3

# ---------- Sperrbildschirm sofort (Overlay) + Sicherung ----------
adb shell am start -W --activity-clear-task -n $PKG/.SettingsActivity --ez preview_lock true
shot 22-sperrbildschirm-sofort 3
adb shell input swipe 540 2250 540 900 250
shot 23-nach-entsperren 3
adb shell input keyevent KEYCODE_HOME
sleep 1
adb shell am start -W -n $PKG/.SettingsActivity
sleep 2
adb shell input swipe 540 1800 540 700 300
shot 24-einstellungen-sicherung 2
# Emulator auf 3 Tasten umstellen (wie dein Xiaomi), damit unsere Strich-Leiste erscheint
adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.threebutton || true
adb shell settings put secure navigation_mode 0 || true
sleep 3
adb shell settings put secure enabled_accessibility_services none
sleep 1
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.system.LauncherAccessibilityService
sleep 4
adb shell input keyevent KEYCODE_HOME
shot 25-strich-unten 3
adb shell am start -W -a android.settings.SETTINGS
shot 26-strich-in-app 3
adb shell input swipe 540 2390 540 1900 200
shot 27-nach-home-geste 3
adb shell dumpsys package $PKG | grep -E "versionCode|versionName" > $OUT/version.txt || true

# ---------- Eigener iOS-Code (Test-Code 123456) ----------
SALT=00112233445566778899aabbccddeeff
HASH=$(python3 -c "import hashlib;print(hashlib.sha256(b'$SALT:123456').hexdigest())")
adb shell am force-stop $PKG
sleep 1
adb shell "sed -i 's#</map>#<boolean name=\"lock_enabled\" value=\"true\" /><int name=\"lock_mode\" value=\"1\" /><int name=\"lock_code_length\" value=\"6\" /><string name=\"lock_code_salt\">$SALT</string><string name=\"lock_code_hash\">$HASH</string></map>#' /data/data/$PKG/shared_prefs/clean_launcher.xml"
adb shell cat /data/data/$PKG/shared_prefs/clean_launcher.xml | grep -o 'lock_[a-z_]*" value="[^"]*"' > $OUT/prefs.txt || true
adb shell settings put secure enabled_accessibility_services none
sleep 1
adb shell settings put secure enabled_accessibility_services $PKG/$PKG.system.LauncherAccessibilityService
sleep 5
adb shell dumpsys accessibility | grep -ic cleanlauncher >> $OUT/prefs.txt || true
adb shell input keyevent KEYCODE_SLEEP
sleep 2
adb shell input keyevent KEYCODE_WAKEUP
shot 28-sperre-mit-code 3
adb shell input swipe 540 2250 540 900 250
shot 29-ios-code-eingabe 3
for d in 1 2 3 4 5 6; do tap_text "$d"; done
shot 30-nach-code 3

adb logcat -d -b crash > $OUT/crash.txt || true
adb logcat -d | grep -iE "cleanlauncher|AndroidRuntime|WindowManager.*(BadToken|permission)" | tail -200 > $OUT/log.txt || true
ls -la $OUT
