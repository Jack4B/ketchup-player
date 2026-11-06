# Ketchup Player: zasady pracy

- Spec: docs/KONSPEKT.md (wiążący). Makiety: obowiązuje język **v5** w kodzie (`core/designsystem` / `Palette.kt`: Morski, Grafit, Czerń, Własny) oraz uzupełnienie **v5.1** w `docs/mockups/v5.1/`. v3 w `docs/mockups/v3-*` jest historią. Sekcja „Motywy kolorystyczne” (v2) w konspekcie jest nieaktualna — przy konflikcie wygrywa `Palette.kt`.
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
- Oceny i opisy filmów: TMDB, OMDb, MDBList, Trakt (klucze użytkownika w Ustawieniach albo z sekretów CI `KETCHUP_TMDB_KEY`/`KETCHUP_OMDB_KEY`/`KETCHUP_MDBLIST_KEY`/`KETCHUP_TRAKT_ID`, nigdy w repo). IMDb i Rotten Tomatoes nie mają publicznych API: ich oceny dają OMDb i MDBList. Filmweb nie ma publicznego API, więc nie jest źródłem. Opis bez żadnych kluczy: polska Wikipedia (REST, `WikipediaSource`); serwisów ocen (Filmweb, IMDb) nie scrapujemy, bo regulaminy tego zabraniają.
- Wbudowany indeks filmów i seriali od 1980 (asset `core/data/src/main/assets/film_index.db`): dane z Wikidata (CC0), generowane skryptami `tools/wikidata/` (`fetch_films.py` -> `fetch_labels.py` -> `pack_index.py`). Daje offline id IMDb/TMDB i stronę plwiki, więc opis i oceny idą po id (mniej zapytań, trafienie pewne). Normalizacja tytułu w `FilmTitles.normalize` i w `pack_index.py` musi być identyczna (test golden). Oceny IMDb (kolumny `rating`/`votes`, dziesiąte części) pochodzą z publicznego pliku IMDb `title.ratings.tsv.gz` (datasets.imdbws.com, użytek osobisty i niekomercyjny; `pack_index.py` trzeci argument): to migawka z dnia budowy, rezerwa gdy brak kluczy lub API nie odpowiada (API ma pierwszeństwo). Etykiety: `fetch_labels_sparql.py` (szybkie, SPARQL VALUES).
- UI: skill `.claude/skills/ketchup-design-taste` (rozpoznanie, pierwszeństwo, antywzorce, ocena 0-100, pętla poprawek) i `docs/DESIGN-LOG.md` (jedna linia na decyzję). Nowy lub zmieniony ekran: przejdź kroki skilla, zero blokerów.

## Mapa kodu i gałęzie (stan 6.11.2026)

Kod Kotlin/Compose jest na `dev` (i kopiach `rcbuild/*`). `main` to zaległy pień dokumentacji (0 plików `.kt`) — nie zgaduj, że „nie ma aplikacji”.

- Gałąź robocza: `dev` (od 2.11.2026). Historia: `faza-0`, `faza-p`, `faza-b`, `faza-d`, `main-azhd2v`.
- Najnowsze wydanie kandydata: tag `v0.9.11-rc1` = commit `60cc938` na `rcbuild/0.9.11-rc1` (APK `KetchupPlayer-0.9.11-rc1-60cc938`). `dev` może mieć 1–2 commity zrzutów CI do przodu.
- Wydania RC: wypchnięcie `dev` na `rcbuild/<wersja>` albo workflow `rc.yml` (bramka: zielone CI z emulatora na tym samym SHA). Podpis wydania z sekretów CI; bez nich APK debug.
- Moduły: `settings.gradle.kts`. Wypełnione m.in. `:app`, `core/*`, `provider/{api,m3u,xtream}`, `epg/{xmltv,radar}`, `player/{engine,failover}`, `feature/{home,guide,vod,details,player-ui,settings,providers}`. Puste szkielety: `feature/{series,search,mylist,recording,profiles,onboarding}` (Filmy/Seriale/Szukaj/Lista są w `:feature:vod`; onboarding w `:feature:providers`), `provider/stalker`, `epg/teleman`, `integration/{cast,vpn,nas,tvprovider}`.
- Build: `./gradlew :app:assembleDebug` ; testy: `./gradlew testDebugUnitTest`. CI: Spotless, Detekt, unit testy, Roborazzi, lint, assemble, emulator TV API 34, gitleaks.

Płytki klon z `fetch` tylko `main` ukrywa `dev`. Zawsze: `git ls-remote --heads origin`.

## Makiety v5 i v5.1

- v5 (2.11.2026, DESIGN-LOG): pigułki ocen ze źródłem, nowość = kropka + etykieta, pigułka listy N z M, diagnostyka INFO, kreator Własny z kontrastem, stany nie samym kolorem. HTML v5 nie jest w git — żyje w kodzie i Roborazzi.
- v5.1 (`docs/mockups/v5.1/`): pełny zestaw w Morskim + porównanie 4 motywów + Home w Grafit/Czerń/Własny; 720p, telefon (podgląd 2.0), PIN (podgląd 1.1), puste/błąd/ładowanie, fokus, placeholdery, wspólny katalog. Tokeny z `Palette.kt`.
- Nowy ekran: kontrakt v5/v5.1 → test → kod → Roborazzi + D-pad + `LayoutAudit`.

## Bezpieczeństwo (P1 do domknięcia)

- `network_security_config.xml`: globalny cleartext jest świadomy dla IPTV; dodać HTTPS-only dla hostów metadanych (OMDb, Trakt, MDBList, Wikipedia/Wikimedia, iptv-org / github.io).
- Własne źródła EPG w DataStore mogą zawierać `password=` w URL — przenieść do `SecretStore` (listy M3U/Xtream już tam są).
- Sekrety produkcji: `DataStoreSecretStore` + AES-256-GCM i klucz w Android Keystore (komentarz o `InMemorySecretStore` w `SecretStore.kt` jest nieaktualny).
- Zero sekretów w repo. Klucze API tylko z Ustawień albo env CI.

## Plan pracy (od istniejącego kodu — nie od Fazy 0)

Faza 0 (Gradle, moduły, CI) jest zrobiona.

1. Domknięcie 1.0 według `docs/PLAN-1.0.md`: jakość na boxie (fokus pilota po danych), brakujące testy emulatora (E1/E2/E5, P1), wielościeżkowe audio/napisy, pomiar płynności, podpisany APK (`docs/RELEASE.md`).
2. Szlif v5 w UI (luki DESIGN-LOG): pigułki kart list, pasek zaznaczenia w Filmach, szkło OSD, ambient, tokeny typografii/odstępów; Profil nie zostaje na `ComingSoon` bez decyzji.
3. Bezpieczeństwo P1 (pinowanie HTTPS metadanych, URL EPG z hasłem w SecretStore).
4. 1.1: profile + PIN, przypomnienia Radaru, scoring między sesjami, Stalker, backup legacy.
5. 2.0: telefon jako pełny obywatel; później Cast/VPN/NAS i nagrywanie.

Nie przepisywać silnika poza `PlayerController`. Nie wklejać plików BK Player Next. Nie force-pushować, nie pushować na `main`, nie scalać bez właściciela.
