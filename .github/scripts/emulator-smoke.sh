#!/usr/bin/env bash
# Smoke test na emulatorze Android TV: start, D-pad + OK, zrzuty. Uruchamiane przez android-emulator-runner.
set -euo pipefail
OUT=docs/qa/emulator
PKG=pl.ketchupplayer.tv.debug
mkdir -p "$OUT"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -n "$PKG/pl.ketchupplayer.tv.MainActivity"
sleep 6
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
key() { adb shell input keyevent "$1"; sleep 1.5; }
shot 01_start
key KEYCODE_DPAD_CENTER      # OK na „Na żywo” (pusty katalog)
shot 01b_na_zywo_pusto
key KEYCODE_BACK
key KEYCODE_DPAD_RIGHT       # Źródła EPG (prawa kolumna, pierwszy wiersz)
key KEYCODE_DPAD_CENTER
shot 02_zrodla_epg
key KEYCODE_DPAD_DOWN
shot 03_teleman_fokus
key KEYCODE_DPAD_CENTER      # przełącz Telemana
shot 04_teleman_przelaczony
key KEYCODE_BACK
shot 05_po_back

FAIL_PROVIDER=0
# Dodanie dostawcy M3U z lokalnego serwera: przechodzi przez prawdziwy Android Keystore (szyfrowanie adresu).
SRV=$(mktemp -d)
printf '#EXTM3U\n#EXTINF:-1 tvg-id="a.pl" group-title="Test",Kanal Testowy\nhttp://10.0.2.2:8099/s.m3u8\n' > "$SRV/t.m3u"
(cd "$SRV" && python3 -m http.server 8099 >/dev/null 2>&1 &)
sleep 2
key KEYCODE_DPAD_DOWN        # Przewodnik
key KEYCODE_DPAD_DOWN        # Dostawcy
key KEYCODE_DPAD_CENTER
shot 06_dostawcy_pusto
key KEYCODE_DPAD_CENTER      # Dodaj dostawcę
shot 07_formularz
adb shell input text "Test"
key KEYCODE_DPAD_DOWN
adb shell input text "http://10.0.2.2:8099/t.m3u"
shot 08_formularz_wypelniony
key KEYCODE_DPAD_DOWN
key KEYCODE_DPAD_CENTER      # Zapisz i pobierz
sleep 8
shot 09_po_zapisie
adb shell uiautomator dump /sdcard/ui.xml >/dev/null
adb pull /sdcard/ui.xml "$OUT/ui_po_zapisie.xml" >/dev/null
if ! grep -q "10.0.2.2" "$OUT/ui_po_zapisie.xml"; then
  echo "=== Po zapisie dostawcy nie widać hosta 10.0.2.2 na liście (zapis się nie udał) ===" >&2
  adb logcat -d -b crash | head -80 >&2
  FAIL_PROVIDER=1
fi
if adb logcat -d | grep -qE "FATAL EXCEPTION|ANR in $PKG"; then
  echo "=== Crash lub ANR wykryty. Bufor crash: ===" >&2
  adb logcat -d -b crash | head -120 >&2
  echo "=== AndroidRuntime / ActivityManager (ostatnie wpisy): ===" >&2
  adb logcat -d -s AndroidRuntime:E ActivityManager:E | tail -80 >&2
  exit 1
fi
if [ "$FAIL_PROVIDER" = 1 ]; then
  exit 1
fi
