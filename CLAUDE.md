# Ketchup Player: zasady pracy

- Spec: docs/KONSPEKT.md (wiążący). Makiety: docs/mockups (obowiązuje v3: 3 motywy + osobny kolor akcentu; sekcja "Motywy kolorystyczne" z v2 w konspekcie jest nieaktualna).
- Repo zawiera tylko nowy kod Kotlin/Compose. Zakaz wklejania starych plików BK Player Next.
- Build: ./gradlew :app:assembleDebug ; testy: ./gradlew testDebugUnitTest
- API Compose for TV i Media3 sprawdzaj przez context7.
- Wydanie 1.0 = tylko TV (poziom); telefon w 2.0 (docs/adr/tv-first.md). Błędy układu telefonu nie blokują wydania.
- Nowy ekran = test Roborazzi + test fokusu D-pad. Wszystko wykonalne D-pad + OK + BACK.
- Zero sekretów w repo. Teksty w strings.xml (PL domyślnie).
- Debrid/scrapery/URL resolver: poza zakresem.
