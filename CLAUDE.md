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
