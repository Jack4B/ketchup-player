---
description: Wczytaj stan projektu z docs/HANDOFF.md (tylko odczyt) i zaczekaj na decyzję
disable-model-invocation: true
---

# /wczytaj-projekt

**Tylko odczyt.** Bez edycji plików, bez commit/push/PR/merge, bez checkout/fetch/reset i bez uruchamiania zmieniających komend.
Wywołaj skill `ketchup-pamiec` (zasady pamięci); reguły mikro-PR są w `ketchup-workflow`.

1. Przeczytaj `CLAUDE.md`, `docs/HANDOFF.md`, końcówkę `docs/HISTORIA-SESJI.md` oraz otwarte punkty
   `docs/PLAN-FINALNY.md` i `docs/PLAN-1.0.md`. Brak HANDOFF = napisz to i zaproponuj `/domknij-projekt` albo `/next-step`.
2. Zmierz git (tylko odczyt): `git status --short --branch`, `git rev-parse HEAD`, `git log --oneline -5`,
   `git ls-remote --heads origin`. Nie zakładaj, że PR z HANDOFF jest nadal otwarty — sprawdź jego live status.
3. CI: sprawdź check runs dla bieżącego HEAD i dla `verified_code_sha` narzędziami GitHub dostępnymi w sesji
   (np. `pull_request_read` → `get_check_runs`). Gdy narzędzia lub dostępu brak: napisz **niezweryfikowane**, nie zgaduj.
4. Uruchom walidator: `python3 -B tools/handoff/validate_handoff.py`.
   Walidator sprawdza strukturę i wykrywalne rozbieżności, **nie** dowodzi prawdziwości opisu.
   ERROR podaj jako błąd formalny; WARN jako ostrzeżenie do oceny (nie jako pewny błąd).
5. Wypisz po polsku, krótko:
   - stan: status, gałąź, PR, `base_sha` / `verified_code_sha`, dirty tree (osobno: zmiany poza plikami handoff/historii),
   - wyniki testów z HANDOFF (komenda, PASS/FAIL/NOT_RUN, SHA lub `working_tree`) i czy dotyczą bieżącego HEAD,
   - ostrzeżenia walidatora i rozbieżności względem git/CI,
   - proponowaną następną czynność z HANDOFF; przy rozbieżności z „pierwszym otwartym punktem” planu wygrywa plan.
6. **Zatrzymaj się i czekaj** na decyzję użytkownika. Niczego nie implementuj.
