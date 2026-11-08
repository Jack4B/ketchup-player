#!/data/data/com.termux/files/usr/bin/bash
# =============================================================================
#  ketchup_tvbox_test.sh :: testy RC Ketchup Player na TV boxie przez ADB z Termuxa.
#  Jeden samodzielny plik (do pobrania razem z RC). Szczegóły: docs/TVBOX-TESTY.md.
#
#  Użycie:  bash ketchup_tvbox_test.sh [opcje] "ścieżka/do/pliku.apk"
#  Adres listy M3U: ukryty monit (nigdy z argumentów ani z pliku konfiguracji) albo --lista-plik PLIK (pierwsza linia).
#  Konfiguracja urządzenia (NIE do repo): ~/.ketchup-test.env, linie KLUCZ="wartość" (plik NIE jest wykonywany):
#     DEVICE_IP="192.168.1.170"   PORT="5555"   ADB_TARGET="host:port"
#  Wymagane narzędzia Termuxa (nie instalujemy ich sami):
#     pkg install android-tools coreutils unzip zip gawk sed grep aapt
#
#  Wynik: <Download>/KetchupPlayerTest/<data>/report.md, results.json i ketchup-wyniki-<data>.zip (wyłącznie oczyszczone
#  materiały). Statusy: PASS, FAIL, BLOCKED, SKIPPED, MANUAL_REQUIRED. Brak dowodu nigdy nie jest sukcesem.
# =============================================================================
set -u -o pipefail

KT_VERSION="rc-1"
PKG_RELEASE="pl.ketchupplayer.tv"
PKG_DEBUG="pl.ketchupplayer.tv.debug"
KEY_UP=19; KEY_DOWN=20; KEY_LEFT=21; KEY_RIGHT=22; KEY_OK=23; KEY_BACK=4; KEY_ENTER=66
RAIL_LABELS=("Profil" "Szukaj" "Home" "Na żywo" "Filmy" "Seriale" "Moja lista" "Ustawienia")

# ---------- opcje i stan ----------
APK=""; LIST_FILE=""; RESET_DATA=0; PILOT=0; STRESS_N=0; SEED=1; DIAG_LOCAL=0; NO_LIST=0
TARGET_ARG=""; USER_CODE_SHA=""
CFG_DEVICE_IP=""; CFG_PORT=""; CFG_TARGET=""; CFG_IGNORED=""
LIST_URL=""            # tylko w pamięci procesu; nigdzie nie eksportowane ani zapisywane
TARGET=""; USR=0; PKG=""; ACT=""; APK_SHA=""; APK_VER=""; APK_CODE=""
WORK=""; OUT=""; SECRETS_FILE=""
ADB_BIN="${ADB_BIN:-adb}"; ADB_T="${ADB_TIMEOUT:-25}"; KEY_DELAY="${KEY_DELAY:-0.6}"
ABORT=0; ABORT_CODE=""; INTERRUPTED=0; FINALIZED=0; ADB_FAILS=0
START_DEVICE_DATE=""; START_PID=""; EXPECTED_RESTARTS=0
FORM_ACTIVE=0; UI_OK=0; UI_SEQ=0; SH_OUT=""; SH_RC=0
DEV_MODEL=""; DEV_ANDROID=""; DEV_SCREEN=""; SCREEN_H=1080
R_ID=(); R_NAME=(); R_STATUS=(); R_CODE=(); R_STEPS=(); R_EXP=(); R_ACT=(); R_EVID=(); R_TOUCH=()
SC_ID=""; SC_NAME=""; SC_STEPS=""; SC_TOUCH=0

# ---------- czas, JSON, Markdown ----------
ts_warsaw() {
  local z
  z=$(TZ=Europe/Warsaw date '+%Y-%m-%dT%H:%M:%S%z' 2>/dev/null) || z=""
  case "$z" in
    ''|*+0000) printf '%s (UTC, brak strefy Europe/Warsaw w systemie)' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')";;
    *) printf '%s:%s' "${z%??}" "${z: -2}";;
  esac
}
json_esc() {
  local s="$1"
  s="${s//\\/\\\\}"; s="${s//\"/\\\"}"
  s="${s//$'\n'/\\n}"; s="${s//$'\r'/\\r}"; s="${s//$'\t'/\\t}"
  printf '%s' "$s" | LC_ALL=C tr -d '\000-\010\013\014\016-\037'
}
md_cell() {
  local s="$1"
  s="${s//\\/\\\\}"; s="${s//|/\\|}"; s="${s//\`/\\\`}"; s="${s//\*/\\*}"; s="${s//_/\\_}"
  s="${s//[/\\[}"; s="${s//]/\\]}"; s="${s//</&lt;}"; s="${s//>/&gt;}"; s="${s//$'\n'/<br>}"; s="${s//$'\r'/}"
  printf '%s' "$s"
}
say() { printf '%s\n' "$*" >&2; }

# ---------- oczyszczanie: dozwolone pola + maski + weryfikacja (nigdy nie obiecujemy pełnej ochrony regexami) ----------
# sanitize: stdin -> stdout. Maskuje adresy, parametry z danymi dostępu, ścieżki host/użytkownik/hasło/id, IP, MAC i
# dosłownie znane składniki wpisanego adresu (plik $SECRETS_FILE, jedna wartość w linii, dłuższe najpierw).
sanitize() {
  LC_ALL=C sed -E \
    -e 's#[A-Za-z][A-Za-z0-9+.-]*://[^[:space:]"'"'"'<>`]+#<URL>#g' \
    -e 's#(^|[^A-Za-z0-9])(user(name)?|login|pass(word|wd)?|pwd|token|key|api[_-]?key|auth|secret|mac)(=|%3[Dd])[^&[:space:]"'"'"']*#\1\2=<ukryte>#Ig' \
    -e 's#/[^/[:space:]"'"'"']+/[^/[:space:]"'"'"']+/[0-9][^[:space:]"'"'"']*#/<P>/<P>/<ID>#g' \
    -e 's#(^|[[:space:]"'"'"'(=:])[A-Za-z0-9._~-]+/[A-Za-z0-9._~-]+/[0-9]+(\.[A-Za-z0-9]{2,5})?#\1<P>/<P>/<ID>#g' \
    -e 's#[0-9]{1,3}(\.[0-9]{1,3}){3}(:[0-9]+)?#<IP>#g' \
    -e 's#([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}#<MAC>#g' |
  LC_ALL=C awk -v sf="${SECRETS_FILE:-}" '
    BEGIN { n = 0; if (sf != "") while ((getline l < sf) > 0) if (length(l) > 0) s[n++] = l }
    { line = $0
      for (i = 0; i < n; i++) while ((p = index(line, s[i])) > 0) line = substr(line, 1, p - 1) "<ukryte>" substr(line, p + length(s[i]))
      print line }'
}
sanitize_str() { printf '%s' "$1" | sanitize; }
# urldecode: do KONTROLI wycieku (kopia z rozkodowanymi %XX oraz \/ i /), nie do zapisu.
urldecode() {
  LC_ALL=C awk '
    function hv(c) { return index("0123456789abcdef", tolower(c)) - 1 }
    { line = $0; gsub(/\\\//, "/", line); gsub(/\\u002[fF]/, "/", line); out = ""
      while (match(line, /%[0-9A-Fa-f][0-9A-Fa-f]/)) {
        out = out substr(line, 1, RSTART - 1) sprintf("%c", hv(substr(line, RSTART + 1, 1)) * 16 + hv(substr(line, RSTART + 2, 1)))
        line = substr(line, RSTART + RLENGTH) }
      print out line }'
}
# leaks PLIK: 0 gdy w pliku (lub w jego rozkodowanej kopii) została jakakolwiek wartość tajna albo surowy adres.
leaks() {
  local f="$1"
  [ -f "$f" ] || return 1
  if [ -n "$SECRETS_FILE" ] && [ -s "$SECRETS_FILE" ]; then
    grep -q -F -f "$SECRETS_FILE" "$f" && return 0
    urldecode < "$f" | grep -q -F -f "$SECRETS_FILE" && return 0
  fi
  grep -q '://' "$f" && return 0
  return 1
}
pct_decode() { # $1 -> tekst po rozkodowaniu %XX (przez awk, bez interpretacji ukośników)
  printf '%s\n' "$1" | urldecode
}
pct_encode() { # $1 -> kodowanie procentowe (wielkie litery szesnastkowe)
  local s="$1" i c out=""
  for ((i = 0; i < ${#s}; i++)); do
    c="${s:i:1}"
    case "$c" in [A-Za-z0-9._~-]) out+="$c";; *) out+=$(printf '%%%02X' "'$c");; esac
  done
  printf '%s' "$out"
}
# build_secrets URL PLIK: składniki adresu i ich zakodowane warianty (surowe, %XX, podwójne %25, base64 par, JSON \/).
# Nazwy parametrów zapytania (username=, password=, type=) NIE są sekretem: dodajemy tylko ich wartości.
_sec_safe() { case "$1" in get.php|player_api.php|panel_api.php|xmltv.php|index.php|playlist.m3u|playlist.m3u8|live|movie|series|m3u|m3u8|m3u_plus) return 0;; esac; return 1; }
build_secrets() {
  local url="$1" f="$2" rest path query tok prev="" e b kv
  : > "$f"; chmod 600 "$f"
  _sec_add() { [ "${#1}" -ge 4 ] && ! _sec_safe "$1" && printf '%s\n' "$1" >> "$f"; return 0; }
  _sec_add "$url"; _sec_add "$(pct_encode "$url")"; _sec_add "$(pct_encode "$(pct_encode "$url")")"
  _sec_add "$(printf '%s' "$url" | sed 's#/#\\/#g')"
  rest="${url#*://}"; _sec_add "${rest%%[/?#]*}"
  path="${rest#*/}"; [ "$path" = "$rest" ] && path=""
  query=""; case "$path" in *\?*) query="${path#*\?}"; path="${path%%\?*}";; esac
  while IFS= read -r tok; do
    [ -z "$tok" ] && continue
    _sec_add "$tok"; e=$(pct_encode "$tok"); [ "$e" != "$tok" ] && _sec_add "$e"
    e=$(pct_decode "$tok"); [ "$e" != "$tok" ] && { _sec_add "$e"; _sec_add "$(pct_encode "$e")"; }
    if [ -n "$prev" ]; then b=$(printf '%s:%s' "$prev" "$tok" | base64 -w0 2>/dev/null) && _sec_add "$b"; fi
    prev="$tok"
  done < <(printf '%s' "$path" | tr '/' '\n')
  prev=""
  while IFS= read -r kv; do
    tok="${kv#*=}"; [ "$tok" = "$kv" ] && continue
    _sec_add "$tok"; e=$(pct_encode "$tok"); [ "$e" != "$tok" ] && _sec_add "$e"
    e=$(pct_decode "$tok"); [ "$e" != "$tok" ] && { _sec_add "$e"; _sec_add "$(pct_encode "$e")"; b=$(printf '%s:%s' "$prev" "$e" | base64 -w0 2>/dev/null) && _sec_add "$b"; }
    if [ -n "$prev" ]; then b=$(printf '%s:%s' "$prev" "$tok" | base64 -w0 2>/dev/null) && _sec_add "$b"; fi
    prev="$tok"
  done < <(printf '%s' "$query" | tr '&;' '\n\n')
  LC_ALL=C awk '{ print length($0) "\t" $0 }' "$f" | sort -t "$(printf '\t')" -k1,1nr -k2,2 | cut -f2- | uniq > "$f.tmp" && mv "$f.tmp" "$f"
  chmod 600 "$f"
}

# ---------- wbudowany analizator pilota (identyczny z tools/tvbox/analyze_keys.sh; pilnuje tego test) ----------
# BEGIN analyze_keys
analyze_keys() {
  local KG="${1:-}" GE="${2:-}" EXPECT="${3:-5}"
  local app_any=0 hw_any=0 app_all=0 app_drop=0 app_mingap=-1 app_maxage=0 hw_total=0 hw_mingap=-1
  if [ -f "$KG" ] && grep -q 'KG t=' "$KG"; then app_any=1; fi
  if [ -f "$GE" ] && grep -q 'EV_KEY' "$GE"; then hw_any=1; fi
  if [ "$app_any" -eq 1 ]; then
    # aplikacja: tylko strzałka w dół (kod 20), nowe naciśnięcia (rep=0)
    read -r app_all app_drop app_mingap app_maxage < <(awk '
      /KG .* key=20 / {
        rep=0; gap=-1; age=0; drop=0
        for (i=1;i<=NF;i++) { split($i,a,"="); if (a[1]=="rep") rep=a[2]; if (a[1]=="gap") gap=a[2]; if (a[1]=="age") age=a[2]; if (a[1]=="drop") drop=a[2] }
        if (age+0 > maxage) maxage=age+0
        if (rep+0 == 0) { total++; if (drop+0==1) dropped++; if (gap+0 >= 0 && (min=="" || gap+0 < min)) min=gap+0 }
      }
      END { printf "%d %d %s %d\n", total, dropped, (min==""?"-1":min), maxage }' "$KG")
  fi
  if [ "$hw_any" -eq 1 ]; then
    # sprzęt: zdarzenia naciśnięcia strzałki w dół w getevent -lt: "[ 1234.567890] /dev/input/eventN: EV_KEY KEY_DOWN DOWN"
    read -r hw_total hw_mingap < <(awk '
      /EV_KEY +KEY_DOWN +DOWN/ { gsub(/[\[\]]/,"",$1); t=$1+0; if ($1=="" ) t=$2+0; n++; if (prev!="" ) { d=(t-prev)*1000; if (min=="" || d<min) min=d } prev=t }
      END { printf "%d %d\n", n, (min==""?-1:int(min)) }' <(sed -E 's/^\[ */[/' "$GE"))
  fi
  local app_acc=$(( app_all - app_drop ))
  local detail="zadeklarowano ${EXPECT}; sprzęt: ${hw_total} zdarzeń (min odstęp ${hw_mingap} ms); aplikacja: ${app_all} naciśnięć, w tym odrzuconych ${app_drop}, przyjętych ${app_acc} (min odstęp ${app_mingap} ms)"
  local loss dup lag rc=0
  if [ "$hw_any" -eq 0 ]; then
    loss="BLOCKED|GETEVENT_UNAVAILABLE|brak zdarzeń sprzętu: getevent niedostępny, odmowa uprawnień albo nie naciśnięto pilota; utraty nie da się ocenić"
    dup="BLOCKED|GETEVENT_UNAVAILABLE|brak zdarzeń sprzętu: duplikatów nie da się ocenić"
  elif [ "$app_any" -eq 0 ]; then
    loss="BLOCKED|APP_DIAG_MISSING|aplikacja nie zalogowała KeyGuard (build release albo brak logu): utraty nie da się ocenić ($detail)"
    dup="BLOCKED|APP_DIAG_MISSING|aplikacja nie zalogowała KeyGuard: duplikatów nie da się ocenić ($detail)"
  else
    if [ "$hw_total" -lt "$EXPECT" ]; then
      loss="MANUAL_REQUIRED|TOO_FEW_PRESSES|sprzęt zgłosił mniej zdarzeń niż zadeklarowano: powtórz test albo sprawdź pilota ($detail)"
    elif [ "$app_all" -lt "$EXPECT" ]; then
      loss="FAIL|LOST_BEFORE_APP|sprzęt zgłosił naciśnięcia, a aplikacja zobaczyła mniej niż zadeklarowano ($detail)"
    elif [ "$app_acc" -lt "$EXPECT" ]; then
      loss="FAIL|DROPPED_BY_FILTER|filtr aplikacji odrzucił prawdziwe naciśnięcia ($detail)"
    else
      loss="PASS|OK|żadne zadeklarowane naciśnięcie nie zginęło ($detail)"
    fi
    if [ "$hw_total" -gt "$EXPECT" ]; then
      if [ "$app_acc" -gt "$EXPECT" ]; then
        dup="FAIL|DUPLICATES_ACCEPTED|pilot wysyła duplikaty, a filtr aplikacji ich nie łapie: zwiększ Czułość pilota w Ustawienia, Odtwarzanie ($detail)"
      else
        dup="PASS|DUPLICATES_FILTERED|pilot wysyła duplikaty, filtr aplikacji je odrzuca ($detail)"
      fi
    elif [ "$app_acc" -gt "$EXPECT" ]; then
      dup="FAIL|APP_EXTRA_PRESSES|aplikacja przyjęła więcej naciśnięć niż sprzęt zgłosił: duplikaty powstają w systemie lub aplikacji ($detail)"
    else
      dup="PASS|NO_DUPLICATES|brak duplikatów ($detail)"
    fi
  fi
  if [ "$app_any" -eq 1 ] && [ "$app_maxage" -gt 120 ]; then
    lag="MANUAL_REQUIRED|MAIN_THREAD_STALL|zdarzenia czekały w kolejce do ${app_maxage} ms: zacięcia głównego wątku mogą dawać skoki po puszczeniu"
  elif [ "$app_any" -eq 1 ]; then
    lag="PASS|OK|opóźnienie zdarzeń w aplikacji do ${app_maxage} ms"
  else
    lag="BLOCKED|APP_DIAG_MISSING|brak diagnostyki aplikacji: opóźnień nie da się ocenić"
  fi
  printf 'LOSS|%s\nDUP|%s\nLAG|%s\n' "$loss" "$dup" "$lag"
  case "$loss$dup$lag" in *FAIL*) rc=2;; *BLOCKED*|*MANUAL_REQUIRED*) rc=3;; esac
  return $rc
}
# END analyze_keys

nap() { # sleep skalowany zmienną NAP_SCALE (testy offline ustawiają 0)
  local sc="${NAP_SCALE:-1}"
  [ "$sc" = 0 ] && return 0
  sleep "$(awk -v a="$1" -v s="$sc" 'BEGIN { print a * s }')"
}

# ---------- ADB: wszystkie wywołania z limitem czasu ----------
# --foreground: polecenie zostaje w grupie terminala, więc Ctrl+C trafia od razu także do adb (inaczej trap czekałby na koniec).
tmo() { timeout --foreground "$@"; }
adbx() { tmo "$ADB_T" "$ADB_BIN" -s "$TARGET" "$@"; }
sh_() { # wynik w SH_OUT, kod w SH_RC; kilka porażek z rzędu = utrata ADB
  SH_OUT=$(adbx shell "$@" 2>/dev/null); SH_RC=$?
  SH_OUT="${SH_OUT//$'\r'/}"
  if [ "$SH_RC" -eq 124 ] || [ "$SH_RC" -eq 255 ]; then ADB_FAILS=$((ADB_FAILS + 1)); else ADB_FAILS=0; fi
  return $SH_RC
}
adb_alive() {
  local st
  st=$(tmo 8 "$ADB_BIN" -s "$TARGET" get-state 2>/dev/null | tr -d '\r')
  [ "$st" = "device" ]
}
abort_run() { ABORT=1; [ -z "$ABORT_CODE" ] && ABORT_CODE="$1"; }
fg_check() { # 0 = Ketchup na pierwszym planie (IME nad naszą aplikacją też się liczy)
  local w
  sh_ "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp|mFocusedWindow'" || true
  w="$SH_OUT"
  case "$w" in *"$PKG"*) return 0;; esac
  case "$w" in *InputMethod*) case "$w" in *mFocusedApp*"$PKG"*) return 0;; esac;; esac
  return 1
}
guard() { # bezpiecznik: bez aplikacji na pierwszym planie albo bez ADB nie wysyłamy żadnego klawisza
  [ "$ABORT" -eq 1 ] && return 1
  [ -z "$PKG" ] && return 0
  if ! adb_alive; then abort_run ADB_LOST; return 1; fi
  fg_check && return 0
  nap 2
  if ! adb_alive; then abort_run ADB_LOST; return 1; fi
  fg_check && return 0
  abort_run NOT_FOREGROUND; return 1
}
key() { guard || return 1; tmo "$ADB_T" "$ADB_BIN" -s "$TARGET" shell input keyevent "$1" >/dev/null 2>&1; nap "$KEY_DELAY"; }
keys() { local k; for k in "$@"; do key "$k" || return 1; done; }
# type_text TEKST: bezpieczne wpisywanie (cudzysłów pojedynczy po stronie urządzenia, spacja -> %s). Tekst nie jest kodem.
type_text() {
  guard || return 1
  local t="$1" q
  t="${t// /%s}"; q="${t//\'/\'\\\'\'}"
  tmo "$ADB_T" "$ADB_BIN" -s "$TARGET" shell "input text '$q'" >/dev/null 2>&1
  nap 0.8
}
type_secret_checked() { # wpisuje, sprawdza pole w pamięci (przez ENVIRON, nie argv) i usuwa zrzut UI; nic nie loguje
  local want="$1" chunk i
  for _ in 1 2 3; do
    for ((i = 0; i < ${#want}; i += 30)); do chunk="${want:i:30}"; type_text "$chunk" || return 1; done
    if ui_refresh; then
      if W="$want" awk -F'\t' '$7 == ENVIRON["W"] { f = 1 } END { exit f ? 0 : 1 }' "$WORK/ui.tsv"; then ui_purge; return 0; fi
      ui_purge
    fi
    key 123 >/dev/null 2>&1                                     # KEYCODE_MOVE_END
    for ((i = 0; i < ${#want} + 5; i++)); do key 67 >/dev/null 2>&1; done   # KEYCODE_DEL
  done
  ui_purge; return 1
}

# ---------- drzewo UI: zawsze świeże; po nieudanym odświeżeniu nic nie wolno czytać ----------
ui_purge() { rm -f "$WORK/ui.xml" "$WORK/ui.tsv"; UI_OK=0; }
ui_refresh() {
  ui_purge
  sh_ "rm -f /sdcard/kt_ui.xml" || true
  sh_ "uiautomator dump /sdcard/kt_ui.xml" || return 1
  case "$SH_OUT" in *"dumped to"*) ;; *) return 1;; esac
  tmo "$ADB_T" "$ADB_BIN" -s "$TARGET" exec-out cat /sdcard/kt_ui.xml 2>/dev/null | tr -d '\r' > "$WORK/ui.xml"
  sh_ "rm -f /sdcard/kt_ui.xml" || true
  if ! grep -q '<hierarchy' "$WORK/ui.xml" || ! grep -q '</hierarchy>' "$WORK/ui.xml"; then ui_purge; return 1; fi
  ui_parse && { UI_OK=1; UI_SEQ=$((UI_SEQ + 1)); return 0; }
  ui_purge; return 1
}
# TSV: focused, clickable, x1, y1, x2, y2, text, desc, class
ui_parse() {
  sed 's/<node /\n<node /g' "$WORK/ui.xml" | LC_ALL=C awk '
    function attr(s, n,   re) { re = " " n "=\"[^\"]*\""; if (match(s, re)) return substr(s, RSTART + length(n) + 3, RLENGTH - length(n) - 4); return "" }
    function dec(s) { gsub(/&quot;/, "\"", s); gsub(/&apos;/, "\047", s); gsub(/&lt;/, "<", s); gsub(/&gt;/, ">", s); gsub(/&#10;/, " ", s); gsub(/&amp;/, "\\&", s); gsub(/\t/, " ", s); return s }
    /^<node / { b = attr($0, "bounds"); gsub(/[\[\]]/, " ", b); gsub(/,/, " ", b); split(b, p, " ")
      printf "%s\t%s\t%d\t%d\t%d\t%d\t%s\t%s\t%s\n", attr($0, "focused"), attr($0, "clickable"), p[1], p[2], p[3], p[4], dec(attr($0, "text")), dec(attr($0, "content-desc")), attr($0, "class") }' > "$WORK/ui.tsv"
  [ -s "$WORK/ui.tsv" ]
}
ui_ok() { [ "$UI_OK" -eq 1 ] && [ -s "$WORK/ui.tsv" ]; }
ui_has() { ui_ok && awk -F'\t' -v w="$1" '$7 == w || $8 == w { f = 1 } END { exit f ? 0 : 1 }' "$WORK/ui.tsv"; }
ui_has_prefix() { ui_ok && awk -F'\t' -v w="$1" 'index($7, w) == 1 || index($8, w) == 1 { f = 1 } END { exit f ? 0 : 1 }' "$WORK/ui.tsv"; }
ui_focus_count() { ui_ok || { echo 0; return; }; awk -F'\t' '$1 == "true" { n++ } END { print n + 0 }' "$WORK/ui.tsv"; }
# ui_focus_text: teksty elementów z fokusem (z potomnych węzłów mieszczących się w ramce), oddzielone znakiem |
ui_focus_text() {
  ui_ok || return 1
  LC_ALL=C awk -F'\t' '
    { f[NR] = $1; x1[NR] = $3; y1[NR] = $4; x2[NR] = $5; y2[NR] = $6; l[NR] = ($7 != "" ? $7 : $8) }
    END { out = ""
      for (i = 1; i <= NR; i++) if (f[i] == "true") { parts = ""
        for (j = 1; j <= NR; j++) if (l[j] != "" && x1[j] >= x1[i] && x2[j] <= x2[i] && y1[j] >= y1[i] && y2[j] <= y2[i]) parts = parts (parts == "" ? "" : " ") l[j]
        out = out (out == "" ? "" : "|") (parts != "" ? parts : l[i]) }
      print out }' "$WORK/ui.tsv"
}
focus_is() { local t; t=$(ui_focus_text) || return 1; case "$t" in *"$1"*) return 0;; esac; return 1; }
focus_eq() { local t; t=$(ui_focus_text) || return 1; [ "$t" = "$1" ]; }
ui_cards() { # liczba dużych klikalnych kart (plakaty): wysokość >= 18% ekranu
  ui_ok || { echo 0; return; }
  awk -F'\t' -v h="$SCREEN_H" '$2 == "true" && ($6 - $4) >= h * 0.18 { n++ } END { print n + 0 }' "$WORK/ui.tsv"
}
ui_first_card_label() { # teksty potomków pierwszej dużej karty (tytuł bywa w węźle potomnym), tylko w pamięci
  ui_ok || return 1
  LC_ALL=C awk -F'\t' -v h="$SCREEN_H" '
    { c[NR] = $2; x1[NR] = $3; y1[NR] = $4; x2[NR] = $5; y2[NR] = $6; l[NR] = ($7 != "" ? $7 : $8) }
    END { for (i = 1; i <= NR; i++) if (c[i] == "true" && (y2[i] - y1[i]) >= h * 0.18) {
        for (j = 1; j <= NR; j++) if (l[j] != "" && x1[j] >= x1[i] && x2[j] <= x2[i] && y1[j] >= y1[i] && y2[j] <= y2[i]) out = out (out == "" ? "" : ", ") l[j]
        print out; exit } }' "$WORK/ui.tsv"
}
tap_label() { # TYLKO konfiguracja (dotyk), zawsze oznaczane w raporcie
  ui_ok || return 1
  local b
  b=$(awk -F'\t' -v w="$1" '$7 == w || $8 == w { print int(($3 + $5) / 2), int(($4 + $6) / 2); exit }' "$WORK/ui.tsv")
  [ -n "$b" ] || return 1
  guard || return 1
  SC_TOUCH=1
  # shellcheck disable=SC2086
  tmo "$ADB_T" "$ADB_BIN" -s "$TARGET" shell input tap $b >/dev/null 2>&1; nap 1.2
}
wait_ui() { # wait_ui SEKUNDY warunek...: świeże drzewo przy każdej próbie, ograniczony czas
  local t="$1" end tries=0 max=1000000; shift
  end=$((SECONDS + t))
  [ "${NAP_SCALE:-1}" = 0 ] && max=2   # testy offline: ograniczona liczba prób zamiast czekania zegarem
  while [ "$SECONDS" -lt "$end" ] && [ "$tries" -lt "$max" ]; do
    tries=$((tries + 1))
    [ "$ABORT" -eq 1 ] && return 2
    if ui_refresh && "$@"; then return 0; fi
    nap 1
  done
  return 1
}

diag_save() { # tylko lokalnie, na jawne żądanie; nigdy podczas formularza z adresem; nigdy do ZIP
  [ "$DIAG_LOCAL" -eq 1 ] && [ "$FORM_ACTIVE" -eq 0 ] || return 0
  [ -f "$WORK/ui.xml" ] && cp "$WORK/ui.xml" "$OUT/lokalne/$1.xml"
  tmo 20 "$ADB_BIN" -s "$TARGET" exec-out screencap -p > "$OUT/lokalne/$1.png" 2>/dev/null
  return 0
}

# ---------- wyniki scenariuszy ----------
scn_begin() { SC_ID="$1"; SC_NAME="$2"; SC_STEPS=""; SC_TOUCH=0; }
st() { SC_STEPS+="${SC_STEPS:+$'\n'}$(sanitize_str "$*")"; }
evid() { mkdir -p "$WORK/dowody"; sanitize_str "$*" >> "$WORK/dowody/$SC_ID.txt"; printf '\n' >> "$WORK/dowody/$SC_ID.txt"; }
scn_end() { # STATUS KOD "oczekiwano" "rzeczywiście"
  local ev=""
  [ -s "$WORK/dowody/$SC_ID.txt" ] && ev="dowody/$SC_ID.txt"
  R_ID+=("$SC_ID"); R_NAME+=("$SC_NAME"); R_STATUS+=("$1"); R_CODE+=("$2")
  R_STEPS+=("$SC_STEPS"); R_EXP+=("$(sanitize_str "$3")"); R_ACT+=("$(sanitize_str "$4")"); R_EVID+=("$ev"); R_TOUCH+=("$SC_TOUCH")
  say "[$1] $SC_ID $SC_NAME ${2:+($2)}"
  case "$1" in BLOCKED) [ "$2" = ADB_LOST ] && abort_run ADB_LOST;; esac
}
quick() { scn_begin "$1" "$2"; scn_end "$3" "$4" "$5" "$6"; }  # scenariusz bez kroków
skip_rest() { # pomija wszystkie jeszcze nieuruchomione scenariusze o podanych ID
  local id
  for id in "$@"; do quick "$id" "$(scn_title "$id")" SKIPPED "$ABORT_CODE" "uruchomienie scenariusza" "pominięto: ${ABORT_CODE:-przerwanie}"; done
}
scn_title() {
  case "$1" in
    S00) echo "Środowisko i połączenie ADB";; S01) echo "APK i instalacja";; S02) echo "Start aplikacji";;
    S03) echo "Import dużej listy M3U";; S04) echo "Sekcje (D-pad)";; S05) echo "Szukaj";; S06) echo "Moja lista: pusta";;
    S07) echo "Ulubione: dodanie, trwałość, usunięcie";; S08) echo "Odtwarzanie próbki";; S09) echo "Test pilota (ręczny)";;
    S10) echo "Awarie i restarty";; S11) echo "Stres strzałkami";; S12) echo "Logi aplikacji w paczce";; *) echo "$1";;
  esac
}

# ---------- konfiguracja i wejście ----------
valid_host() { [[ "$1" =~ ^[A-Za-z0-9._-]{1,253}$ ]]; }
load_config() { # parser, NIE source: nic z pliku nie jest wykonywane; M3U_URL i XTREAM_* są świadomie ignorowane
  local f="$1" line key val
  [ -f "$f" ] || return 0
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in ''|'#'*) continue;; esac
    line="${line#export }"; key="${line%%=*}"; val="${line#*=}"
    [ "$key" = "$line" ] && continue
    val="${val%\"}"; val="${val#\"}"; val="${val%\'}"; val="${val#\'}"
    case "$key" in
      DEVICE_IP) valid_host "$val" && CFG_DEVICE_IP="$val";;
      PORT) case "$val" in ''|*[!0-9]*) ;; *) CFG_PORT="$val";; esac;;
      ADB_TARGET) [[ "$val" =~ ^[A-Za-z0-9._:-]{1,260}$ ]] && CFG_TARGET="$val";;
      M3U_URL|XTREAM_*) CFG_IGNORED+="${CFG_IGNORED:+, }$key";;
    esac
  done < "$f"
}
usage() {
  cat <<'EOF'
Użycie: ketchup_tvbox_test.sh [opcje] "ścieżka/do/pliku.apk"
  --lista-plik PLIK   pierwsza linia pliku = adres listy M3U (domyślnie ukryty monit)
  --bez-listy         pomiń scenariusze wymagające listy
  --reset-dane        DESTRUKCYJNE: wyczyść dane aplikacji (wymaga ręcznego potwierdzenia)
  --test-pilota       opcjonalny ręczny test pilota (5 naciśnięć strzałki w dół)
  --stres N           opcjonalnie N losowych STRZAŁEK (bez OK i BACK), odtwarzalne z --ziarno
  --ziarno S          ziarno generatora dla --stres (domyślnie 1)
  --diag-lokalne      zapisz lokalnie zrzuty ekranu i drzewa UI (NIGDY do ZIP; nigdy formularza z adresem)
  --urzadzenie H:P    adres urządzenia (zamiast konfiguracji)
  --sha-kodu SHA      SHA kodu z notatek wydania (zapisane jako podane, niezweryfikowane)
EOF
}
parse_args() {
  local pos=()
  while [ $# -gt 0 ]; do
    case "$1" in
      -h|--help) usage; exit 0;;
      --lista-plik) LIST_FILE="${2:-}"; shift;;
      --bez-listy) NO_LIST=1;;
      --reset-dane) RESET_DATA=1;;
      --test-pilota) PILOT=1;;
      --stres) STRESS_N="${2:-}"; shift;;
      --ziarno) SEED="${2:-}"; shift;;
      --diag-lokalne) DIAG_LOCAL=1;;
      --urzadzenie) TARGET_ARG="${2:-}"; shift;;
      --sha-kodu) USER_CODE_SHA="${2:-}"; shift;;
      --) shift; while [ $# -gt 0 ]; do pos+=("$1"); shift; done; break;;
      -*) say "Nieznana opcja: $1"; usage >&2; exit 2;;
      *) pos+=("$1");;
    esac
    shift
  done
  case "$STRESS_N" in ''|*[!0-9]*) say "--stres wymaga liczby"; exit 2;; esac
  case "$SEED" in ''|*[!0-9]*) say "--ziarno wymaga liczby"; exit 2;; esac
  [ "$STRESS_N" -gt 1000 ] && { say "--stres: maksymalnie 1000"; exit 2; }
  case "$USER_CODE_SHA" in ''|*[!0-9a-fA-F]*) [ -n "$USER_CODE_SHA" ] && { say "--sha-kodu: tylko znaki szesnastkowe"; exit 2; };; esac
  if [ "${#pos[@]}" -eq 0 ]; then say "Brak ścieżki do pliku APK."; usage >&2; exit 2; fi
  APK="${pos[0]}"
  if [ "${#pos[@]}" -gt 1 ]; then  # ścieżka ze spacjami podana bez cudzysłowu: skleja tylko, gdy taki plik istnieje
    APK="${pos[*]}"
    [ -f "$APK" ] || { say "Podano wiele argumentów. Ścieżkę ze spacjami wpisz w cudzysłowie."; exit 2; }
  fi
}
preflight_tools() {
  local missing="" t hint
  for t in "$ADB_BIN" tmo sha256sum unzip zip awk sed grep tr date mktemp stat base64 head sort cut; do
    command -v "$t" >/dev/null 2>&1 || missing+=" $t"
  done
  AAPT=""
  for t in aapt2 aapt; do command -v "$t" >/dev/null 2>&1 && { AAPT="$t"; break; }; done
  [ -z "$AAPT" ] && missing+=" aapt"
  if [ -n "$missing" ]; then
    hint="pkg install android-tools coreutils unzip zip gawk sed grep aapt"
    say "BLOCKED: brakuje narzędzi:$missing"
    say "Doinstaluj samodzielnie w Termuxie: $hint (skrypt niczego nie instaluje)."
    return 1
  fi
  return 0
}
read_list_url() {
  [ "$NO_LIST" -eq 1 ] && return 1
  local u=""
  if [ -n "$LIST_FILE" ]; then
    [ -f "$LIST_FILE" ] || { say "Plik listy nie istnieje."; return 1; }
    u=$(head -n1 "$LIST_FILE" | tr -d '\r')
    local perm; perm=$(stat -c %a "$LIST_FILE" 2>/dev/null || echo "")
    case "$perm" in ''|*00) ;; *) say "Uwaga: plik z adresem ma szerokie uprawnienia ($perm); zalecane chmod 600.";; esac
  elif [ -t 0 ] && [ -r /dev/tty ]; then
    printf 'Adres listy M3U (wpisywanie ukryte, Enter bez treści = pomiń import): ' > /dev/tty
    IFS= read -rs u < /dev/tty; printf '\n' > /dev/tty
  else
    say "Adres listy nie został podany (brak terminala i --lista-plik): scenariusze z listą zostaną pominięte."
    return 1
  fi
  [ -n "$u" ] || { say "Pusty adres listy: scenariusze z listą zostaną pominięte."; return 1; }
  [[ "$u" =~ ^https?://[^[:space:]]+$ ]] || { say "Adres listy ma niepoprawną postać (oczekiwano http:// lub https://)."; return 1; }
  LIST_URL="$u"; return 0
}

# ---------- przygotowanie, zakończenie, sprzątanie ----------
init_workspace() {
  local base ts
  umask 077
  WORK=$(mktemp -d "${TMPDIR:-/tmp}/ketchup.XXXXXX") || { say "Nie można utworzyć katalogu roboczego."; exit 3; }
  chmod 700 "$WORK"; mkdir -p "$WORK/dowody" "$WORK/logi"
  SECRETS_FILE="$WORK/secrets"; : > "$SECRETS_FILE"
  base="${KETCHUP_OUT_BASE:-/storage/emulated/0/Download/KetchupPlayerTest}"
  [ -d "$(dirname "$base")" ] || base="$HOME/KetchupPlayerTest"
  ts=$(date '+%Y%m%d_%H%M%S')
  OUT="$base/$ts"; mkdir -p "$OUT" || { say "Brak dostępu do $base"; exit 3; }
  [ "$DIAG_LOCAL" -eq 1 ] && mkdir -p "$OUT/lokalne"
}
on_signal() { INTERRUPTED=1; abort_run INTERRUPTED; exit 130; }
cleanup_work() { [ -n "$WORK" ] && [ -d "$WORK" ] && rm -rf "$WORK"; }

pick_target() {
  local dev n
  if [ -n "$TARGET_ARG" ]; then TARGET="$TARGET_ARG"
  elif [ -n "$CFG_TARGET" ]; then TARGET="$CFG_TARGET"
  elif [ -n "$CFG_DEVICE_IP" ]; then TARGET="$CFG_DEVICE_IP:${CFG_PORT:-5555}"
  else
    dev=$(tmo 10 "$ADB_BIN" devices 2>/dev/null | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')
    n=$(printf '%s\n' "$dev" | grep -c .)
    if [ "$n" -eq 1 ]; then TARGET="$dev"
    elif [ -t 0 ] && [ -r /dev/tty ]; then
      printf 'Adres urządzenia (host:port): ' > /dev/tty; IFS= read -r TARGET < /dev/tty
    fi
  fi
  [[ "$TARGET" =~ ^[A-Za-z0-9._:-]{1,260}$ ]] || { TARGET=""; return 1; }
  return 0
}

# ---------- S00 środowisko ----------
scn_env() {
  scn_begin S00 "$(scn_title S00)"
  st "wybór urządzenia z konfiguracji lub jedynego podłączonego"
  if ! pick_target; then scn_end BLOCKED NO_DEVICE "jednoznaczne urządzenie ADB" "brak konfiguracji i niejednoznaczny wybór"; return 1; fi
  case "$TARGET" in *:*) tmo 12 "$ADB_BIN" connect "$TARGET" >/dev/null 2>&1;; esac
  local st_; st_=$(tmo 8 "$ADB_BIN" -s "$TARGET" get-state 2>&1 | tr -d '\r' | head -n1)
  case "$st_" in
    device) ;;
    unauthorized) scn_end BLOCKED ADB_UNAUTHORIZED "urządzenie autoryzowane" "ADB nieautoryzowany: zatwierdź klucz na boxie (skrypt nie obchodzi parowania)"; return 1;;
    *) scn_end BLOCKED ADB_UNAVAILABLE "stan device" "stan ADB: ${st_:-brak odpowiedzi}"; return 1;;
  esac
  sh_ "am get-current-user"; USR=$(printf '%s' "$SH_OUT" | tr -dc '0-9'); USR="${USR:-0}"
  sh_ "getprop ro.product.model"; DEV_MODEL="$SH_OUT"
  sh_ "getprop ro.build.version.release"; DEV_ANDROID="$SH_OUT"
  sh_ "wm size"; DEV_SCREEN=$(printf '%s' "$SH_OUT" | grep -o '[0-9]*x[0-9]*' | tail -n1)
  [ -n "$DEV_SCREEN" ] && SCREEN_H="${DEV_SCREEN#*x}"
  START_DEVICE_DATE=""; sh_ "date '+%F %T'"; START_DEVICE_DATE="$SH_OUT"
  st "odczyt użytkownika Androida, modelu, wersji i rozdzielczości"
  evid "model=$DEV_MODEL android=$DEV_ANDROID ekran=$DEV_SCREEN użytkownik=$USR"
  scn_end PASS "" "połączenie ADB i dane urządzenia" "model $DEV_MODEL, Android $DEV_ANDROID, ekran ${DEV_SCREEN:-?}, użytkownik $USR"
}

# ---------- S01 APK i instalacja ----------
apk_meta() {
  local badge
  badge=$("$AAPT" dump badging "$APK" 2>/dev/null) || return 1
  PKG=$(printf '%s\n' "$badge" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -n1)
  APK_CODE=$(printf '%s\n' "$badge" | sed -n "s/^package:.* versionCode='\([^']*\)'.*/\1/p" | head -n1)
  APK_VER=$(printf '%s\n' "$badge" | sed -n "s/^package:.* versionName='\([^']*\)'.*/\1/p" | head -n1)
  ACT=$(printf '%s\n' "$badge" | sed -n "s/^launchable-activity: name='\([^']*\)'.*/\1/p" | head -n1)
  [ -n "$PKG" ] && [ -n "$ACT" ]
}
scn_install() {
  scn_begin S01 "$(scn_title S01)"
  st "sprawdzenie pliku APK (istnienie, rozmiar, integralność ZIP)"
  if [ ! -f "$APK" ] || [ ! -r "$APK" ]; then scn_end BLOCKED APK_MISSING "istniejący plik APK" "plik nie istnieje albo nie jest czytelny"; return 1; fi
  case "$APK" in *.apk|*.APK) ;; *) scn_end BLOCKED APK_BAD_EXT "plik .apk" "inne rozszerzenie"; return 1;; esac
  if [ "$(stat -c %s "$APK" 2>/dev/null || echo 0)" -le 0 ] || ! unzip -tq "$APK" >/dev/null 2>&1; then
    scn_end BLOCKED APK_CORRUPT "poprawne archiwum APK" "plik pusty lub uszkodzony"; return 1; fi
  APK_SHA=$(sha256sum "$APK" | awk '{print $1}')
  st "odczyt pakietu, wersji i aktywności ze wskazanego APK (aapt)"
  if ! apk_meta; then scn_end BLOCKED APK_META_UNKNOWN "pakiet i aktywność z APK" "aapt nie odczytał metadanych"; return 1; fi
  case "$PKG" in "$PKG_RELEASE"|"$PKG_DEBUG") ;; *) scn_end BLOCKED WRONG_PACKAGE "pakiet $PKG_RELEASE lub $PKG_DEBUG" "APK ma pakiet $PKG"; return 1;; esac
  evid "apk=$(basename "$APK") sha256=$APK_SHA pakiet=$PKG wersja=$APK_VER kod=$APK_CODE aktywnosc=$ACT"
  st "instalacja adb install -r --user $USR (bez obniżania wersji, bez odinstalowywania)"
  local out code
  out=$(tmo "${INSTALL_TIMEOUT:-300}" "$ADB_BIN" -s "$TARGET" install -r --user "$USR" "$APK" 2>&1 | tr -d '\r'); local rc=$?
  if [ "$rc" -eq 124 ]; then scn_end BLOCKED INSTALL_TIMEOUT "instalacja zakończona" "limit czasu instalacji"; return 1; fi
  if ! printf '%s' "$out" | grep -q 'Success'; then
    code=$(printf '%s' "$out" | grep -o 'INSTALL_[A-Z_]*' | head -n1)
    scn_end BLOCKED "INSTALL_FAILED${code:+:$code}" "instalacja Success" "adb install nie powiódł się ${code:+($code)}; dane i wersja zainstalowana bez zmian"; return 1
  fi
  st "potwierdzenie pakietu i wersji po instalacji (użytkownik $USR)"
  sh_ "pm list packages --user $USR"
  if ! printf '%s\n' "$SH_OUT" | grep -qx "package:$PKG"; then scn_end BLOCKED NOT_INSTALLED "pakiet na liście użytkownika $USR" "pakiet nie występuje po instalacji"; return 1; fi
  sh_ "dumpsys package $PKG"
  local iv ic
  iv=$(printf '%s\n' "$SH_OUT" | sed -n 's/^ *versionName=\(.*\)$/\1/p' | head -n1)
  ic=$(printf '%s\n' "$SH_OUT" | sed -n 's/^ *versionCode=\([0-9]*\).*/\1/p' | head -n1)
  if [ "$iv" != "$APK_VER" ] || [ "$ic" != "$APK_CODE" ]; then
    scn_end BLOCKED VERSION_MISMATCH "wersja $APK_VER ($APK_CODE)" "zainstalowano ${iv:-?} (${ic:-?})"; return 1; fi
  scn_end PASS "" "pakiet $PKG, wersja $APK_VER ($APK_CODE) zainstalowane" "potwierdzono po instalacji; SHA-256 w dowodach"
}

# ---------- S02 start ----------
app_pid() { sh_ "pidof $PKG"; printf '%s' "$SH_OUT" | awk '{print $1}'; }
screen_state() { # echo: WIZARD | HOME | UNKNOWN
  if ui_has "Witaj w Ketchup Player"; then echo WIZARD
  elif ui_has "Home" && ui_has "Ustawienia"; then echo HOME
  else echo UNKNOWN; fi
}
scn_start() {
  scn_begin S02 "$(scn_title S02)"
  if [ "$RESET_DATA" -eq 1 ]; then
    st "reset danych aplikacji (tryb destrukcyjny, ręczne potwierdzenie)"
    local ans=""
    if [ -t 0 ] && [ -r /dev/tty ]; then
      printf 'To USUNIE dane aplikacji %s (listy, ulubione). Wpisz dokładnie: USUN DANE %s : ' "$PKG" "$PKG" > /dev/tty
      IFS= read -r ans < /dev/tty
    fi
    if [ "$ans" != "USUN DANE $PKG" ]; then scn_end BLOCKED RESET_NOT_CONFIRMED "ręczne potwierdzenie resetu" "brak potwierdzenia: danych nie ruszono"; return 1; fi
    sh_ "pm clear --user $USR $PKG"
  fi
  st "uruchomienie $PKG/$ACT i oczekiwanie na pierwszy plan"
  sh_ "am start --user $USR -n $PKG/$ACT"
  local i ok=0
  for ((i = 0; i < ${START_WAIT_S:-40}; i++)); do fg_check && { ok=1; break; }; nap 1; done
  if [ "$ok" -ne 1 ]; then scn_end FAIL START_TIMEOUT "aplikacja na pierwszym planie w ${START_WAIT_S:-40} s" "okno aplikacji nie pojawiło się"; return 1; fi
  START_PID=$(app_pid)
  nap 4
  ui_refresh || { scn_end BLOCKED UI_UNAVAILABLE "drzewo UI" "uiautomator nie zwrócił drzewa"; return 1; }
  local s; s=$(screen_state)
  evid "stan=$s pid=${START_PID:-?}"
  if [ "$s" = UNKNOWN ]; then scn_end MANUAL_REQUIRED UNKNOWN_SCREEN "kreator albo Home z szyną" "nierozpoznany ekran startowy"; return 0; fi
  SCREEN_AT_START="$s"
  scn_end PASS "" "ekran startowy rozpoznany" "stan: $s, pid ${START_PID:-?}"
}
SCREEN_AT_START=""

# ---------- nawigacja pilotem po szynie ----------
rail_focused() { local t; t=$(ui_focus_text) || return 1; local l; for l in "${RAIL_LABELS[@]}"; do [ "$t" = "$l" ] && return 0; done; return 1; }
# goto_rail LABEL: LEWO do szyny, GÓRA na początek, DÓŁ do pozycji, OK. Kody: 0 ok, 1 nieosiągnięte (fokus widać),
# 3 fokusu nie widać (brak obserwowalności), 2 przerwanie.
goto_rail() {
  local label="$1" i
  keys "$KEY_LEFT" || return 2
  for ((i = 0; i < 6; i++)); do
    ui_refresh || return 3
    if [ "$(ui_focus_count)" -eq 0 ]; then return 3; fi
    rail_focused && break
    key "$KEY_LEFT" || return 2
  done
  rail_focused || return 1
  nap 0.8
  for ((i = 0; i < 9; i++)); do key "$KEY_UP" || return 2; done
  for ((i = 0; i < 9; i++)); do
    ui_refresh || return 3
    focus_eq "$label" && { key "$KEY_OK" || return 2; return 0; }
    key "$KEY_DOWN" || return 2
  done
  return 1
}
nav_status() { # tłumaczy kod goto_rail na status
  case "$1" in 0) echo "";; 1) echo "FAIL NAV_UNREACHABLE";; 3) echo "BLOCKED NOT_OBSERVABLE";; *) echo "BLOCKED ${ABORT_CODE:-ABORTED}";; esac
}

# ---------- S03 import ----------
scn_import() {
  scn_begin S03 "$(scn_title S03)"
  if [ -z "$LIST_URL" ]; then scn_end SKIPPED NO_LIST_URL "import listy" "adres listy nie został podany"; return 0; fi
  local name t0 s ch="" end p res
  name="RC-test-$(date '+%H%M%S')"
  IMPORT_NAME="$name"
  st "dodanie dostawcy '$name' z adresem z ukrytego wejścia (wpisywanie przez ADB: krok konfiguracji, nie test pilota)"
  FORM_ACTIVE=1
  if [ "$SCREEN_AT_START" = WIZARD ]; then
    key "$KEY_OK" || { FORM_ACTIVE=0; scn_end BLOCKED "${ABORT_CODE:-ABORTED}" "kreator" "przerwanie"; return 1; }
    if ! wait_ui 15 ui_has_prefix "Dodaj pierwszą listę"; then FORM_ACTIVE=0; scn_end BLOCKED WIZARD_STEP_MISSING "krok 2 kreatora" "nie widać formularza listy"; return 1; fi
  else
    goto_rail "Ustawienia"; res=$?
    if [ "$res" -ne 0 ]; then FORM_ACTIVE=0; p=$(nav_status "$res"); scn_end "${p% *}" "${p#* }" "otwarcie Ustawień pilotem" "kod nawigacji $res"; return 1; fi
    if ! wait_ui 15 ui_has "Listy i dostawcy"; then FORM_ACTIVE=0; scn_end BLOCKED SETTINGS_NOT_SHOWN "panel Ustawień" "brak kategorii Listy i dostawcy"; return 1; fi
    key "$KEY_OK"; wait_ui 10 ui_has "Dodaj dostawcę" || true
    key "$KEY_RIGHT"
    local i added=0
    for ((i = 0; i < 8; i++)); do
      ui_refresh && focus_is "Dodaj dostawcę" && { key "$KEY_OK"; added=1; break; }
      key "$KEY_DOWN" || break
    done
    if [ "$added" -ne 1 ]; then FORM_ACTIVE=0; scn_end BLOCKED NOT_OBSERVABLE "przycisk Dodaj dostawcę osiągalny pilotem" "nie ustalono fokusu na przycisku"; return 1; fi
    nap 1
  fi
  type_text "$name" || { FORM_ACTIVE=0; scn_end BLOCKED "${ABORT_CODE:-ABORTED}" "nazwa" "przerwanie"; return 1; }
  key "$KEY_ENTER"
  if ! type_secret_checked "$LIST_URL"; then FORM_ACTIVE=0; scn_end BLOCKED INPUT_MISMATCH "pole adresu zgodne z wpisanym" "pole nie zawiera wpisanego adresu po 3 próbach"; return 1; fi
  key "$KEY_ENTER"; FORM_ACTIVE=0
  ui_purge
  st "oczekiwanie na wynik importu (limit ${IMPORT_WAIT_S:-900} s)"
  t0=$SECONDS; end=$((SECONDS + ${IMPORT_WAIT_S:-900}))
  while [ "$SECONDS" -lt "$end" ]; do
    [ "$ABORT" -eq 1 ] && { scn_end BLOCKED "$ABORT_CODE" "import zakończony" "przerwano po $((SECONDS - t0)) s"; return 1; }
    if [ -z "$(app_pid)" ]; then
      scn_end FAIL APP_CRASH "aplikacja żyje podczas importu" "proces zniknął po $((SECONDS - t0)) s"; return 1; fi
    if ui_refresh; then
      if ui_has_prefix "Lista została dodana" || ui_has_prefix "Lista zapisana"; then
        ch=$(awk -F'\t' '{ if (match($7 $8, /Kanały: [0-9]+/)) { print substr($7 $8, RSTART + 8, RLENGTH - 8); exit } }' "$WORK/ui.tsv")
        evid "import sekund=$((SECONDS - t0)) kanaly=${ch:-?}"
        scn_end PASS "" "import zakończony sukcesem" "lista dodana w $((SECONDS - t0)) s, kanały: ${ch:-nieodczytane}"; return 0
      fi
      if ui_has_prefix "Nie udało się" || ui_has_prefix "Nie można" || ui_has_prefix "Błąd"; then
        sh_ "ping -c 1 -W 3 1.1.1.1"
        case "$SH_OUT" in *" 0% packet loss"*) scn_end BLOCKED SOURCE_ERROR "import" "aplikacja zgłasza błąd źródła; sieć urządzenia działa: źródło lub lista, nie dowód błędu aplikacji";;
          *) scn_end BLOCKED NETWORK_DOWN "import" "brak łączności urządzenia";; esac
        return 1
      fi
    fi
    nap "${IMPORT_POLL_S:-15}"
  done
  scn_end FAIL IMPORT_TIMEOUT "import w ${IMPORT_WAIT_S:-900} s" "brak wyniku w limicie czasu (wolne urządzenie lub zawieszenie)"
}
IMPORT_NAME=""

# ---------- S04 sekcje ----------
scn_sections() {
  scn_begin S04 "$(scn_title S04)"
  local pair label marker res p bad="" ok=0 total=0
  for pair in "Na żywo|Przewodnik" "Filmy|Najnowsze" "Seriale|Najnowsze" "Moja lista|Moja lista" "Szukaj|Szukaj" "Ustawienia|Listy i dostawcy"; do
    label="${pair%%|*}"; marker="${pair#*|}"; total=$((total + 1))
    st "D-pad: szyna do '$label', OK, oczekiwany ekran ze znacznikiem '$marker'"
    goto_rail "$label"; res=$?
    if [ "$res" -eq 3 ]; then scn_end BLOCKED NOT_OBSERVABLE "nawigacja do wszystkich sekcji" "fokusu nie widać w drzewie UI (sekcja $label)"; return 0; fi
    if [ "$res" -ne 0 ]; then
      [ "$res" -eq 2 ] && { scn_end BLOCKED "${ABORT_CODE:-ABORTED}" "nawigacja" "przerwano"; return 1; }
      bad+="${bad:+; }$label: nieosiągalna pilotem"; continue; fi
    if wait_ui 20 ui_has "$marker"; then ok=$((ok + 1)); diag_save "sekcja_${ok}"; else bad+="${bad:+; }$label: brak znacznika"; fi
    key "$KEY_BACK" >/dev/null 2>&1
    [ -z "$(app_pid)" ] && { scn_end FAIL APP_CRASH "aplikacja żyje" "proces zniknął w sekcji $label"; return 1; }
  done
  evid "sekcje ok=$ok z $total"
  if [ "$ok" -eq "$total" ]; then scn_end PASS "" "wszystkie sekcje osiągalne pilotem" "$ok z $total"
  else scn_end FAIL SECTION_FAILED "wszystkie sekcje osiągalne pilotem" "$bad"; fi
}

# ---------- S05 Szukaj ----------
ascii_query() { # z tytułu bierze pierwsze ASCII-słowo >= 4 liter (w pamięci; tytułów nie zapisujemy)
  local t="$1"; t="${t#[A-Z], }"
  printf '%s' "$t" | LC_ALL=C grep -oE '[A-Za-z]{4,}' | head -n1
}
scn_search() {
  scn_begin S05 "$(scn_title S05)"
  local res p before after q
  st "D-pad: szyna do 'Szukaj', OK"
  goto_rail "Szukaj"; res=$?
  if [ "$res" -ne 0 ]; then p=$(nav_status "$res"); scn_end "${p% *}" "${p#* }" "otwarcie Szukaj pilotem" "kod nawigacji $res"; return 0; fi
  wait_ui 20 ui_has_prefix "Szukaj filmów i seriali" || true
  nap 2; ui_refresh
  before=$(ui_cards)
  if [ "$before" -lt 1 ]; then scn_end SKIPPED NO_CATALOG "katalog w Szukaj" "brak kart (pusty katalog)"; return 0; fi
  q=$(ascii_query "$(ui_first_card_label)")
  if [ -z "$q" ]; then scn_end SKIPPED NO_ASCII_QUERY "fraza z tytułu" "brak słowa ASCII o długości co najmniej 4"; return 0; fi
  st "GÓRA do pola, wpisanie frazy przez ADB (IME: krok pomocniczy), liczenie kart przed i po"
  key "$KEY_UP"; ui_refresh
  if ! { awk -F'\t' '$1 == "true" && $9 ~ /EditText/ { f = 1 } END { exit f ? 0 : 1 }' "$WORK/ui.tsv"; }; then
    if [ "$(ui_focus_count)" -eq 0 ]; then scn_end BLOCKED NOT_OBSERVABLE "fokus w polu wyszukiwania" "fokusu nie widać w drzewie UI"
    else scn_end FAIL NAV_UNREACHABLE "GÓRA wchodzi w pole wyszukiwania" "pole nie dostało fokusu"; fi
    return 0; fi
  type_text "$q"; nap 2; ui_refresh; after=$(ui_cards)
  evid "karty przed=$before po=$after"
  if [ "$after" -lt 1 ] || [ "$after" -gt "$before" ]; then
    scn_end FAIL SEARCH_NO_RESULTS "fraza z istniejącego tytułu zwraca wynik" "karty przed $before, po $after"; return 0; fi
  st "fraza bez wyników i BACK z pola"
  type_text "zzzqqq"; nap 2; ui_refresh
  local none=0; ui_has_prefix "Brak wyników dla" && none=1
  key "$KEY_BACK"; ui_refresh
  local back_ok=0; rail_focused && back_ok=1
  if [ "$none" -eq 1 ] && [ "$back_ok" -eq 1 ]; then scn_end PASS "" "wyniki zawężone, brak wyników zgłoszony, BACK wraca na szynę" "karty $before do $after, komunikat tak, BACK na szynę tak"
  else scn_end FAIL SEARCH_BEHAVIOR "komunikat braku wyników i BACK na szynę" "komunikat: $none, BACK na szynę: $back_ok"; fi
}

# ---------- S06 i S07 Moja lista ----------
MYLIST_COUNT0=-1; FAV_CARD_INDEX=-1
open_first_card_details() { # w Filmach: RIGHT do kart, n-te (od 0) kafelek, OK; 0 gdy widać szczegóły
  local n="$1" i
  key "$KEY_RIGHT"; for ((i = 0; i < n; i++)); do key "$KEY_RIGHT"; done
  key "$KEY_OK"
  wait_ui 15 ui_has_prefix "▶" || return 1
}
scn_mylist_empty() {
  scn_begin S06 "$(scn_title S06)"
  local res p
  st "D-pad: szyna do 'Moja lista', OK; sprawdzenie stanu początkowego bez zmieniania danych"
  goto_rail "Moja lista"; res=$?
  if [ "$res" -ne 0 ]; then p=$(nav_status "$res"); scn_end "${p% *}" "${p#* }" "otwarcie Mojej listy pilotem" "kod nawigacji $res"; return 0; fi
  wait_ui 15 mylist_loaded || true
  if ui_has_prefix "Moja lista jest pusta"; then
    MYLIST_COUNT0=0
    key "$KEY_BACK"; ui_refresh
    if rail_focused; then scn_end PASS "" "pusty stan widoczny, BACK wraca na szynę" "stan pusty potwierdzony, BACK na szynę tak"
    else scn_end FAIL BACK_FAILED "BACK z pustej Mojej listy wraca na szynę" "fokus nie trafił na szynę"; fi
  else
    MYLIST_COUNT0=$(ui_cards)
    evid "moja lista niepusta: pozycji=$MYLIST_COUNT0 (dane użytkownika zachowane, stan pusty niesprawdzany)"
    scn_end SKIPPED NOT_EMPTY "stan pusty (tylko przy potwierdzonej pustej liście)" "lista użytkownika nie jest pusta: nie ruszam wpisów"
  fi
}
mylist_loaded() { ui_has_prefix "Moja lista jest pusta" || [ "$(ui_cards)" -ge 1 ]; }
CNT=-1
count_mylist() { # liczba kart w Mojej liście w CNT (-1 gdy nieznana); bez podpowłoki, żeby przerwanie się propagowało
  local res; CNT=-1
  goto_rail "Moja lista"; res=$?; [ "$res" -ne 0 ] && return 1
  wait_ui 15 mylist_loaded || return 1
  if ui_has_prefix "Moja lista jest pusta"; then CNT=0; else CNT=$(ui_cards); fi
}
toggle_fav_in_details() { # oczekuje otwartych szczegółów; przesuwa fokus na przycisk Moja lista i naciska OK
  local i
  for ((i = 0; i < 8; i++)); do
    ui_refresh || return 1
    if focus_is "Moja lista"; then key "$KEY_OK"; return 0; fi
    key "$KEY_RIGHT"
  done
  return 1
}
scn_favorites() {
  scn_begin S07 "$(scn_title S07)"
  local res p n k=0 added=0 c1 c2 c3
  [ "$MYLIST_COUNT0" -lt 0 ] && { count_mylist; MYLIST_COUNT0=$CNT; }
  if [ "$MYLIST_COUNT0" -lt 0 ]; then scn_end BLOCKED NOT_OBSERVABLE "liczba pozycji Mojej listy" "nie udało się ustalić stanu początkowego"; return 0; fi
  st "stan początkowy: $MYLIST_COUNT0 pozycji (dane zachowane)"
  st "D-pad: Filmy, kafelek bez ✓ Moja lista, przycisk + Moja lista, OK"
  for ((k = 0; k < 4; k++)); do
    goto_rail "Filmy"; res=$?
    if [ "$res" -ne 0 ]; then p=$(nav_status "$res"); scn_end "${p% *}" "${p#* }" "otwarcie Filmów pilotem" "kod nawigacji $res"; return 0; fi
    wait_ui 20 ui_has "Najnowsze" || true; nap 2
    open_first_card_details "$k" || { scn_end SKIPPED NO_CANDIDATE "szczegóły filmu" "nie udało się otworzyć kafelka $k"; return 0; }
    if ui_has "✓ Moja lista"; then key "$KEY_BACK"; continue; fi
    if toggle_fav_in_details; then added=1; FAV_CARD_INDEX=$k; wait_ui 10 ui_has "✓ Moja lista" || added=0; fi
    break
  done
  if [ "$added" -ne 1 ]; then scn_end SKIPPED NO_CANDIDATE "film do dodania" "brak kandydata lub przycisku osiągalnego pilotem; nic nie zmieniono"; return 0; fi
  count_mylist; c1=$CNT
  st "restart aplikacji (force-stop) i ponowne sprawdzenie Mojej listy"
  sh_ "am force-stop $PKG"; EXPECTED_RESTARTS=$((EXPECTED_RESTARTS + 1)); nap 2
  sh_ "am start --user $USR -n $PKG/$ACT"; nap 6
  wait_ui 40 ui_has "Home" || true
  count_mylist; c2=$CNT
  st "sprzątanie: usunięcie tylko wpisu dodanego przez ten przebieg (kafelek $FAV_CARD_INDEX w Filmach)"
  goto_rail "Filmy"; res=$?
  if [ "$res" -eq 0 ]; then wait_ui 20 ui_has "Najnowsze" || true; nap 2
    if open_first_card_details "$FAV_CARD_INDEX" && ui_has "✓ Moja lista" && toggle_fav_in_details; then wait_ui 10 ui_has "+ Moja lista" || true; fi
  fi
  count_mylist; c3=$CNT
  evid "moja lista: przed=$MYLIST_COUNT0 po dodaniu=$c1 po restarcie=$c2 po sprzataniu=$c3"
  local want=$((MYLIST_COUNT0 + 1))
  if [ "$c1" = "$want" ] && [ "$c2" = "$want" ] && [ "$c3" = "$MYLIST_COUNT0" ]; then
    scn_end PASS "" "dodanie $((MYLIST_COUNT0))->$want, trwałość po restarcie, usunięcie ->$MYLIST_COUNT0" "$MYLIST_COUNT0 -> $c1 -> restart $c2 -> $c3"
  elif [ "$c3" != "$MYLIST_COUNT0" ]; then
    scn_end MANUAL_REQUIRED CLEANUP_UNVERIFIED "stan końcowy równy początkowemu ($MYLIST_COUNT0)" "po sprzątaniu $c3 pozycji: sprawdź Moją listę ręcznie (mógł zostać wpis dodany przez test)"
  else
    scn_end FAIL FAVORITES_BEHAVIOR "dodanie $want, po restarcie $want" "po dodaniu $c1, po restarcie $c2"
  fi
}

# ---------- S08 odtwarzanie próbki ----------
audio_started() { sh_ "dumpsys audio"; printf '%s\n' "$SH_OUT" | grep -E 'AudioPlaybackConfiguration' | grep -F "pack:$PKG" | grep -q 'state:started'; }
play_evidence() { # ID etykieta: zbiera dowody po uruchomieniu odtwarzania
  local id="$1" label="$2" i started=0 alive err=0 layers
  for ((i = 0; i < ${PLAY_WAIT_S:-25}; i++)); do audio_started && { started=1; break; }; nap 1; done
  alive=0; [ -n "$(app_pid)" ] && alive=1
  ui_refresh && { ui_has_prefix "Nie udało się odtworzyć" && err=1; }
  sh_ "dumpsys SurfaceFlinger --list"; layers=$(printf '%s\n' "$SH_OUT" | grep -cF "$PKG")
  scn_begin "${id}1" "Odtwarzanie: $label (dowody)"
  evid "$label: audio_started=$started proces_zyje=$alive panel_bledu=$err warstwy_powierzchni=$layers"
  if [ "$alive" -ne 1 ]; then scn_end FAIL APP_CRASH "aplikacja żyje podczas odtwarzania" "proces zniknął"
  elif [ "$err" -eq 1 ]; then
    sh_ "ping -c 1 -W 3 1.1.1.1"
    case "$SH_OUT" in *" 0% packet loss"*) scn_end BLOCKED SOURCE_ERROR "odtwarzanie" "panel błędu aplikacji przy działającej sieci: źródło lub strumień, nie dowód błędu aplikacji";;
      *) scn_end BLOCKED NETWORK_DOWN "odtwarzanie" "panel błędu i brak łączności urządzenia";; esac
  elif [ "$started" -eq 1 ]; then scn_end PASS "" "aktywne audio aplikacji, brak panelu błędu" "audio: started, warstwy powierzchni: $layers (obraz wymaga oceny człowieka)"
  else scn_end MANUAL_REQUIRED NO_AUDIO_EVIDENCE "dowód aktywnego odtwarzania" "brak aktywnego audio w ${PLAY_WAIT_S:-25} s: oceń obraz ręcznie (strumień może być bez dźwięku)"; fi
  # BACK musi zatrzymać odtwarzanie (regresja z raportu 0.9.4)
  key "$KEY_BACK" >/dev/null 2>&1; nap 3
  local stopped=0; audio_started || stopped=1
  scn_begin "${id}2" "Odtwarzanie: $label, BACK zatrzymuje odtwarzanie"
  if [ "$started" -ne 1 ]; then scn_end SKIPPED NO_BASELINE "BACK po aktywnym odtwarzaniu" "wcześniej nie wykryto aktywnego audio"
  elif [ "$stopped" -eq 1 ]; then scn_end PASS "" "po BACK brak aktywnego audio aplikacji" "audio zatrzymane"
  else scn_end FAIL PLAYER_KEEPS_PLAYING "po BACK brak aktywnego audio aplikacji" "audio nadal aktywne po 3 s"; fi
}
scn_playback() {
  local res p
  # kanał na żywo
  scn_begin S08a "Odtwarzanie: kanał na żywo (nawigacja)"
  st "D-pad: Na żywo, PRAWO do listy kanałów, OK"
  goto_rail "Na żywo"; res=$?
  if [ "$res" -ne 0 ]; then p=$(nav_status "$res"); scn_end "${p% *}" "${p#* }" "otwarcie Na żywo pilotem" "kod nawigacji $res"
  else wait_ui 20 ui_has "Przewodnik" || true; nap 3; keys "$KEY_RIGHT" "$KEY_OK"; scn_end PASS "" "kanał uruchomiony pilotem" "wysłano PRAWO i OK"; play_evidence S08a "kanał na żywo"; fi
  [ "$ABORT" -eq 1 ] && return 1
  # film
  scn_begin S08b "Odtwarzanie: film (nawigacja)"
  st "D-pad: Filmy, pierwszy kafelek, szczegóły, OK na Oglądaj"
  goto_rail "Filmy"; res=$?
  if [ "$res" -ne 0 ]; then p=$(nav_status "$res"); scn_end "${p% *}" "${p#* }" "otwarcie Filmów pilotem" "kod nawigacji $res"
  else
    wait_ui 20 ui_has "Najnowsze" || true; nap 2
    if open_first_card_details 0; then key "$KEY_OK"; scn_end PASS "" "film uruchomiony pilotem" "szczegóły otwarte, OK na pierwszym przycisku"; play_evidence S08b "film"
    else scn_end SKIPPED NO_CONTENT "film do odtworzenia" "brak filmów lub szczegółów"; fi
  fi
  [ "$ABORT" -eq 1 ] && return 1
  # odcinek
  scn_begin S08c "Odtwarzanie: odcinek (nawigacja)"
  st "D-pad: Seriale, pierwszy kafelek, lista odcinków, OK na 1. odcinku"
  goto_rail "Seriale"; res=$?
  if [ "$res" -ne 0 ]; then p=$(nav_status "$res"); scn_end "${p% *}" "${p#* }" "otwarcie Seriali pilotem" "kod nawigacji $res"
  else
    wait_ui 20 ui_has "Najnowsze" || true; nap 2
    key "$KEY_RIGHT"; key "$KEY_OK"
    local i found=0
    for ((i = 0; i < 10; i++)); do ui_refresh && focus_is "1. " && { found=1; break; }; key "$KEY_DOWN"; done
    if [ "$found" -eq 1 ]; then key "$KEY_OK"; scn_end PASS "" "odcinek uruchomiony pilotem" "fokus na 1. odcinku, OK"; play_evidence S08c "odcinek"
    else scn_end SKIPPED NO_CONTENT "odcinek do odtworzenia" "nie znaleziono listy odcinków (brak seriali lub inny układ)"; fi
  fi
  scn_begin S08z "Odtwarzanie: ocena obrazu i dźwięku"
  scn_end MANUAL_REQUIRED VISUAL_CHECK "człowiek ocenia obraz, synchronizację i płynność" "automat nie dowodzi poprawnego obrazu; sam dźwięk, rozmiar zrzutu ani różnica klatek tego nie dowodzą"
}

# ---------- S09 test pilota (ręczny, opcjonalny) ----------
scn_pilot() {
  scn_begin S09 "$(scn_title S09)"
  if [ "$PILOT" -ne 1 ]; then scn_end SKIPPED NOT_REQUESTED "test pilota" "nie wybrano --test-pilota"; return 0; fi
  if ! { [ -t 0 ] && [ -r /dev/tty ]; }; then scn_end SKIPPED NO_TTY "test interaktywny" "brak terminala"; return 0; fi
  local ke="$WORK/getevent.txt" kg="$WORK/keyguard.txt" out line st_ code desc
  st "zapis zdarzeń sprzętu (getevent) i KeyGuard; użytkownik naciska strzałkę w dół na pilocie 5 razy"
  guard || { scn_end BLOCKED "${ABORT_CODE:-ABORTED}" "aplikacja na pierwszym planie" "przerwano"; return 1; }
  printf '>>> TEST PILOTA: wejdź w Filmy i 5 razy KRÓTKO naciśnij strzałkę w dół na PILOCIE (nie z telefonu). Masz 25 s.\n' > /dev/tty
  local n0
  sh_ "logcat -d -s KeyGuard:I"; n0=$(printf '%s\n' "$SH_OUT" | grep -c 'KG t=')
  tmo 28 "$ADB_BIN" -s "$TARGET" shell getevent -lt > "$ke" 2>/dev/null &
  nap 26
  sh_ "logcat -d -s KeyGuard:I"; printf '%s\n' "$SH_OUT" | grep 'KG t=' | tail -n +$((n0 + 1)) > "$kg"
  out=$(analyze_keys "$kg" "$ke" 5)
  while IFS= read -r line; do
    desc="${line%%|*}"; line="${line#*|}"; st_="${line%%|*}"; line="${line#*|}"; code="${line%%|*}"; line="${line#*|}"
    scn_begin "S09-$desc" "Pilot: $desc"; scn_end "$st_" "$code" "porównanie: deklaracja, sprzęt, aplikacja" "$line"
  done <<< "$out"
  scn_begin S09z "Pilot: duplikaty fizycznego pilota"
  scn_end MANUAL_REQUIRED PHYSICAL_REMOTE "ocena fizycznego pilota" "ADB nie odtwarza duplikatów pilota; wynik dotyczy tylko zdarzeń z pilota naciśniętych przez człowieka"
}

# ---------- S10 awarie, S11 stres ----------
scn_crashes() {
  scn_begin S10 "$(scn_title S10)"
  local lines pid_now bad=0 info
  st "dumpsys activity exit-info: powody zakończenia procesu po starcie testu"
  sh_ "dumpsys activity exit-info $PKG"; info="$SH_OUT"
  lines=$(printf '%s\n' "$info" | LC_ALL=C awk -v start="${START_DEVICE_DATE:-0}" '
    /timestamp=/ { match($0, /timestamp=[0-9-]+ [0-9:]+/); ts = substr($0, RSTART + 10, RLENGTH - 10) }
    /reason=[0-9]+ \((CRASH|CRASH_NATIVE|ANR|LOW_MEMORY|INITIALIZATION_FAILURE|SIGNALED)/ { if (ts >= start) print ts " " $0 }' | head -n 20)
  pid_now=$(app_pid)
  [ -n "$lines" ] && bad=1
  evid "pid_start=${START_PID:-?} pid_teraz=${pid_now:-brak} oczekiwane_restarty=$EXPECTED_RESTARTS"
  [ -n "$lines" ] && evid "exit-info (powody): $(printf '%s' "$lines" | tr '\n' ';' | cut -c1-600)"
  if [ -z "$pid_now" ]; then scn_end FAIL APP_NOT_RUNNING "aplikacja działa po testach" "proces nie działa"
  elif [ "$bad" -eq 1 ]; then scn_end MANUAL_REQUIRED EXIT_INFO_REASONS "brak crashy/ANR/OOM w exit-info" "exit-info zawiera wpisy awarii: sprawdź czas względem testu (mogą być sprzed testu)"
  else scn_end PASS "" "brak crashy i ANR w dostępnych źródłach" "exit-info czysty; brak logów nie dowodzi braku awarii (ograniczenie: logi per PID i bufor urządzenia)"; fi
}
scn_stress() {
  scn_begin S11 "$(scn_title S11)"
  if [ "$STRESS_N" -le 0 ]; then scn_end SKIPPED NOT_REQUESTED "stres strzałkami" "nie wybrano --stres"; return 0; fi
  local i x=$SEED k
  st "$STRESS_N strzałek (bez OK i BACK), ziarno $SEED"
  for ((i = 0; i < STRESS_N; i++)); do
    x=$(( (x * 1103515245 + 12345) & 0x7fffffff )); k=$(( KEY_UP + (x >> 16) % 4 ))
    key "$k" || { scn_end BLOCKED "${ABORT_CODE:-ABORTED}" "$STRESS_N strzałek" "przerwano po $i"; return 1; }
  done
  if [ -n "$(app_pid)" ]; then scn_end PASS "" "aplikacja żyje po $STRESS_N strzałkach" "proces działa"; else scn_end FAIL APP_CRASH "aplikacja żyje po stresie" "proces zniknął"; fi
}

# ---------- logi (po teście, tylko potrzebne, oczyszczone, z limitem czasu) ----------
collect_logs() {
  case "$ABORT_CODE" in ADB_LOST|INTERRUPTED) LOGS_STATUS="pominięte (${ABORT_CODE}): brak wiszącego zbierania logów"; return 0;; esac
  [ -n "$PKG" ] || { LOGS_STATUS="pominięte (brak pakietu)"; return 0; }
  local pid raw="$WORK/logi/raw.txt" outf="$WORK/logi/app.txt"
  ADB_T=8
  pid=$(app_pid)
  : > "$raw"
  if [ -n "$pid" ] && [[ "$pid" =~ ^[0-9]+$ ]]; then
    tmo 10 "$ADB_BIN" -s "$TARGET" shell "logcat -d -v threadtime --pid=$pid *:W" 2>/dev/null | tr -d '\r' >> "$raw"
  fi
  tmo 10 "$ADB_BIN" -s "$TARGET" shell "logcat -d -b crash -v threadtime" 2>/dev/null | tr -d '\r' | grep -F -A25 "Process: $PKG" >> "$raw"
  tmo 10 "$ADB_BIN" -s "$TARGET" shell "logcat -d -s KeyGuard:I" 2>/dev/null | tr -d '\r' >> "$raw"
  sanitize < "$raw" > "$outf"
  rm -f "$raw"
  if leaks "$outf"; then rm -f "$outf"; LOGS_STATUS="REDACTION_FAILED"; else LOGS_STATUS="ok"; fi
}
LOGS_STATUS="niezebrane"

scn_logs() {
  scn_begin S12 "$(scn_title S12)"
  case "$LOGS_STATUS" in
    ok) scn_end PASS "" "oczyszczone logi aplikacji w paczce" "dołączono logi po oczyszczeniu i kontroli wycieku";;
    REDACTION_FAILED) scn_end BLOCKED REDACTION_FAILED "logi bez danych wrażliwych" "logu nie dało się bezpiecznie oczyścić: wykluczono z paczki (brak logów nie dowodzi braku awarii)";;
    *) scn_end SKIPPED LOGS_NOT_COLLECTED "logi aplikacji" "$LOGS_STATUS";;
  esac
}
# redaction_pass: przed zapisem raportu każde pole tekstowe i plik dowodowy przechodzi kontrolę wycieku. REDACTION_FAILED jest
# kodem przyczyny przy istniejącym statusie (PASS zamieniamy na BLOCKED), nie osobnym statusem.
redaction_pass() {
  local i f tmp="$WORK/redact.tmp" bad
  for i in "${!R_ID[@]}"; do
    printf '%s\n%s\n%s\n%s\n%s\n' "${R_NAME[$i]}" "${R_STEPS[$i]}" "${R_EXP[$i]}" "${R_ACT[$i]}" "${R_CODE[$i]}" > "$tmp"
    bad=0; leaks "$tmp" && bad=1
    f="${R_EVID[$i]}"
    if [ -n "$f" ] && leaks "$WORK/$f"; then rm -f "$WORK/$f"; R_EVID[i]=""; bad=1; fi
    if [ "$bad" -eq 1 ]; then
      R_STEPS[i]="[usunięto: REDACTION_FAILED]"; R_EXP[i]="[usunięto]"; R_ACT[i]="[usunięto: REDACTION_FAILED]"
      R_CODE[i]="${R_CODE[$i]:+${R_CODE[$i]};}REDACTION_FAILED"
      [ "${R_STATUS[$i]}" = PASS ] && R_STATUS[i]=BLOCKED
    fi
  done
  rm -f "$tmp"
}

# ---------- raport ----------
write_outputs() {
  local rj="$OUT/results.json" rm_="$OUT/report.md" i n=${#R_ID[@]} pass=0 fail=0 blk=0 skip=0 man=0 now
  for ((i = 0; i < n; i++)); do
    case "${R_STATUS[$i]}" in PASS) pass=$((pass + 1));; FAIL) fail=$((fail + 1));; BLOCKED) blk=$((blk + 1));; SKIPPED) skip=$((skip + 1));; MANUAL_REQUIRED) man=$((man + 1));; esac
  done
  now=$(ts_warsaw)
  {
    echo "# Raport testów Ketchup Player na TV boxie"
    echo
    echo "- czas (Europe/Warsaw): $(md_cell "$now")"
    echo "- skrypt: $KT_VERSION"
    echo "- urządzenie: $(md_cell "${DEV_MODEL:-?}"), Android $(md_cell "${DEV_ANDROID:-?}"), ekran $(md_cell "${DEV_SCREEN:-?}")"
    echo "- APK: $(md_cell "$(basename "${APK:-?}")"), pakiet $(md_cell "${PKG:-?}"), wersja $(md_cell "${APK_VER:-?}") ($(md_cell "${APK_CODE:-?}"))"
    echo "- SHA-256 APK: ${APK_SHA:-nieustalone}"
    if [ -n "$USER_CODE_SHA" ]; then echo "- SHA kodu: $USER_CODE_SHA (podane przez użytkownika, niezweryfikowane)"; else echo "- SHA kodu: nieustalone (brak wiarygodnego źródła)"; fi
    echo "- przerwanie: ${ABORT_CODE:-brak}"
    echo "- logi aplikacji w paczce: $LOGS_STATUS"
    [ -n "$CFG_IGNORED" ] && echo "- ostrzeżenie: w pliku konfiguracji pominięto wpisy z danymi dostępu: $CFG_IGNORED (adres listy podaje się tylko w ukrytym monicie)"
    [ -n "$IMPORT_NAME" ] && echo "- dostawca utworzony przez test: $(md_cell "$IMPORT_NAME") (usuń ręcznie, jeśli niepotrzebny; skrypt nie usuwa dostawców)"
    echo
    echo "**Podsumowanie:** PASS=$pass FAIL=$fail BLOCKED=$blk MANUAL_REQUIRED=$man SKIPPED=$skip"
    echo
    echo "| ID | Scenariusz | Status | Kod | Oczekiwano | Rzeczywiście | Dowód |"
    echo "|---|---|---|---|---|---|---|"
    for ((i = 0; i < n; i++)); do
      printf '| %s | %s | %s | %s | %s | %s | %s |\n' "$(md_cell "${R_ID[$i]}")" "$(md_cell "${R_NAME[$i]}")" "${R_STATUS[$i]}" "$(md_cell "${R_CODE[$i]}")" \
        "$(md_cell "${R_EXP[$i]}")" "$(md_cell "${R_ACT[$i]}")" "$(md_cell "${R_EVID[$i]}")"
    done
    echo
    echo "## Kroki"
    for ((i = 0; i < n; i++)); do
      [ -n "${R_STEPS[$i]}" ] || continue
      echo; echo "**${R_ID[$i]}**$([ "${R_TOUCH[$i]}" = 1 ] && echo ' (zawiera kroki dotykowe w konfiguracji)')"
      printf '%s\n' "${R_STEPS[$i]}" | while IFS= read -r l; do echo "- $(md_cell "$l")"; done
    done
    echo
    echo "## Następny krok"
    if [ "$fail" -gt 0 ]; then echo "Są scenariusze FAIL: przekaż paczkę ZIP do analizy; nie oznaczaj RC jako zaakceptowanego."
    elif [ "$blk" -gt 0 ] || [ "$man" -gt 0 ]; then echo "Są scenariusze BLOCKED lub MANUAL_REQUIRED: brak dowodu to nie sukces; wykonaj je ręcznie lub usuń blokadę i powtórz."
    else echo "Brak FAIL i BLOCKED; scenariusze SKIPPED opisano w tabeli. Ocena obrazu zawsze wymaga człowieka."; fi
  } > "$rm_"
  {
    printf '{\n  "wersja_skryptu": "%s",\n  "czas": "%s",\n' "$(json_esc "$KT_VERSION")" "$(json_esc "$now")"
    printf '  "urzadzenie": {"model": "%s", "android": "%s", "ekran": "%s"},\n' "$(json_esc "${DEV_MODEL:-}")" "$(json_esc "${DEV_ANDROID:-}")" "$(json_esc "${DEV_SCREEN:-}")"
    printf '  "apk": {"plik": "%s", "sha256": "%s", "pakiet": "%s", "wersja": "%s", "kod_wersji": "%s", "aktywnosc": "%s", "sha_kodu": "%s"},\n' \
      "$(json_esc "$(basename "${APK:-}")")" "$(json_esc "${APK_SHA:-}")" "$(json_esc "${PKG:-}")" "$(json_esc "${APK_VER:-}")" "$(json_esc "${APK_CODE:-}")" "$(json_esc "${ACT:-}")" \
      "$(json_esc "${USER_CODE_SHA:-nieustalone}")"
    printf '  "przerwanie": "%s",\n  "logi_aplikacji": "%s",\n' "$(json_esc "${ABORT_CODE:-}")" "$(json_esc "$LOGS_STATUS")"
    printf '  "podsumowanie": {"PASS": %d, "FAIL": %d, "BLOCKED": %d, "MANUAL_REQUIRED": %d, "SKIPPED": %d},\n  "scenariusze": [\n' "$pass" "$fail" "$blk" "$man" "$skip"
    for ((i = 0; i < n; i++)); do
      local steps="" l first=1
      while IFS= read -r l; do [ -z "$l" ] && continue; steps+="${first:+}$([ "$first" = 1 ] || printf ', ')\"$(json_esc "$l")\""; first=0; done <<< "${R_STEPS[$i]}"
      printf '    {"id": "%s", "nazwa": "%s", "status": "%s", "kod": "%s", "kroki": [%s], "oczekiwano": "%s", "rzeczywiscie": "%s", "dowod": %s, "dotyk_w_konfiguracji": %s}%s\n' \
        "$(json_esc "${R_ID[$i]}")" "$(json_esc "${R_NAME[$i]}")" "${R_STATUS[$i]}" "$(json_esc "${R_CODE[$i]}")" "$steps" \
        "$(json_esc "${R_EXP[$i]}")" "$(json_esc "${R_ACT[$i]}")" \
        "$([ -n "${R_EVID[$i]}" ] && printf '"%s"' "$(json_esc "${R_EVID[$i]}")" || printf null)" \
        "$([ "${R_TOUCH[$i]}" = 1 ] && echo true || echo false)" "$([ "$i" -lt $((n - 1)) ] && echo ,)"
    done
    printf '  ]\n}\n'
  } > "$rj"
}
make_bundle() {
  local stage="$WORK/paczka" f z
  rm -rf "$stage"; mkdir -p "$stage/dowody" "$stage/logi"
  cp "$OUT/report.md" "$OUT/results.json" "$stage/" 2>/dev/null
  for f in "$WORK"/dowody/*.txt; do [ -f "$f" ] && cp "$f" "$stage/dowody/"; done
  [ -f "$WORK/logi/app.txt" ] && cp "$WORK/logi/app.txt" "$stage/logi/"
  # ostatnia kontrola: każdy plik w paczce musi przejść test wycieku, inaczej jest wykluczany (nigdy nie dołączamy "na wiarę")
  while IFS= read -r f; do
    if leaks "$f"; then rm -f "$f"; echo "REDACTION_FAILED: $(basename "$f") wykluczony z paczki" >> "$OUT/uwagi.txt"; fi
  done < <(find "$stage" -type f)
  z="$OUT/ketchup-wyniki-$(basename "$OUT").zip"
  (cd "$stage" && tmo 60 zip -qr "$z" . ) || say "Nie udało się utworzyć ZIP."
  say "Paczka do przekazania: $z"
}
finalize() {
  [ "$FINALIZED" -eq 1 ] && return; FINALIZED=1
  trap '' INT TERM
  trap - EXIT
  [ -n "$OUT" ] || { cleanup_work; return; }
  [ "$INTERRUPTED" -eq 1 ] && ABORT_CODE="INTERRUPTED"
  collect_logs
  scn_logs
  redaction_pass
  write_outputs
  if leaks "$OUT/report.md" || leaks "$OUT/results.json"; then
    printf '# Raport\n\nREDACTION_FAILED: raport zawierał dane wrażliwe i został zastąpiony (kod przyczyny przy statusie BLOCKED).\n' > "$OUT/report.md"
    printf '{"status": "BLOCKED", "kod": "REDACTION_FAILED"}\n' > "$OUT/results.json"
  fi
  make_bundle
  cleanup_work
  say "Wyniki: $OUT"
  local fails=0 blks=0 i
  for i in "${!R_STATUS[@]}"; do case "${R_STATUS[$i]}" in FAIL) fails=1;; BLOCKED) blks=1;; esac; done
  say "Podsumowanie: $(printf '%s\n' "${R_STATUS[@]}" | sort | uniq -c | awk '{ printf "%s=%s ", $2, $1 }')"
  if [ "$INTERRUPTED" -eq 1 ]; then FINAL_RC=130; elif [ "$fails" -eq 1 ]; then FINAL_RC=1; elif [ "$blks" -eq 1 ]; then FINAL_RC=3; else FINAL_RC=0; fi
}
FINAL_RC=0

# ---------- kolejność przebiegu ----------
run_all() {
  scn_env || { skip_rest S01 S02 S03 S04 S05 S06 S07 S08 S09 S10 S11; return; }
  scn_install || { ABORT_CODE="${ABORT_CODE:-INSTALL_BLOCKED}"; skip_rest S02 S03 S04 S05 S06 S07 S08 S09 S10 S11; return; }
  scn_start || { skip_rest S03 S04 S05 S06 S07 S08 S09 S10 S11; return; }
  [ "$ABORT" -eq 1 ] && { skip_rest S03 S04 S05 S06 S07 S08 S09 S10 S11; return; }
  scn_import
  [ "$ABORT" -eq 1 ] && { skip_rest S04 S05 S06 S07 S08 S09 S10 S11; return; }
  scn_sections;   [ "$ABORT" -eq 1 ] && { skip_rest S05 S06 S07 S08 S09 S10 S11; return; }
  scn_search;     [ "$ABORT" -eq 1 ] && { skip_rest S06 S07 S08 S09 S10 S11; return; }
  scn_mylist_empty; [ "$ABORT" -eq 1 ] && { skip_rest S07 S08 S09 S10 S11; return; }
  scn_favorites;  [ "$ABORT" -eq 1 ] && { skip_rest S08 S09 S10 S11; return; }
  scn_playback;   [ "$ABORT" -eq 1 ] && { skip_rest S09 S10 S11; return; }
  scn_pilot;      [ "$ABORT" -eq 1 ] && { skip_rest S10 S11; return; }
  scn_crashes
  scn_stress
}
main() {
  parse_args "$@"
  preflight_tools || exit 3
  load_config "$HOME/.ketchup-test.env"
  init_workspace
  trap on_signal INT TERM
  trap finalize EXIT
  if read_list_url; then build_secrets "$LIST_URL" "$SECRETS_FILE"; fi
  run_all
  finalize
  exit "$FINAL_RC"
}
if [ "${KETCHUP_SOURCE_ONLY:-}" != 1 ]; then main "$@"; fi
