# Raport fazy 0 — 2026-10-31

Gałąź `faza-0`, PR do `main` (link w opisie PR). Środowisko pracy: `docs/reports/env.md`.

## Zrobione
- **Build:** Gradle Kotlin DSL, version catalog (`gradle/libs.versions.toml`), `build-logic` z convention pluginami (`ketchup.android.application|library|compose`, `ketchup.jvm.library`, `ketchup.hilt`, `ketchup.quality`, `ketchup.module-rules`).
- **Moduły:** wszystkie 38 szkieletów z §3.2 + `:app`. `:core:model`, `:core:policy`, `:epg:radar`, `:provider:api` to czysta JVM.
- **Jakość:** Detekt (zakaz pustych `catch`, `SwallowedException`, `TooGenericExceptionCaught`), Spotless/ktlint (`.editorconfig`), Android Lint z `warningsAsErrors`.
- **Bezpieczeństwo:** `network_security_config` wg §8.1 (HTTPS-only dla domen metadanych/EPG), brak kopii zapasowych (`data_extraction_rules`, `backup_rules`), `RedactingTree` + `Redactor` z testami (hasła, tokeny, ścieżki Xtream, nagłówki, MAC), szkielet `SecretStore` (`InMemorySecretStore`) z testami.
- **Reguły modułów:** `verifyModuleDependencies` podpięte pod `check`; `ModuleRules` + 5 testów.
- **Źródła EPG (zakres dodatkowy na prośbę właściciela):** epg.ovh (domyślnie WŁ.), Teleman (domyślnie WYŁ.), własne źródła XMLTV (http/https); model, `EpgSourcePolicy`, repozytorium na DataStore, ekran „Źródła EPG” (D-pad + OK + BACK), `docs/adr/teleman.md`. Samo pobieranie XMLTV/scraping to Faza E.
- **CI** (`.github/workflows/ci.yml`): reguły modułów, Spotless, Detekt, testy jednostkowe, Roborazzi (verify; bez baseline — record i commit PNG), Lint, `assembleDebug`, `assembleRelease` (R8), gitleaks.
- **Narzędzia:** skille `ponytail`, `android-tv-ui` i inne w `.claude/skills`, allow-lista poleceń w `.claude/settings.json`.

## DoD — checklista
| Punkt | Stan | Dowód |
|---|---|---|
| Aplikacja „Ketchup Player” startuje na emulatorze TV (API 34) i telefonie | ❌ niezweryfikowane | brak urządzenia/KVM w chmurze; ekran startowy tylko w Roborazzi (`app/src/test/screenshots/start_screen.png`); do sprawdzenia przez właściciela lokalnie |
| Release z R8 działa | ⚠️ częściowo | `:app:assembleRelease` buduje się (APK ~2 MB, niepodpisany); uruchomienia na urządzeniu brak; podpis kluczem CI — nie skonfigurowany (sekrety poza repo) |
| CI zielone | ✅ | run #14: https://github.com/Jack4B/ketchup-player/actions/runs/41220268049 (build-logic, reguły modułów, Spotless, Detekt, testy, Roborazzi verify, Lint, debug, release R8, gitleaks) |
| Złamanie „feature nie zależy od feature” łamie build | ✅ | ręcznie: dodanie `:feature:home → :feature:vod` daje `Naruszone reguły zależności modułów: :feature:home -> :feature:vod`; test logiki: `ModuleRulesTest` |
| `network_security_config`, `RedactingTree`, szkielet `SecretStore` z testami | ✅ | `RedactorTest`, `SecretStoreTest`, plik XML w `app/src/main/res/xml` |
| Hilt, Detekt/ktlint/Lint, Roborazzi | ✅ | kroki CI; zrzuty w `*/src/test/screenshots` |
| Nowy ekran = Roborazzi + test fokusu D-pad (CLAUDE.md) | ✅ | `EpgSourcesScreenTest` (lista, formularz, D-pad+OK, BACK) |

## Metryki
Brak (aplikacja bez funkcji). Rozmiar release APK: ~2 MB.

## Odstępstwa od konspektu (ADR)
- **Teleman domyślnie WYŁ.** (zalecenie §13.3) — `docs/adr/teleman.md`, do potwierdzenia 🔒.
- **JDK:** build lokalnie na JDK 17; maszyna miała 21. CI: Temurin 17.
- **`mavenCentral` w tej sesji:** `repo.maven.apache.org` zwracał 429; lokalnie obejście init-scriptem poza repo, CI bez zmian.
- **Lint `Typos` wyłączony** (słownik angielski, UI po polsku), `InsecureBaseConfiguration` wyłączony punktowo w `network_security_config` (wymóg §8.1).
- **Baseline Roborazzi commituje CI** (`github-actions[bot]`), bo nie mam jak pobrać artefaktów.
- **Opcje poza konspektem:** brak Tink/Keystore w `SecretStore` (Faza D), DataStore z JSON dla własnych źródeł EPG (migracja do Room w Fazie D).

## Ryzyka / znane problemy
- Brak weryfikacji na prawdziwym TV; fokus i overscan sprawdzone tylko Roborazzi.
- Placeholdery ikony/banera w `app` (Faza B).
- Przycisk i listy używają domyślnych komponentów `tv-material` w ciemnym schemacie; docelowy design system dopiero w Fazie B.
- URL własnych źródeł EPG może zawierać hasło (`xmltv.php?password=`); trafia do DataStore jawnie do czasu przeniesienia do `SecretStore` (Faza D).

## Sprzeczność w konspekcie: §10.2 (`:legacy:policy-java`) vs zakaz katalogu `legacy/`
- §10.1 pkt 2 każe utworzyć tymczasowy moduł `:legacy:policy-java` z **kopią klas Java** (port pod osłoną testów).
- §14 / CLAUDE.md: „brak katalogu `legacy/` ze starymi źródłami”, zakaz wklejania starych plików BK Player Next.
- **Propozycja rozstrzygnięcia:** nie tworzyć `legacy/` w tym repo. Golden-master generować **poza repo** (osobny lokalny/prywatny projekt Gradle z kopią Javy), a do repo wchodzą tylko wyniki: fixture'y wejść i oczekiwane wyjścia jako JSON/Kotlin + testy charakteryzujące w `:core:policy`. Logikę, której nie da się opisać fixture'em, opisujemy w `docs/PORTING.md`/commit message (zgodnie z §14). Dowód „te same testy zielone na Javie i Kotlinie” zapisujemy jako log w raporcie Fazy P. Wymaga zgody właściciela 🔒.

## Pytania do właściciela
**Blokujące 🔒**
1. Zgoda na rozstrzygnięcie §10.2 vs `legacy/` powyżej (blokuje start Fazy P).
2. Domyślny stan Telemana (obecnie WYŁ.) i `applicationId` (obecnie `pl.ketchupplayer.tv`).
3. Uruchomienie `app-debug.apk` na emulatorze TV API 34 i telefonie (jedyny punkt DoD niemożliwy w chmurze).

**Nieblokujące**
4. Klucz podpisu release w CI (secret `KEYSTORE_*`) — kiedy i jak.
5. Czy utrzymać ekran „Źródła EPG” w Ustawieniach docelowo pod tą nazwą.

## Plan następnej fazy
Zgodnie z §12: Faza B (branding, `core:designsystem`, komponenty, galeria) oraz równolegle Faza P po decyzji 🔒 nr 1. Następnie D (Room, providery M3U/Xtream/Stalker) i E (XMLTV, Teleman, Radar) — źródła EPG z tej fazy zostaną tam podpięte.
