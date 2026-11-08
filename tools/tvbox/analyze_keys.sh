#!/data/data/com.termux/files/usr/bin/bash
# Analiza testu pilota: porównuje to, co zadeklarował użytkownik, co zgłosił sprzęt (getevent) i co przyjęła aplikacja
# (tag KeyGuard, tylko build debug). Utratę naciśnięć i duplikaty ocenia osobno.
# Użycie: analyze_keys.sh keyguard.txt getevent.txt [deklarowana_liczba_naciśnięć=5]
# Wyjście (trzy linie): LOSS|STATUS|KOD|opis, DUP|STATUS|KOD|opis, LAG|STATUS|KOD|opis, gdzie STATUS to
# PASS, FAIL, BLOCKED (nie da się ocenić: brak diagnostyki) albo MANUAL_REQUIRED (potrzebna decyzja człowieka).
# Kod wyjścia: 0 wszystko PASS, 2 jest FAIL, 3 jest BLOCKED/MANUAL_REQUIRED (bez FAIL).
# Format linii aplikacji: patrz app/.../KeyLog.kt:  KG t=.. key=20 rep=0 age=.. gap=.. dev=.. src=.. drop=0|1
# Brak diagnostyki NIGDY nie jest sukcesem: bez zdarzeń sprzętu albo bez linii KeyGuard wynik to BLOCKED.
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
if [ "${BASH_SOURCE[0]}" = "$0" ]; then analyze_keys "$@"; fi
