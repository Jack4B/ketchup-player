# Ketchup Player — zasady pracy dla agentów

Status na 6.11.2026 (Europe/Warsaw): **aplikacja istnieje**. Kod Kotlin/Compose jest na `dev` i na kandydatach `rcbuild/*`, nie na `main`. Baseline recenzji i najnowsze wydanie: tag `v0.9.11-rc1` = commit `60cc938` na `rcbuild/0.9.11-rc1` (APK `KetchupPlayer-0.9.11-rc1-60cc938`). `dev` (`e2f4627`) = ten sam kod + 2 commity zrzutów CI.

`main` (`4a2cff7`) to zaległy pień dokumentacji (0 plików `.kt`). Ta gałąź docs (`feat/v4-approved-mockups-docs-skills`, PR #7) też nie zawiera kodu aplikacji — **nie zaczynaj Fazy 0 od zera**. Pracuj na `dev` od `60cc938`. **Nie scalaj PR #7 bez przeróbki** (kolizja nazwy makiet v4 vs język v5 produktu; wcześniejsza wersja tego pliku mylnie mówiła, że nie ma Kotlina).

## 1. Mapa gałęzi

| Gałąź | Tip | vs `main` | `.kt` | Rola |
|---|---|---|---|---|
| `main` | `4a2cff7` | — | 0 | Docs / v3; nie zawiera aplikacji |
| `dev` | `e2f4627` | +391 | 295 | Gałąź robocza od 2.11.2026; otwarty [PR #5](https://github.com/Jack4B/ketchup-player/pull/5) |
| `rcbuild/0.9.11-rc1` | `60cc938` | +389 | 295 | Wydanie 0.9.11-rc1 — baseline kodu |
| `rcbuild/0.9.6-rc1` … `0.9.10-rc1` | tagi `v0.9.*-rc1` | | | Historia RC |
| `faza-0`, `faza-p`, `faza-b`, `faza-d`, `main-azhd2v` | | | | Historia; zastąpione przez `dev` |
| `feat/v4-approved-mockups-docs-skills` | ta gałąź | +2 | 0 | PR #7: storyboardy z 6.11 + ten plik |

Płytki klon z `remote.origin.fetch` ustawionym tylko na `main` **nie pokaże** `dev`. Zawsze: `git ls-remote --heads origin` albo `git fetch origin '+refs/heads/*:refs/remotes/origin/*'`.

Build: `./gradlew :app:assembleDebug` ; testy: `./gradlew testDebugUnitTest`. Na `60cc938` CI (w tym emulator TV API 34 i gitleaks) jest zielone; APK wydania jest podpisany kluczem debug (brak sekretów wydania w tamtym biegu).

## 2. Co jest w kodzie (`60cc938` / `dev`)

- Kotlin 2.1.21, JVM 17, AGP 8.10.1, Compose BOM 2025.06.00, `androidx.tv:tv-material` 1.0.1, Media3 1.8.0, Hilt 2.56.2, Room 2.7.1, Coil 3, OkHttp 4.12, minSdk 28, compile/target 35.
- Moduły jak `settings.gradle.kts` / `docs/KONSPEKT.md` §3.2. Feature nie zależy od feature. `:core:policy` jest czystym JVM.
- Wypełnione: `:app`, niemal cały `core/*`, `provider/{api,m3u,xtream}`, `epg/{xmltv,radar}`, `player/{engine,failover}`, `feature/{home,guide,vod,details,player-ui,settings,providers}`.
- Puste szkielety (`.gitkeep`): `feature/{series,search,mylist,recording,profiles,onboarding}`, `provider/stalker`, `epg/teleman`, `integration/{cast,vpn,nas,tvprovider}`. Filmy/Seriale/Szukaj/Moja lista żyją w `:feature:vod` (`VodMode`). Onboarding jest w `:feature:providers`.
- MVVM + UDF, Hilt, Room, DataStore. Nawigacja: stos `String` w `:app` (bez NavHost), odtwarzacz pełnoekranowy poza powłoką.
- Media3 `ExoPlayerController` (OkHttp DataSource, HLS/TS/MP4, failover, diagnostyka). Pauza w `ON_STOP`, `release()` w `onCleared()`. Brak `MediaSessionService` i jawnego `exoplayer-dash`.
- Sekrety dostawców: `DataStoreSecretStore` + `AesGcmCipher` (AES-256-GCM, klucz w Android Keystore). Komentarz w `SecretStore.kt` o `InMemorySecretStore` jest nieaktualny.
- TV-first (ADR `docs/adr/tv-first.md`): 1.0 = TV poziomo, D-pad + OK + BACK. Telefon w 2.0 (debug da się zainstalować).
- Testy obowiązkowe: jednostkowe, Roborazzi, scenariusze E2E (`ScenariosTest` + `LayoutAudit`), smoke emulatora. Nowy błąd = najpierw test. Fixture’y: listy publiczne (iptv-org) i dane syntetyczne — nigdy dane kont użytkownika.
- Bramka wydania: APK do użytkownika dopiero po zielonym CI **na tym samym SHA** (w tym job `emulator`). Dokument: `docs/PLAN-TESTY-EMULATOR.md`, `docs/PLAN-1.0.md`.
- Zero sekretów w repo. Klucze metadanych: Ustawienia albo sekrety CI `KETCHUP_TMDB_KEY` / `KETCHUP_OMDB_KEY` / `KETCHUP_MDBLIST_KEY` / `KETCHUP_TRAKT_ID`. Debrid/scrapery/URL resolver: poza zakresem. Filmwebu nie scrapujemy.

## 3. Makiety — hierarchia (ważne, kolizja nazw)

1. **Język produktu = v5** (decyzja użytkownika 1.11.2026, wpis 2.11 w `docs/DESIGN-LOG.md`). Motywy w kodzie: `ThemeId.MORSKI` (domyślny, turkus `#35C2B4` na `#0B1112`), `GRAFIT`, `CZERN`, `WLASNY`. Tokeny: `core/designsystem/.../Palette.kt`. Zasady v5: dane z podpisem źródła, stan nigdy samym kolorem, pigułka listy N z M, oceny w pigułkach, panel INFO, kreator akcentu z kontrastem WCAG.
2. Wewnętrzny artefakt „Ketchup Player **v4** makiety” (4 plansze) z `docs/PLAN-1.0.md` Etap 3 **to poprzednik v5**, nie nasze pliki z 6.11. HTML/PNG v5 **nie są w git** — v5 żyje w kodzie, Roborazzi i DESIGN-LOG.
3. Folder `docs/mockups/v4/` na **tej** gałęzi (6.11.2026) to niezależny storyboard z recenzji samego `main`: jeden zestaw Granat+Turkus, spójny katalog danych, placeholdery bez cudzych plakatów, brakujące stany, 720p, telefon, PIN, fokus. **Nie nadpisuje v5.** Traktować jako materiał na **v5.1** (przerysować na tokenach `Palette.kt`). Proponowana przyszła ścieżka: `docs/mockups/v5.1/`.
4. v3 (`docs/mockups/v3-*` na `dev`): historyczne, z rozpoznawalnymi plakatami. Na tej gałęzi docs PNG/HTML v3 usunięto; notatki zostają w `NOTATKI-v3.md`. Nie implementować v3 od nowa.
5. Przy konflikcie kolorystycznym z appendixem v2 w `docs/KONSPEKT.md` wygrywa `Palette.kt` (v5), nie folder `docs/mockups/v4/` z tej gałęzi.

Nowy ekran: makieta (v5 / v5.1) → test → kod. Potem Roborazzi + test D-pad + wpis w `LayoutAudit` / `ScenariosTest.ukladGlownychEkranowSpelniaZasadyDesignu`.

## 4. Workflow

1. Kod: gałąź od `dev`, PR do `dev` (albo aktualizacja PR #5). **Nie pushować wprost na `main`, nie force-pushować, nie scalać bez właściciela.**
2. Docs-only na bazie `main` (jak PR #7) nie mogą twierdzić, że aplikacji nie ma.
3. TDD: najpierw czerwony test (`test-driven-development`). Nowy błąd z urządzenia = scenariusz w testach i w `.github/scripts/emulator-smoke.sh`.
4. Debugowanie od przyczyny (`systematic-debugging`). Review: `code-review-excellence`, `requesting-code-review`.
5. Teksty UI w `strings.xml`, PL domyślnie. API Compose for TV i Media3 sprawdzaj na bieżącej dokumentacji (np. context7).

## 5. Poprawiony plan (start od istniejącego kodu — nie od Fazy 0)

Faza 0 (Gradle, moduły, CI, pierwsze źródła) **jest zrobiona** (historia: `faza-0` … `dev`).

1. **Dokumentacja i makiety.** Ujednolicić CLAUDE/README na `dev` z językiem v5. Przerobić PR #7: nie scalać jako „jedyne źródło prawdy”; przenieść storyboardy do v5.1 na tokenach Morski/Grafit/Czerń/Własny; zachować placeholdery, 720p, stany, spójny katalog, PIN jako podgląd 1.1.
2. **Domknięcie 1.0** (`docs/PLAN-1.0.md` na `dev`): jakość na boxie (przeskoki fokusu po danych z pilota), brakujące testy emulatora (E1/E2/E5, P1), wielościeżkowe audio/napisy, pomiar płynności, podpis wydania z sekretów CI (`docs/RELEASE.md`).
3. **Szlif v5 w UI:** luki z DESIGN-LOG (pigułki kart list, pasek zaznaczenia w Filmach, szkło OSD, ambient z plakatu, tokeny typografii/odstępów). Profil nie może zostawać na `ComingSoon` bez decyzji (ekran albo zdjęcie z szyny do 1.1).
4. **Bezpieczeństwo (P1):** pinowanie HTTPS dla hostów metadanych (OMDb, Trakt, MDBList, Wikipedia, iptv-org) w `network_security_config.xml`; URL-e własnych źródeł EPG z hasłem przenieść do `SecretStore` (dziś JSON w DataStore).
5. **1.1:** profile + PIN, przypomnienia Radaru, scoring stabilności między sesjami, Stalker, backup legacy (BKP/BKL).
6. **2.0 / później:** telefon jako pełny obywatel, Cast/VPN/NAS, nagrywanie.

DoD zmiany UI: test odtwarzający zachowanie, D-pad+OK+BACK, Roborazzi, `LayoutAudit` (czcionka ≥ 14 sp, cele ≥ 36 dp, odstęp tekstów ≥ 4 dp), zero sekretów, zielone CI na tym SHA.

Nie przepisywać silnika odtwarzania poza `PlayerController`. Nie wklejać plików BK Player Next.

## 6. Skrót recenzji kodu `60cc938` (6.11.2026)

Pełny raport: strona „Ketchup Player — przegląd” oraz kopia robocza recenzenta. Najważniejsze:

- Dobrze: wielomodułowy Compose TV, Hilt, Room, szyfrowane sekrety (Keystore + AES-GCM), redakcja logów, HostGate, failover, Radar, metadane (TMDB/OMDb/… + indeks Wikidata), bogate testy i CI z emulatora.
- P1: Profil = `ComingSoon` (`AppNavigation.kt`); custom EPG URL może trzymać hasło w DataStore; globalny cleartext bez pinowania hostów metadanych; Szukaj tylko VOD; docs na `dev` wciąż wskazują v3; brak jawnego DASH i MediaSession.
- P2: puste moduły-szkielety, otwarte punkty PLAN-1.0 (fokus pilota, pomiary), hero Home, literały `sp`/`dp`.
- Lokalny `./gradlew` w środowisku recenzji nie był uruchamiany (brak JDK/SDK). CI na `60cc938` — sukces.

## 7. Skille

Pełna lista: `.claude/skills/README.md` (na tej gałęzi docs) oraz skill `ketchup-design-taste` / `docs/DESIGN-LOG.md` na `dev`.

Obowiązkowe: `android-tv-ui`, `android-architecture`, `mobile-android-design`, `compose-adaptive`, `compose-component-design`, `frontend-design`, `design-taste-frontend` / `ketchup-design-taste`, `improve-animations` / `review-animations`, `test-driven-development`, `systematic-debugging`, `code-review-excellence`, `requesting-code-review`, `receiving-code-review`.

## 8. Zakazy stałe

- Zero sekretów w repo. Nie logować haseł ani pełnych URL-i Xtream.
- Nie scrapować Filmwebu / IMDb / RT (brak publicznych API / regulamin).
- Nie force-pushować, nie pushować na `main`, nie scalać bez właściciela.
- Nie ogłaszać, że „nie ma kodu”, bez `git ls-remote --heads`.
