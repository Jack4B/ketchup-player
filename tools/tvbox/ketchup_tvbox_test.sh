#!/data/data/com.termux/files/usr/bin/bash
# =============================================================================
#  ketchup_tvbox_test.sh  ::  automatyczne testy Ketchup Player na TV boxie
#  (Leelbox L1 / Android TV 12) przez ADB z Termuxa. Odpowiada punktom z
#  docs/TEST-CHECKLIST.md. Zrzuty, zrzuty UI, logi i raport trafiają na telefon do
#  /storage/emulated/0/Download/KetchupPlayerTest/<data_godzina>/
#
#  Użycie:   bash ketchup_tvbox_test.sh [ścieżka/do/app-debug.apk]
#  Konfiguracja prywatna (NIE do repo): ~/.ketchup-test.env, np.
#     DEVICE_IP="192.168.1.170"; PORT="5555"
#     M3U_URL="http://..."            # lista do testów (domyślnie publiczna iptv-org PL)
#     XTREAM_SERVER="http://host:8080"; XTREAM_USER="..."; XTREAM_PASS="..."   # opcjonalnie
#  Wymaga: pkg install android-tools coreutils
# =============================================================================
set -o pipefail
PKG=""   # wykrywany po instalacji: wersja debug to pl.ketchupplayer.tv.debug, release pl.ketchupplayer.tv
ACT=""
ENV_FILE="$HOME/.ketchup-test.env"
# shellcheck disable=SC1090
[ -f "$ENV_FILE" ] && source "$ENV_FILE"
DEVICE_IP="${DEVICE_IP:-192.168.1.170}"; PORT="${PORT:-5555}"; TARGET="${DEVICE_IP}:${PORT}"
M3U_URL="${M3U_URL:-https://iptv-org.github.io/iptv/countries/pl.m3u}"
# Interaktywnie: wklej własny adres listy M3U (Enter = bierze z ~/.ketchup-test.env albo publiczną iptv-org).
# Adres trafia tylko do tej sesji; do pliku/raportu nie jest zapisywany (zawiera login i hasło).
if [ -t 0 ] && [ -z "${KETCHUP_NO_ASK:-}" ]; then
  printf 'Wklej adres listy M3U i naciśnij Enter (puste = domyślna): '
  read -r ASK_URL
  [ -n "$ASK_URL" ] && M3U_URL="$ASK_URL"
fi
APK="${1:-}"
BASE="/storage/emulated/0/Download/KetchupPlayerTest"
[ -d /storage/emulated/0/Download ] || BASE="$HOME/KetchupPlayerTest"
OUT="$BASE/$(date +%Y%m%d_%H%M%S)"
mkdir -p "$OUT/shots" "$OUT/ui" "$OUT/logs" || { echo "Brak dostępu do $BASE (termux-setup-storage)"; exit 1; }
REPORT="$OUT/report.md"; PASS=0; FAIL=0; WARN=0
LOG="$OUT/logs/test.log"

log() { printf '[%s] %s\n' "$(date +%T)" "$*" | tee -a "$LOG"; }
adb_s() { timeout "${ADB_TIMEOUT:-25}" adb -s "$TARGET" "$@"; }
sh_() { adb_s shell "$@" 2>/dev/null | tr -d '\r'; }
key() { adb_s shell input keyevent "$@" >/dev/null 2>&1; sleep "${KEY_DELAY:-0.7}"; }
sleepf() { sleep "$1"; }

# ---------- wynik ----------
record() { # id nazwa PASS|FAIL|WARN [uwaga]
  local id="$1" name="$2" res="$3" note="${4:-}"
  case "$res" in PASS) PASS=$((PASS+1));; FAIL) FAIL=$((FAIL+1));; *) WARN=$((WARN+1));; esac
  printf '| %s | %s | %s | %s |\n' "$id" "$name" "$res" "$note" >> "$REPORT"
  log "[$res] $id $name ${note:+— $note}"
}

# ---------- narzędzia UI ----------
shot() { # nazwa
  local f="$OUT/shots/$1.png"
  adb -s "$TARGET" exec-out screencap -p > "$f" 2>/dev/null
  [ "$(od -An -tx1 -N4 "$f" 2>/dev/null | tr -d ' \n')" = "89504e47" ] || { rm -f "$f"; log "zrzut $1 nieudany"; return 1; }
  stat -c %s "$f"
}
ui_dump() {
  adb_s shell uiautomator dump /sdcard/ketchup_ui.xml >/dev/null 2>&1
  adb -s "$TARGET" exec-out cat /sdcard/ketchup_ui.xml 2>/dev/null | tr -d '\r' > "$OUT/ui/last.xml"
  [ -s "$OUT/ui/last.xml" ]
}
save_ui() { cp "$OUT/ui/last.xml" "$OUT/ui/$1.xml" 2>/dev/null; }
has() { grep -qF -- "$1" "$OUT/ui/last.xml" 2>/dev/null; }          # fragment atrybutu, np. text="Filmy"
has_text() { has "text=\"$1" || has "content-desc=\"$1"; }
wait_text() { # fragment [timeout_s]
  local t="${2:-20}" i=0
  while [ "$i" -lt "$t" ]; do ui_dump && has_text "$1" && return 0; sleep 1; i=$((i+1)); done
  return 1
}
bounds() { # fragment atrybutu -> "x1 y1 x2 y2"
  sed 's/<node/\n<node/g' "$OUT/ui/last.xml" | grep -F -- "$1" | head -1 |
    sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p'
}
tap() { # etykieta (dokładna, potem content-desc, potem prefiks)
  ui_dump || return 1
  local b x1 y1 x2 y2
  for pat in "text=\"$1\"" "content-desc=\"$1\"" "text=\"$1" ; do
    b=$(bounds "$pat"); [ -n "$b" ] && break
  done
  [ -z "$b" ] && { log "tap: nie znaleziono '$1'"; return 1; }
  read -r x1 y1 x2 y2 <<< "$b"
  adb_s shell input tap $(((x1+x2)/2)) $(((y1+y2)/2)) >/dev/null 2>&1
  sleep 1.2
}
alive() { [ -n "$(sh_ pidof "$PKG")" ]; }
crashes() { sh_ "logcat -d -b crash" | grep -E "FATAL EXCEPTION|$PKG" | head -5; }
playing() { sh_ "dumpsys audio" | grep -E "AudioPlaybackConfiguration" | grep "pack:$PKG" | grep -q "state:started"; }
goto() { # etykieta sekcji z szyny (content-desc); najpierw LEFT, by rozwinąć szynę
  key 4 >/dev/null 2>&1; key 21; key 21; tap "$1" || return 1
}

# ---------- start ----------
echo "# Raport testów Ketchup Player na TV boxie" > "$REPORT"
{ echo "- data: $(date '+%F %T')"; echo "- target: $TARGET"; echo; echo "| ID | Test | Wynik | Uwagi |"; echo "|---|---|---|---|"; } >> "$REPORT"
log "OUT=$OUT"

for t in adb timeout od stat sed grep; do command -v $t >/dev/null || { echo "Brak: $t (pkg install android-tools coreutils)"; exit 1; }; done
timeout 15 adb start-server >/dev/null 2>&1
[ "$(timeout 5 adb -s "$TARGET" get-state 2>/dev/null)" = "device" ] || timeout 10 adb connect "$TARGET" >/dev/null 2>&1
[ "$(timeout 5 adb -s "$TARGET" get-state 2>/dev/null)" = "device" ] || { echo "Brak połączenia z $TARGET"; exit 1; }
{ echo "- model: $(sh_ getprop ro.product.model), Android $(sh_ getprop ro.build.version.release)"; } >> "$REPORT"
sh_ "wm size" > "$OUT/logs/wm_size.txt"; sh_ "logcat -c" >/dev/null

# ---------- 0. instalacja ----------
# Box może mieć kilku użytkowników (np. 0 i 10): instalujemy i uruchamiamy u aktywnego.
USR=$(sh_ "am get-current-user" | tr -dc '0-9'); USR=${USR:-0}
log "użytkownik: $USR"
if [ -n "$APK" ] && [ -f "$APK" ]; then
  out=$(adb -s "$TARGET" install -r -d --user "$USR" "$APK" 2>&1 | tr -d '\r'); echo "$out" | grep -q Success \
    && record "—" "Instalacja APK" PASS "$(basename "$APK")" || record "—" "Instalacja APK" FAIL "$out"
fi
sleep 3
pk=$(ADB_TIMEOUT=90 sh_ "pm list packages --user $USR")
echo "$pk" > "$OUT/logs/packages.txt"
for p in pl.ketchupplayer.tv.debug pl.ketchupplayer.tv; do
  if echo "$pk" | grep -q "package:$p\$"; then PKG="$p"; break; fi
done
if [ -z "$PKG" ]; then
  [ -z "$(echo "$pk" | tr -d '[:space:]')" ] && why="pusta odpowiedź pm (adb/uprawnienia/timeout)" || why="pakietu nie ma na liście ($(echo "$pk" | grep -ci ketchup) trafień 'ketchup')"
  record "—" "Aplikacja zainstalowana" FAIL "$why"; cat "$REPORT"; exit 1
fi
ACT="$PKG/pl.ketchupplayer.tv.MainActivity"
log "pakiet: $PKG"
sh_ "pm clear --user $USR $PKG" >/dev/null
sh_ "settings put global window_animation_scale 1.0" >/dev/null

# ---------- A1 start + kreator ----------
sh_ "am start --user $USR -n $ACT" >/dev/null; sleep 6
s=$(shot 01_start); ui_dump; save_ui 01_start
if wait_text "Witaj w Ketchup Player" 25; then record A1 "Pierwsze uruchomienie: kreator" PASS "zrzut ${s:-?} B"; else record A1 "Pierwsze uruchomienie: kreator" FAIL "brak ekranu powitalnego"; fi
alive || record A3 "Aplikacja żyje po starcie" FAIL "brak procesu"

key 23; sleep 1.5                                  # OK na „Dalej”
wait_text "Dodaj pierwszą listę" 10 && shot 02_krok2 >/dev/null
adb_s shell input text "Test%sTV" ; key 66          # nazwa, Enter = następne pole
adb_s shell input text "$(printf '%s' "$M3U_URL" | sed 's/&/\\&/g; s/?/\\?/g')"; key 66   # adres, Enter = zapis
shot 03_wysylanie >/dev/null
# Czeka do 15 min na wynik importu i co 20 s zapisuje, co widać na ekranie (postęp, błąd, formularz).
MAX_WAIT="${IMPORT_WAIT_S:-900}"; t0=$(date +%s); okimp=0; lastline=""
while [ $(( $(date +%s) - t0 )) -lt "$MAX_WAIT" ]; do
  ui_dump
  if has_text "Lista została dodana"; then okimp=1; break; fi
  line=$(sed 's/<node/\n<node/g' "$OUT/ui/last.xml" | grep -oE 'text="(Pobieranie|Zapisywanie|Lista zapisana|Podaj|Nie )[^"]*"' | head -2 | tr '\n' ' ')
  [ -z "$line" ] && line="(brak komunikatu na ekranie)"
  log "po $(( $(date +%s) - t0 )) s: $line"
  case "$line" in *"Lista zapisana"*) break;; esac    # błąd importu: formularz wrócił z komunikatem
  sleep 20
done
if [ "$okimp" = 1 ]; then
  ui_dump; ch=$(grep -o 'Kanały: [0-9]*' "$OUT/ui/last.xml" | head -1)
  record B1 "Dodanie listy M3U w kreatorze (klawiatura ekranowa/Enter)" PASS "$ch, $(( $(date +%s) - t0 )) s"; shot 04_gotowe >/dev/null
else record B1 "Dodanie listy M3U w kreatorze" FAIL "po $(( $(date +%s) - t0 )) s: $line"; shot 04_blad >/dev/null; save_ui 04_blad; fi
key 23; sleep 2                                    # „Zaczynamy”
wait_text "Home" 15 && record A1b "Home z szyną po kreatorze" PASS || record A1b "Home z szyną po kreatorze" FAIL
shot 05_home >/dev/null; save_ui 05_home

# ---------- sekcje ----------
check_section() { # id etykieta-szyny oczekiwany-tekst nazwa-zrzutu
  goto "$1" || { record "$2" "Nawigacja: $1" FAIL "brak w szynie"; return; }
  sleep 2
  if wait_text "$3" 20; then record "$2" "Sekcja $1" PASS; else record "$2" "Sekcja $1" FAIL "brak '$3'"; fi
  shot "$4" >/dev/null; save_ui "$4"
  alive || record "$2" "Crash w sekcji $1" FAIL "$(crashes)"
}
check_section "Na żywo" C1 "Grupy" 10_live
check_section "Przewodnik" F1 "Siatka" 11_guide
check_section "Filmy" J1 "Szukaj w: Filmy" 12_filmy
check_section "Seriale" J1s "Szukaj w: Seriale" 13_seriale
check_section "Moja lista" J6 "Moja lista" 14_mojalista
check_section "Szukaj" J7 "Szukaj filmów i seriali" 15_szukaj
check_section "Ustawienia" K1 "Listy i dostawcy" 16_ustawienia

# ---------- ustawienia: wygląd, odtwarzanie ----------
tap "Wygląd" && { sleep 1.5; shot 17_wyglad >/dev/null
  for th in "Czerń" "Bordo" "Granat"; do tap "$th" && { sleep 1; shot "18_motyw_$th" >/dev/null; }; done
  record G1 "Zmiana motywu (zrzuty 18_motyw_*)" PASS "oceń wizualnie"; }
tap "Odtwarzanie" && { sleep 1.5; shot 19_odtwarzanie >/dev/null; record E4 "Ustawienia odtwarzania widoczne" PASS; }

# ---------- Na żywo: odtwarzanie ----------
goto "Na żywo"; sleep 2
key 22; key 23; sleep 12                                # PRAWO do kanałów, OK = odtwórz pierwszy
sa=$(shot 20_odtwarzacz_a); sleep 3; sb=$(shot 20_odtwarzacz_b)
ui_dump; save_ui 20_odtwarzacz
if has_text "Nie udało się odtworzyć"; then record D3 "Odtwarzanie kanału na żywo" FAIL "panel błędu"
elif playing; then record D1 "Obraz/dźwięk kanału na żywo" PASS "audio: started; zrzuty ${sa:-?}/${sb:-?} B"
else record D1 "Obraz/dźwięk kanału na żywo" WARN "brak aktywnego odtwarzacza audio (sprawdź zrzuty)"; fi
[ "${sa:-0}" -lt 40000 ] 2>/dev/null && record D2 "Klatka wideo nie jest czarna" WARN "mały zrzut (${sa} B)"
cmp -s "$OUT/shots/20_odtwarzacz_a.png" "$OUT/shots/20_odtwarzacz_b.png" && record D2b "Obraz się zmienia" WARN "dwa zrzuty identyczne" || record D2b "Obraz się zmienia" PASS
key 4; sleep 2; ui_dump; has_text "Grupy" && record D4 "BACK z odtwarzacza wraca do listy" PASS || record D4 "BACK z odtwarzacza wraca do listy" FAIL

# ---------- ulubione (długie OK) ----------
adb_s shell input keyevent --longpress 23 >/dev/null 2>&1; sleep 1.5; ui_dump
has_text "Ulubione" && record C4 "Długie OK: ulubione" PASS || record C4 "Długie OK: ulubione" WARN "sprawdź zrzut 21_ulubione"
shot 21_ulubione >/dev/null

# ---------- Filmy: szczegóły i odtwarzanie (jeśli lista ma VOD) ----------
goto "Filmy"; sleep 2; ui_dump
if has_text "Brak filmów"; then record J3 "Filmy z listy" WARN "lista bez filmów (użyj M3U/Xtream z VOD)"
else
  key 23; sleep 3; ui_dump; shot 22_szczegoly >/dev/null; save_ui 22_szczegoly
  if has_text "FILM" || has_text "SERIAL"; then record J3 "Szczegóły filmu" PASS; else record J3 "Szczegóły filmu" FAIL; fi
  tap "▶ Oglądaj" || tap "▶ Wznów"; sleep 15; shot 23_vod >/dev/null
  playing && record J5 "Odtwarzanie filmu" PASS || record J5 "Odtwarzanie filmu" WARN "brak aktywnego audio"
  key 22; sleep 1; key 22; sleep 1; shot 24_vod_przewijanie >/dev/null; key 4; sleep 2
fi

# ---------- stabilność ----------
mem0=$(sh_ "dumpsys meminfo $PKG" | grep -m1 "TOTAL PSS" | awk '{print $3}')
log "stres: 150 losowych klawiszy"
for i in $(seq 1 150); do
  k=$(printf '19 20 21 22 23 4' | tr ' ' '\n' | shuf -n1)
  adb_s shell input keyevent "$k" >/dev/null 2>&1; sleep 0.25
done
sleep 2; shot 30_po_stresie >/dev/null
mem1=$(sh_ "dumpsys meminfo $PKG" | grep -m1 "TOTAL PSS" | awk '{print $3}')
alive && record I2 "Stres 150 klawiszy bez crasha" PASS "PSS ${mem0:-?} → ${mem1:-?} kB" || record I2 "Stres 150 klawiszy bez crasha" FAIL "$(crashes)"

# ---------- logi ----------
sh_ "logcat -d" > "$OUT/logs/logcat_full.txt"
sh_ "logcat -d -b crash" > "$OUT/logs/logcat_crash.txt"
sh_ "dumpsys meminfo $PKG" > "$OUT/logs/meminfo.txt"
sh_ "dumpsys activity processes" | grep -A3 "$PKG" > "$OUT/logs/process.txt" 2>/dev/null
grep -qE "FATAL EXCEPTION|ANR in $PKG" "$OUT/logs/logcat_full.txt" \
  && record "—" "Crash/ANR w logach" FAIL "patrz logs/logcat_crash.txt" || record "—" "Crash/ANR w logach" PASS
grep -E "ketchup|Timber|E/AndroidRuntime" "$OUT/logs/logcat_full.txt" | tail -200 > "$OUT/logs/app_tail.txt"

{ echo; echo "**Podsumowanie:** PASS=$PASS WARN=$WARN FAIL=$FAIL"; echo; echo "Zrzuty: shots/, zrzuty UI: ui/, logi: logs/"; } >> "$REPORT"
cat "$REPORT"
echo; echo "Wyniki: $OUT"
[ "$FAIL" -eq 0 ]
