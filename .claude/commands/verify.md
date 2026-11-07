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
3. Wynik każdej kontroli: **PASS**, **FAIL** (błąd kodu/testu) albo **NOT_RUN** z powodem (np. brak Android SDK) — brak SDK nie jest sukcesem.
   - sprawdź CI przez `gh` / narzędzia GitHub (check runs na dokładnym SHA gałęzi; nie zgaduj; bez dostępu: „niezweryfikowane”)
   - jeśli zmiany nie są commitnięte/wypchnięte: napisz to i **zapytaj użytkownika**; sam nie commituj, nie pushuj i nie otwieraj PR
4. Podsumowanie po polsku:
   - lokalne: PASS / FAIL / NOT_RUN (powód), z SHA albo `working_tree` (PASS na `working_tree` nie jest dowodem dla commita)
   - CI: zielone / czerwone / brak PR / niezweryfikowane — z linkiem i SHA
   - gotowe do PR→`dev`? (tak/nie + braki)
5. **Nigdy po cichu commit, push ani PR.** Nie force-push. Nie merge. Nie push na `main`.
