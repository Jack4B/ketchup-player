# ADR: stary kod BK Player Next jako materiał referencyjny, nie wzorzec

Data: 2026-11-01. Decyzja właściciela: stara aplikacja ma błędy i nie wszystko w niej działa, więc jej zachowanie
**nie jest wiążące** (zmienia to wymóg „1:1” z docs/KONSPEKT.md §10.1 pkt 4 i §10.3, w zakresie, w jakim wymagał zgody
właściciela na każdą rozbieżność).

Zasady:
1. Test golden master (wartości wygenerowane starym kodem poza repo; do repo trafia tylko fixture liczbowy) służy do
   wykrycia rozbieżności, nie do ich zakazu.
2. Przy rozbieżności decyduje implementujący: jeśli nowe zachowanie jest lepsze lub stare jest błędem, zostaje nowe.
   Rozbieżność jest wtedy zapisana w teście (jawny wyjątek z komentarzem) i w `docs/KNOWN-EDGE-CASES.md`.
3. Zachowania widoczne dla użytkownika (kolejność w Radarze, wznowienie odcinka) sprawdza właściciel na boxie; to
   nie blokuje wydania.
4. Kod starej aplikacji nie trafia do repo (CLAUDE.md).
