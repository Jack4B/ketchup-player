#!/data/data/com.termux/files/usr/bin/bash
# Analiza testu pilota: porównuje to, co zgłosił sprzęt (getevent), z tym, co zobaczyła aplikacja (tag KeyGuard).
# Użycie: analyze_keys.sh keyguard.txt getevent.txt [oczekiwana_liczba_naciśnięć=5]
# Wyjście (jedna linia): STATUS|opis, gdzie STATUS to PASS, WARN albo FAIL. Kod wyjścia: 0 PASS, 1 WARN, 2 FAIL.
# Format linii aplikacji: patrz app/.../KeyLog.kt:  KG t=.. key=20 rep=0 age=.. gap=.. dev=.. src=.. drop=0|1
# BEGIN analyze_keys
analyze_keys() {
  KG="${1:-}"; GE="${2:-}"; EXPECT="${3:-5}"
  [ -f "$KG" ] || { echo "FAIL|brak pliku keyguard"; return 2; }
  # --- aplikacja: tylko strzałka w dół (kod 20), nowe naciśnięcia (rep=0)
  read -r app_total app_drop app_mingap app_maxage < <(awk '
    /KG .* key=20 / {
      rep=0; gap=-1; age=0; drop=0
      for (i=1;i<=NF;i++) { split($i,a,"="); if (a[1]=="rep") rep=a[2]; if (a[1]=="gap") gap=a[2]; if (a[1]=="age") age=a[2]; if (a[1]=="drop") drop=a[2] }
      if (age+0 > maxage) maxage=age+0
      if (rep+0 == 0) { total++; if (drop+0==1) dropped++; if (gap+0 >= 0 && (min=="" || gap+0 < min)) min=gap+0 }
    }
    END { printf "%d %d %s %d\n", total, dropped, (min==""?"-1":min), maxage }' "$KG")
  # --- sprzęt: zdarzenia KEY_DOWN (strzałka w dół) w getevent -lt: "[ 1234.567890] /dev/input/eventN: EV_KEY KEY_DOWN DOWN"
  hw_total=0; hw_mingap=-1
  if [ -f "$GE" ]; then
    read -r hw_total hw_mingap < <(awk '
      /EV_KEY +KEY_DOWN +DOWN/ { gsub(/[\[\]]/,"",$1); t=$1+0; if ($1=="" ) t=$2+0; n++; if (prev!="" ) { d=(t-prev)*1000; if (min=="" || d<min) min=d } prev=t }
      END { printf "%d %d\n", n, (min==""?-1:int(min)) }' <(sed -E 's/^\[ */[/' "$GE"))
  fi
  detail="aplikacja: ${app_total} naciśnięć (odrzucone ${app_drop}), min odstęp ${app_mingap} ms, max opóźnienie ${app_maxage} ms; sprzęt: ${hw_total} zdarzeń, min odstęp ${hw_mingap} ms"
  if [ "$app_total" -eq 0 ] && [ "$hw_total" -eq 0 ]; then
    echo "FAIL|brak zdarzeń: nie naciśnięto strzałki w dół na pilocie albo pilot zgłasza inny kod ($detail)"; return 2
  fi
  accepted=$(( app_total - app_drop ))
  if [ "$hw_total" -gt "$EXPECT" ]; then
    adv=$(( hw_mingap>0 ? hw_mingap+30 : 150 ))
    if [ "$accepted" -gt "$EXPECT" ]; then
      echo "WARN|pilot wysyła duplikaty (${hw_total} zdarzeń na ${EXPECT} naciśnięć), a filtr aplikacji ich nie łapie. Ustaw Ustawienia → Odtwarzanie → Czułość pilota na co najmniej ${adv} ms ($detail)"; return 1
    fi
    echo "PASS|pilot wysyła duplikaty (${hw_total} zdarzeń na ${EXPECT} naciśnięć), ale filtr aplikacji je odrzuca: do aplikacji dotarło ${accepted} ($detail)"; return 0
  fi
  if [ "$accepted" -gt "$EXPECT" ]; then
    echo "WARN|aplikacja przyjęła więcej naciśnięć niż sprzęt zgłosił (${accepted} > ${hw_total}): duplikaty powstają w systemie lub aplikacji ($detail)"; return 1
  fi
  if [ "$app_maxage" -gt 120 ]; then
    echo "WARN|zdarzenia czekały w kolejce do ${app_maxage} ms: zacięcia głównego wątku mogą dawać „skoki” po puszczeniu ($detail)"; return 1
  fi
  echo "PASS|liczba naciśnięć zgodna ($detail)"; return 0
}
# END analyze_keys
analyze_keys "$@"
