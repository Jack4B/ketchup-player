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
# Wpisywanie z kontrolą: na wolnym emulatorze `input text` potrafi zgubić lub przestawić znaki (np. „127.00.1”),
# co daje fałszywe błędy testu. Po wpisaniu sprawdzamy zawartość pola w drzewie UI, a w razie różnicy czyścimy i powtarzamy.
type_checked() {
  local want="$1" attempt send xml
  # `&` jest metaznakiem powłoki urządzenia (adb shell skleja argumenty), a uiautomator zapisuje go jako `&amp;`.
  send=$(printf '%s' "$want" | sed 's/&/\\\&/g')
  xml=$(printf '%s' "$want" | sed 's/&/\&amp;/g')
  for attempt in 1 2 3 4; do
    adb shell input text "$send"
    sleep 1
    adb shell uiautomator dump /sdcard/ui_type.xml >/dev/null
    adb pull /sdcard/ui_type.xml "$OUT/ui_type.xml" >/dev/null
    if grep -qF "text=\"$xml\"" "$OUT/ui_type.xml"; then return 0; fi
    echo "wpisywanie: niezgodne pole (próba $attempt), czyszczę i powtarzam: $want" >&2
    adb shell input keyevent KEYCODE_MOVE_END
    adb shell 'for i in $(seq 1 80); do input keyevent KEYCODE_DEL; done'
  done
  echo "wpisywanie: nie udało się wpisać '$want'" >&2
  return 0
}
shot 06_dostawcy_pusto
key KEYCODE_DPAD_CENTER      # Dodaj dostawcę
shot 07_formularz
type_checked "ListaA"
key KEYCODE_ENTER            # Enter w polu nazwy: przejście do adresu
type_checked "http://127.0.0.1:8099/a.m3u"
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
type_checked "ListaB"
key KEYCODE_ENTER
type_checked "http://127.0.0.1:8099/b.m3u"
key KEYCODE_ENTER
sleep 6
shot 10_po_zapisie_B
tap "Na żywo" || FAIL_PROVIDER=1   # szyna nawigacji (opis dostępności pozycji)
sleep 3
shot 11_na_zywo_dwa_kanaly
HITS_BEFORE=$(wc -l < "$OUT/http-server.log")
tap "Kanal Testowy" || FAIL_PROVIDER=1   # pierwszy kanał (Lista A, 404) -> failover na Listę B
sleep 4
# Dotyk w tekst bywa zjadany przez nakładkę/podgląd w układzie Na żywo: bez żądania strumienia próbujemy pilotem
# (pierwszy klawisz po dotyku wychodzi z trybu dotykowego, drugi to OK na kanale z fokusem).
if ! tail -n +$((HITS_BEFORE + 1)) "$OUT/http-server.log" | grep -q "hls/"; then
  key KEYCODE_DPAD_DOWN
  key KEYCODE_DPAD_UP
  key KEYCODE_DPAD_CENTER
  sleep 3
fi
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

# --- Regresja: po BACK z odtwarzacza nic nie może grać w tle (błąd z telefonu/boxa, rc0.9.4) ---
# Wykrywanie przez AudioPlaybackConfiguration (dumpsys audio) procesu aplikacji. Kontrola pozytywna PRZED BACK: gdy
# format dumpsys się zmieni i nic nie widać, test ma się wywrócić, a nie przechodzić „na ślepo”.
APP_UID=$(adb shell pm list packages -U "$PKG" | sed -n 's/.*uid:\([0-9]*\).*/\1/p' | head -1 | tr -d '\r')
started_players() {
  adb shell dumpsys audio > "$OUT/dumpsys_audio_$1.txt" 2>&1 || true
  grep "AudioPlaybackConfiguration" "$OUT/dumpsys_audio_$1.txt" | grep -c "u/pid:$APP_UID/.*state:started" || true
}
BEFORE=$(started_players przed_back)
if [ "${BEFORE:-0}" -lt 1 ]; then
  echo "=== Kontrola: podczas odtwarzania dumpsys audio nie pokazuje działającego odtwarzacza aplikacji (uid $APP_UID); test BACK nieważny ===" >&2
  FAIL_PROVIDER=1
fi
key KEYCODE_BACK
sleep 3
AFTER=$(started_players po_back)
shot 14_po_back_z_odtwarzacza
if [ "${AFTER:-0}" -ne 0 ]; then
  echo "=== Po BACK z odtwarzacza nadal gra odtwarzacz aplikacji ($AFTER) ===" >&2
  grep "AudioPlaybackConfiguration" "$OUT/dumpsys_audio_po_back.txt" >&2 || true
  FAIL_PROVIDER=1
fi

# --- Regresja: sortowanie Filmów dotykiem na długim katalogu bez metadanych (błąd z telefonu, rc0.9.4) ---
(for i in $(seq 60 -1 1); do
  # Matrix na początku katalogu: oceny IMDb z wbudowanego indeksu (offline, bez kluczy) muszą się pojawić na karcie.
  [ "$i" = 60 ] && printf '#EXTM3U\n#EXTINF:-1 group-title="Dramat",Matrix (1999)\nhttp://127.0.0.1:8099/movie/u/p/0.mkv\n'
  printf '#EXTINF:-1 group-title="Dramat",Film %02d\nhttp://127.0.0.1:8099/movie/u/p/%d.mkv\n' "$i" "$i"
done) > "$SRV/filmy.m3u"
tap "Ustawienia" || FAIL_PROVIDER=1
tap "Listy i dostawcy" || true
tap "Dodaj dostawcę" || FAIL_PROVIDER=1
type_checked "ListaFilmy"
key KEYCODE_ENTER
type_checked "http://127.0.0.1:8099/filmy.m3u"
key KEYCODE_ENTER
sleep 8
tap "Filmy" || FAIL_PROVIDER=1
sleep 4
# Pierwszy widoczny film (wiersz, potem kolumna) z drzewa UI.
first_film() {
  adb shell uiautomator dump /sdcard/ui_vod.xml >/dev/null
  adb pull /sdcard/ui_vod.xml "$OUT/ui_vod_$1.xml" >/dev/null
  python3 - "$OUT/ui_vod_$1.xml" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8").read()
items = []
for m in re.finditer(r"<node [^>]*>", s):
    n = m.group(0)
    t = re.search(r'(?:text|content-desc)="(?:[A-Z], )?(Film \d\d)"', n)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if t and b:
        # Wiersze w pasmach po 50 px: karta z fokusem jest powiększona i jej tytuł leży o kilka px niżej.
        items.append((int(b.group(2)) // 50, int(b.group(1)), t.group(1)))
items.sort()
print(items[0][2] if items else "")
PY
}
shot 15_filmy_domyslnie
F0=$(first_film domyslnie)
if [ "$F0" != "Film 60" ]; then
  echo "=== Filmy: domyślnie (Najnowsze) pierwszy powinien być Film 60 (kolejność dostawcy), jest '$F0' ===" >&2
  FAIL_PROVIDER=1
fi
# Wbudowany indeks filmów (asset film_index.db): Matrix (1999) dostaje ocenę IMDb 8,7 bez klucza API i bez sieci.
MATRIX_OK=0
for _ in $(seq 1 12); do
  dump ui_vod_matrix
  if grep -qE '8[,.]7' "$OUT/ui_vod_matrix.xml"; then MATRIX_OK=1; break; fi
  sleep 2
done
shot 15b_filmy_ocena_indeks
if [ "$MATRIX_OK" != 1 ]; then
  echo "=== Filmy: brak oceny IMDb 8,7 dla Matrix (1999) z wbudowanego indeksu (asset nie załadowany?) ===" >&2
  adb logcat -d | grep -iE "film_index|SQLite|FilmIndex" | head -10 >&2 || true
  FAIL_PROVIDER=1
fi
adb shell input swipe 960 800 960 200 300   # przewinięcie siatki w dół, jak palcem
sleep 1.5
tap "A–Z" || FAIL_PROVIDER=1
sleep 2
shot 16_filmy_az
F1=$(first_film az)
if [ "$F1" != "Film 01" ]; then
  echo "=== Filmy: po A–Z (dotyk, siatka przewinięta) pierwszy powinien być Film 01, jest '$F1' ===" >&2
  FAIL_PROVIDER=1
fi

# --- Regresja: nawigacja w Filmach (raport z boxa 3.11): z kafli LEWO ma przywrócić grupy (kategorie) ---
# Panel kategorii jest po dotyku widoczny; PRAWO wchodzi na kafle (panel się chowa), LEWO z pierwszej kolumny wraca.
adb shell input keyevent KEYCODE_BACK; sleep 1.5   # BACK z siatki wysuwa kategorie (albo nic nie robi, gdy już są)
dump ui_filmy_start
if ! grep -q 'Wszystkie' "$OUT/ui_filmy_start.xml"; then
  echo "=== Filmy: po BACK nie widać grup (Wszystkie) ===" >&2
  FAIL_PROVIDER=1
fi
key KEYCODE_DPAD_RIGHT
dump ui_filmy_kafle
if grep -q 'text="Wszystkie"' "$OUT/ui_filmy_kafle.xml"; then
  echo "=== Filmy: PRAWO nie weszło na kafle (grupy dalej widoczne) ===" >&2
  FAIL_PROVIDER=1
fi
key KEYCODE_DPAD_LEFT
dump ui_filmy_lewo
shot 17_filmy_lewo
if ! grep -q 'Wszystkie' "$OUT/ui_filmy_lewo.xml"; then
  echo "=== Filmy: LEWO z kafli nie przywróciło grup (błąd nawigacji z boxa) ===" >&2
  FAIL_PROVIDER=1
fi

# --- Regresja: duża lista (raport z boxa 3.11: lista ok. 100 tys. pozycji, w tym 76 tys. odcinków, nie wczytywała się) ---
# Dane syntetyczne o kształcie prawdziwej listy M3U (4000 kanałów + 96 tys. filmów i odcinków, ok. 25 MB).
python3 - "$SRV/duza.m3u" <<'PY'
import sys
with open(sys.argv[1], "w", encoding="utf-8") as f:
    f.write("#EXTM3U\n")
    for i in range(4000):
        f.write(f'#EXTINF:-1 tvg-id="K{i}.pl" tvg-name="PL| Kanal {i}" tvg-logo="http://127.0.0.1:8099/logo/{i}.png" '
                f'group-title="PL| LIVE {i % 40}",PL| Kanal {i}\nhttp://127.0.0.1:8099/live/u/p/{i}\n')
    for i in range(96000):
        kind = "series" if i % 4 else "movie"
        f.write(f'#EXTINF:-1 tvg-id="" tvg-name="Pozycja {i} S01E01" tvg-logo="http://127.0.0.1:8099/img/{i}.jpg" '
                f'group-title="|PL| SERIALE {i % 100}",Pozycja {i} S01E01\nhttp://127.0.0.1:8099/{kind}/u/p/{i}.mkv\n')
PY
tap "Ustawienia" || FAIL_PROVIDER=1
tap "Listy i dostawcy" || true
tap "Dodaj dostawcę" || FAIL_PROVIDER=1
type_checked "ListaDuza"
key KEYCODE_ENTER
type_checked "http://127.0.0.1:8099/duza.m3u"
key KEYCODE_ENTER
BIG_OK=0
for _ in $(seq 1 60); do
  sleep 6
  adb shell uiautomator dump /sdcard/ui_big.xml >/dev/null
  adb pull /sdcard/ui_big.xml "$OUT/ui_big.xml" >/dev/null
  if grep -q "Kanały: 4000" "$OUT/ui_big.xml"; then BIG_OK=1; break; fi
done
shot 21_duza_lista
if [ "$BIG_OK" != 1 ]; then
  echo "=== Duża lista (100 tys. pozycji) nie wczytała się w 6 minut (brak „Kanały: 4000”) ===" >&2
  adb logcat -d | grep -E "OutOfMemory|FATAL EXCEPTION|ANR in" | head -10 >&2 || true
  FAIL_PROVIDER=1
fi

# --- Regresja: panel za Cloudflare (raport z telefonu 9.11: HTTP 520 na wszystko, w IPTV Extreme lista działa) ---
# Dane syntetyczne. Serwer odpowiada 520 na get.php oraz na każde zapytanie, którego User-Agent nie jest okhttp/4.10.0 albo
# które ma nagłówek Accept (IPTV Extreme wysyła tylko User-Agent, Host, Connection i Accept-Encoding). Lista dodana jako
# get.php z loginem i hasłem ma się wczytać przez player_api.php (konto Xtream), bez żadnego żądania get.php.
PANEL_LOG="$(pwd)/$OUT/panel-server.log"
python3 - "$SRV/panel.py" <<'PY'
import sys
open(sys.argv[1], "w", encoding="utf-8").write(r"""
import http.server, json, re, sys
LOG = open(sys.argv[1], "a", buffering=1)
CATS = [{"category_id": "1", "category_name": "PL | Info"}]
LIVE = [{"num": i, "name": "Panel Kanal %d" % i, "stream_id": 100 + i, "category_id": "1",
         "stream_icon": "", "epg_channel_id": "p%d.pl" % i} for i in range(1, 4)]
AUTH = {"user_info": {"auth": 1, "status": "Active", "exp_date": "1893456000"}, "server_info": {}}
class H(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, *a): pass
    def do_GET(self):
        ua = self.headers.get("User-Agent", "")
        names = ",".join(self.headers.keys())
        blocked = self.path.startswith("/get.php") or ua != "okhttp/4.10.0" or "Accept" in self.headers
        if blocked:
            code, body = 520, b"cf"
        elif "action=get_live_categories" in self.path:
            code, body = 200, json.dumps(CATS).encode()
        elif "action=get_live_streams" in self.path:
            code, body = 200, json.dumps(LIVE).encode()
        elif "action=" in self.path:
            code, body = 200, b"[]"
        else:
            code, body = 200, json.dumps(AUTH).encode()
        shown = re.sub(r"password=[^&]*", "password=***", self.path)
        LOG.write("GET %s %d ua=%s headers=%s\n" % (shown, code, ua, names))
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
http.server.ThreadingHTTPServer(("0.0.0.0", 8100), H).serve_forever()
""")
PY
# Ścieżki bezwzględne: po cd do $SRV względne wskazywałyby w nieistniejące miejsce (jak przy $LOG powyżej).
PANEL_STDOUT="$(pwd)/$OUT/panel-stdout.log"
(cd "$SRV" && exec python3 panel.py "$PANEL_LOG" > "$PANEL_STDOUT" 2>&1) &
PANEL_PID=$!
for _ in $(seq 1 20); do
  curl -s -o /dev/null http://127.0.0.1:8100/get.php && break
  sleep 0.5
done
adb reverse tcp:8100 tcp:8100
tap "Dodaj dostawcę" || FAIL_PROVIDER=1
type_checked "ListaPanel"
key KEYCODE_ENTER
type_checked "http://127.0.0.1:8100/get.php?username=u&password=p&type=m3u_plus"
key KEYCODE_ENTER
PANEL_OK=0
for _ in $(seq 1 20); do
  sleep 3
  if grep -q "action=get_live_streams 200" "$PANEL_LOG" 2>/dev/null; then PANEL_OK=1; break; fi
done
shot 22_panel_cloudflare
if [ "$PANEL_OK" != 1 ]; then
  echo "=== Panel za Cloudflare: aplikacja nie pobrała get_live_streams z poprawnymi nagłówkami (log serwera): ===" >&2
  cat "$PANEL_LOG" >&2 || true
  FAIL_PROVIDER=1
fi
if grep -q "GET /get.php" "$PANEL_LOG" 2>/dev/null; then
  echo "=== Panel za Cloudflare: aplikacja pobierała get.php zamiast player_api.php ===" >&2
  cat "$PANEL_LOG" >&2 || true
  FAIL_PROVIDER=1
fi
if grep -v " 200 ua=okhttp/4.10.0 headers=User-Agent,Host,Connection,Accept-Encoding" "$PANEL_LOG" 2>/dev/null | grep -q "player_api"; then
  echo "=== Panel za Cloudflare: zapytanie z nagłówkami innymi niż IPTV Extreme (User-Agent, Host, Connection, Accept-Encoding) ===" >&2
  cat "$PANEL_LOG" >&2 || true
  FAIL_PROVIDER=1
fi
kill "$PANEL_PID" 2>/dev/null || true

# --- Regresja: Ustawienia > Wygląd (raport z boxa 2.11): wszystkie motywy w panelu i LEWO wraca do kategorii ---
# focused_text: teksty zaznaczonego elementu z drzewa UI. Compose daje tekst w węzłach potomnych, więc zbieramy
# teksty wszystkich węzłów mieszczących się w ramce elementu z focused="true".
focused_text() {
  adb shell uiautomator dump /sdcard/ui_f.xml >/dev/null
  adb pull /sdcard/ui_f.xml "$OUT/ui_focus_$1.xml" >/dev/null
  python3 - "$OUT/ui_focus_$1.xml" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8").read()
nodes = []
for m in re.finditer(r"<node [^>]*>", s):
    n = m.group(0)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    t = re.search(r' text="([^"]*)"', n)
    d = re.search(r'content-desc="([^"]*)"', n)
    nodes.append((tuple(map(int, b.groups())) if b else None, (t.group(1) if t else "") or (d.group(1) if d else ""), 'focused="true"' in n))
out = []
for box, label, focused in nodes:
    if not focused or box is None:
        continue
    x1, y1, x2, y2 = box
    parts = [l for bb, l, _ in nodes if l and bb and bb[0] >= x1 and bb[2] <= x2 and bb[1] >= y1 and bb[3] <= y2]
    out.append(" ".join(parts) or label)
print("|".join(out))
PY
}
tap "Ustawienia" || FAIL_PROVIDER=1
sleep 2
tap "Wygląd" || FAIL_PROVIDER=1
sleep 2
shot 17_ustawienia_wyglad
adb shell uiautomator dump /sdcard/ui_w.xml >/dev/null
adb pull /sdcard/ui_w.xml "$OUT/ui_wyglad.xml" >/dev/null
for theme in Morski Grafit Czerń Własny; do
  right=$(python3 - "$OUT/ui_wyglad.xml" "$theme" <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8").read()
best = ""
for m in re.finditer(r"<node [^>]*>", s):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if t and b and t.group(1).startswith(sys.argv[2]) and "…" not in t.group(1):
        best = b.group(3)
print(best)
PY
)
  if [ -z "$right" ] || [ "$right" -gt 1900 ]; then
    echo "=== Wygląd: karta motywu '$theme' nie mieści się w panelu (prawa krawędź: '${right:-brak}') ===" >&2
    FAIL_PROVIDER=1
  fi
done
# Nawigacja pilotem: do kategorii „Wygląd” samymi klawiszami, potem PRAWO (opcje) i LEWO (z powrotem na kategorię).
# Panel Ustawień otwieramy od nowa (dotyk), żeby fokus startował na pierwszej kategorii.
tap "Ustawienia" || true
sleep 2
key KEYCODE_DPAD_DOWN   # po dotyku pierwszy klawisz może tylko przełączyć tryb wejścia
FOCUS_CAT=""
for _ in $(seq 1 12); do
  FOCUS_CAT=$(focused_text kategoria)
  case "$FOCUS_CAT" in *Wygląd*) break ;; esac
  key KEYCODE_DPAD_DOWN
done
shot 18_fokus_kategoria
key KEYCODE_DPAD_RIGHT
FOCUS_IN=$(focused_text opcje)
shot 19_fokus_opcje
key KEYCODE_DPAD_LEFT
FOCUS_OUT=$(focused_text po_lewo)
shot 20_fokus_po_lewo
echo "fokus: kategoria='$FOCUS_CAT' opcje='$FOCUS_IN' po LEWO='$FOCUS_OUT'" | tee -a "$OUT/diag.txt"
case "$FOCUS_CAT" in
  *Wygląd*) ;;
  *) echo "=== Ustawienia: nie udało się ustawić fokusu na kategorii 'Wygląd' klawiszami (fokus: '$FOCUS_CAT') ===" >&2
     FAIL_PROVIDER=1 ;;
esac
# PRAWO z kategorii ma wejść w opcje panelu: fokus niepusty i nie na liście kategorii (która opcja, zależy od układu).
CATEGORY_LABELS="Listy i dostawcy|Grupy i kategorie|Odtwarzanie|Oceny i opisy|Telewizja|Sieć i VPN|O aplikacji"
if [ -z "$FOCUS_IN" ] || echo "$FOCUS_IN" | grep -qE "$CATEGORY_LABELS"; then
  echo "=== Wygląd: PRAWO z kategorii nie weszło w opcje (fokus: '$FOCUS_IN') ===" >&2
  # Diagnostyka #2: czy drugi klawisz PRAWO i klawisz DÓŁ cokolwiek zaznaczają (pierwszy klawisz mógł zniknąć w trybie wejścia).
  key KEYCODE_DPAD_RIGHT
  D_R2=$(focused_text po_prawo2)
  key KEYCODE_DPAD_DOWN
  D_DN=$(focused_text po_dol)
  echo "diag: po 2. PRAWO='$D_R2' po DOLE='$D_DN'" | tee -a "$OUT/diag.txt" >&2
  # Diagnostyka: elementy fokusowalne i zaznaczone z drzewa UI po PRAWO (klasa, ramka, tekst).
  python3 - "$OUT/ui_focus_opcje.xml" >&2 <<'PY'
import re, sys
s = open(sys.argv[1], encoding="utf-8").read()
for m in re.finditer(r"<node [^>]*>", s):
    n = m.group(0)
    if 'focusable="true"' in n or 'focused="true"' in n:
        g = lambda k: (re.search(k + r'="([^"]*)"', n) or [None, ""])[1]
        print("  FOCUS", "F" if 'focused="true"' in n else "-", g("class").split(".")[-1], g("bounds"), g("text") or g("content-desc"))
PY
  FAIL_PROVIDER=1
fi
if ! echo "$FOCUS_OUT" | grep -q "Wygląd"; then
  echo "=== Wygląd: LEWO z opcji nie wróciło na kategorię 'Wygląd' (fokus: '$FOCUS_OUT') ===" >&2
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
