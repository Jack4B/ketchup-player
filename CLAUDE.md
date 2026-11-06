# Ketchup Player — zasady pracy dla agentów

Status produktu na 6.11.2026 (Europe/Warsaw): **repozytorium projektowe**. Jest konspekt, branding i zaakceptowane makiety v4. **Nie ma jeszcze projektu Gradle ani kodu Kotlina.** `status: mockups v4 approved 2026-11-06`. Plan budowy (Faza 0 na początku) obowiązuje.

## 1. Stan repozytorium (aktualny — nie zgaduj)

- Brak `settings.gradle.kts`, `gradlew`, modułów `:app`, plików `.kt`. **Nie wołaj** `./gradlew` — komenda nie istnieje, dopóki Faza 0 nie wejdzie na `main`.
- Wiążąca specyfikacja produktu: `docs/KONSPEKT.md` (logika, dostawcy, bezpieczeństwo, port polityk).
- **Wygląd i tokeny:** obowiązują makiety **v4** (`docs/mockups/v4/`), nie paleta z logo w §4.0 konspektu i nie appendix „Motywy kolorystyczne” (Turkus/Szary/Bursztyn — to v2). `CLAUDE.md` + `docs/mockups/v4/NOTATKI-v4.md` wygrywają przy konflikcie kolorystycznym, aż konspekt zostanie zaktualizowany ADR-em.
- v3: w repo zostają tylko notatki (`docs/mockups/NOTATKI-v3.md`). PNG i HTML v3 z rozpoznawalnymi plakatami usunięto 6.11.2026. Implementować wyłącznie v4.
- Logo: `branding/source/ketchup-player-logo.jpg` (2752×1536). Nie projektować nowego. Wariantów `branding/derived/` jeszcze nie ma (zadanie Fazy B).
- Zakaz wklejania plików ze starego BK Player Next.
- Zero sekretów w repo. Debrid / scrapery / URL resolver: poza zakresem (poza zaplanowanym, wyłączalnym modułem Teleman z §13.3).

## 2. Stack docelowy (po akceptacji v4)

| Obszar | Wybór |
|---|---|
| Język | Kotlin 2.x, JVM 17 — zero nowej Javy |
| UI TV | Jetpack Compose + `androidx.tv:tv-material` (nie Leanback) |
| UI telefon | Ten sam APK, Material 3, `NavigationBar` (5 pozycji: Home, Na żywo, Filmy, Seriale, Więcej) |
| Wzorzec | MVVM + UDF: `ViewModel` → `StateFlow<UiState>`, `onEvent`, efekty przez `Channel` |
| DI | Hilt |
| Baza / ustawienia | Room (KSP, `exportSchema`), DataStore |
| Nawigacja | Compose Navigation; jedna `MainActivity` |
| Odtwarzacz | Media3 / ExoPlayer (HLS, DASH, TS, session, ui-compose, datasource-okhttp) |
| Sieć | OkHttp: `MetadataHttpClient` (tylko TLS) i `StreamHttpClient` (TLS + cleartext dla strumieni) |
| Tło | WorkManager; nagrywanie = foreground service |
| Testy | JUnit, Turbine, MockK, MockWebServer, Robolectric, Roborazzi, Compose UI test (D-pad) |
| Jakość | ktlint/Spotless, Detekt, Android Lint (ostrzeżenia = błędy w CI) |
| SDK | minSdk 28, targetSdk 35, compileSdk 35 |

Moduły i reguły zależności: `docs/KONSPEKT.md` §3.2. Feature nie zależy od feature. `:core:policy` jest czystym JVM.

## 3. Paleta v4 (tokeny do `core:designsystem`)

Motyw **Granat** + akcent **Turkus** — jedyny zestaw do implementacji.

- tło `#0A1A2F`, gradient `160deg #0F2A4A → #0A1A2F → #07121F`
- panele `rgba(22,48,82,.72)`, powierzchnia `#163152`, focus/aktywne `#21466F`, linie `#2A4B73`
- tekst `#EAF2FA`, drugorzędny `#A6BCD6`
- akcent `#33B5C4`, tekst na akcencie `#0B0B0B`
- zieleń `#3DBE4B` **tylko** wskaźnik nowych odcinków
- czerwień `#D4534A` **tylko** błędy / nagrywanie (nie jako akcent motywu)
- promień 18 px, płytki kanałów `#E8EEF6` / tekst `#0A1A2F`

Focus: skala 1,05–1,08 + obrys 4 px w akcencie + poświata (dwa sygnały naraz), ≤ 150 ms. Body ≥ 18 sp. Overscan ≥ 48 dp / 27 dp. Ikony odtwarzacza: wektory `currentColor`, nie emoji.

## 4. Makiety

- Opis i katalog danych: `docs/mockups/v4/NOTATKI-v4.md`
- HTML: `docs/mockups/v4/ketchup-player-makiety-v4.html` (generator `generate.py`, zrzuty `screenshot.py`)
- PNG:
  - `docs/mockups/v4/screenshots/tv-1080/` — 18 ekranów 1920×1080
  - `docs/mockups/v4/screenshots/tv-720/` — Home, Na żywo, EPG, Odtwarzacz (1280×720)
  - `docs/mockups/v4/screenshots/phone/` — Home, Na żywo, Filmy, Szukaj, Odtwarzacz (1080×1920)
- Zasada: **makieta przed kodem ekranu**. Nowy ekran Compose musi mieć zaakceptowany odpowiednik (albo ADR + nowy PNG). Potem test Roborazzi + test fokusu D-pad.
- Katalog pokazowy jest wspólny (godzina 19:42, kanał 102 „Rozmowa dnia” 19:15–20:15 na Live, EPG, Player, Radar i Szukaj). Placeholdery = gradient + monogram, bez cudzych plakatów.

## 5. Workflow

1. Gałąź tematyczna, PR do `main` (albo `test`, jeśli powstanie). **Nie pushować wprost na `main`, nie force-pushować, nie scalać bez właściciela.**
2. **TDD:** najpierw czerwony test, potem minimalny kod (skill `test-driven-development`). Port klas `*Policy` według `docs/KONSPEKT.md` §10.1.
3. Debugowanie: najpierw przyczyna źródłowa (`systematic-debugging`).
4. Przed prośbą o merge: niezależne review (`requesting-code-review`, `code-review-excellence`).
5. Commity konwencjonalne (`feat(mockups): …`, `docs: …`). Teksty UI w `strings.xml`, PL domyślnie.
6. Wszystko wykonalne zestawem D-pad + OK + BACK. API Compose for TV i Media3 sprawdzaj na bieżącej dokumentacji (np. context7), nie z pamięci.

## 6. Skille, których należy przestrzegać

Pełna lista i uzasadnienie: `.claude/skills/README.md`.

Obowiązkowe przy pracy nad tym projektem:

- `android-tv-ui` — 10-foot UI
- `android-architecture` — moduły, Hilt
- `mobile-android-design` — telefon
- `compose-adaptive` — TV + telefon
- `compose-component-design` — komponenty
- `frontend-design` — hierarchia wizualna
- `test-driven-development`
- `systematic-debugging`
- `requesting-code-review` / `receiving-code-review` / `code-review-excellence`

Nie stosuj skilli PHP, Next.js ani Node backend — to nie jest ten stack.

---

## 7. Raport z przeglądu (zaktualizowany po v4)

Przegląd kodu i makiet v3 (6.11.2026) plus weryfikacja v4 w tym samym dniu. Źródło v3: `main` @ `4a2cff7`. v4 jest w tym PR.

### Werdykt

v3 był świetnym kierunkiem 10-foot UI, ale nie nadawał się jako jedyne źródło prawdy: trzy palety w dokumentach, rozjechane godziny EPG/Live i rozpoznawalne plakaty w publicznym repo. v4 to naprawia w warstwie makiet. **Aplikacji nadal nie ma** — Faza 0 nie ruszyła.

### Oceny (0–10), po v4

| Obszar | v3 | v4 | Komentarz |
|---|---|---|---|
| Konspekt / architektura docelowa | 9 | 9 | Nadal bardzo dobry plan; appendix motywów v2 w `KONSPEKT.md:1292` jest nieaktualny (do ADR po akceptacji v4). |
| Plan bezpieczeństwa | 8 | 8 | Bez zmian w kodzie (kodu nie ma). |
| Makiety — wygląd 10-foot | 8 | 8 | Granat + Turkus spójny; placeholdery gradientowe są celowo surowe (legalne). |
| Makiety — kompletność | 5 | 8 | Doszły Radar, Szukaj, Moja lista, PIN, pusty/błąd/szkielet, focus, krok 3, 720p i telefon. Brak: krok 1 onboardingu (język/profil), widok „Moje zainteresowania”, szczegóły *filmu* (jest serial), jasny motyw. |
| Spójność danych UI | 4 | 8 | 102 Rozmowa dnia 19:15–20:15 wszędzie; filmy posortowane jako Najnowsze; hero = Złote miasto. |
| Jedno źródło prawdy o kolorach | 4 | 8 | v4 + ten plik. Konspekt §4.0 i appendix v2 jeszcze nie poprawione. |
| Kod / testy / CI | 1 | 1 | Nadal zero Gradle. |

### Dobrze (v4)

- Jedna paleta z wypisanymi tokenami i uzasadnieniem wyboru (Granat + Turkus).
- Wspólny katalog: Live, EPG, Player, Radar i Szukaj pokazują te same godziny audycji 102.
- EPG ma osobne audycje per gatunek kanału (dzieci ≠ liga, muzyka ≠ reportaż).
- Brak osadzonych plakatów filmowych; monogram + gradient.
- Ikony OSD są SVG (`currentColor`), focus pauzy w kolorze akcentu.
- Onboarding: ostrzeżenie HTTP, krok 3 z postępem Kanały → EPG → VOD i „Pomiń ładowanie VOD”.
- Nowe stany: PIN, pusta Moja lista, błąd sieci z kodem `NET-TIMEOUT`, szkielet Home, plansza stanów focusu.
- Warianty 1280×720 i telefon z dolnym paskiem z §6.3.
- HTML ma `viewport` i obsługuje `f` oraz `F`.
- Konspekt nadal dobrze opisuje stack, failover, sekrety (Tink), redakcję logów i port polityk.

### Źle / pozostałe luki (nie blokują akceptacji v4, ale trzeba znać)

- Nadal **brak kodu i CI** — największa luka projektu.
- `docs/KONSPEKT.md` §4.0 (pomidor `#E5322D`, złoto `#D4A64A`) i appendix v2 (`:1292`) mylą agentów, dopóki nie powstanie ADR.
- Appendix Trakt / „Ustawienia → Konta” (`KONSPEKT.md:1300`) nie jest w drzewie §6.4 ani na makiecie ustawień — odroczyć po MVP albo dodać do IA.
- Home 1080 pokazuje hero, Kontynuuj i Nowe odcinki; rzędy „Na żywo teraz” i „Radar” są w HTML poniżej krawędzi (można do nich zjechać w galerii, nie widać ich na PNG).
- Placeholdery są płaskie (świadomie). Po akceptacji można je zastąpić oryginalnymi ilustracjami, nie cudzymi plakatami.
- Krok 1 onboardingu (język/profil) nie jest narysowany jako osobny ekran (jest w stepperze).
- Brak jasnego motywu (świadoma decyzja v4: TV-first, OLED).
- v3 z plakatami zostaje w historii repo — nie usuwać w tym PR bez osobnej decyzji (duża historia binariów); nie powielać ich.
- Drobne: na bardzo ciasnych kafelkach podpisy nadal wymagają uwagi przy skali focusu 1,08.

### Błędy v3 i status w v4

| ID | Sev. | Problem v3 | Status v4 |
|---|---|---|---|
| B01 | P0 | Trzy palety w docs | **Naprawione w v4 / CLAUDE.md.** Konspekt appendix nadal stary — ADR po akceptacji. |
| B02 | P0 | Panel EPG ≠ komórka w focusie | **Naprawione:** panel = Rozmowa dnia 19:15–20:15 = focus. |
| B03 | P0 | Inne godziny Live vs EPG | **Naprawione:** 102 zawsze 19:15–20:15, dalej Serwis 20:15. |
| B04 | P0 | Plakaty ≠ tytuły, dzieła chronione | **Naprawione w v4** (brak rasterowych plakatów). v3 historyczne zostaje. |
| B05 | P1 | `./gradlew` w dokumentacji bez projektu | **Naprawione w CLAUDE.md** (opisuje brak). Realny scaffold = Faza 0 po akceptacji. |
| B06 | P1 | Brak ekranów i szerokości | **W większości naprawione** (patrz kompletność). |
| B07 | P1 | „Najnowsze” nie sortuje | **Naprawione** (2024 → 2018). |
| B08 | P1 | Konflikt focusu ustawień | v4: ekran Wygląd, focus na karcie Granat; wejście z szyny nadal „ostatnia kategoria” jak w notatkach v3. |
| B09 | P1 | Złote emoji na OSD | **Naprawione:** SVG. |
| B10 | P1 | Obcy kadr przy „Rozmowa dnia” | **Naprawione:** placeholder RD. |
| B11 | P1 | Trakt poza IA | **Otwarte** — odroczyć (nie blokuje v4). |
| B12–B22 | P2/P3 | Home vs §4.2, etykiety, QR, HTML×3, LICENSE… | Część naprawiona (HTTP warning, jeden HTML v4, F/f). Reszta higieny (LICENSE, przycięcie starych skilli animacji, BACK) pozostaje. |

---

## 8. Plan budowy aplikacji (po akceptacji v4)

Makiety v4 są zaakceptowane (2026-11-06). PNG v4 to referencja Roborazzi. Następny krok inżynierski: **Faza 0** (Gradle + CI).

### Faza 0 — Fundament repo (ok. 1 tydz.)

Gradle Kotlin DSL, version catalog, convention plugins (`build-logic/`), szkielety modułów z §3.2, Hilt, Detekt, ktlint/Spotless, Lint. CI (GitHub Actions): `assembleDebug`, `test`, `lint`, `detekt`, gitleaks. `network_security_config` jak w §8.1. Szkielet `SecretStore` + `RedactingTree` z testami. Pusta aplikacja „Ketchup Player” rusza na emulatorze Android TV (API 34) i telefonie. `applicationIdSuffix = ".debug"`.

**DoD:** `./gradlew :app:assembleDebug` i `./gradlew testDebugUnitTest` działają; release nie składany po cichu na kluczu debug; CI zielone na PR; złamanie „feature → feature” łamie build; ten plik zaktualizowany („Gradle jest”).

### Faza B — Branding i design system (ok. 1 tydz.) 🔒

Skrypt `tools/branding/generate.py` z `ketchup-player-logo.jpg` (bez nowego logo): przezroczyste tło, sygnet, adaptive + monochrome, banner TV 320×180 i 640×360, splash. `core:designsystem` z tokenami v4, typografią TV, `FocusCard`, `PosterCard`, `ChannelRow`, `NewEpisodeBadge`, stanami loading/empty/error. Galeria `@Preview` + Roborazzi vs PNG v4.

**DoD:** napis na bannerze czytelny; tokeny = tabela z §3; akceptacja właściciela zrzutów komponentów.

### Faza P — Port logiki do `:core:policy` (ok. 2 tyg.)

`docs/PORTING.md`, testy charakteryzujące, tłumaczenie `*Policy` Java → Kotlin pod osłoną tych samych testów (§10.1). `docs/KNOWN-EDGE-CASES.md`.

**DoD:** klasy (L) przeniesione; brak `android.*` w `:core:policy`; pokrycie linii ≥ 90%; liczby failover identyczne jak w konspekcie.

### Faza D — Dane i dostawcy (ok. 3 tyg.)

Room, DataStore, `IptvProvider` + M3U / Xtream / Stalker, testy kontraktowe, import TXT, WorkManager katalogu, FTS.

**DoD:** kontrakty trzech providerów zielone; import 50k kanałów w budżecie z konspektu; w pliku bazy nie ma haseł z fixture'ów.

### Faza E — EPG i Radar (ok. 2 tyg.)

XMLTV strumieniowo, single-flight, aliasy, przesunięcie czasu. `:epg:teleman` za flagą (domyślnie wg decyzji właściciela; aplikacja działa bez Telemana). Port Radaru.

**DoD:** golden-master Radaru; import `pltv.xml.gz` bez OOM na profilu 2 GB; test zmiany czasu.

### Faza X — Odtwarzacz, smart buffer, kaskadowy failover (ok. 3 tyg.)

`PlaybackService`, `PlayerController`, Media3, watchdogi, `FailoverCoordinator` (§7.1), Cast opcjonalnie (bez Play Services aplikacja działa).

**DoD:** testy kaskady na wirtualnym czasie; zerwanie / 5xx / DROP → Lista 1→2→3 bez wyjścia z odtwarzacza; zmierzony czas przełączenia w raporcie fazy.

### Faza F — Ekrany funkcjonalne (ok. 4–5 tyg.)

Kolejność jak w konspekcie: Onboarding + Dostawcy → Na żywo → UI odtwarzacza → Przewodnik (Siatka / Radar) → Home → Filmy → Seriale + nowe odcinki → Szczegóły → Moja lista → Szukaj → Ustawienia → Profile/PIN → Nagrywanie → VPN → NAS.

**DoD per ekran:** ViewModel + Turbine; ścieżka D-pad i `focusRestorer` (klucz ID); screenshot Roborazzi zgodny z v4 (albo ADR); brak literałów; `contentDescription`; stany loading/empty/error/offline. Telefon: bottom bar, te same przypadki.

### Faza M — Migracja z BK Player (ok. 1 tydz.)

`LegacyBackupReader` / `LegacyImporter`, ekran „Importuj z BK Player”, raport.

**DoD:** import zanonimizowanych backupów v5 i v7; sekrety tylko w `SecretStore`; import idempotentny.

### Faza Q — Jakość i RC (ok. 2 tyg.)

Baseline Profiles, Macrobenchmark, monkey+pilot 2 h, a11y, EN, decyzja OTA vs sklep.

**DoD:** kryteria `docs/KONSPEKT.md` §1.4; 0 ostrzeżeń Lint/Detekt; `docs/PARITY.md` domknięte.

Szacunek konspektu: 5–6 miesięcy przy krótkich iteracjach; B równolegle z P/D, makiety już są (v4).
