#!/usr/bin/env bash
# Test analizatora pilota na próbkach (uruchom lokalnie: bash tools/tvbox/test_analyze_keys.sh).
cd "$(dirname "$0")" || exit 1
fail=0
check() { # nazwa oczekiwany_status keyguard getevent
  out=$(bash ./analyze_keys.sh "testdata/$3" "testdata/$4" 5); st=${out%%|*}
  if [ "$st" = "$2" ]; then echo "ok   $1 -> $st"; else echo "BŁĄD $1: oczekiwano $2, jest $st ($out)"; fail=1; fi
}
check "poprawny pilot" PASS keyguard_ok.txt getevent_ok.txt
check "duplikaty sprzętowe odfiltrowane" PASS keyguard_dupes.txt getevent_dupes.txt
check "duplikaty sprzętowe bez filtra" WARN keyguard_dupes_unfiltered.txt getevent_dupes_unfiltered.txt
check "brak zdarzeń" FAIL keyguard_empty.txt getevent_empty.txt
# Funkcja wbudowana w skrypt główny musi być identyczna z samodzielną wersją (inaczej analiza na boxie się rozjedzie).
blk() { sed -n '/^# BEGIN analyze_keys/,/^# END analyze_keys/p' "$1"; }
if [ "$(blk analyze_keys.sh)" = "$(blk ketchup_tvbox_test.sh)" ] && [ -n "$(blk analyze_keys.sh)" ]; then
  echo "ok   funkcja w ketchup_tvbox_test.sh zgodna z analyze_keys.sh"
else
  echo "BŁĄD funkcja analyze_keys w ketchup_tvbox_test.sh różni się od analyze_keys.sh"; fail=1
fi
exit $fail
