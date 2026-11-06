---
description: Wybierz następny punkt planu Ketchup i zaproponuj mikro-PR w trybie plan (bez kodu aż do OK)
argument-hint: "[opcjonalnie: id punktu np. 1.3 lub krok 2]"
disable-model-invocation: true
---

# /next-step

Wywołaj skill `ketchup-workflow`. Potem:

1. Przeczytaj `docs/PLAN-FINALNY.md` i `docs/PLAN-1.0.md` (checkboxy `[ ]` / `[x]` / `[~]`).
2. Jeśli podano argumenty: `$ARGUMENTS` — traktuj jako wskazany punkt. Inaczej weź **pierwszy otwarty** punkt w kolejności bramek (nie skacz bez pytania).
3. Wejdź w **plan mode** (tylko odczyt / plan; bez edycji kodu).
4. Wypisz:
   - punkt planu + DoD
   - pliki / moduły
   - testy (TDD; D-pad jeśli UI TV)
   - limity Cloud (brak boxa/adb → CI)
   - tytuł gałęzi `feat/…` i zakres mikro-PR (S/M)
5. Zakończ pytaniem: czekam na **OK** albo korektę. **Nie implementuj** w tej turze.
