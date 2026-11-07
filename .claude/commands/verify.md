---
description: Bramka przed PR — gradle lokalnie albo CI na GitHub
disable-model-invocation: true
---

# /verify

Wywołaj skill `ketchup-workflow` (reguły gałęzi/PR).

1. `git status` + `git diff --stat` — potwierdź zakres.
2. Spróbuj kolejno (raportuj exit code):
   - `./gradlew spotlessCheck`
   - `./gradlew testDebugUnitTest`
   - `./gradlew :app:assembleDebug`
   - jeśli ruszany UI/zasoby: `./gradlew lintDebug`
3. Jeśli gradle / SDK niedostępne (Cloud bez Android SDK):
   - nie udawaj sukcesu
   - upewnij się, że zmiany są commitnięte i wypchnięte na `feat/*`
   - sprawdź CI przez `gh` / narzędzia GitHub (check runs na SHA gałęzi; nie zgaduj)
4. Podsumowanie po polsku:
   - lokalne: OK / FAIL / pominięte (powód)
   - CI: zielone / czerwone / brak PR — z linkiem
   - gotowe do PR→`dev`? (tak/nie + braki)
5. Nie force-push. Nie merge. Nie push na `main`.
