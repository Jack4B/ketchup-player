---
name: ketchup-pamiec
description: Pamięć projektu Ketchup Player. Użyj na początku KAŻDEJ sesji i przed każdą paczką prac: wczytaj plan z checklistą (docs/PLAN-1.0.md), działaj wg niego, odhaczaj zrobione i dopisuj nowe zgłoszenia z boxa.
---
# Ketchup Player: pamięć i tryb pracy

## Na starcie sesji (obowiązkowo)
0. Przeczytaj `docs/HANDOFF.md` (migawka ostatniej sesji, nie backlog; `/wczytaj-projekt`). Plan poniżej nadal wygrywa.
1. Przeczytaj `docs/PLAN-1.0.md` (jedyne źródło prawdy: etap 1 i etap 2, checklisty `[ ]`/`[x]`) i `CLAUDE.md`.
2. Sprawdź `git log --oneline -15` oraz stan CI ostatniego commita (`get_check_runs`, nie list_workflow_runs).
3. Powiedz użytkownikowi jednym zdaniem, który punkt planu robisz teraz.

## Zasady dostarczania (użytkownik tego wymaga)
- Z KAŻDYM APK: APK + aktualny `tools/tvbox/ketchup_tvbox_test.sh` + komplet komend Termux (rm/cp/sed CRLF/chmod/adb connect/uruchomienie z nazwą APK). APK dopiero po paczce kilku punktów, nie po jednym.
- Nigdy nie commituj/nie pushuj przy czerwonych testach lokalnych. Najpierw `spotlessApply detekt` + testy zmienionych modułów, potem commit i push. Po `git pull --rebase` konflikty PNG: `--theirs`.
- Nowy błąd z boxa: NAJPIERW test, który go odtwarza, potem poprawka. Nowy ekran: Roborazzi + test D-pad + scenariusz E2E.
- Sekrety: adres listy z loginem i hasłem NIGDY do repo, raportów ani logów.
- Zrzuty poglądowe wysyłaj (SendUserFile), sprawdź je przed wysłaniem (ciemne tło, realistyczne dane, kilka pozycji).
- Status (HANDOFF + punkty w `docs/PLAN-1.0.md`) aktualizuj w tym samym PR przed końcowym CI, nie po merge. Odhaczaj tylko spełnione kryteria (stany: nagłówek `docs/HANDOFF.md`). Commit i push dopiero po zgodzie użytkownika.

## Zasady techniczne (nauczone na błędach)
- Repozytoria ze stanem (EPG, katalog) muszą być `@Singleton` w DataModule.
- Gating fokusu szyny: GÓRA/DÓŁ w szynie obsługuje jawny handler; BACK z listy kategorii Ustawień idzie na szynę.
- Robolectric: pierwszy klawisz po starcie to tylko zmiana trybu wejścia (ShiftLeft); `Key` nie może być vararg.
- Sieć: User-Agent wybiera użytkownik (domyślnie okhttp/4.10.0, odtwarzacz VLC), lista w Ustawieniach → Sieć; wybór w Room (`providers.userAgent`), własny UA w `SecretStore`.
- CI: exit 134 w testach = crash runnera, nie błąd kodu; uruchom ponownie.
- Zrzuty Roborazzi nagrywa WYŁĄCZNIE CI (push na faza-* robi `recordRoborazziDebug` i commit bota). Lokalnie wolno je
  generować tylko do obejrzenia, potem cofnąć: `git checkout -- '*/src/test/screenshots'`. Zrzuty z mojej maszyny różnią
  się renderowaniem od CI i po commicie psują weryfikację na PR (`verifyRoborazziDebug`), a `git add -A` je wciąga.
- Przed pushem uruchamiaj lint osobno (`./gradlew lintDebug`), nie razem z testami: razem wywraca `lintAnalyzeDebugUnitTest`.

