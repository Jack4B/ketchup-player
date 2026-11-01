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
shot 01_start                  # pierwsze uruchomienie: kreator (krok 1 Witaj)
key KEYCODE_DPAD_RIGHT         # z „Dalej” na „Pomiń”
key KEYCODE_DPAD_CENTER        # pominięcie kreatora -> Home
shot 01a_home
key KEYCODE_DPAD_LEFT          # fokus na szynę (aktywna pozycja: Home)
shot 01b_szyna
for _ in 1 2 3 4 5 6; do key KEYCODE_DPAD_DOWN; done   # Ustawienia (ostatnia pozycja szyny)
key KEYCODE_DPAD_CENTER
shot 01c_ustawienia            # lista kategorii po lewej, opcje „Listy i dostawcy” po prawej
key KEYCODE_DPAD_DOWN
key KEYCODE_DPAD_DOWN          # Telewizja i przewodnik (źródła EPG)
shot 02_zrodla_epg
key KEYCODE_DPAD_UP
key KEYCODE_DPAD_UP            # Listy i dostawcy
key KEYCODE_DPAD_RIGHT         # z kategorii do opcji (Dodaj dostawcę)

FAIL_PROVIDER=0
# Scenariusz z prawdziwym odtwarzaniem: dwie listy z tym samym kanałem, pierwsza zepsuta (404), druga gra (HLS z ffmpeg).
# Dodawanie dostawców przechodzi przez prawdziwy Android Keystore, odtwarzanie przez prawdziwy ExoPlayer.
SRV=$(mktemp -d)
mkdir -p "$SRV/hls/good"
ffmpeg -loglevel error -f lavfi -i testsrc=size=640x360:rate=25 -f lavfi -i sine=frequency=440 -t 30 \
  -c:v libx264 -pix_fmt yuv420p -preset ultrafast -c:a aac -f hls -hls_time 4 -hls_list_size 0 "$SRV/hls/good/index.m3u8"
printf '#EXTM3U\n#EXTINF:-1 tvg-chno="1" group-title="Test",Kanal Testowy\nhttp://127.0.0.1:8099/hls/missing/index.m3u8\n' > "$SRV/a.m3u"
printf '#EXTM3U\n#EXTINF:-1 tvg-chno="2" group-title="Test",Kanal Testowy\nhttp://127.0.0.1:8099/hls/good/index.m3u8\n' > "$SRV/b.m3u"
# Ścieżka logu bezwzględna: po cd do $SRV ścieżka względna wskazywałaby w nieistniejące miejsce i serwer by nie ruszył.
LOG="$(pwd)/$OUT/http-server.log"
(cd "$SRV" && exec python3 -m http.server 8099 --bind 0.0.0.0 > "$LOG" 2>&1) &
SERVER_PID=$!
for _ in $(seq 1 20); do
  curl -s -o /dev/null http://127.0.0.1:8099/a.m3u && break
  sleep 0.5
done
if ! curl -sf -o /dev/null http://127.0.0.1:8099/a.m3u; then
  echo "=== Serwer testowy nie odpowiada (PID $SERVER_PID) ===" >&2
  cat "$LOG" >&2 || true
  exit 1
fi
# adb reverse: localhost:8099 w emulatorze trafia na serwer hosta (niezależnie od routingu 10.0.2.2).
adb reverse tcp:8099 tcp:8099
{
  echo "host curl a.m3u: $(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8099/a.m3u)"
  echo "host curl hls: $(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8099/hls/good/index.m3u8)"
  echo "emulator curl: $(adb shell 'toybox nc -w 3 127.0.0.1 8099 </dev/null >/dev/null 2>&1 && echo ok || echo brak')"
  ls "$SRV/hls/good" | head -3
} > "$OUT/diag.txt" 2>&1 || true
shot 06_dostawcy_pusto
key KEYCODE_DPAD_CENTER      # Dodaj dostawcę
shot 07_formularz
adb shell input text "ListaA"
key KEYCODE_ENTER            # Enter w polu nazwy: przejście do adresu
adb shell input text "http://127.0.0.1:8099/a.m3u"
shot 08_formularz_wypelniony
key KEYCODE_ENTER            # Enter w ostatnim polu: zapis i pobranie
sleep 6
shot 09_po_zapisie_A
dump() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml "$OUT/$1.xml" >/dev/null; }
# Klik po tekście lub opisie z drzewa UI: niezależny od położenia fokusu (D-pad sprawdzają testy aplikacji).
tap() {
  adb shell uiautomator dump /sdcard/ui_tap.xml >/dev/null
  adb pull /sdcard/ui_tap.xml "$OUT/ui_tap.xml" >/dev/null
  local b
  for pat in "text=\"$1\"" "content-desc=\"$1\""; do
    b=$(sed 's/<node/\n<node/g' "$OUT/ui_tap.xml" | grep -F -- "$pat" | head -1 |
      sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\].*/\1 \2 \3 \4/p')
    [ -n "$b" ] && break
  done
  if [ -z "$b" ]; then echo "tap: nie znaleziono '$1'" >&2; return 1; fi
  read -r x1 y1 x2 y2 <<< "$b"
  adb shell input tap $(((x1+x2)/2)) $(((y1+y2)/2))
  sleep 1.5
}
dump ui_po_zapisie_A
if ! grep -q "Kanały: 1" "$OUT/ui_po_zapisie_A.xml"; then
  echo "=== Po zapisie listy A nie widać „Kanały: 1” (zapis lub pobranie listy się nie udało) ===" >&2
  adb logcat -d -b crash | head -80 >&2
  FAIL_PROVIDER=1
fi
tap "Dodaj dostawcę" || FAIL_PROVIDER=1
adb shell input text "ListaB"
key KEYCODE_ENTER
adb shell input text "http://127.0.0.1:8099/b.m3u"
key KEYCODE_ENTER
sleep 6
shot 10_po_zapisie_B
tap "Na żywo" || FAIL_PROVIDER=1   # szyna nawigacji (opis dostępności pozycji)
sleep 3
shot 11_na_zywo_dwa_kanaly
tap "Kanal Testowy" || FAIL_PROVIDER=1   # pierwszy kanał (Lista A, 404) -> failover na Listę B
sleep 3
shot 12_failover_przelaczenie
sleep 12
shot 13_odtwarzanie
dump ui_odtwarzanie
if grep -q "Nie udało się odtworzyć" "$OUT/ui_odtwarzanie.xml"; then
  echo "=== Panel błędu odtwarzacza mimo działającej drugiej listy ===" >&2
  FAIL_PROVIDER=1
fi
if ! grep -q "hls/good/.*\.ts" "$OUT/http-server.log"; then
  echo "=== Serwer nie dostał żądań segmentów działającego strumienia: failover nie zadziałał ===" >&2
  tail -20 "$OUT/http-server.log" >&2
  FAIL_PROVIDER=1
fi
MISSING_HITS=$(grep -c "hls/missing" "$OUT/http-server.log" || true)
if [ "$MISSING_HITS" -gt 4 ]; then
  echo "=== Martwe źródło (404) ponawiane $MISSING_HITS razy: powinno kończyć się od razu, a failover zdecydować ===" >&2
  FAIL_PROVIDER=1
fi
if ! grep -q "hls/missing" "$OUT/http-server.log"; then
  echo "=== Serwer nie dostał żądania do zepsutej listy A (kanał nie był próbowany) ===" >&2
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
