---
description: Zapisz stan sesji do docs/HANDOFF.md i docs/HISTORIA-SESJI.md, zwaliduj i pokaż diff (bez publikacji)
argument-hint: "[opcjonalnie: krótka uwaga do wpisu]"
disable-model-invocation: true
---

# /domknij-projekt

Wywołaj skill `ketchup-workflow` (mikro-PR) i `ketchup-pamiec`. **Zapis tylko do `docs/HANDOFF.md` i `docs/HISTORIA-SESJI.md`.**
**Bez commit, push, PR i merge** — nawet gdy `settings.json` pozwala na te komendy. Publikację zatwierdza osobno użytkownik.

1. **Pomiar najpierw, edycja potem.** Zanim dotkniesz plików handoff: `git status --short --branch`, `git rev-parse HEAD`,
   `git branch --show-current`. Zapisz osobno: pełną listę zmian drzewa (`dirty_files`) oraz zmiany poza plikami
   handoff/historii (`outside_handoff_changes`). Nie pisz `clean`, jeśli drzewo ma zmiany.
2. CI i PR: live status PR i check runs dla dokładnego SHA narzędziami GitHub z sesji. Brak dostępu = **niezweryfikowane**.
3. Uaktualnij `docs/HANDOFF.md` według zasad z jego nagłówka (pola, `task_state`, wiersze `test:`, PASS/FAIL/NOT_RUN,
   `working_tree`, znaczenie `base_sha` / `verified_code_sha`). `updated_at`: `TZ=Europe/Warsaw date -Iseconds`.
   Brak SDK = NOT_RUN tylko dla kontroli, których naprawdę nie dało się wykonać. Zero sekretów, adresów list z loginem i hasłem.
4. Dopisz **na końcu** `docs/HISTORIA-SESJI.md` krótki wpis (data, co zrobiono, wynik, następny krok). Nie przepisuj starych wpisów.
5. Plany (`docs/PLAN-*.md`): odhaczaj tylko kryteria faktycznie spełnione (`[~]` dla częściowych). HANDOFF nie jest backlogiem.
6. Zwaliduj: `python3 -B tools/handoff/validate_handoff.py` oraz `git diff --check`.
7. Pokaż `git diff` (i nieśledzone pliki) oraz podsumowanie: wyniki walidatora, testy, niewykonane, ograniczenia.
8. **Zatrzymaj się.** Zapytaj o osobną zgodę na commit/push/PR. Pliki Cloud nie są trwałym handoffem między świeżymi sesjami,
   dopóki nie są commitnięte i wypchnięte.
