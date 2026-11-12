#!/usr/bin/env bash
# Test analizatora pilota na próbkach (uruchom lokalnie: bash tools/tvbox/test_analyze_keys.sh).
# Każdy przypadek sprawdza osobno trzy oceny: LOSS (utrata naciśnięć), DUP (duplikaty), LAG (opóźnienia).
cd "$(dirname "$0")" || exit 1
fail=0
check() { # nazwa keyguard getevent deklarowane "LOSS=STATUS" "DUP=STATUS" ["LAG=STATUS"]
  local name="$1" kg="$2" ge="$3" expect="$4" out kind want got
  shift 4
  out=$(bash ./analyze_keys.sh "testdata/$kg" "testdata/$ge" "$expect")
  for pair in "$@"; do
    kind="${pair%%=*}"; want="${pair#*=}"
    got=$(printf '%s\n' "$out" | awk -F'|' -v k="$kind" '$1 == k { print $2 }')
    if [ "$got" = "$want" ]; then echo "ok   $name: $kind -> $got"; else echo "BŁĄD $name: $kind oczekiwano $want, jest $got"; printf '%s\n' "$out"; fail=1; fi
  done
}
check "poprawny pilot" keyguard_ok.txt getevent_ok.txt 5 LOSS=PASS DUP=PASS LAG=PASS
check "duplikaty sprzętowe odfiltrowane" keyguard_dupes.txt getevent_dupes.txt 5 LOSS=PASS DUP=PASS
check "duplikaty sprzętowe bez filtra" keyguard_dupes_unfiltered.txt getevent_dupes_unfiltered.txt 5 LOSS=PASS DUP=FAIL
check "brak zdarzeń sprzętu i aplikacji" keyguard_empty.txt getevent_empty.txt 5 LOSS=BLOCKED DUP=BLOCKED
check "za mało naciśnięć nie jest sukcesem" keyguard_ok.txt getevent_ok.txt 9 LOSS=MANUAL_REQUIRED
check "brak diagnostyki aplikacji (build release) nie jest sukcesem" keyguard_empty.txt getevent_ok.txt 5 LOSS=BLOCKED DUP=BLOCKED LAG=BLOCKED
check "brak getevent nie jest sukcesem" keyguard_ok.txt getevent_empty.txt 5 LOSS=BLOCKED DUP=BLOCKED
check "utrata po stronie aplikacji (sprzęt 5, aplikacja 3)" keyguard_lost.txt getevent_ok.txt 5 LOSS=FAIL
check "filtr odrzucił prawdziwe naciśnięcia" keyguard_filter_drop.txt getevent_ok.txt 5 LOSS=FAIL
check "kolejka zdarzeń wskazuje zacięcia" keyguard_lag.txt getevent_ok.txt 5 LOSS=PASS LAG=MANUAL_REQUIRED
# Funkcja wbudowana w skrypt główny musi być identyczna z samodzielną wersją (inaczej analiza na boxie się rozjedzie).
blk() { sed -n '/^# BEGIN analyze_keys/,/^# END analyze_keys/p' "$1"; }
if [ "$(blk analyze_keys.sh)" = "$(blk ketchup_tvbox_test.sh)" ] && [ -n "$(blk analyze_keys.sh)" ]; then
  echo "ok   funkcja w ketchup_tvbox_test.sh zgodna z analyze_keys.sh"
else
  echo "BŁĄD funkcja analyze_keys w ketchup_tvbox_test.sh różni się od analyze_keys.sh"; fail=1
fi
exit $fail
