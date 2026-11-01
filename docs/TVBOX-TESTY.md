# Automatyczne testy na TV boxie (Leelbox L1) z Termuxa

Skrypt `tools/tvbox/ketchup_tvbox_test.sh` przechodzi przez aplikację przez ADB (jak `leelbox_manager.sh`),
robi zrzuty ekranu i zrzuty drzewa UI, sprawdza crashe i zapisuje raport na telefonie w
`/storage/emulated/0/Download/KetchupPlayerTest/<data_godzina>/` (`report.md`, `shots/`, `ui/`, `logs/`).

## Przygotowanie (raz)
1. W Termuxie: `termux-setup-storage`, `pkg install android-tools coreutils`.
2. Połącz z boxem: `adb connect 192.168.1.170:5555` (Wireless Debugging jak w `leelbox_manager.sh`).
3. Prywatna konfiguracja (poza repo): plik `~/.ketchup-test.env`
   ```
   DEVICE_IP="192.168.1.170"
   PORT="5555"
   M3U_URL="http://twoja-lista/get.php?..."   # opcjonalnie; domyślnie publiczna lista iptv-org PL
   ```
4. Skopiuj skrypt i APK na telefon (np. do `Download/skrypty/`).

## Uruchomienie
```
bash ketchup_tvbox_test.sh /storage/emulated/0/Download/skrypty/app-debug.apk
```
Skrypt czyści dane aplikacji, przechodzi kreator, dodaje listę, odwiedza wszystkie sekcje, zmienia motywy, odtwarza
kanał i film (jeśli lista ma VOD), przewija, wykonuje 150 losowych klawiszy i zbiera logi.

## Co pokrywa (ID z docs/TEST-CHECKLIST.md)
A1/A1b/A3, B1, C1/C4, D1–D4, E4, F1, G1, I2, J1/J3/J5/J6/J7, K1 oraz crash/ANR w logach.
Wyniki PASS/WARN/FAIL; WARN = sprawdź zrzut (np. brak VOD na liście, brak aktywnego audio).

## Czego skrypt nie sprawdzi (zostaje 🖐)
Ocena wizualna (zrzuty `18_motyw_*`, `20_odtwarzacz_*`), overscan, płynność/zacięcia, failover na prawdziwych listach
(wymaga dwóch list, jedna zepsuta), czas startu, długie oglądanie (I1).

## Uwaga
Dane list z kontem trzymaj tylko w `~/.ketchup-test.env` na telefonie. Nigdy w repo.

## Komplet komend (przy każdym nowym APK)
Pliki `KetchupPlayer-<wersja>-<commit>.apk` i `ketchup_tvbox_test.sh` wgraj do `Download/player/` na telefonie (nadpisz stare).
```
termux-setup-storage            # jednorazowo
pkg install -y android-tools    # jednorazowo
rm -f ~/ketchup_tvbox_test.sh
cp ~/storage/downloads/player/ketchup_tvbox_test.sh ~/
sed -i 's/\r$//' ~/ketchup_tvbox_test.sh
chmod +x ~/ketchup_tvbox_test.sh
adb connect 192.168.1.170:5555
~/ketchup_tvbox_test.sh ~/storage/downloads/player/KetchupPlayer-<wersja>-<commit>.apk
```
Skrypt zapyta o adres listy M3U (nie jest zapisywany). Wyniki: `Download/KetchupPlayerTest/<data>/`. Ctrl+C przerywa, logi i tak się zapiszą.
Tylko instalacja bez testu: `adb -s 192.168.1.170:5555 install -r -d --user 0 ~/storage/downloads/player/<apk>`.
Log po awarii: `adb -s 192.168.1.170:5555 logcat -d | grep -iE "ketchup|FATAL|AndroidRuntime|OutOfMemory|lmkd|am_kill" | tail -300 > ~/storage/downloads/player/log.txt`
