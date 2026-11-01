---
name: ketchup-pamiec
description: Pamięć projektu Ketchup Player. Użyj na początku KAŻDEJ sesji i przed każdą paczką prac: wczytaj plan z checklistą (docs/PLAN-1.0.md), działaj wg niego, odhaczaj zrobione i dopisuj nowe zgłoszenia z boxa.
---
# Ketchup Player: pamięć i tryb pracy

## Na starcie sesji (obowiązkowo)
1. Przeczytaj `docs/PLAN-1.0.md` (jedyne źródło prawdy: etap 1 i etap 2, checklisty `[ ]`/`[x]`) i `CLAUDE.md`.
2. Sprawdź `git log --oneline -15` oraz stan CI ostatniego commita (`get_check_runs`, nie list_workflow_runs).
3. Powiedz użytkownikowi jednym zdaniem, który punkt planu robisz teraz.

## Zasady dostarczania (użytkownik tego wymaga)
- Z KAŻDYM APK: APK + aktualny `tools/tvbox/ketchup_tvbox_test.sh` + komplet komend Termux (rm/cp/sed CRLF/chmod/adb connect/uruchomienie z nazwą APK). APK dopiero po paczce kilku punktów, nie po jednym.
- Nigdy nie commituj/nie pushuj przy czerwonych testach lokalnych. Najpierw `spotlessApply detekt` + testy zmienionych modułów, potem commit i push. Po `git pull --rebase` konflikty PNG: `--theirs`.
- Nowy błąd z boxa: NAJPIERW test, który go odtwarza, potem poprawka. Nowy ekran: Roborazzi + test D-pad + scenariusz E2E.
- Sekrety: adres listy z loginem i hasłem NIGDY do repo, raportów ani logów.
- Zrzuty poglądowe wysyłaj (SendUserFile), sprawdź je przed wysłaniem (ciemne tło, realistyczne dane, kilka pozycji).
- Po każdym dostarczeniu odhacz punkty w `docs/PLAN-1.0.md` i zacommituj plan.

## Zasady techniczne (nauczone na błędach)
- Repozytoria ze stanem (EPG, katalog) muszą być `@Singleton` w DataModule.
- Gating fokusu szyny: GÓRA/DÓŁ w szynie obsługuje jawny handler; BACK z listy kategorii Ustawień idzie na szynę.
- Robolectric: pierwszy klawisz po starcie to tylko zmiana trybu wejścia (ShiftLeft); `Key` nie może być vararg.
- Sieć: User-Agent wybiera użytkownik (domyślnie OTT Navigator), lista w Ustawieniach → Sieć; własne wpisy bez rejestru (`custom:`).
- CI: exit 134 w testach = crash runnera, nie błąd kodu; uruchom ponownie.
