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

## Workflow sesji

- Opcjonalnie `/ub-mode`: skill `.claude/skills/ub-mode` (MIT, Robert C. Martin); ładuj tylko na prośbę. Zasady projektu i zakres mikro-PR wygrywają; bez refaktoru na zapas i bez automatycznego merge.

- Pętla mikro-PR, bramki 8 kroków, Cloud (bez boxa/adb), reguły PR→`dev`: skill `.claude/skills/ketchup-workflow` (`ketchup-workflow`).
- Pamięć checklist / APK / box: skill `ketchup-pamiec` + checkboxy w `docs/PLAN-1.0.md` i `docs/PLAN-FINALNY.md` (to status między sesjami).
- Komendy: `/next-step` — wybór punktu i plan mikro-PR (czekaj na OK); `/verify` — spotless/test/assemble lub CI gdy brak SDK.
- Jedna sesja ≈ jeden mikro-PR. Nigdy `main`, nigdy force-push, nie merguj bez właściciela. Status (plan + HANDOFF) w tym samym PR przed końcowym CI; krótki raport (format w skillu).
- Stan ostatniej sesji: `docs/HANDOFF.md` (migawka, nie backlog; plany i checkboxy zostają w `docs/PLAN-*`) i `docs/HISTORIA-SESJI.md`. Komendy `/wczytaj-projekt` (tylko odczyt) i `/domknij-projekt` (zapis stanu, bez commit/push). Walidator `tools/handoff/` (testy: `python3 -B -m unittest discover -s tools/handoff`; obecne CI ich nie uruchamia).

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

## Plan finalny (zaakceptowany 2026-11-06)

Pełna treść z zakresem, miejscami w kodzie, DoD, testami, nakładem i zależnościami: `docs/PLAN-FINALNY.md`. Start od istniejącego kodu na `dev` — **bez Fazy 0**. Nakład: S ≈ do 1 dnia, M ≈ kilka dni, L ≈ tydzień+.

Nie przepisywać silnika poza `PlayerController`. Nie wklejać plików BK Player Next. Nie force-pushować, nie pushować na `main`, nie scalać bez właściciela.

### imdb-db i licencja IMDb

Źródło: [bbilly1/imdb-db](https://github.com/bbilly1/imdb-db) (FastAPI + PostgreSQL, ingest oficjalnych TSV z [datasets.imdbws.com](https://datasets.imdbws.com/), nie scraper imdb.com). Datasety IMDb są wyłącznie na **użytek osobisty i niekomercyjny** (zakaz scrapowania strony, zakaz publikowania ponownie / odsprzedaży / przerabiania na publiczną bazę filmów; wymagane wskazanie źródła: *Information courtesy of IMDb (https://www.imdb.com). Used with permission.*).

- **Wolno:** użytkownik sam stawia serwis na swoim serwerze/NAS; aplikacja na jego TV czyta **jego** instancję (opcja C).
- **Nie wolno:** publicznego proxy Ketchupa z danymi IMDb, bundle’a pełnej bazy w sklepie, commitu TSV do repo, scrapowania imdb.com.
- **Nie powiększamy** szarej strefy migawki `film_index.db` (`pack_index.py` + `title.ratings.tsv.gz` w APK). Indeks identyfikatorów z Wikidaty (CC0) zostaje; pełniejszego IMDb do APK nie pakujemy.
- Na TV / w Room pełna baza (~2 GB+ już przy `basics`+`ratings`) jest odrzucona. Rozszerzanie migawki w APK jako ścieżka imdb-db — nie.
- Integracja: opcjonalne źródło `ImdbDbSource` w `MetadataSources`, wyłączone do wpisania URL. Merge: `imdb = omdb ?: mdblist ?: imdbDb ?: indexImdb`. Token w `SecretStore`. Attribution na pigułce i w Ustawieniach / O aplikacji.

### Krok 1 — Domknięcie jakości 1.0 na boxie (L)

- **Zakres:** `PLAN-1.0.md` 1.1, 1.2, 1.3 (D-pad Szukaj / Moja lista), 1.5 (podgląd Na żywo na urządzeniu). Analiza `keyguard.txt` / `getevent.txt`, raport z boxa (rc 0.9.11).
- **Miejsca:** `MainActivity` / `KeyRepeatGuard`, `feature/vod` (tryby SEARCH/MYLIST), testy D-pad analogiczne do siatki Filmów, `tools/tvbox/`, `docs/PLAN-1.0.md`.
- **DoD:** Codzienne ścieżki (import, EPG, live, VOD, player, ustawienia, BACK) bez znanych blokerów na boxie; skoki fokusu naprawione albo świadomie zostawione z danymi.
- **Testy:** Istniejące E2E + nowe D-pad Szukaj/Moja lista; `ketchup_tvbox_test.sh` bez FAIL na ścieżkach rdzenia.
- **Zależności:** dostęp do boxa i logów pilota.

### Krok 2 — Bramka emulatora i odtwarzacz wielościeżkowy (M–L)

- **Zakres:** `PLAN-1.0` 1.7 i 1.11 — E1/E2/E5, P1 w `docs/PLAN-TESTY-EMULATOR.md`; HLS z ffmpeg (audio/napisy); ewentualne poprawki selekcji ścieżek w `player/engine`.
- **Miejsca:** `.github/scripts/emulator-smoke.sh`, `ExoPlayerController`, `PlayerTracks`, `feature/player-ui`.
- **DoD:** Nazwane scenariusze E/P zielone w CI na tym samym SHA co kandydat; ścieżki audio/napisów przełączalne na strumieniu testowym.
- **Testy:** Emulator CI + unit `TrackLabels` / `PlayerStateMapper` jeśli zmieni się mapowanie.
- **Zależności:** krok 1 nie blokuje startu; blokuje wydanie 1.0.

### Krok 3 — Wydajność na urządzeniu (M)

- **Zakres:** `PLAN-1.0` 1.8 (płynność VOD i otwarcie playera); import: kanały najpierw, VOD w tle (jeśli jeszcze nie na boxie). 2.3 (LazyLayout EPG, prefetch Coil) tylko jeśli pomiary pokażą potrzebę.
- **Miejsca:** `core/data` import katalogu, Coil w `feature/vod` / `guide`, ewentualnie siatka EPG.
- **DoD:** Zapisane liczby z boxa i poprawka największego zacięcia albo świadoma decyzja „wystarczy”.
- **Testy:** Regresja importu (`M3uPerformanceTest` / scenariusze); brak nowego jank w LayoutAudit.
- **Zależności:** krok 1 (żeby mierzyć realne dane).

### Krok 4 — Wydanie 1.0 (M)

- **Zakres:** 2.1 keystore i sekrety (`docs/RELEASE.md`), 2.2 baseline profile na urządzeniu, 2.5 pełny przebieg CI+emulator+skrypt boxa+checklista, 2.6 tag `1.0.0`.
- **Miejsca:** `.github/workflows/release.yml`, `app/build.gradle.kts` signing, Macrobenchmark jeśli wejdzie.
- **DoD:** Podpisany APK (nie debug), zielone CI na SHA tagu, numer biegu CI przy APK, checklista ręczna odhaczona.
- **Testy:** Brama `rc.yml` / `release.yml` + `docs/TEST-CHECKLIST.md`.
- **Zależności:** kroki 1–2 (i 3, jeśli wyszedł bloker wydajności).

### Krok 5 — Szlif v5 / v5.1 (M–L; równolegle do 3–4, nie blokuje rc jakości)

- **Zakres:** Luki DESIGN-LOG: pigułki kart list, pasek zaznaczenia w Filmach, szkło OSD, ambient z plakatu, pełniejsze rozmycie, tokeny typografii/odstępów; decyzja o `ComingSoon` profilu. PR #8 niesie makiety v5.1 i ten plan; merge zostaje decyzją właściciela.
- **Miejsca:** `core/designsystem`, `feature/vod`, `feature/player-ui`, `feature/home`, `app/.../AppNavigation.kt`, `docs/mockups/v5.1/`, DESIGN-LOG.
- **DoD:** Zrzuty Roborazzi zgodne z v5.1 (Morski); LayoutAudit przechodzi; zero nowych literałów palety obok `Palette.kt`.
- **Testy:** Roborazzi, scenariusze układu, testy Appearance/ThemePicker.
- **Zależności:** zaakceptowane v5.1 (PR #8).

### Krok 6 — Bezpieczeństwo P1 (M)

- **Zakres:** (a) własne URL-e EPG z `password=` wyjąć z JSON DataStore do `SecretStore` (`DataStoreEpgSourcesRepository`); (b) w `network_security_config.xml` wymusić HTTPS dla hostów metadanych: omdbapi.com, trakt.tv, mdblist.com, wikipedia.org / wikimedia.org, iptv-org (github.io) — obok już pinowanego TMDB; (c) zaktualizować nieaktualny komentarz w `SecretStore.kt`.
- **Miejsca:** `core/data/.../epg/DataStoreEpgSourcesRepository.kt`, `app/src/main/res/xml/network_security_config.xml`, `core/security/SecretStore.kt`.
- **DoD:** Custom EPG z credentials nie leży jawnym tekstem w preferencjach; metadane nie zejdą na HTTP; testy repozytorium EPG i (jeśli są) konfiguracji sieci.
- **Testy:** `EpgSourcesRepositoryTest`; regresja importu XMLTV Xtream (URL z loginem już jest w SecretStore katalogu).
- **Zależności:** niezależne od imdb-db; warto przed krokiem 7 (ten sam config sieci).

### Krok 7 — Integracja imdb-db (M; po 1.0 albo równolegle do szlifu, nie w ścieżce krytycznej wydania)

- **Zakres:** ADR + krótka instrukcja Docker (tylko `basics`+`ratings`, cron ingest, `API_TOKEN`). Pole URL + token w Ustawieniach → Oceny i opisy; `ImdbDbSource` (OkHttp, HostGate, timeout); `GET /api/titles/{tconst}` i fallback search; wpisanie w `MetadataSources` / `mergeRatings`; podpis courtesy w UI. **Nie** commitujemy datasetów ani forka serwisu, **nie** powiększamy `film_index.db`.
- **Miejsca:** `core/data/.../metadata/ImdbDbSource.kt` (nowy), `MetadataService.kt`, `MetadataEndpoints.kt` / `DataModule.kt`, `ImdbDbSettings` w DataStore+SecretStore, `MetadataSettingsScreen.kt` / ViewModel, `network_security_config.xml`, strings (attribution).
- **DoD:** Bez URL źródło milczy. Z URL i `tt…` z indeksu pigułka IMDb wypełnia się bez OMDb. Token nie w logach. Dokument licencyjny: osobisty self-host, zakaz publicznego proxy, zakaz scrapingu imdb.com.
- **Testy:** MockWebServer (`ImdbDbSourceTest`): tconst, fallback title+year, 404, zły token, brak hosta; golden merge (OMDb > imdb-db > migawka indeksu).
- **Zależności:** krok 6 (sieć/sekrety); działający `FilmIndex` (już jest).

### Krok 8 — 1.1: profile i PIN (L)

- **Zakres:** Profile z PIN-em (ekran z makiet v5.1), hash w `SecretStore` (PBKDF2), przypomnienia Radaru (1.10), scoring między sesjami, Stalker, backup legacy (BKP/BKL), zdrowie EPG. Telefon = 2.0. Nagrywanie / Cast / VPN / NAS — później.
- **Miejsca:** `:feature:profiles`, nawigacja `Section.PROFILE` / `ComingSoon`, `core/security`, WorkManager/powiadomienia dla Radaru.
- **DoD:** PIN blokuje profil dziecięcy (D-pad), sekrety nie w Room jawnym tekstem, testy PIN (bez wektorów ataku w logach), scenariusze E2E.
- **Testy:** unit hash/verify, UI PIN, brak wycieku w `Redactor`.
- **Zależności:** po 1.0 (krok 4); makieta PIN już jest w v5.1.
