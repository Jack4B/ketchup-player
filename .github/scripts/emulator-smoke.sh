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
key KEYCODE_DPAD_CENTER      # OK na „Źródła EPG”
shot 02_zrodla_epg
key KEYCODE_DPAD_DOWN
shot 03_teleman_fokus
key KEYCODE_DPAD_CENTER      # przełącz Telemana
shot 04_teleman_przelaczony
key KEYCODE_BACK
shot 05_po_back
if adb logcat -d | grep -E "FATAL EXCEPTION|ANR in $PKG"; then
  echo "Crash lub ANR wykryty" >&2
  exit 1
fi
