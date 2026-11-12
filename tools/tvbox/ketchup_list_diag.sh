#!/data/data/com.termux/files/usr/bin/bash
# =============================================================================
#  ketchup_list_diag.sh :: diagnostyka pobierania listy IPTV (get.php) z telefonu, bez ADB i bez aplikacji.
#  Jeden samodzielny plik, tylko curl. Szczegóły: docs/TVBOX-TESTY.md (sekcja „Diagnostyka listy”).
#
#  Użycie:  bash ketchup_list_diag.sh [--lista-plik PLIK] [--przerwa S] [--max-czas S] [--bez-wariantow]
#  Adres listy: ukryty monit (nigdy z argumentów) albo --lista-plik PLIK (pierwsza linia; zalecane chmod 600).
#  Wymagane narzędzia Termuxa (nie instalujemy ich sami):  pkg install curl coreutils gawk sed grep gzip
#
#  Co robi: jedno żądanie bazowe (jak aplikacja) i maks. 4 warianty, z przerwą co najmniej 30 s (i dłużej, gdy serwer
#  poda Retry-After):
#    B   GET  get.php                        UA aplikacji (żądanie bazowe)
#    V1  GET  player_api.php?action=get_live_categories    (czy API Xtream odpowiada, gdy get.php daje 504)
#    V2  POST get.php, dane w ciele          (username, password, type; bez parametrów w adresie)
#    V3  POST player_api.php, dane w ciele   (username, password, action=get_live_categories)
#    V4  GET  get.php                        inny User-Agent (VLC)
#    E1  GET  player_api.php bez action      jak IPTV Extreme: UA okhttp/4.10.0, HTTP/1.1, tylko Host, Connection, Accept-Encoding
#    E2  GET  player_api.php get_live_categories  jak E1 (ten sam zestaw czterech nagłówków)
#    E3  GET  player_api.php get_live_categories  dotychczasowy UA aplikacji (OTT Navigator) i Accept, dla porównania z E2
#  Wynik: <Download>/KetchupPlayerTest/diag-<data>/diag.md. Raport NIE zawiera adresu, hosta, loginu, hasła, nagłówków
#  Server ani Set-Cookie; po złożeniu raport jest czyszczony literalnie i w wariantach zakodowanych. Dane idą do curl
#  przez plik konfiguracyjny i plik ciała (chmod 600), nigdy w argumentach procesu.
# =============================================================================
set -u -o pipefail

KD_VERSION="diag-2"
# Muszą zgadzać się z NetworkClients.DEFAULT_USER_AGENT / OTT_USER_AGENT i UserAgentProfiles.VLC (test w tools/tvbox/tests).
UA_APP="okhttp/4.10.0"
UA_OTT="OTT Navigator/1.7.2.1 (Linux;Android 12) ExoPlayerLib/2.19.1"
UA_VLC="VLC/3.0.20 LibVLC/3.0.20"
MIN_PAUSE=30
MAX_WAIT=900
CONNECT_TIMEOUT=20
MAX_REDIRS=20
VARIANT_MAX_BYTES=2097152
SLEEP_CMD="${KD_SLEEP_CMD:-sleep}"
CURL_BIN="${CURL_BIN:-curl}"

PAUSE=$MIN_PAUSE
MAX_TIME=300
LIST_FILE=""
NO_VARIANTS=0
URL=""
WORK=""
OUT=""
INTERRUPTED=0
LAST_RETRY=0
RESULTS=()

# ---------- pomocnicze ----------
say() { printf '%s\n' "$*" >&2; }
die() {
  say "BŁĄD: $2"
  exit "$1"
}

pct_enc() {
  local s=$1 out="" c i
  local LC_ALL=C
  for ((i = 0; i < ${#s}; i++)); do
    c=${s:i:1}
    case $c in
      [A-Za-z0-9._~-]) out+=$c ;;
      *) out+=$(printf '%%%02X' "'$c") ;;
    esac
  done
  printf '%s' "$out"
}

pct_dec() {
  local s=${1//\\/\\\\}
  s=${s//+/ }
  # shellcheck disable=SC2059
  printf '%b' "${s//%/\\x}"
}

cleanup() {
  [[ -n "$WORK" && -d "$WORK" ]] && rm -rf -- "$WORK"
}

on_int() {
  INTERRUPTED=1
  say ""
  say "Przerwano. Raport powstanie z tego, co zebrano."
}

need_tools() {
  local missing=() t
  for t in "$CURL_BIN" awk sed grep gzip od head tr cut wc date mktemp; do
    command -v "$t" >/dev/null 2>&1 || missing+=("$t")
  done
  if ((${#missing[@]})); then
    say "Brak narzędzi: ${missing[*]}"
    say "Zainstaluj w Termuxie: pkg install curl coreutils gawk sed grep gzip"
    exit 3
  fi
}

# ---------- argumenty ----------
parse_args() {
  while (($#)); do
    case "$1" in
      --lista-plik)
        (($# >= 2)) || die 2 "--lista-plik wymaga ścieżki pliku."
        LIST_FILE=$2
        shift 2
        ;;
      --przerwa)
        (($# >= 2)) || die 2 "--przerwa wymaga liczby sekund."
        [[ $2 =~ ^[0-9]{1,4}$ ]] || die 2 "--przerwa: podaj liczbę sekund."
        ((10#$2 >= MIN_PAUSE)) || die 2 "--przerwa: minimum to ${MIN_PAUSE} s (panele IPTV ograniczają liczbę połączeń)."
        PAUSE=$((10#$2))
        shift 2
        ;;
      --max-czas)
        (($# >= 2)) || die 2 "--max-czas wymaga liczby sekund."
        [[ $2 =~ ^[0-9]{2,4}$ ]] || die 2 "--max-czas: podaj liczbę sekund (10-9999)."
        MAX_TIME=$((10#$2))
        shift 2
        ;;
      --bez-wariantow)
        NO_VARIANTS=1
        shift
        ;;
      -h | --help)
        sed -n '2,20p' "$0" >&2
        exit 0
        ;;
      *) die 2 "Nieznana opcja: $1 (adres podajesz w ukrytym monicie, nie w argumentach)." ;;
    esac
  done
}

read_url() {
  if [[ -n "$LIST_FILE" ]]; then
    [[ -r "$LIST_FILE" ]] || die 2 "Nie można odczytać pliku z adresem."
    IFS= read -r URL <"$LIST_FILE" || true
  elif [[ -r /dev/tty ]] && { : </dev/tty; } 2>/dev/null; then
    IFS= read -rs -p "Adres listy (wpisywanie ukryte): " URL </dev/tty || true
    printf '\n' >&2
  else
    die 2 "Brak terminala: użyj --lista-plik PLIK (pierwsza linia pliku to adres)."
  fi
  URL=${URL%$'\r'}
  [[ -n "$URL" ]] || die 2 "Pusty adres."
}

# ---------- rozbiór adresu (wartości zostają tylko w pamięci procesu) ----------
HOST="" HOSTPORT="" SCHEME="" BASE="" PATH_PART="" QUERY="" URL_NOQUERY=""
Q_USER="" Q_PASS="" Q_TYPE="" HAS_CREDS=0 IS_GETPHP=0

parse_url() {
  [[ $URL =~ ^https?://[^[:space:]\"\\]+$ ]] || die 2 "Adres musi zaczynać się od http:// lub https:// i nie może zawierać spacji, cudzysłowów ani ukośników odwrotnych."
  local rest after authority
  SCHEME=${URL%%://*}
  rest=${URL#*://}
  authority=${rest%%[/?#]*}
  after=${rest#"$authority"}
  HOSTPORT=${authority##*@}
  HOST=${HOSTPORT%%:*}
  PATH_PART=${after%%[?#]*}
  QUERY=""
  if [[ $after == *\?* ]]; then
    QUERY=${after#*\?}
    QUERY=${QUERY%%#*}
  fi
  [[ -n "$HOST" ]] || die 2 "Adres nie ma nazwy hosta."
  BASE="${SCHEME}://${authority}"
  URL_NOQUERY="${BASE}${PATH_PART}"
  local pair key
  local IFS='&'
  set -f
  for pair in $QUERY; do
    key=${pair%%=*}
    case $key in
      username) Q_USER=${pair#*=} ;;
      password) Q_PASS=${pair#*=} ;;
      type) Q_TYPE=${pair#*=} ;;
    esac
  done
  set +f
  IFS=$' \t\n'
  [[ -n "$Q_USER" && -n "$Q_PASS" ]] && HAS_CREDS=1
  [[ $PATH_PART == */get.php ]] && IS_GETPHP=1
  return 0
}

# Pełna lista literałów do usunięcia z raportu (surowe, zdekodowane i zakodowane ponownie).
secret_list() {
  local v d
  printf '%s\n' "$HOST" "$HOSTPORT" "$QUERY" "$URL"
  for v in "$Q_USER" "$Q_PASS" "$Q_TYPE"; do
    [[ -n "$v" ]] || continue
    d=$(pct_dec "$v")
    printf '%s\n' "$v" "$d" "$(pct_enc "$d")" "$(pct_enc "$(pct_enc "$d")")"
    printf '%s\n' "$(pct_enc "$d" | sed 's/%\([0-9A-F][0-9A-F]\)/%\L\1/g')"
  done
}

# ---------- jedno żądanie ----------
# wynik w tablicach R_* (bez żadnych wartości z adresu)
R_STATUS="" R_HTTPV="" R_REDIR="" R_SCHEME="" R_HOSTCHG="" R_T_DNS="" R_T_CONN="" R_T_TLS="" R_T_TTFB="" R_T_TOTAL=""
R_SIZE="" R_CTYPE="" R_CENC="" R_CLEN="" R_TENC="" R_RETRY="" R_FLAGS="" R_SHAPE="" R_ITEMS=""

safe_header_value() {
  # tylko znaki drukowalne ASCII, bez przecinków sterujących; maks. 80 znaków
  printf '%s' "$1" | LC_ALL=C tr -cd '[:print:]' | tr -d '|' | cut -c1-80
}

header_of() { # nazwa pliku nagłówków (ostatni blok odpowiedzi), nazwa nagłówka
  tr -d '\r' <"$1" | awk -v want="$(printf '%s' "$2" | tr '[:upper:]' '[:lower:]')" '
    /^HTTP\// { delete h; next }
    { i = index($0, ":"); if (i > 0) { k = tolower(substr($0, 1, i - 1)); v = substr($0, i + 1); sub(/^[ \t]+/, "", v); h[k] = v } }
    END { if (want in h) print h[want] }
  '
}

has_header() {
  tr -d '\r' <"$1" | awk -v want="$(printf '%s' "$2" | tr '[:upper:]' '[:lower:]')" '
    BEGIN { f = 0 }
    /^HTTP\// { f = 0; next }
    { i = index($0, ":"); if (i > 0 && tolower(substr($0, 1, i - 1)) == want) f = 1 }
    END { print (f ? "tak" : "nie") }
  '
}

shape_and_items() { # plik ciała -> R_SHAPE, R_ITEMS
  local f=$1 first magic
  R_SHAPE="pusta" R_ITEMS="-"
  [[ -s "$f" ]] || return 0
  magic=$(head -c2 "$f" | od -An -tx1 | tr -d ' \n')
  local reader=(cat -- "$f")
  [[ $magic == 1f8b ]] && reader=(gzip -dc -- "$f")
  first=$("${reader[@]}" 2>/dev/null | head -c 64 | tr -d '\r\n' | sed 's/^[[:space:]]*//' | cut -c1-8)
  case $first in
    "#EXTM3U"*)
      R_SHAPE="M3U"
      R_ITEMS=$("${reader[@]}" 2>/dev/null | awk '
        /^#EXTINF/ { e++; next }
        /^#/ { next }
        /^[ \t\r]*$/ { next }
        /\/movie\// { m++; next }
        /\/series\// { s++; next }
        { o++ }
        END { printf "EXTINF=%d filmy=%d seriale=%d pozostałe=%d", e, m, s, o }')
      ;;
    "{"* | "["*)
      R_SHAPE="JSON"
      local cats auth
      cats=$("${reader[@]}" 2>/dev/null | grep -o '"category_id"' | wc -l | tr -d ' ')
      auth=$("${reader[@]}" 2>/dev/null | grep -o '"auth" *: *"\?[01]"\?' | head -n1 | tr -cd '01')
      R_ITEMS="category_id=${cats:-0} auth=${auth:-?}"
      ;;
    "<"*) R_SHAPE="HTML/XML" ;;
    *) R_SHAPE="inny tekst" ;;
  esac
}

curl_exit_text() {
  case $1 in
    0) printf 'ok' ;;
    6) printf 'brak adresu hosta (DNS)' ;;
    7) printf 'połączenie odrzucone' ;;
    23 | 18) printf 'ucięta odpowiedź' ;;
    28) printf 'przekroczony czas' ;;
    35 | 51 | 58 | 60) printf 'błąd TLS' ;;
    47) printf 'za dużo przekierowań' ;;
    52) printf 'pusta odpowiedź serwera' ;;
    55 | 56) printf 'zerwane połączenie' ;;
    63) printf 'przerwano po limicie rozmiaru próbki' ;;
    *) printf 'kod curl %s' "$1" ;;
  esac
}

# do_request ID OPIS METODA URL UA [PLIK_CIAŁA] [LIMIT_BAJTÓW]
do_request() {
  local id=$1 desc=$2 method=$3 url=$4 ua=$5 body=${6:-} limit=${7:-0} style=${8:-full}
  local cfg="$WORK/cfg.$id" hdr="$WORK/hdr.$id" out="$WORK/body.$id" wfile="$WORK/w.$id"
  : >"$cfg"
  chmod 600 "$cfg"
  {
    printf 'url = "%s"\n' "$url"
    printf 'user-agent = "%s"\n' "$ua"
    if [[ $style == minimal ]]; then
      # Zestaw jak w aplikacji i w IPTV Extreme: User-Agent, Host, Connection, Accept-Encoding (bez Accept).
      printf 'http1.1\n'
      printf 'header = "Accept:"\n'
    else
      printf 'header = "Accept: */*"\n'
    fi
    printf 'header = "Accept-Encoding: gzip"\n'
    printf 'header = "Connection: Keep-Alive"\n'
    printf 'location\n'
    printf 'max-redirs = %s\n' "$MAX_REDIRS"
    printf 'connect-timeout = %s\n' "$CONNECT_TIMEOUT"
    printf 'max-time = %s\n' "$MAX_TIME"
    if [[ "$method" == POST ]]; then
      printf 'header = "Content-Type: application/x-www-form-urlencoded"\n'
      printf 'data = "@%s"\n' "$body"
    fi
    ((limit > 0)) && printf 'max-filesize = %s\n' "$limit"
  } >>"$cfg"
  local fmt='%{http_code}|%{http_version}|%{num_redirects}|%{url_effective}|%{time_namelookup}|%{time_connect}|%{time_appconnect}|%{time_starttransfer}|%{time_total}|%{size_download}|%{content_type}'
  local rc
  "$CURL_BIN" -q -s -S -K "$cfg" -D "$hdr" -o "$out" -w "$fmt" >"$wfile" 2>"$WORK/err.$id"
  rc=$?
  rm -f -- "$cfg"
  local line
  line=$(<"$wfile")
  local eff
  IFS='|' read -r R_STATUS R_HTTPV R_REDIR eff R_T_DNS R_T_CONN R_T_TLS R_T_TTFB R_T_TOTAL R_SIZE R_CTYPE <<<"$line"
  R_CTYPE=$(safe_header_value "${R_CTYPE:-}")
  R_SCHEME=${eff%%://*}
  [[ $eff == *"://"* ]] || R_SCHEME="-"
  local eff_host=${eff#*://}
  eff_host=${eff_host%%[/?#]*}
  eff_host=${eff_host##*@}
  eff_host=${eff_host%%:*}
  if [[ -n "$eff" && "$eff_host" != "$HOST" ]]; then R_HOSTCHG="tak"; else R_HOSTCHG="nie"; fi
  R_CENC="-" R_CLEN="-" R_TENC="-" R_RETRY="-" R_FLAGS="-"
  if [[ -s "$hdr" ]]; then
    R_CENC=$(safe_header_value "$(header_of "$hdr" content-encoding)")
    R_CLEN=$(safe_header_value "$(header_of "$hdr" content-length)")
    R_TENC=$(safe_header_value "$(header_of "$hdr" transfer-encoding)")
    local ra
    ra=$(header_of "$hdr" retry-after | tr -d '\r ')
    if [[ -z "$ra" ]]; then
      R_RETRY="brak"
    elif [[ $ra =~ ^[0-9]{1,6}$ ]]; then
      R_RETRY="${ra}"
    else
      R_RETRY="niepoprawny"
    fi
    local flags="" h
    for h in via cf-ray x-cache x-served-by; do
      flags+="$h=$(has_header "$hdr" "$h") "
    done
    R_FLAGS=${flags% }
  else
    R_RETRY="brak"
  fi
  : "${R_CENC:=-}" "${R_CLEN:=-}" "${R_TENC:=-}"
  [[ -n "$R_CENC" ]] || R_CENC="-"
  [[ -n "$R_CLEN" ]] || R_CLEN="-"
  [[ -n "$R_TENC" ]] || R_TENC="-"
  shape_and_items "$out"
  rm -f -- "$out" "$hdr" "$wfile" "$WORK/err.$id"
  local ctext
  ctext=$(curl_exit_text "$rc")
  RESULTS+=("$id|$desc|$method|${R_STATUS:-000}|${R_HTTPV:--}|${R_REDIR:-0}|${R_SCHEME}|${R_HOSTCHG}|${R_T_DNS:--}|${R_T_CONN:--}|${R_T_TLS:--}|${R_T_TTFB:--}|${R_T_TOTAL:--}|${R_SIZE:-0}|${R_CTYPE:--}|${R_CENC}|${R_CLEN}|${R_TENC}|${R_RETRY}|${R_FLAGS}|${R_SHAPE}|${R_ITEMS}|${rc}|${ctext}")
  say "  $id $desc: HTTP ${R_STATUS:-000}, ${R_T_TOTAL:--} s, $ctext"
}

wait_before_next() {
  local w=$PAUSE
  if [[ $LAST_RETRY =~ ^[0-9]+$ ]] && ((LAST_RETRY > w)); then w=$LAST_RETRY; fi
  ((w > MAX_WAIT)) && w=$MAX_WAIT
  say "  Czekam ${w} s (przerwa lub Retry-After serwera)..."
  "$SLEEP_CMD" "$w"
}

# ---------- raport ----------
field() { # numer pola (1-based) z wyniku
  local IFS='|'
  local -a a
  read -ra a <<<"$2"
  printf '%s' "${a[$(($1 - 1))]:-}"
}

status_of() { # id -> status HTTP
  local r
  for r in "${RESULTS[@]}"; do
    [[ $(field 1 "$r") == "$1" ]] && {
      field 4 "$r"
      return
    }
  done
}

is_ok() { [[ ${1:-} =~ ^2[0-9][0-9]$ ]]; }

build_report() {
  local r
  printf '# Diagnostyka listy (%s)\n\n' "$KD_VERSION"
  printf 'Data: %s\n\n' "$(date '+%Y-%m-%d %H:%M:%S %Z')"
  printf 'Adres: schemat=%s, port=%s, ścieżka get.php=%s, dane w adresie=%s (wartości celowo pominięte)\n\n' \
    "$SCHEME" "$([[ $HOSTPORT == *:* ]] && echo niestandardowy || echo domyślny)" \
    "$([[ $IS_GETPHP == 1 ]] && echo tak || echo nie)" "$([[ $HAS_CREDS == 1 ]] && echo tak || echo nie)"
  printf 'Przerwa między żądaniami: %s s (min %s s), limit czasu żądania: %s s%s\n\n' "$PAUSE" "$MIN_PAUSE" "$MAX_TIME" \
    "$([[ $INTERRUPTED == 1 ]] && echo ', PRZERWANO przez użytkownika')"
  printf '| id | opis | metoda | HTTP | wersja | przekier. | schemat końc. | inny host | DNS s | połącz. s | TLS s | do 1. bajtu s | całość s | bajty | typ | kodowanie | długość | transfer | Retry-After s | obecność nagłówków | kształt | pozycje | curl | wynik curl |\n'
  printf '|%s\n' "---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|"
  for r in "${RESULTS[@]}"; do
    printf '| %s |\n' "${r//|/ | }"
  done
  printf '\nNagłówki Server i Set-Cookie nie są zapisywane. Obecność pośredników (via, cf-ray, x-cache, x-served-by) to tylko tak/nie.\n\n'
  printf '## Odczyt (wskazówki, nie dowód)\n\n'
  hints
}

hints() {
  local b v1 v2 v3 v4 e1 e2 e3 line=0 r ttfb
  b=$(status_of B) v1=$(status_of V1) v2=$(status_of V2) v3=$(status_of V3) v4=$(status_of V4)
  e1=$(status_of E1) e2=$(status_of E2) e3=$(status_of E3)
  if [[ -n "$b" ]] && ! is_ok "$b"; then
    printf -- '- GET get.php (żądanie bazowe) zwraca HTTP %s.\n' "$b"
    line=1
  elif is_ok "${b:-}"; then
    printf -- '- GET get.php z tego telefonu działa (HTTP %s): różnica leży w aplikacji albo stan serwera się zmienia.\n' "$b"
    line=1
  fi
  if [[ -n "$b" ]] && ! is_ok "$b" && is_ok "${v1:-}"; then
    printf -- '- API Xtream (player_api.php) odpowiada (HTTP %s), a get.php nie: warto dodać listę jako konto Xtream.\n' "$v1"
    line=1
  fi
  if [[ -n "$b" ]] && ! is_ok "$b" && { is_ok "${v2:-}" || is_ok "${v3:-}"; }; then
    printf -- '- Panel odpowiada na POST (get.php: %s, player_api.php: %s), a na GET nie.\n' "${v2:--}" "${v3:--}"
    line=1
  fi
  if [[ -n "$b" && -n "${v4:-}" ]]; then
    local ob=0 ov=0
    is_ok "$b" && ob=1
    is_ok "$v4" && ov=1
    if ((ob != ov)); then
      printf -- '- Wynik zależy od User-Agent (aplikacja: %s, VLC: %s).\n' "$b" "$v4"
      line=1
    fi
  fi
  if is_ok "${e1:-}" || is_ok "${e2:-}"; then
    printf -- '- API z nagłówkami jak IPTV Extreme działa (konto: %s, kategorie: %s): import przez player_api.php ma sens.\n' \
      "${e1:--}" "${e2:--}"
    line=1
  fi
  if [[ -n "${e2:-}" && -n "${e3:-}" ]] && is_ok "$e2" && ! is_ok "$e3"; then
    printf -- '- Ten sam adres z dotychczasowym UA OTT Navigator i Accept dostaje HTTP %s, a z zestawem IPTV Extreme HTTP %s: przyczyną jest User-Agent lub nagłówki.\n' "$e3" "$e2"
    line=1
  fi
  if [[ -n "${e2:-}" && -n "${v1:-}" ]] && is_ok "$e2" && ! is_ok "$v1"; then
    printf -- '- Różnica między E2 i V1 to tylko Accept i HTTP/1.1: serwer reaguje na te nagłówki (V1: %s, E2: %s).\n' "$v1" "$e2"
    line=1
  fi
  for r in "${RESULTS[@]}"; do
    if [[ $(field 1 "$r") == B ]]; then
      ttfb=$(field 12 "$r")
      if [[ $ttfb =~ ^[0-9]+(\.[0-9]+)?$ ]] && ! is_ok "$b"; then
        if awk -v t="$ttfb" 'BEGIN { exit !(t < 2) }'; then
          printf -- '- Odpowiedź bazowa przyszła od razu (%s s): to raczej blokada lub limit, nie wolne generowanie listy.\n' "$ttfb"
        elif awk -v t="$ttfb" 'BEGIN { exit !(t >= 50 && t <= 75) }'; then
          printf -- '- Odpowiedź bazowa przyszła po ok. minucie (%s s): to wygląda na limit czasu bramki serwera przy generowaniu listy.\n' "$ttfb"
        fi
        line=1
      fi
    fi
  done
  ((line)) || printf -- '- Brak jednoznacznych wniosków; przekaż ten raport.\n'
}

write_report() {
  local text s
  text=$(build_report)
  while IFS= read -r s; do
    [[ -n "$s" && ${#s} -ge 3 ]] || continue
    text=${text//"$s"/***}
  done < <(secret_list)
  # kontrola: żaden dłuższy literał nie może przeżyć
  while IFS= read -r s; do
    [[ -n "$s" && ${#s} -ge 3 ]] || continue
    if [[ $text == *"$s"* ]]; then
      say "REDACTION_FAILED: nie udało się oczyścić raportu; nie zapisuję go."
      return 1
    fi
  done < <(secret_list)
  mkdir -p -- "$OUT" || die 1 "Nie można utworzyć katalogu wyników."
  printf '%s\n' "$text" >"$OUT/diag.md"
  say "Raport: $OUT/diag.md"
}

pick_out_dir() {
  local stamp base
  stamp=$(date '+%Y%m%d-%H%M%S')
  if [[ -n "${KD_OUT:-}" ]]; then
    base=$KD_OUT
  elif [[ -d "$HOME/storage/downloads" ]]; then
    base="$HOME/storage/downloads/KetchupPlayerTest"
  else
    base="$HOME/KetchupPlayerTest"
  fi
  OUT="$base/diag-$stamp"
}

main() {
  parse_args "$@"
  need_tools
  read_url
  parse_url
  WORK=$(mktemp -d "${TMPDIR:-/tmp}/ketchup-diag.XXXXXX") || die 1 "Nie można utworzyć katalogu tymczasowego."
  chmod 700 "$WORK"
  trap cleanup EXIT
  trap on_int INT TERM
  pick_out_dir

  say "Ketchup Player: diagnostyka listy ($KD_VERSION). Adres nie jest wypisywany ani zapisywany."
  say "Żądania: bazowe + do 4 wariantów; przerwa co najmniej ${PAUSE} s."
  local api_base="" body_get body_api
  if [[ $IS_GETPHP == 1 ]]; then api_base="${BASE}${PATH_PART%get.php}"; fi

  say "Żądanie bazowe (jak aplikacja):"
  do_request B "GET get.php (UA aplikacji)" GET "$URL" "$UA_APP" "" 0 minimal
  LAST_RETRY=$R_RETRY

  if ((NO_VARIANTS == 0)) && ((INTERRUPTED == 0)); then
    if [[ $IS_GETPHP == 1 && $HAS_CREDS == 1 ]]; then
      body_get="$WORK/body-getphp.txt"
      body_api="$WORK/body-api.txt"
      (
        umask 077
        printf 'username=%s&password=%s%s' "$Q_USER" "$Q_PASS" "${Q_TYPE:+&type=$Q_TYPE}" >"$body_get"
        printf 'username=%s&password=%s&action=get_live_categories' "$Q_USER" "$Q_PASS" >"$body_api"
      )
      chmod 600 "$body_get" "$body_api"
      local step
      for step in V1 V2 V3 V4 E1 E2 E3; do
        ((INTERRUPTED == 0)) || break
        wait_before_next
        case $step in
          V1) do_request V1 "GET player_api.php get_live_categories" GET \
            "${api_base}player_api.php?username=${Q_USER}&password=${Q_PASS}&action=get_live_categories" "$UA_APP" "" "$VARIANT_MAX_BYTES" ;;
          V2) do_request V2 "POST get.php (dane w ciele)" POST "$URL_NOQUERY" "$UA_APP" "$body_get" "$VARIANT_MAX_BYTES" ;;
          V3) do_request V3 "POST player_api.php (dane w ciele)" POST "${api_base}player_api.php" "$UA_APP" "$body_api" "$VARIANT_MAX_BYTES" ;;
          V4) do_request V4 "GET get.php (UA VLC)" GET "$URL" "$UA_VLC" "" "$VARIANT_MAX_BYTES" ;;
          E1) do_request E1 "GET player_api.php bez action (jak IPTV Extreme)" GET \
            "${api_base}player_api.php?username=${Q_USER}&password=${Q_PASS}" "$UA_APP" "" "$VARIANT_MAX_BYTES" minimal ;;
          E2) do_request E2 "GET player_api.php get_live_categories (jak IPTV Extreme)" GET \
            "${api_base}player_api.php?username=${Q_USER}&password=${Q_PASS}&action=get_live_categories" \
            "$UA_APP" "" "$VARIANT_MAX_BYTES" minimal ;;
          E3) do_request E3 "GET player_api.php get_live_categories (UA OTT Navigator, Accept)" GET \
            "${api_base}player_api.php?username=${Q_USER}&password=${Q_PASS}&action=get_live_categories" \
            "$UA_OTT" "" "$VARIANT_MAX_BYTES" ;;
        esac
        LAST_RETRY=$R_RETRY
      done
    else
      say "Adres nie wygląda na get.php z username i password: warianty API i POST pominięte."
      RESULTS+=("-|warianty API/POST pominięte (adres bez get.php lub bez danych w zapytaniu)|-|-|-|-|-|-|-|-|-|-|-|-|-|-|-|-|-|-|-|-|-|-")
    fi
  fi
  write_report
}

main "$@"
