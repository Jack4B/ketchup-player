# Plan pracy: sesje i testy (stan na 1.11.2026)

Cel: aplikacja zgodna z makietami v3 i z ergonomią TiViMate (`RAPORT-UI-TIVIMATE.md` od użytkownika), sprawdzona
na prawdziwym boxie (Leelbox „UHD Google TV STB”, Android 12). Zasady: najpierw test odtwarzający błąd, wszystko
D-pad + OK + BACK, zero sekretów w repo, teksty w `strings.xml`.

## Stan faktyczny (potwierdzony na boxie, beta 0.3.5)
- Import listy M3U z 100 tys. pozycji działa (4 min na tym boxie: pobranie i czytanie ok. 3:50), kreator dochodzi do końca.
- Na żywo gra, Filmy/Seriale z plakatami, szczegóły filmu z wznowieniem, odtwarzacz VOD.
- Znane problemy: czas importu (4 min), układ Na żywo (przerobiony w tej sesji), marginesy (zmniejszone w tej sesji).

## Sesja bieżąca (zrobione)
- [x] Naprawy z testów na boxie: Xtream VOD strumieniowo, błędy listy po ludzku, kreator ✓ z klawiatury, postęp importu.
- [x] Skrypt `tools/tvbox`: bezpiecznik (nic nie klika poza aplikacją), logi po Ctrl+C, wybór użytkownika, adres listy z pytania.
- [x] Na żywo w stylu TiViMate: opis u góry, kompaktowa lista, grupy jako wysuwany panel.
- [x] Marginesy TV 16×10 dp.

## Następne sesje (kolejność wg wartości dla użytkownika)
1. **Wydajność importu** (box: 4 min). Zmierzyć udział sieci i parsowania; parsowanie M3U jednoprzebiegowe bez regexów
   na gorącej ścieżce, zapis do bazy partiami w jednej transakcji, VOD opcjonalnie w tle po pokazaniu kanałów.
   Test: syntetyczna lista 100 tys. pozycji w teście JVM z budżetem czasu; pomiar na boxie skryptem.
2. **Szuflada nawigacji** (raport §3.2, §8.3): szyna z etykietami nakładana ze scrimem, panel playlist i grup, 180 ms.
3. **Ustawienia jako panel z prawej** nad treścią (§3.3, §8.4), podmenu `AnimatedContent`.
4. **EPG split-view z podglądem wideo** (§3.1, §8.6): jedna oś czasu, `EpgTimeState`, żywy podgląd (jedna instancja
   ExoPlayer przenoszona do pełnego ekranu), focus tylko kolorem tła, „czas kursora” przy ruchu pionowym.
5. **Radar i Moje zainteresowania w Przewodniku** (zrobione: Radar; do zrobienia: zakładka zainteresowań).
6. **Odtwarzacz**: HUD wg §3.4 (auto-hide 4 s), wybór ścieżek audio/napisów, lista kanałów jako nakładka (§3.5),
   menu kontekstowe + toast (§3.7), historia (§3.8).
7. **Polityki z §10.2 konspektu** (BkSmartBufferPolicy, StreamRecoveryPolicy, SeriesEpisodePolicy, BkTvFailoverDeadline)
   z golden master na kodzie starej aplikacji.
8. **Profile/PIN, nagrywanie, VPN/NAS, migracja z BK Player Next, Stalker.**
9. **Wydanie 1.0**: podpis przez sekrety CI, baseline profile, ikona/banner na boxie, `KNOWN-EDGE-CASES.md`.

## Plan testów (każdy etap)
- Jednostkowe + Roborazzi + test D-pad dla nowego ekranu; E2E w `ScenariosTest` na realistycznych danych (syntetyczne o kształcie
  prawdziwych, publiczne iptv-org).
- Błąd z boxa → najpierw test, który go odtwarza (jak `kreatorZatwierdzonyKlawiaturaPrzechodziDoKroku3`).
- CI: build + detekt + spotless + lint + testy + emulator Android TV (smoke). Zrzuty bazowe commituje bot na gałęziach `faza-*`.
- Box: `tools/tvbox/ketchup_tvbox_test.sh` (adres listy podawany przy starcie, logi i zrzuty do `Download/KetchupPlayerTest/`).
- Ręcznie na boxie po każdej wersji: kreator → import własnej listy → Na żywo (grupy, OK, długie OK) → Filmy → szczegóły →
  odtwarzanie → BACK po wszystkich ekranach → ocena czytelności z kanapy.

## Ryzyka
- Czas importu na słabym boxie (patrz pkt 1). Mitygacja: etapy z postępem (jest), import w tle (do zrobienia).
- Rozjazd skryptu na boxie z układem aplikacji (tapy w szynie trafiały w sąsiednią pozycję): przejść na nawigację D-pad.
- Podgląd wideo w EPG = jedna instancja playera, uwaga na pamięć i dekoder (Leelbox ma słabe SoC).
