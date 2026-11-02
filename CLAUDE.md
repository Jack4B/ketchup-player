# Ketchup Player: zasady pracy

- Spec: docs/KONSPEKT.md (wiążący). Makiety: docs/mockups (obowiązuje v3: 3 motywy + osobny kolor akcentu; sekcja "Motywy kolorystyczne" z v2 w konspekcie jest nieaktualna).
- Repo zawiera tylko nowy kod Kotlin/Compose. Zakaz wklejania starych plików BK Player Next.
- Build: ./gradlew :app:assembleDebug ; testy: ./gradlew testDebugUnitTest
- API Compose for TV i Media3 sprawdzaj przez context7.
- Wydanie 1.0 = tylko TV (poziom); telefon w 2.0 (docs/adr/tv-first.md). Błędy układu telefonu nie blokują wydania.
- Nowy ekran = test Roborazzi + test fokusu D-pad. Wszystko wykonalne D-pad + OK + BACK.
- Zero sekretów w repo. Teksty w strings.xml (PL domyślnie).
- Debrid/scrapery/URL resolver: poza zakresem.
- Testy realnych scenariuszy (E2E) są obowiązkowe: każda funkcja ma scenariusz użytkownika na realistycznych danych (pełny graf Hilt, prawdziwy Room, serwer HTTP, UI sterowany D-pad/dotykiem), a nie tylko test jednostkowy. Nowy błąd = najpierw test, który go odtwarza. Fixture'y: tylko publiczne listy (iptv-org) i dane syntetyczne o kształcie prawdziwych; dane z kont użytkownika nigdy w repo.
- Bramka wydania (docs/PLAN-TESTY-EMULATOR.md): APK do użytkownika dopiero po zielonym CI **na tym samym SHA**, w tym job `emulator` (klikanie: dotyk i D-pad, prawdziwy ExoPlayer). Lokalne testy to za mało. Podaj numer biegu CI w wiadomości z APK. Każdy błąd z urządzenia dostaje też scenariusz w `.github/scripts/emulator-smoke.sh`.
- Silnik: Media3 ExoPlayer rozbudowujemy wokół (fabryka rendererów, TS, dekodery), nie piszemy własnego; drugi silnik tylko przez `PlayerController` i ADR.
- Gałąź robocza: `dev` (od 2.11.2026; zastąpiła rozproszone `faza-*` i `main-azhd2v`). CI na `dev` nagrywa baseline Roborazzi i commituje zrzuty z emulatora. Stare gałęzie `faza-*` zostają tylko jako historia.
- Nowy ekran przechodzi też audyt układu (`LayoutAudit` w `ScenariosTest.ukladGlownychEkranowSpelniaZasadyDesignu`): nic poza ekranem, czcionka >= 14 sp, cele fokusu >= 36 dp, teksty bez nachodzenia i bez „sklejania” (odstęp >= 4 dp). Dodaj ekran do tego scenariusza. Scenariusze E2E działają jako TV (`TvBoxRule`, cecha leanback).
- Oceny i opisy filmów: TMDB + OMDb (klucze użytkownika w Ustawieniach albo z sekretów CI `KETCHUP_TMDB_KEY`/`KETCHUP_OMDB_KEY`, nigdy w repo). Filmweb nie ma publicznego API, więc nie jest źródłem.
- UI: skill `.claude/skills/ketchup-design-taste` (rozpoznanie, pierwszeństwo, antywzorce, ocena 0-100, pętla poprawek) i `docs/DESIGN-LOG.md` (jedna linia na decyzję). Nowy lub zmieniony ekran: przejdź kroki skilla, zero blokerów.
