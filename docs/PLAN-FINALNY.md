# Plan finalny wdrożenia — Ketchup Player

Stan na 6 listopada 2026, Europe/Warsaw. Baseline kodu: `dev` / `e2f4627` (ten sam Kotlin co wydanie `60cc938` / `v0.9.11-rc1`).

**Status: zaakceptowany 2026-11-06** — wpisany do `CLAUDE.md` i tego pliku w PR #8 (gałąź `feat/v5.1-mockups-docs-from-dev` → `dev`). Nie scalać bez decyzji właściciela; `main` bez zmian.

Źródła: [bbilly1/imdb-db](https://github.com/bbilly1/imdb-db) (README, `v0.1.0`), [IMDb Non-Commercial Datasets](https://developer.imdb.com/non-commercial-datasets/), [IMDb: Can I use IMDb data in my software?](https://help.imdb.com/article/imdb/general-information/can-i-use-imdb-data-in-my-software/G5JTRESSHJBBHTGX), kod `core/data/.../metadata/*`, `docs/PLAN-1.0.md`, recenzja `60cc938`.

---

## 1. Werdykt: bbilly1/imdb-db

**Co to jest.** Samodzielny serwis „lokalna baza IMDb”: pobiera oficjalne pliki TSV z [datasets.imdbws.com](https://datasets.imdbws.com/), ładuje je do **PostgreSQL** (COPY do tabeli staging, potem upsert) i wystawia cienkie API **Python / FastAPI** plus prosty front React do eksploracji. Nie jest to scraper strony imdb.com (to byłoby zakazane). Wersja `v0.1.0` (marzec 2026) — młody projekt (pierwsze wydanie, brak szerokiej adopcji); obrazy multi-arch na GHCR (`unstable` z `master` i tagi wydań).

**Stack i hosting.** Dwa kontenery (przykład `docker-compose.yml` w repo): Postgres + API. API na porcie **8000**, wolumen `/data` na archiwa `.tsv.gz` i rozpakowane TSV. Wymaga `DATABASE_URL` (asyncpg) i `DATABASE_URL_SYNC`. Opcjonalnie `DATASET_TO_KEEP` (rotacja cache) i `API_TOKEN` (nagłówek `Authorization: Bearer …`). Ingest: `POST /api/ingest` (wszystko albo wybrana lista plików, np. `title.basics.tsv` + `title.ratings.tsv`) albo CLI w kontenerze. Kolejne ingesty to upsert; czasy zależą od IO.

**Zestawy i rozmiary** (z README projektu, dysk NVMe; po imporcie do vacuum jeszcze więcej):

| Plik | Wiersze | .tsv.gz | .tsv | Po indeksie | Ingest |
|---|---:|---:|---:|---:|---|
| title.basics.tsv | 12 mln | 220 MB | 1 GB | 2 GB | ~1 min |
| name.basics.tsv | 15 mln | 300 MB | 1 GB | 2,4 GB | ~2 min |
| title.ratings.tsv | 2 mln | 10 MB | 30 MB | 220 MB | ~30 s |
| title.episode.tsv | 10 mln | 50 MB | 250 MB | 1,4 GB | ~5 min |
| title.akas.tsv | 55 mln | 500 MB | 2,7 GB | 12,6 GB | ~17 min |
| title.principals.tsv | 98 mln | 750 MB | 4,3 GB | 24 GB | ~1 h |

Do ocen i dopasowania tytułu/roku **wystarczą `title.basics` + `title.ratings`** (~2,2 GB po indeksie, plus cache plików). Pełny zestaw (aka + principals) to rząd **~40 GB** — niepotrzebny na start.

**API (OpenAPI pod `/docs`):** `GET /api`, `POST /api/ingest`, `GET /api/import-tasks`, `GET /api/stats`, `GET /api/titles` (filtry m.in. gatunek, rok, lista `tconst`), `GET /api/titles/{tconst}`, `…/principals`, `GET /api/people/{nconst}`, `…/credits`, `GET /api/series/{tconst}/episodes`, `GET /api/search/titles`, `GET /api/search/people`. Odpowiedź tytułu zawiera m.in. `average_rating` i `num_votes`.

**Kadencja.** Oficjalne datasety IMDb są **odświeżane codziennie**. Sam serwis nie cronuje z pudełka — użytkownik musi cyklicznie wołać `POST /api/ingest` (np. codziennie ratings+basics).

**Licencja i ToS — uczciwie, bez owijania.** Datasety IMDb są wyłącznie na **użytek osobisty i niekomercyjny**. Warunki (help IMDb + strona Non-Commercial Datasets):

- Dane wyłącznie z plików na datasets.imdbws.com — **zakaz scrapowania** strony IMDb.
- Nie wolno ich zmieniać / publikować ponownie / odsprzedawać / przerabiać na „jakąkolwiek internetową lub offline’ową bazę informacji o filmach”, **z wyjątkiem indywidualnego użytku osobistego**.
- Wymagane wskazanie źródła: *Information courtesy of IMDb (https://www.imdb.com). Used with permission.*
- IMDb może cofnąć zgodę w każdej chwili.
- Historycznie helpdesk IMDb uznawał nawet darmową publiczną stronę z wykresami ocen za użycie **poza** licencją darmowych plików (wymagałoby licencji komercyjnej). „Niekomercyjne” ≠ „wolno pokazać każdemu w internecie”.

`imdb-db` sam o tym pisze („local private personal and non-commercial use”). W Ketchupu oznacza to:

- **Wolno** (najbliżej licencji): użytkownik sam stawia serwis na swoim serwerze/NAS i aplikacja na jego TV czyta **jego** instancję — indywidualny użytek osobisty, bez republishu danych w naszym APK ani w chmurze.
- **Nie wolno:** publicznego proxy Ketchupa z danymi IMDb, bundle’a pełnej bazy w sklepie, commitu TSV do repo, scrapowania imdb.com.
- **Szara strefa już obecna:** `tools/wikidata/pack_index.py` wciąga `title.ratings.tsv.gz` do assetu `film_index.db` i ten plik idzie w APK jako migawka. CLAUDE.md na `dev` już oznacza to jako użytek osobisty/niekomercyjny i rezerwę, gdy brak kluczy. Rozprowadzanie tej migawki w wydanym APK jest bliżej „republish” niż self-host u użytkownika. Nowy plan **nie powiększa** tej szarej strefy (nie pakujemy pełniejszej bazy IMDb do APK). Indeks identyfikatorów z Wikidaty (CC0) zostaje.

Aplikacja musi pokazać podpis źródła „IMDb” na pigułce (już jest w języku v5) oraz formułę courtesy w Ustawieniach / O aplikacji, gdy korzystamy z datasetów.

---

## 2. Jak to mapuje się na Ketchup (`60cc938` / `e2f4627`)

**Dziś oceny i metadane** idą przez `MetadataService` + `MetadataRepository` (`core/data/.../metadata/`):

1. `FilmIndex` (`film_index.db`, Wikidata CC0) — offline `imdb` / `tmdb` / plwiki + opcjonalna migawka oceny IMDb z dnia budowy indeksu.
2. **TMDB** (klucz) — opis PL, ocena TMDB, obsada, plakat, `imdbId`.
3. **Trakt** (klucz) — ocena, opis, czasem `imdbId`.
4. **OMDb** (klucz, budżet dzienny; na kartach ograniczony, pełniej na szczegółach) — ocena IMDb, RT, Metacritic.
5. **MDBList** (klucz, głównie `deep` / szczegóły) — zbiór ocen.
6. **Wikipedia** (bez klucza, tylko szczegóły, gdy brak polskiego opisu).

Łączenie ocen (`MetadataService.mergeRatings`): IMDb = OMDb, potem MDBList, potem **migawka z indeksu**. Pigułki: `Ratings.best` = IMDb albo TMDB; UI w `feature/details/RatingPills.kt` i na kartach VOD/Home. Radar **nie woła** tych API — punktuje audycje EPG (gatunek, ulubiony kanał, ewentualna ocena wpisana w opisie EPG). Live TV nie zależy od IMDb.

**Trzy opcje podłączenia imdb-db:**

| Opcja | Opis | Werdykt |
|---|---|---|
| A. Na TV / w Room | Pełny Postgres albo nawet sam `title.basics`+`ratings` (~2 GB+) na boxie | **Odrzucone.** Za dużo RAM/ROM, za długi import, nie ten SoC. Nawet sam ratings (220 MB po indeksie) + 12 mln tytułów do matchowania nie nadaje się jako „kolejna tabela Room” obok katalogu IPTV. |
| B. Podzbiór w APK (rozszerzyć `film_index.db`) | Świeższe oceny w assetcie wydania | **Nie robić jako ścieżka imdb-db.** Już jest wąska migawka; powiększanie jej pogarsza licencję (republish) i puchnie APK. Odświeżanie indeksu Wikidaty zostaje osobnym narzędziem builda, bez pełnego IMDb. |
| C. Self-host u użytkownika (NAS/serwer) | Docker imdb-db w LAN; aplikacja ma URL + token w Ustawieniach / `SecretStore` | **Rekomendowane.** Pasuje do „indywidualnego użytku osobistego”, nie puchnie APK, oceny mogą być świeższe niż migawka, działa offline względem TMDB/OMDb (online względem LAN). TV zostaje klientem lekkich GET-ów. |

**Rekomendacja: opcja C**, jako dodatkowe, **opcjonalne** źródło `ImdbDbSource` w `MetadataSources` — wyłączone, dopóki użytkownik nie wpisze adresu.

- **Dopasowanie:** najpierw `tconst` z `FilmIndex` / TMDB / Trakt → `GET /api/titles/{tconst}`; bez id → `GET /api/search/titles` + filtr roku (parametry listy mają `year_from`). Tytuł normalizować tak jak `FilmTitles.normalize`.
- **Merge:** `imdb = omdb ?: mdblist ?: imdbDb ?: indexImdb` — żywe API (OMDb/MDBList) mają pierwszeństwo, jak dziś; imdb-db zastępuje nieświeżą migawkę z APK i działa **bez kluczy** TMDB/OMDb.
- **Sieć:** HostGate (lekkie GET, nie ingest z TV). Adres użytkownika jak lista M3U: HTTPS preferowane; HTTP tylko w LAN z ostrzeżeniem (jak przy HTTP w kreatorze). Dodać host do pinowania HTTPS, gdy URL jest https. Token tylko w `SecretStore`, redakcja logów (`Authorization`).
- **Prywatność:** tytuły katalogu idą na **maszynę użytkownika**, nie do nowej chmury. Ingest z datasets.imdbws.com dzieje się na NAS, nie na TV.
- **Nie** forkuje się imdb-db do repo Ketchupa — dokumentujemy compose + minimalny zestaw (`basics`+`ratings`) i cykl dzienny.
- Radar / EPG: poza pierwszą paczką (osobna decyzja 1.1+, match tytułu audycji jest rzadszy i bardziej błędogenny).

---

## 3. Plan numerowany (kolejność wdrożenia)

Każdy krok: zakres, główne miejsca w kodzie, Definition of Done, testy, nakład (S ≈ do 1 dnia, M ≈ kilka dni, L ≈ tydzień+), zależności. Start od istniejącego kodu na `dev` — **bez Fazy 0**.

### Krok 1 — Domknięcie jakości 1.0 na boxie
- **Zakres:** Punkty `PLAN-1.0.md` 1.1, 1.2, 1.3 (D-pad Szukaj / Moja lista), 1.5 (podgląd Na żywo na urządzeniu). Analiza `keyguard.txt` / `getevent.txt`, zebranie raportu z boxa (rc 0.9.11).
- **Miejsca:** `MainActivity` / `KeyRepeatGuard`, `feature/vod` (tryby SEARCH/MYLIST), testy D-pad analogiczne do siatki Filmów, `tools/tvbox/`, `docs/PLAN-1.0.md` (odhaczyć).
- **DoD:** Codzienne ścieżki (import, EPG, live, VOD, player, ustawienia, BACK) bez znanych blokerów na boxie; skoki fokusu albo naprawione, albo świadomie zostawione z danymi.
- **Testy:** Istniejące scenariusze E2E + nowe testy D-pad Szukaj/Moja lista; przebieg `ketchup_tvbox_test.sh` bez FAIL na ścieżkach rdzenia.
- **Nakład:** L. **Zależności:** dostęp do boxa i logów pilota.

### Krok 2 — Bramka emulatora i odtwarzacz wielościeżkowy
- **Zakres:** `PLAN-1.0` 1.7 i 1.11 — E1/E2/E5, P1 w `docs/PLAN-TESTY-EMULATOR.md`; HLS z ffmpeg (audio/napisy); ewentualne poprawki selekcji ścieżek w `player/engine`.
- **Miejsca:** `.github/scripts/emulator-smoke.sh`, `ExoPlayerController`, `PlayerTracks`, `feature/player-ui`.
- **DoD:** Nazwane scenariusze E/P zielone w CI na tym samym SHA co kandydat; ścieżki audio/napisów przełączalne na strumieniu testowym.
- **Testy:** Emulator CI + unit `TrackLabels` / `PlayerStateMapper` jeśli zmieni się mapowanie.
- **Nakład:** M–L. **Zależności:** krok 1 nie blokuje startu; blokuje wydanie 1.0.

### Krok 3 — Wydajność na urządzeniu
- **Zakres:** `PLAN-1.0` 1.8 (płynność VOD i otwarcie playera); import: kanały najpierw, VOD w tle (jeśli jeszcze nie na boxie). 2.3 (LazyLayout EPG, prefetch Coil) tylko jeśli pomiary pokażą potrzebę.
- **Miejsca:** `core/data` import katalogu, Coil w `feature/vod` / `guide`, ewentualnie siatka EPG.
- **DoD:** Zapisane liczby z boxa i poprawka największego zacięcia albo świadoma decyzja „wystarczy”.
- **Testy:** Regresja importu (`M3uPerformanceTest` / scenariusze); brak nowego jank w LayoutAudit.
- **Nakład:** M. **Zależności:** krok 1 (żeby mierzyć realne dane).

### Krok 4 — Wydanie 1.0
- **Zakres:** 2.1 keystore i sekrety (`docs/RELEASE.md`), 2.2 baseline profile na urządzeniu (zamiast ręcznego), 2.5 pełny przebieg CI+emulator+skrypt boxa+checklista, 2.6 tag `1.0.0`.
- **Miejsca:** `.github/workflows/release.yml`, `app/build.gradle.kts` signing, Macrobenchmark jeśli wejdzie.
- **DoD:** Podpisany APK (nie debug), zielone CI na SHA tagu, numer biegu CI przy APK, checklista ręczna odhaczona.
- **Testy:** Brama `rc.yml` / `release.yml` + checklista `docs/TEST-CHECKLIST.md`.
- **Nakład:** M. **Zależności:** kroki 1–2 (i 3, jeśli wyszedł bloker wydajności).

### Krok 5 — Szlif v5 / v5.1 (równolegle do 3–4, nie blokuje rc jakości)
- **Zakres:** Luki DESIGN-LOG: pigułki kart list, pasek zaznaczenia w Filmach, szkło OSD, ambient z plakatu, pełniejsze rozmycie (`Modifier.blur` + poziomy szkła), tokeny typografii/odstępów; decyzja o `ComingSoon` profilu (ekran albo zdjęcie z szyny do 1.1). PR #8 niesie makiety v5.1, ten plan i zaktualizowane `CLAUDE.md`; merge zostaje decyzją właściciela.
- **Miejsca:** `core/designsystem`, `feature/vod`, `feature/player-ui`, `feature/home`, `app/.../AppNavigation.kt`, `docs/mockups/v5.1/`, DESIGN-LOG.
- **DoD:** Zrzuty Roborazzi zgodne z v5.1 (Morski); LayoutAudit przechodzi; zero nowych literałów palety obok `Palette.kt`.
- **Testy:** Roborazzi, scenariusze układu, testy Appearance/ThemePicker.
- **Nakład:** M–L. **Zależności:** zaakceptowane v5.1 (PR #8).

### Krok 6 — Bezpieczeństwo P1
- **Zakres:** (a) własne URL-e EPG z `password=` wyjąć z JSON DataStore do `SecretStore` (`DataStoreEpgSourcesRepository`); (b) w `network_security_config.xml` wymusić HTTPS dla hostów metadanych: omdbapi.com, trakt.tv, mdblist.com, wikipedia.org / wikimedia.org, iptv-org (github.io) — obok już pinowanego TMDB; (c) zaktualizować nieaktualny komentarz w `SecretStore.kt`.
- **Miejsca:** `core/data/.../epg/DataStoreEpgSourcesRepository.kt`, ewentualnie Room na źródła EPG (konspekt i tak to planował), `app/src/main/res/xml/network_security_config.xml`, `core/security/SecretStore.kt`.
- **DoD:** Custom EPG z credentials nie leży jawnym tekstem w preferencjach; metadane nie zejdą na HTTP; testy repozytorium EPG i (jeśli są) konfiguracji sieci.
- **Testy:** `EpgSourcesRepositoryTest`, ewentualnie instrumentacja/unit na ścieżkę zapisu sekretu; regresja importu XMLTV Xtream (URL z loginem już jest w SecretStore katalogu).
- **Nakład:** M. **Zależności:** niezależne od imdb-db; warto przed krokiem 7 (ten sam config sieci).

### Krok 7 — Integracja imdb-db (po 1.0 albo równolegle do szlifu, nie w ścieżce krytycznej wydania)
- **Zakres:** ADR + krótka instrukcja Docker (tylko `basics`+`ratings`, cron ingest, `API_TOKEN`). W aplikacji: pole URL + token w Ustawieniach → Oceny i opisy; `ImdbDbSource` (OkHttp, HostGate, timeout); `GET /api/titles/{tconst}` i fallback search; wpisanie w `MetadataSources` / `mergeRatings`; podpis courtesy w UI; **nie** commitujemy datasetów ani forka serwisu.
- **Miejsca:** `core/data/.../metadata/ImdbDbSource.kt` (nowy), `MetadataService.kt`, `MetadataEndpoints.kt` / `DataModule.kt`, `ApiKeys` albo osobny `ImdbDbSettings` w DataStore+SecretStore, `MetadataSettingsScreen.kt` / ViewModel, `network_security_config.xml` (https użytkownika nie da się z góry wpisać — zostaje reguła jasnego tekstu jak u IPTV + ostrzeżenie), strings (attribution).
- **DoD:** Bez URL źródło milczy (jak pusty klucz). Z URL i `tt…` z indeksu pigułka IMDb wypełnia się bez OMDb. Token nie w logach. Dokument licencyjny w `docs/` jasno mówi: osobisty self-host, zakaz publicznego proxy, zakaz scrapingu imdb.com.
- **Testy:** MockWebServer (`MetadataTest` / nowy `ImdbDbSourceTest`): trafienie po tconst, fallback title+year, 404, zły token, brak hosta; golden merge order (OMDb wygrywa z imdb-db, imdb-db wygrywa z migawką indeksu).
- **Nakład:** M. **Zależności:** krok 6 (sieć/sekrety); działający `FilmIndex` (już jest).

### Krok 8 — 1.1: profile i PIN (oraz reszta przesunięta)
- **Zakres:** Profile z PIN-em (ekran z makiet v5.1), hash w `SecretStore` (PBKDF2 zgodnie z konspektem), przypomnienia Radaru (punkt 1.10), scoring stabilności między sesjami, Stalker, backup legacy (BKP/BKL), zdrowie EPG. Telefon = 2.0. Nagrywanie / Cast / VPN / NAS — później.
- **Miejsca:** puste dziś `:feature:profiles`, nawigacja `Section.PROFILE` / `ComingSoon`, `core/security`, WorkManager/powiadomienia dla Radaru.
- **DoD:** PIN blokuje profil dziecięcy (D-pad), sekrety nie w Room jawnym tekstem, testy PIN (bez wektorów ataku w logach), scenariusze E2E.
- **Testy:** unit hash/verify, UI PIN, brak wycieku w `Redactor`.
- **Nakład:** L. **Zależności:** po 1.0 (krok 4); makieta PIN już jest w v5.1.

---

## 4. Czego ten plan świadomie nie robi

- Nie startuje Fazy 0 ani przepisywania Compose od zera.
- Nie wciąga imdb-db do APK i nie stawia publicznego API IMDb „w chmurze Ketchupa”.
- Nie scrapuje imdb.com / Filmwebu / RT.
- Nie blokuje wydania 1.0 na imdb-db ani na PIN.
- Nie scala PR #8 bez decyzji właściciela.
