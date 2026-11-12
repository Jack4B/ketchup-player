# ADR: port logiki `*Policy` bez katalogu `legacy/` (rozstrzygnięcie §10.1/§10.2 vs §14)

Status: **zdecydowane** (właściciel dał zgodę na samodzielne rozstrzygnięcie, 2026-10-31).

## Problem
§10.1 pkt 2 każe utworzyć tymczasowy moduł `:legacy:policy-java` z kopią klas Java, a §14 i `CLAUDE.md` zakazują katalogu `legacy/` i wklejania starych plików BK Player Next.
Dodatkowo stary kod nie jest dostępny w repo ani w środowisku chmurowym.

## Decyzja
1. W tym repo **nie powstaje** `legacy/` ani `:legacy:policy-java`. Zakaz z §14 ma pierwszeństwo.
2. Dowód zgodności zachowania (golden master) generuje się **poza repo**: osobny, lokalny projekt właściciela z kopią Javy produkuje pliki `fixtures/*.json` (wejścia → oczekiwane wyjścia). Do repo trafiają **wyłącznie fixture'y i testy** (`core/policy/src/test/resources/golden/`), bez kodu Javy.
3. Do czasu dostarczenia fixture'ów port opiera się na **specyfikacji** (`KONSPEKT.md`): stałe i algorytmy opisane wprost są portowane z testami wartości granicznych; elementy tylko nazwane, ale nieopisane, mają w `docs/PORTING.md` status „czeka na fixture'y”.
4. Każda klasa portowana ze specyfikacji ma w KDoc źródło (`KONSPEKT §…`) i oznaczenie, co jest odtworzone ze spec, a co zgadnięte. Zgadnięte zachowania są wyliczone w `PORTING.md` do weryfikacji względem golden master.
5. Dowód „te same testy zielone na Javie i Kotlinie” to log z lokalnego uruchomienia właściciela dołączony do raportu Fazy P.

## Konsekwencje
- `:core:policy` pozostaje czystą JVM bez `android.*`.
- Pokrycie ≥ 90% i golden master w CI wymagają fixture'ów od właściciela; do tego czasu CI sprawdza testy wartości granicznych.
- Zmiana wyniku względem fixture'ów = czerwony test = zgoda właściciela (etykieta PR `frozen-approved`).
