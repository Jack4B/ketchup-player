---
name: ketchup-workflow
description: Pętla mikro-PR Ketchup Player (Cloud i lokalnie). Użyj na starcie sesji kodowej, przy /next-step, przed PR i gdy nie wiadomo jak domknąć punkt planu. Status = checkboxy w docs/PLAN-1.0.md i docs/PLAN-FINALNY.md.
---

# Workflow sesji Ketchup Player

Uzupełnia `ketchup-pamiec` (pamięć PLAN-1.0 / APK / box). Ten skill = **jak pracować dalej**: mikro-PR, Cloud, bramki 8 kroków.

## Środowisko Cloud (claude.ai/code)

- Świeży klon wybranej gałęzi; gałąź musi być na GitHub.
- **Brak TV-boxa / adb / sensownego mobile-mcp.** Nie udawaj testów na urządzeniu.
- Brak Android SDK w VM → nie blokuj się: kod + testy → push → **CI** (gitleaks, unit, lint, assemble, emulator).
- `.mcp.json` / skills z repo; user-scope MCP z laptopa nie działa.
- Status sesji: odhaczaj w planach w git — to pamięć między sesjami.

## Pętla mikro-PR (obowiązkowa)

1. Wybierz **pierwszy otwarty** punkt (`PLAN-FINALNY` / `PLAN-1.0`) albo argument użytkownika.
2. **Plan mode**: pliki, testy, DoD, ryzyka (w tym brak urządzenia). Czekaj na **OK**.
3. **TDD** (`test-driven-development`): test → implementacja minimalna (`ponytail` przy rozroście).
4. Check: `./gradlew spotlessCheck testDebugUnitTest :app:assembleDebug` (+ `lintDebug` przy UI). Brak gradle → od razu push.
5. Gałąź `feat/<krótki-opis>` → **PR tylko do `dev`**.
6. Czekaj na CI; czerwone = fix + push na **tej samej** gałęzi.
7. Raport użytkownikowi. **Nie merguj** bez decyzji.
8. Po akceptacji/merge: odhacz checkboxy w `docs/PLAN-1.0.md` / `docs/PLAN-FINALNY.md` (i krótki status w `CLAUDE.md` jeśli dotyczy) → `/clear` lub nowa sesja.

Komendy: `/next-step` (wybór + plan), `/verify` (bramka przed PR).

## Bramki 8 kroków (`docs/PLAN-FINALNY.md`)

| # | Temat | Gate |
|---|---|---|
| 1 | Jakość 1.0 (D-pad Szukaj/Lista, fokus) | Testy/CI; box = user lub świadomy defer |
| 2 | Emulator + HLS ścieżki | E/P z PLAN-TESTY-EMULATOR zielone na SHA |
| 3 | Perf | Pomiar + fix lub „OK” |
| 4 | Wydanie 1.0 | CI zielone **na tym SHA** + tag |
| 5 | Szlif v5/v5.1 | Tylko po decyzji usera |
| 6 | Security (EPG secrets, pinning) | Review + testy |
| 7 | imdb-db self-host | Nie blokuje 1.0; licencja IMDb |
| 8 | 1.1 PIN/profile/… | Po 1.0 |

Krok 2 może iść równolegle z 1, ale **blokuje** wydanie. Nie skacz bramek bez pytania.

## Reguły twarde

- PR → **`dev`**. Nigdy push/merge na **`main`**.
- Nigdy **force-push**.
- Nie merguj bez użytkownika.
- Zero sekretów w repo (Ustawienia app / sekrety CI / network secrets Cloud).
- Nie wklejaj BK Player Next. Silnik tylko przez `PlayerController`.
- UI: `strings.xml` (PL); TV: `ketchup-design-taste` + `android-tv-ui`.

## Gdy zablokowany

| Blokada | Akcja |
|---|---|
| Brak SDK / gradle fail | PR + CI; napisz w raporcie |
| Potrzeba boxa | Testy auto + checklista dla usera; nie blokuj całego kroku |
| Czerwone CI | Logi joba → fix → push |
| Niejasny wymóg | Jedno pytanie, stop |
| Kontekst pełny | `/compact` lub nowa sesja |

## Raport końca sesji / mikro-PR

Po polsku, krótko:

```
Zrobione: <1–3 punkty>
PR: <url> → dev
CI: <zielone|czerwone + job>
Checkboxy: <które odhaczone / czekają na merge>
Następne: <punkt planu>
Ryzyka / niewykonane na boxie: <…>
```
