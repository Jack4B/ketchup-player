# Indeks filmów z Wikidata (offline w APK)

Źródło: Wikidata (CC0), filmy (Q11424) i seriale (Q5398426) od 1980 z identyfikatorem IMDb. Indeks daje aplikacji
`tytuł (PL/EN/oryginalny) + rok -> id IMDb, id TMDB, strona polskiej Wikipedii`, bez zapytania do sieci. Nie zawiera ocen.

Odtworzenie (ok. 1 godzina, głównie czekanie na Wikidata Query Service; skrypty są wznawialne):

    python3 tools/wikidata/fetch_films.py /tmp/wd 1980 2026      # faza 1: id, daty, plwiki (kwartały/lata)
    python3 tools/wikidata/fetch_labels.py /tmp/wd                # faza 2: etykiety pl/en (wbgetentities)
    python3 tools/wikidata/pack_index.py /tmp/wd core/data/src/main/assets/film_index.db

Normalizacja tytułu (`normalize`) jest taka sama jak `FilmTitles.normalize` w aplikacji; zmiana jednej strony wymaga
zmiany drugiej i wartości w `FilmIndexTest.normalizacjaJestZgodnaZSkryptemPython`.
