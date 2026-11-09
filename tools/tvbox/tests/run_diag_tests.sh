#!/usr/bin/env bash
# Testy offline ketchup_list_diag.sh z atrapą curl (tylko fikcyjne dane). Uruchomienie: bash tools/tvbox/tests/run_diag_tests.sh
# Sprawdza m.in., że adres, host, login i hasło (także zakodowane) nie trafiają do raportu, wyjścia, argumentów
# procesów ani środowiska, a dane POST idą przez plik chmod 600.
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
SCRIPT="$ROOT/tools/tvbox/ketchup_list_diag.sh"
PASS=0 FAIL=0 SKIP=0
ok() { PASS=$((PASS + 1)); printf 'PASS  %s\n' "$1"; }
bad() { FAIL=$((FAIL + 1)); printf 'FAIL  %s\n' "$1"; }
check() { # opis, polecenie...
  local d=$1
  shift
  if "$@" >/dev/null 2>&1; then ok "$d"; else bad "$d"; fi
}

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
URL='http://srv.example.invalid:8080/get.php?username=user123&password=pa%24s%2Bw0rd&type=m3u_plus&output=ts'
# shellcheck disable=SC2016  # '$' w haśle jest literałem
SECRETS=(user123 'pa%24s%2Bw0rd' 'pa$s+w0rd' 'pa%24s%2bw0rd' srv.example.invalid 'srv.example.invalid:8080' "$URL")

cat >"$TMP/sleep" <<SL
#!/usr/bin/env bash
echo "\$1" >>"$TMP/sleep.log"
SL
chmod +x "$TMP/sleep"

# run NAZWA PLIK_SCENARIUSZA URL [opcje...] -> $TMP/<nazwa>/{out,err,code,log/,rep/}
run() {
  local name=$1 scen=$2 url=$3
  shift 3
  local d="$TMP/$name"
  mkdir -p "$d/log" "$d/rep"
  printf '%s\n' "$url" >"$d/url.txt"
  chmod 600 "$d/url.txt"
  : >"$TMP/sleep.log"
  mkdir -p "$d/tmp"
  TMPDIR="$d/tmp" FAKE_CURL_LOG="$d/log" FAKE_CURL_SCENARIO="$scen" CURL_BIN="$HERE/fake_curl" KD_SLEEP_CMD="$TMP/sleep" KD_OUT="$d/rep" \
    bash "$SCRIPT" --lista-plik "$d/url.txt" "$@" >"$d/out" 2>"$d/err"
  echo $? >"$d/code"
  cp "$TMP/sleep.log" "$d/sleep.log"
  REP=""
  local f
  for f in "$d"/rep/diag-*/diag.md; do [[ -e "$f" ]] && REP=$f; done
}

no_secrets_in() { # plik...
  local f s
  for f in "$@"; do
    [[ -e "$f" ]] || continue
    for s in "${SECRETS[@]}"; do
      if grep -qF -- "$s" "$f"; then
        echo "sekret '${s:0:6}…' w $f" >&2
        return 1
      fi
    done
  done
  return 0
}

# ---------- składnia, ShellCheck, zgodność User-Agent ----------
check "bash -n" bash -n "$SCRIPT"
check "bash -n testów i atrapy" bash -n "$HERE/run_diag_tests.sh" "$HERE/fake_curl"
SC="${SHELLCHECK_BIN:-$(command -v shellcheck || true)}"
if [[ -n "$SC" ]]; then
  check "ShellCheck skryptu" "$SC" -x "$SCRIPT"
else
  SKIP=$((SKIP + 1))
  echo "NOT_RUN  ShellCheck (brak shellcheck; ustaw SHELLCHECK_BIN)"
fi
ua_app=$(sed -n 's/^    const val DEFAULT_USER_AGENT = "\(.*\)"$/\1/p' "$ROOT/core/network/src/main/kotlin/pl/ketchupplayer/tv/core/network/NetworkClients.kt")
ua_vlc=$(sed -n 's/.*val VLC = UserAgentProfile("vlc", "VLC", "\(.*\)")$/\1/p' "$ROOT/core/network/src/main/kotlin/pl/ketchupplayer/tv/core/network/UserAgentProfiles.kt")
check "UA aplikacji zgodny z NetworkClients.kt" grep -qF "UA_APP=\"$ua_app\"" "$SCRIPT"
check "UA VLC zgodny z UserAgentProfiles.kt" grep -qF "UA_VLC=\"$ua_vlc\"" "$SCRIPT"
ua_ott=$(sed -n 's/^    const val OTT_USER_AGENT = "\(.*\)"$/\1/p' "$ROOT/core/network/src/main/kotlin/pl/ketchupplayer/tv/core/network/NetworkClients.kt")
check "UA OTT zgodny z NetworkClients.kt" grep -qF "UA_OTT=\"$ua_ott\"" "$SCRIPT"
check "UA aplikacji to okhttp/4.10.0" test "$ua_app" = "okhttp/4.10.0"

# ---------- scenariusz A: GET get.php daje 504 z Retry-After, reszta działa ----------
cat >"$TMP/scenA" <<'S'
GET:get 504 120 0 html 61.2 text/html
GET:api 200 - 0 json 0.4 application/json
POST:get 200 - 0 m3u 1.2 text/plain
POST:api 200 - 0 json 0.3 application/json
GET:get:vlc 504 - 0 html 60.9 text/html
GET:api:ott 403 - 0 html 0.2 text/html
S
run A "$TMP/scenA" "$URL"
d="$TMP/A"
check "A: kod wyjścia 0" test "$(cat "$d/code")" = 0
check "A: raport powstał" test -s "$REP"
check "A: osiem żądań (baza, V1-V4, E1-E3)" test "$(cat "$d/log/count")" = 8
check "A: baza GET get.php 504" grep -q '^| B | GET get.php (UA aplikacji) | GET | 504 |' "$REP"
check "A: API GET 200 JSON z kategoriami" grep -q 'JSON .*category_id=2' "$REP"
check "A: POST get.php 200, pozycje M3U" grep -q 'EXTINF=3 filmy=1 seriale=1 pozostałe=1' "$REP"
check "A: Retry-After 120 w raporcie" grep -q '| 120 |' "$REP"
check "A: przerwy 120 i sześć razy 30" test "$(tr '\n' ' ' <"$d/sleep.log")" = "120 30 30 30 30 30 30 "
check "A: wskazówka o API Xtream" grep -q 'API Xtream' "$REP"
check "A: wskazówka o POST" grep -q 'odpowiada na POST' "$REP"
check "A: wskazówka o ok. minucie do odpowiedzi" grep -q 'po ok. minucie' "$REP"
check "A: brak sekretów w raporcie, wyjściu, argv i środowisku" no_secrets_in "$REP" "$d/out" "$d/err" "$d/log/argv.log" "$d/log/env.log"
check "A: brak Server i Set-Cookie w raporcie" bash -c "! grep -qE 'SECRETSERVER|COOKIESECRET' '$REP' '$d/out' '$d/err'"
check "A: każde wywołanie curl zaczyna od -q" bash -c "awk '/^--- wywołanie/{getline; if (\$0 != \"-q\") exit 1}' '$d/log/argv.log'"
check "A: adres tylko w pliku konfiguracyjnym (-K), nie w argv" bash -c "grep -q 'http://srv.example.invalid:8080/get.php' '$d/log/cfg.1' && ! grep -q 'srv.example' '$d/log/argv.log'"
check "A: UA aplikacji w żądaniu bazowym, VLC w V4" bash -c "grep -qF 'user-agent = \"$ua_app\"' '$d/log/cfg.1' && grep -qF 'user-agent = \"$ua_vlc\"' '$d/log/cfg.5'"
check "A: POST get.php: ciało form, plik chmod 600" bash -c "head -n1 '$d/log/post.3' | grep -q 'mode=600' && sed -n 2p '$d/log/post.3' | grep -qx 'username=user123&password=pa%24s%2Bw0rd&type=m3u_plus'"
check "A: POST get.php bez parametrów w adresie, dane przez plik" bash -c "grep -q '^url = \"http://srv.example.invalid:8080/get.php\"\$' '$d/log/cfg.3' && grep -q '^data = \"@' '$d/log/cfg.3' && ! grep -q 'user123' '$d/log/cfg.3'"
check "A: POST player_api: ciało z action" bash -c "sed -n 2p '$d/log/post.4' | grep -qx 'username=user123&password=pa%24s%2Bw0rd&action=get_live_categories' && head -n1 '$d/log/post.4' | grep -q 'mode=600'"
check "A: Content-Type formularza w żądaniu POST" grep -q 'Content-Type: application/x-www-form-urlencoded' "$d/log/cfg.3"
check "A: GET API ma action=get_live_categories" grep -q 'player_api.php?username=user123&password=pa%24s%2Bw0rd&action=get_live_categories' "$d/log/cfg.2"
check "A: baza ma zestaw minimalny (bez Accept, HTTP/1.1)" bash -c "grep -qx 'http1.1' '$d/log/cfg.1' && grep -qx 'header = \"Accept:\"' '$d/log/cfg.1' && ! grep -q 'Accept: ' '$d/log/cfg.1'"
check "A: E1 to player_api bez action, UA okhttp, zestaw minimalny" bash -c "grep -q 'player_api.php?username=user123&password=pa%24s%2Bw0rd\"\$' '$d/log/cfg.6' && ! grep -q 'action' '$d/log/cfg.6' && grep -qF 'user-agent = \"$ua_app\"' '$d/log/cfg.6' && grep -qx 'http1.1' '$d/log/cfg.6' && grep -qx 'header = \"Accept:\"' '$d/log/cfg.6'"
check "A: E1 ma dokładnie cztery źródła nagłówków (UA, Accept-Encoding, Connection, usunięty Accept)" bash -c "grep -cE '^(user-agent|header) = ' '$d/log/cfg.6' | grep -qx 4"
check "A: E2 to get_live_categories w zestawie minimalnym" bash -c "grep -q 'action=get_live_categories' '$d/log/cfg.7' && grep -qx 'http1.1' '$d/log/cfg.7'"
check "A: E3 ma UA OTT Navigator i Accept, bez wymuszonego HTTP/1.1" bash -c "grep -qF 'user-agent = \"$ua_ott\"' '$d/log/cfg.8' && grep -qF 'Accept: */*' '$d/log/cfg.8' && ! grep -qx 'http1.1' '$d/log/cfg.8'"
check "A: wskazówka o zestawie IPTV Extreme" grep -q 'nagłówkami jak IPTV Extreme działa' "$REP"
check "A: wskazówka o różnicy UA OTT i IPTV Extreme" grep -q 'przyczyną jest User-Agent lub nagłówki' "$REP"
check "A: katalog roboczy (z adresem w konfiguracji) usunięty po pracy" test -z "$(find "$d/tmp" -mindepth 1 -maxdepth 1)"
# kontrola ujemna: wykrywacz sekretów musi łapać wyciek
printf 'x user123 y\n' >"$TMP/leak.txt"
SECRETS_BAK=("${SECRETS[@]}")
SECRETS=(user123)
if no_secrets_in "$TMP/leak.txt" 2>/dev/null; then bad "kontrola ujemna: no_secrets_in wykrywa wyciek"; else ok "kontrola ujemna: no_secrets_in wykrywa wyciek"; fi
SECRETS=("${SECRETS_BAK[@]}")

# ---------- B: adres bez danych dostępu: tylko żądanie bazowe ----------
cat >"$TMP/scenB" <<'S'
GET:get 200 - 0 m3u 0.5 text/plain
S
run B "$TMP/scenB" 'http://list.example.invalid/playlist.m3u'
check "B: jedno żądanie" test "$(cat "$TMP/B/log/count")" = 1
check "B: informacja o pominiętych wariantach" grep -q 'warianty API/POST pominięte' "$REP"
check "B: brak przerw" test ! -s "$TMP/B/sleep.log"

# ---------- C: opcje ----------
run C "$TMP/scenA" "$URL" --przerwa 10
check "C: --przerwa 10 odrzucone (kod 2)" test "$(cat "$TMP/C/code")" = 2
check "C: komunikat o minimum 30 s" grep -q 'minimum to 30' "$TMP/C/err"
run C2 "$TMP/scenA" "$URL" --bez-wariantow
check "C2: --bez-wariantow = jedno żądanie" test "$(cat "$TMP/C2/log/count")" = 1
run C3 "$TMP/scenA" "$URL" --przerwa 45
check "C3: przerwa 45 s (a po Retry-After 120: 120)" test "$(tr '\n' ' ' <"$TMP/C3/sleep.log")" = "120 45 45 45 45 45 45 "

# ---------- D: adres z cudzysłowem, brak terminala ----------
run D "$TMP/scenA" 'http://srv.example.invalid/get.php?username=u"x&password=p'
check "D: zły adres odrzucony (kod 2)" test "$(cat "$TMP/D/code")" = 2
check "D: komunikat nie zawiera adresu" bash -c "! grep -q 'srv.example' '$TMP/D/err' '$TMP/D/out'"
if command -v setsid >/dev/null 2>&1; then
  mkdir -p "$TMP/E"
  setsid -w env CURL_BIN="$HERE/fake_curl" bash "$SCRIPT" </dev/null >"$TMP/E/out" 2>"$TMP/E/err"
  check "E: bez terminala i bez pliku: kod 2" test "$?" = 2
  check "E: komunikat o --lista-plik" grep -q 'lista-plik' "$TMP/E/err"
else
  SKIP=$((SKIP + 1))
  echo "NOT_RUN  brak terminala (brak setsid)"
fi

# ---------- F: serwer zwraca w nagłówku sekrety; Retry-After nietypowy ----------
cat >"$TMP/scenF" <<'S'
GET:get 504 Wed,_21_Oct_2015_07:28:00_GMT 0 html 1.0 text/html;_host=srv.example.invalid_pw=pa$s+w0rd_u=user123
GET:api 503 5000 0 html 0.2 text/plain
POST:get 200 - 28 none 0.0 -
POST:api 200 - 0 gzm3u 0.3 text/plain
GET:get:vlc 200 - 0 m3u 0.3 text/plain
GET:api:ott 200 - 0 json 0.3 application/json
S
run F "$TMP/scenF" "$URL"
check "F: kod 0" test "$(cat "$TMP/F/code")" = 0
check "F: sekrety z Content-Type serwera usunięte z raportu" no_secrets_in "$REP" "$TMP/F/out" "$TMP/F/err"
check "F: Retry-After w formie daty to 'niepoprawny'" grep -q 'niepoprawny' "$REP"
check "F: przerwy 30 (data), 900 (limit), 30, 30, 30, 900, 900" test "$(tr '\n' ' ' <"$TMP/F/sleep.log")" = "30 900 30 30 30 900 900 "
check "F: timeout curl (28) opisany" grep -q 'przekroczony czas' "$REP"
check "F: gzip M3U policzony" grep -q 'EXTINF=2 filmy=1' "$REP"
check "F: wskazówka o User-Agent" grep -q 'zależy od User-Agent' "$REP"

printf '\nPASS=%d FAIL=%d NOT_RUN=%d\n' "$PASS" "$FAIL" "$SKIP"
[[ $FAIL -eq 0 ]]
