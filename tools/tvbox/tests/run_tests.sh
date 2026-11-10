#!/usr/bin/env bash
# shellcheck disable=SC2319,SC1090,SC2034,SC2088
# Testy offline skryptu ketchup_tvbox_test.sh na atrapie adb (bez urządzenia, bez sieci, tylko fikcyjne dane).
# Uruchomienie: bash tools/tvbox/tests/run_tests.sh   (obecne CI tych testów nie uruchamia)
# To NIE jest test na boxie: sprawdza logikę skryptu, statusy, prywatność i odporność na błędy.
cd "$(dirname "$0")/.." || exit 1
TV="$(pwd)"
SCRIPT="$TV/ketchup_tvbox_test.sh"
FAILS=0; TOTAL=0
ok() { TOTAL=$((TOTAL + 1)); if [ "$2" = 0 ]; then echo "ok    $1"; else echo "BŁĄD  $1"; FAILS=$((FAILS + 1)); fi; }
eq() { TOTAL=$((TOTAL + 1)); if [ "$2" = "$3" ]; then echo "ok    $1"; else echo "BŁĄD  $1: oczekiwano '$3', jest '$2'"; FAILS=$((FAILS + 1)); fi; }
has() { TOTAL=$((TOTAL + 1)); if printf '%s' "$2" | grep -qF -- "$3"; then echo "ok    $1"; else echo "BŁĄD  $1: brak '$3'"; FAILS=$((FAILS + 1)); fi; }
hasnt() { TOTAL=$((TOTAL + 1)); if printf '%s' "$2" | grep -qF -- "$3"; then echo "BŁĄD  $1: znaleziono '$3'"; FAILS=$((FAILS + 1)); else echo "ok    $1"; fi; }

# Fikcyjne sekrety (nie istnieją nigdzie poza testami).
SECRET_URL='http://srv.example.invalid:8080/get.php?username=Jan_Kowalski7&password=S3kr3t%21x9&type=m3u_plus'
SECRET_PATH_URL='http://h.example.invalid:80/zbyszek12/tajne456/77.ts'

new_env() { # tworzy izolowane środowisko testu
  T=$(mktemp -d); mkdir -p "$T/bin" "$T/home" "$T/tmp" "$T/dl" "$T/fake" "$T/spacje w nazwie"
  ln -s "$TV/tests/fake_aapt" "$T/bin/aapt"
  APK="$T/spacje w nazwie/KetchupPlayer 1.0.0-rc1.apk"
  (cd "$T" && echo "x" > a.txt && zip -q "$APK" a.txt)
  printf 'DEVICE_IP="192.168.1.170"\nPORT="5555"\n' > "$T/home/.ketchup-test.env"
}
run() { # run [env...] -- argumenty skryptu ; stdout/stderr w $T/stdout i $T/stderr
  local envs=()
  while [ "$1" != "--" ]; do envs+=("$1"); shift; done; shift
  env -i PATH="$T/bin:$PATH" HOME="$T/home" TMPDIR="$T/tmp" ADB_BIN="$TV/tests/fake_adb" FAKE_DIR="$T/fake" \
    KETCHUP_OUT_BASE="$T/dl/KetchupPlayerTest" NAP_SCALE=0 KEY_DELAY=0 ADB_TIMEOUT=10 START_WAIT_S=2 PLAY_WAIT_S=1 IMPORT_WAIT_S=3 IMPORT_POLL_S=0 \
    "${envs[@]}" bash "$SCRIPT" "$@" > "$T/stdout" 2> "$T/stderr"
  RC=$?
  OUTDIR=$(ls -d "$T"/dl/KetchupPlayerTest/*/ 2>/dev/null | head -n1)
}
js() { jq -r "$1" "$OUTDIR/results.json" 2>/dev/null; }
st_of() { js ".scenariusze[] | select(.id==\"$1\") | .status"; }
code_of() { js ".scenariusze[] | select(.id==\"$1\") | .kod"; }
bundle_text() { # cała zawartość ZIP jako tekst
  local z; z=$(ls "$OUTDIR"/*.zip 2>/dev/null | head -n1); [ -n "$z" ] || { echo ""; return; }
  unzip -p "$z" 2>/dev/null
}
no_workdirs() { [ -z "$(ls -d "$T"/tmp/ketchup.* 2>/dev/null)" ]; }

echo "== Składnia i narzędzia =="
bash -n "$SCRIPT"; ok "bash -n ketchup_tvbox_test.sh" $?
bash -n "$TV/analyze_keys.sh"; ok "bash -n analyze_keys.sh" $?
bash -n "$TV/tests/fake_adb"; ok "bash -n fake_adb" $?
SC_BIN="${SHELLCHECK_BIN:-$(command -v shellcheck 2>/dev/null)}"
if [ -n "$SC_BIN" ]; then
  "$SC_BIN" -S warning -x "$SCRIPT" "$TV/analyze_keys.sh" "$TV/test_analyze_keys.sh" "$TV/tests/fake_adb" "$TV/tests/fake_aapt" "$TV/tests/run_tests.sh"; sc_rc=$?
  ok "ShellCheck $("$SC_BIN" --version | sed -n 's/^version: //p') (ostrzeżenia i błędy)" "$sc_rc"
else echo "NOT_RUN ShellCheck: narzędzie niedostępne w tym środowisku (uruchom w Termuxie: pkg install shellcheck)"; fi

echo "== Analizator pilota: wersja wbudowana identyczna z samodzielną =="
blk() { sed -n '/^# BEGIN analyze_keys/,/^# END analyze_keys/p' "$1"; }
[ "$(blk "$TV/analyze_keys.sh")" = "$(blk "$SCRIPT")" ] && [ -n "$(blk "$TV/analyze_keys.sh")" ]; ok "blok analyze_keys zgodny" $?
bash "$TV/test_analyze_keys.sh" > /tmp/ak.out 2>&1; ok "test_analyze_keys.sh" $?; cat /tmp/ak.out | grep -v '^ok' ; rm -f /tmp/ak.out

echo "== Escapowanie JSON i Markdown (jednostkowo) =="
new_env
(
  export KETCHUP_SOURCE_ONLY=1; source "$SCRIPT"
  nasty=$'cudzysłów " ukośnik \\ tabulator\tnowa linia\nkoniec | rura `kod` <b>*gwiazdka*_pod_ [link](x) zażółć 日本'
  printf '{"a": "%s"}' "$(json_esc "$nasty")" > "$T/j.json"
  got=$(jq -r '.a' "$T/j.json" 2>/dev/null); [ "$got" = "$nasty" ]; r1=$?
  c=$(md_cell "$nasty"); case "$c" in *'|'*) printf '%s' "$c" | grep -q '[^\\]|' && r2=1 || r2=0;; *) r2=1;; esac
  case "$c" in *$'\n'*) r3=1;; *) r3=0;; esac
  printf '%s %s %s' "$r1" "$r2" "$r3" > "$T/esc.res"
)
read -r e1 e2 e3 < "$T/esc.res"
ok "JSON: round-trip przez jq (cudzysłowy, ukośniki, nowe linie, Unicode)" "$e1"
ok "Markdown: rura w komórce zamaskowana" "$e2"
ok "Markdown: brak surowych nowych linii w komórce" "$e3"

echo "== Oczyszczanie: fikcyjne sekrety, kodowane warianty, ścieżki URL =="
(
  export KETCHUP_SOURCE_ONLY=1; source "$SCRIPT"
  WORK="$T/w"; mkdir -p "$WORK"; SECRETS_FILE="$WORK/secrets"
  build_secrets "$SECRET_URL" "$SECRETS_FILE"
  enc=$(pct_encode "$SECRET_URL"); enc2=$(pct_encode "$enc")
  b64=$(printf 'Jan_Kowalski7:S3kr3t%%21x9' | base64 -w0)
  cat > "$T/in.txt" <<EOT
GET $SECRET_URL HTTP/1.1
url zakodowany: $enc
podwójnie: $enc2
json: ${SECRET_URL//\//\\/}
nagłówek base64: Authorization: Basic $b64
parametr: username=Jan_Kowalski7&password=S3kr3t%21x9
czysta wartość: Jan_Kowalski7 i S3kr3t!x9
log: nie można pobrać http://h.example.invalid:80/zbyszek12/tajne456/77.ts timeout
ścieżka bez schematu: zbyszek12/tajne456/77.ts oraz /zbyszek12/tajne456/77.ts
adres IP 10.20.30.40:8080 i MAC aa:bb:cc:dd:ee:ff
EOT
  build_secrets "$SECRET_PATH_URL" "$WORK/secrets2"; cat "$WORK/secrets2" >> "$SECRETS_FILE"
  sanitize < "$T/in.txt" > "$T/out.txt"
  leaks "$T/out.txt"; echo $? > "$T/leak.rc"
  grep -c -E 'Jan_Kowalski7|S3kr3t|zbyszek12|tajne456|srv.example.invalid|h.example.invalid|10\.20\.30\.40|aa:bb:cc' "$T/out.txt" > "$T/left.n"
  leaks "$T/in.txt"; echo $? > "$T/leak_in.rc"
  # kontrola wykrywa wyciek także w wersji z pominiętą maską (kodowanie procentowe nieznanego wariantu)
  printf 'x %s y\n' "$(pct_encode 'S3kr3t!x9')" > "$T/enc_only.txt"; leaks "$T/enc_only.txt"; echo $? > "$T/leak_enc.rc"
)
eq "sanitize: żaden sekret ani host nie został w tekście" "$(cat "$T/left.n")" "0"
eq "leaks: oczyszczony plik przechodzi kontrolę" "$(cat "$T/leak.rc")" "1"
eq "leaks: surowy plik z sekretami jest wykrywany" "$(cat "$T/leak_in.rc")" "0"
eq "leaks: wykrywa wariant zakodowany procentowo" "$(cat "$T/leak_enc.rc")" "0"

echo "== Konfiguracja nie jest wykonywana jako kod =="
new_env
cat > "$T/home/.ketchup-test.env" <<EOT
DEVICE_IP="192.168.1.170"; touch "$T/pwned"
PORT="5555\$(touch $T/pwned2)"
M3U_URL="$SECRET_URL"
ADB_TARGET="\`touch $T/pwned3\`"
EOT
run -- "$APK" --bez-listy
[ ! -e "$T/pwned" ] && [ ! -e "$T/pwned2" ] && [ ! -e "$T/pwned3" ]; ok "zawartość pliku konfiguracji nie została wykonana" $?
hasnt "M3U_URL z konfiguracji nie trafił do raportu" "$(cat "$OUTDIR/report.md" 2>/dev/null)$(bundle_text)" "srv.example.invalid"

echo "== Brak wymaganych danych =="
new_env; run -- --bez-listy
eq "bez ścieżki APK: kod 2" "$RC" "2"
new_env; run -- "$T/nie-ma.apk" --bez-listy
eq "nieistniejący APK: S01 BLOCKED" "$(st_of S01)" "BLOCKED"; eq "nieistniejący APK: kod APK_MISSING" "$(code_of S01)" "APK_MISSING"
eq "nieistniejący APK: S02 SKIPPED" "$(st_of S02)" "SKIPPED"
[ ! -f "$T/fake/input.log" ]; ok "nieistniejący APK: żaden klawisz nie wysłany" $?
no_workdirs; ok "nieistniejący APK: katalog roboczy usunięty" $?
new_env; echo "to nie jest apk" > "$T/zly.apk"; run -- "$T/zly.apk" --bez-listy
eq "uszkodzony APK: BLOCKED APK_CORRUPT" "$(code_of S01)" "APK_CORRUPT"
new_env; rm -f "$T/bin/aapt"; run -- "$APK" --bez-listy
eq "brak aapt: kod 3" "$RC" "3"; has "brak aapt: komunikat o narzędziu" "$(cat "$T/stderr")" "aapt"; has "brak aapt: nie instaluje samodzielnie" "$(cat "$T/stderr")" "niczego nie instaluje"
new_env; run -- "$APK" --lista-plik "$T/brak.txt"
ok "brak pliku listy: przebieg trwa, import pominięty" "$([ "$(st_of S03)" = SKIPPED ] && echo 0 || echo 1)"

echo "== Instalacja: błędy nie przechodzą w testy starej wersji =="
new_env; run FAKE_INSTALL_FAIL=INSTALL_FAILED_UPDATE_INCOMPATIBLE -- "$APK" --bez-listy
eq "konflikt podpisu: S01 BLOCKED" "$(st_of S01)" "BLOCKED"; has "konflikt podpisu: kod z przyczyną" "$(code_of S01)" "INSTALL_FAILED_UPDATE_INCOMPATIBLE"
eq "konflikt podpisu: S02 SKIPPED" "$(st_of S02)" "SKIPPED"; eq "konflikt podpisu: S04 SKIPPED" "$(st_of S04)" "SKIPPED"
[ ! -f "$T/fake/input.log" ]; ok "konflikt podpisu: zero klawiszy po nieudanej instalacji" $?
hasnt "konflikt podpisu: brak odinstalowania" "$(cat "$T/fake/adb.log")" "uninstall"
hasnt "konflikt podpisu: brak flagi -d (obniżanie wersji)" "$(grep -E ' install ' "$T/fake/adb.log")" " -d "
hasnt "brak pm clear domyślnie" "$(cat "$T/fake/adb.log")" "pm clear"
eq "konflikt podpisu: kod wyjścia 3" "$RC" "3"
new_env; run FAKE_INSTALL_FAIL=INSTALL_FAILED_VERSION_DOWNGRADE -- "$APK" --bez-listy
has "obniżenie wersji: BLOCKED z przyczyną" "$(code_of S01)" "INSTALL_FAILED_VERSION_DOWNGRADE"
new_env; run FAKE_APK_PKG=com.example.inny -- "$APK" --bez-listy
eq "niewłaściwy pakiet: WRONG_PACKAGE" "$(code_of S01)" "WRONG_PACKAGE"
hasnt "niewłaściwy pakiet: nie instalowano" "$(cat "$T/fake/adb.log")" " install "
new_env; run FAKE_AAPT_FAIL=1 -- "$APK" --bez-listy
eq "aapt nie czyta APK: APK_META_UNKNOWN" "$(code_of S01)" "APK_META_UNKNOWN"
new_env; run FAKE_INSTALL_SLEEP=5 INSTALL_TIMEOUT=1 -- "$APK" --bez-listy
eq "timeout instalacji: INSTALL_TIMEOUT" "$(code_of S01)" "INSTALL_TIMEOUT"
new_env; run FAKE_INSTALLED_NAME=0.0.1 -- "$APK" --bez-listy
eq "inna wersja po instalacji: VERSION_MISMATCH" "$(code_of S01)" "VERSION_MISMATCH"
new_env; run FAKE_PACKAGES="package:pl.ketchupplayer.tv.debug" -- "$APK" --bez-listy
eq "pakiet nie na liście użytkownika: NOT_INSTALLED (nie wybiera .debug z urządzenia)" "$(code_of S01)" "NOT_INSTALLED"

echo "== Przebieg bez listy: nawigacja pilotem, statusy, paczka =="
new_env; run -- "$APK" --bez-listy
eq "S00 PASS" "$(st_of S00)" "PASS"; eq "S01 PASS" "$(st_of S01)" "PASS"; eq "S02 PASS" "$(st_of S02)" "PASS"
eq "S03 SKIPPED NO_LIST_URL" "$(st_of S03)/$(code_of S03)" "SKIPPED/NO_LIST_URL"
eq "S04 sekcje osiągalne pilotem PASS" "$(st_of S04)" "PASS"
eq "S05 bez katalogu SKIPPED" "$(st_of S05)" "SKIPPED"
eq "S06 pusta Moja lista PASS" "$(st_of S06)" "PASS"
eq "S08z ocena obrazu zawsze MANUAL_REQUIRED" "$(st_of S08z)" "MANUAL_REQUIRED"
eq "S09 test pilota SKIPPED bez żądania" "$(st_of S09)" "SKIPPED"
eq "S11 stres SKIPPED bez żądania" "$(st_of S11)" "SKIPPED"
[ "$(jq '.scenariusze | length' "$OUTDIR/results.json")" -ge 15 ]; ok "results.json zawiera scenariusze" $?
jq -e . "$OUTDIR/results.json" >/dev/null; ok "results.json jest poprawnym JSON" $?
has "report.md: SHA-256 APK" "$(cat "$OUTDIR/report.md")" "SHA-256 APK: $(sha256sum "$APK" | cut -d' ' -f1)"
has "report.md: SHA kodu nieustalone" "$(cat "$OUTDIR/report.md")" "SHA kodu: nieustalone"
has "report.md: czas z Europe/Warsaw" "$(cat "$OUTDIR/report.md")" "Europe/Warsaw"
[ -n "$(ls "$OUTDIR"/*.zip 2>/dev/null)" ]; ok "powstała jedna paczka ZIP" $?
hasnt "paczka: brak zrzutów ekranu i surowych XML" "$(unzip -l "$OUTDIR"/*.zip)" ".png"
hasnt "paczka: brak surowych dumpów UI" "$(unzip -l "$OUTDIR"/*.zip)" "ui.xml"
no_workdirs; ok "katalog roboczy usunięty po zakończeniu" $?
[ ! -f "$T/fake/destructive.log" ]; ok "dane aplikacji nie zostały wyczyszczone" $?
ok "BACK/OK/strzałki: nawigacja tylko zdarzeniami klawiszy (brak tapnięć)" "$([ -f "$T/fake/input.log" ] && ! grep -q TAP "$T/fake/input.log" && echo 0 || echo 1)"

echo "== Wybór urządzenia i autoryzacja ADB =="
new_env; rm -f "$T/home/.ketchup-test.env"; run -- "$APK" --bez-listy
eq "bez konfiguracji i z jednym urządzeniem: S00 PASS" "$(st_of S00)" "PASS"
new_env; rm -f "$T/home/.ketchup-test.env"; run FAKE_MULTI=1 -- "$APK" --bez-listy
eq "bez konfiguracji i z wieloma urządzeniami: BLOCKED NO_DEVICE (bez zgadywania)" "$(st_of S00)/$(code_of S00)" "BLOCKED/NO_DEVICE"
[ ! -f "$T/fake/input.log" ] && ! grep -q ' install ' "$T/fake/adb.log"; ok "niejednoznaczny wybór: nic nie instalowano ani nie wysyłano" $?
new_env; run FAKE_STATE=unauthorized -- "$APK" --bez-listy
eq "ADB nieautoryzowany: BLOCKED ADB_UNAUTHORIZED" "$(code_of S00)" "ADB_UNAUTHORIZED"
hasnt "ADB nieautoryzowany: skrypt nie paruje" "$(cat "$T/fake/adb.log")" "pair"

echo "== Brak obserwowalności fokusu to nie PASS i nie błąd aplikacji =="
new_env; run FAKE_FOCUS_HIDDEN=1 -- "$APK" --bez-listy
eq "fokus niewidoczny: S04 BLOCKED NOT_OBSERVABLE" "$(st_of S04)/$(code_of S04)" "BLOCKED/NOT_OBSERVABLE"
ok "fokus niewidoczny: żaden scenariusz nie jest FAIL" "$(js '[.scenariusze[] | select(.status=="FAIL")] | length' | grep -qx 0 && echo 0 || echo 1)"

echo "== Utrata pierwszego planu i ADB =="
new_env; run FAKE_FG_LOST_AFTER=6 -- "$APK" --bez-listy
has "utrata pierwszego planu: przerwanie NOT_FOREGROUND" "$(js .przerwanie)" "NOT_FOREGROUND"
keys_sent=$(grep -c '^KEY' "$T/fake/input.log" 2>/dev/null || echo 0)
ok "utrata pierwszego planu: po utracie nie wysyłano dalej klawiszy (wysłano $keys_sent)" "$([ "$keys_sent" -lt 25 ] && echo 0 || echo 1)"
eq "utrata pierwszego planu: kolejne scenariusze SKIPPED" "$(st_of S08)" "SKIPPED"
[ -f "$OUTDIR/results.json" ]; ok "utrata pierwszego planu: raport powstał" $?
new_env; run FAKE_LOSE_AFTER=40 -- "$APK" --bez-listy
has "utrata ADB: przerwanie ADB_LOST" "$(js .przerwanie)" "ADB_LOST"
[ -f "$OUTDIR/report.md" ] && [ -n "$(ls "$OUTDIR"/*.zip 2>/dev/null)" ]; ok "utrata ADB: report.md i ZIP powstały" $?
[ ! -f "$T/fake/logcat.calls" ]; ok "utrata ADB: brak wiszącego zbierania logów" $?
ok "utrata ADB: logi pominięte" "$(js .logi_aplikacji | grep -q '^pomini' && echo 0 || echo 1)"

echo "== Przerwanie (Ctrl+C) =="
# Proces w tle w nieinteraktywnej powłoce ignoruje SIGINT, więc uruchamiamy go z domyślną dyspozycją (jak z terminala).
new_env
if command -v python3 >/dev/null 2>&1; then
  export T TV SCRIPT APK; python3 "$TV/tests/send_sigint.py" > "$T/int.res"
  read -r was_alive rc_int took < "$T/int.res"
  eq "Ctrl+C: skrypt pracował do przerwania" "$was_alive" "1"
  eq "Ctrl+C: kod wyjścia 130" "$rc_int" "130"
  ok "Ctrl+C: zakończył się w kilka sekund ($took s)" "$(awk -v t="$took" 'BEGIN { exit (t < 5) ? 0 : 1 }'; echo $?)"
  OUTDIR=$(ls -d "$T"/dl/KetchupPlayerTest/*/ 2>/dev/null | head -n1)
  eq "Ctrl+C: przerwanie zapisane w raporcie" "$(js .przerwanie)" "INTERRUPTED"
  [ ! -f "$T/fake/logcat.calls" ]; ok "Ctrl+C: brak zbierania logów po przerwaniu" $?
  no_workdirs; ok "Ctrl+C: katalog roboczy usunięty" $?
  [ -n "$(ls "$OUTDIR"/*.zip 2>/dev/null)" ]; ok "Ctrl+C: paczka ZIP powstała" $?
else echo "NOT_RUN Ctrl+C: brak python3 do wysłania SIGINT z domyślną dyspozycją"; fi

echo "== Stare drzewo UI nie jest czytane po nieudanym odświeżeniu =="
new_env
(
  export KETCHUP_SOURCE_ONLY=1 ADB_BIN="$TV/tests/fake_adb" FAKE_DIR="$T/fake" ADB_TIMEOUT=10 NAP_SCALE=0
  source "$SCRIPT"; WORK="$T/w"; mkdir -p "$WORK"; TARGET="x:1"; PKG="pl.ketchupplayer.tv"
  ui_refresh; a=$?; ui_has "Home"; b=$?
  export FAKE_UI_FAIL=1 FAKE_UI_STALE=1
  ui_refresh; c=$?; ui_has "Home"; d=$?; ui_ok; e=$?
  printf '%s %s %s %s %s' "$a" "$b" "$c" "$d" "$e" > "$T/stale.res"
)
read -r a b c d e < "$T/stale.res"
eq "świeże odświeżenie udane" "$a$b" "00"; eq "nieudane odświeżenie zgłasza błąd" "$c" "1"
eq "po nieudanym odświeżeniu stare UI jest nieczytelne" "$d$e" "11"

echo "== Wpisywanie adresu: dane, nie kod (prawdziwa powłoka sh po stronie atrapy urządzenia) =="
new_env
nasty="a b;\$(touch $T/pwned_text)\`touch $T/pwned_text2\`'x|y&z\"q\\r;touch $T/pwned_text3 && touch $T/pwned_text4"
(
  export KETCHUP_SOURCE_ONLY=1 ADB_BIN="$TV/tests/fake_adb" FAKE_DIR="$T/fake" ADB_TIMEOUT=10 NAP_SCALE=0
  source "$SCRIPT"; WORK="$T/w"; mkdir -p "$WORK/../fake/state" "$WORK"; TARGET="x:1"; PKG="pl.ketchupplayer.tv"
  type_text "$nasty"
)
want_arg="${nasty// /%s}"
eq "type_text: urządzenie dostaje dokładnie jeden argument z całym tekstem (apostrof, spacje, &, średnik, \$(), backtick)" "$(cat "$T/fake/args.log")" "ARG[$want_arg]"
eq "type_text: po zdekodowaniu %s pole ma oryginalny tekst" "$(cat "$T/fake/state/field")" "$nasty"
for f in pwned_text pwned_text2 pwned_text3 pwned_text4; do [ ! -e "$T/$f" ]; ok "type_text: żadne polecenie z tekstu nie zostało wykonane ($f)" $?; done
# Kontrola ujemna: gdyby tekst NIE był w cudzysłowie, ta sama atrapa wykonałaby polecenie (dowód, że test coś mierzy).
FAKE_DIR="$T/fake" "$TV/tests/fake_adb" shell "input text zle;touch $T/kontrola" >/dev/null 2>&1
[ -e "$T/kontrola" ]; ok "kontrola ujemna: surowe wywołanie wykonuje ; (test jest czuły na wykonanie)" $?
new_env; printf 'DEVICE_IP="192.168.1.170"\n' > "$T/home/.ketchup-test.env"

echo "== Adres listy: ukryty, poza raportem i środowiskiem procesów podrzędnych =="
new_env
printf '%s\n' "$SECRET_URL" > "$T/lista.txt"; chmod 600 "$T/lista.txt"
printf '1' > /dev/null
mkdir -p "$T/fake/state"; echo Wizard > "$T/fake/state/section"; echo 1 > "$T/fake/state/wiz"
run -- "$APK" --lista-plik "$T/lista.txt"
eq "wizard + import: S03 PASS" "$(st_of S03)" "PASS"
has "import: liczba kanałów odczytana z ekranu" "$(js '.scenariusze[] | select(.id=="S03") | .rzeczywiscie')" "4578"
all="$(cat "$OUTDIR/report.md" "$OUTDIR/results.json")$(bundle_text)"
for frag in "Jan_Kowalski7" "S3kr3t" "srv.example.invalid" "get.php" "http://" "https://"; do hasnt "paczka i raport bez '$frag'" "$all" "$frag"; done
hasnt "adres nie jest w środowisku procesów podrzędnych" "$(cat "$T/fake/env.log")" "srv.example.invalid"
hasnt "adres nie jest w stdout/stderr skryptu" "$(cat "$T/stdout" "$T/stderr")" "srv.example.invalid"
no_workdirs; ok "katalog roboczy (z zrzutami UI i sekretami) usunięty" $?
ok "wpisywanie przez ADB oznaczone w krokach, nie jako test pilota" "$(js '.scenariusze[] | select(.id=="S03") | .kroki | join(" ")' | grep -q 'nie test pilota' && echo 0 || echo 1)"
new_env; mkdir -p "$T/fake/state"; echo Wizard > "$T/fake/state/section"; echo 1 > "$T/fake/state/wiz"
printf '%s\n' "$SECRET_URL" > "$T/lista.txt"
run FAKE_UI_FAIL=1 -- "$APK" --lista-plik "$T/lista.txt"
hasnt "gdy UI niedostępne: adres też nie wycieka" "$(cat "$OUTDIR/report.md" "$OUTDIR/results.json" "$T/stdout" "$T/stderr")" "Jan_Kowalski7"

echo "== Logi, których nie da się oczyścić, są wykluczane (REDACTION_FAILED jako kod przyczyny) =="
new_env; printf '%s\n' "$SECRET_URL" > "$T/lista.txt"
mkdir -p "$T/fake/state"; echo Wizard > "$T/fake/state/section"; echo 1 > "$T/fake/state/wiz"
printf '11-08 10:00:00.000 W/Player( 4242): blad strumienia Jan%%5FKowalski7 nie dziala\n' > "$T/fake/logcat"
run -- "$APK" --lista-plik "$T/lista.txt"
eq "log z nieznanym kodowaniem sekretu: S12 BLOCKED" "$(st_of S12)" "BLOCKED"
has "log z nieznanym kodowaniem sekretu: REDACTION_FAILED jako kod" "$(code_of S12)" "REDACTION_FAILED"
hasnt "log wykluczony z paczki" "$(unzip -l "$OUTDIR"/*.zip)" "logi/app.txt"
hasnt "sekret nie trafił do paczki" "$(cat "$OUTDIR/report.md" "$OUTDIR/results.json")$(bundle_text)" "Kowalski7"
new_env; printf '11-08 10:00:00.000 E/AndroidRuntime( 4242): FATAL EXCEPTION: main\n' > "$T/fake/logcat"
run -- "$APK" --bez-listy
eq "czysty log: S12 PASS i plik w paczce" "$(st_of S12)" "PASS"
has "czysty log: logi/app.txt w paczce" "$(unzip -l "$OUTDIR"/*.zip)" "logi/app.txt"

echo "== Dwa monity: ścieżka APK (widoczna) i adres listy (ukryty); argument APK jest tylko skrótem =="
new_env; mkdir -p "$T/fake/state"; echo Wizard > "$T/fake/state/section"; echo 1 > "$T/fake/state/wiz"
printf '"%s"\n%s\n' "$APK" "$SECRET_URL" > "$T/odpowiedzi"
run KETCHUP_TTY="$T/odpowiedzi" KETCHUP_TTY_OUT="$T/ttyout" -- 
eq "bez argumentu z terminalem: S01 PASS (APK ze spacjami i cudzysłowami z monitu)" "$(st_of S01)" "PASS"
eq "bez argumentu: adres z drugiego monitu, import S03 PASS" "$(st_of S03)" "PASS"
has "monit APK był zadany" "$(cat "$T/ttyout")" "Ścieżka do pliku APK"
has "monit adresu był zadany" "$(cat "$T/ttyout")" "Adres listy M3U"
hasnt "adres nie został wypisany na terminal (wpisywanie ukryte)" "$(cat "$T/ttyout")" "srv.example.invalid"
hasnt "adres nie trafił do raportu ani ZIP" "$(cat "$OUTDIR/report.md" "$OUTDIR/results.json")$(bundle_text)" "Kowalski7"
new_env; printf 'nie-ma-takiego.apk\n%s\n' "$APK" > "$T/odpowiedzi"
run KETCHUP_TTY="$T/odpowiedzi" KETCHUP_TTY_OUT="$T/ttyout" -- --bez-listy
eq "zła ścieżka, potem poprawna: S01 PASS" "$(st_of S01)" "PASS"
has "po złej ścieżce komunikat i ponowny monit" "$(cat "$T/stderr")" "Nie ma takiego pliku"
new_env; printf 'a\nb\nc\n' > "$T/odpowiedzi"
run KETCHUP_TTY="$T/odpowiedzi" KETCHUP_TTY_OUT="$T/ttyout" -- --bez-listy
eq "trzy złe ścieżki: BLOCKED APK_MISSING" "$(st_of S01)/$(code_of S01)" "BLOCKED/APK_MISSING"
new_env; cp "$APK" "$T/home/x.apk"; printf '%s\n' '~/x.apk' > "$T/odpowiedzi"
run KETCHUP_TTY="$T/odpowiedzi" KETCHUP_TTY_OUT="$T/ttyout" -- --bez-listy
eq "ścieżka z ~ rozwinięta: S01 PASS" "$(st_of S01)" "PASS"
new_env; run -- --bez-listy
eq "bez argumentu i bez terminala: kod 2" "$RC" "2"
new_env; printf '%s\n' "$APK" > "$T/odpowiedzi"
run KETCHUP_TTY="$T/odpowiedzi" KETCHUP_TTY_OUT="$T/ttyout" -- "$APK" --bez-listy
eq "argument APK jako skrót: bez monitu o ścieżkę" "$(grep -c 'Ścieżka do pliku APK' "$T/ttyout" 2>/dev/null; true)" "0"

echo "== Reset danych tylko po jawnym potwierdzeniu =="
new_env; run -- "$APK" --bez-listy --reset-dane
eq "--reset-dane bez potwierdzenia: BLOCKED RESET_NOT_CONFIRMED" "$(st_of S02)/$(code_of S02)" "BLOCKED/RESET_NOT_CONFIRMED"
[ ! -f "$T/fake/destructive.log" ]; ok "--reset-dane bez potwierdzenia: nic nie wyczyszczono" $?

echo "== Test pilota: opcjonalny, bez TTY pomijany =="
new_env; run -- "$APK" --bez-listy --test-pilota
eq "test pilota bez terminala: SKIPPED NO_TTY" "$(st_of S09)/$(code_of S09)" "SKIPPED/NO_TTY"

echo
echo "Wynik: $((TOTAL - FAILS)) z $TOTAL sprawdzeń OK, błędów: $FAILS"
exit "$([ "$FAILS" -eq 0 ] && echo 0 || echo 1)"
