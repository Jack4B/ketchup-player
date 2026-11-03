#!/usr/bin/env python3
"""Faza 1: pobiera z Wikidata (CC0) filmy i seriale od 1980 z identyfikatorami IMDb/TMDB/Filmweb i stroną plwiki, miesiąc po miesiącu
(tytuły: fetch_labels.py).

Użycie: python3 fetch_films.py KATALOG_WYNIKOW [ROK_OD] [ROK_DO]
Wynik: KATALOG/films_RRRR-MM.json, series_RRRR-MM.json (wiersze bindings SPARQL). Wznawialne: istniejące pliki są pomijane.
Wikidata Query Service ma limit 60 s na zapytanie i limity równoległości, więc zapytania są małe (miesiąc), z ponawianiem.
"""
import json, os, sys, time, urllib.parse, urllib.request, urllib.error, concurrent.futures

ENDPOINT = "https://query.wikidata.org/sparql"
UA = "KetchupPlayerIndexBuilder/1.0 (offline film index for a personal Android TV app)"

QUERY = """SELECT ?f ?imdb ?tmdb ?fw ?d ?plwiki WHERE {{
  ?f wdt:P31 wd:{cls}; wdt:{datep} ?d; wdt:P345 ?imdb.
  FILTER(?d >= "{a}T00:00:00Z"^^xsd:dateTime && ?d < "{b}T00:00:00Z"^^xsd:dateTime)
  OPTIONAL {{ ?f wdt:P4947 ?tmdb }}
  OPTIONAL {{ ?f wdt:P5033 ?fw }}
  OPTIONAL {{ ?plwiki schema:about ?f; schema:isPartOf <https://pl.wikipedia.org/> }}
}}"""

KINDS = {"films": ("Q11424", "P577"), "series": ("Q5398426", "P580")}


def span(kind, y, part):
    """Filmy: kwartały (part 1-4), seriale: cały rok (part 0). Zwraca (nazwa pliku, od, do)."""
    if part == 0:
        return f"{kind}_{y:04d}", f"{y:04d}-01-01", f"{y + 1:04d}-01-01"
    m = (part - 1) * 3 + 1
    end = f"{y + 1:04d}-01-01" if part == 4 else f"{y:04d}-{m + 3:02d}-01"
    return f"{kind}_{y:04d}-Q{part}", f"{y:04d}-{m:02d}-01", end


def run(kind, y, part, out):
    name, a, b = span(kind, y, part)
    path = os.path.join(out, name + ".json")
    first = (part - 1) * 3 + 1
    months = [os.path.join(out, f"{kind}_{y:04d}-{mm:02d}.json") for mm in range(first, first + 3)]
    if os.path.exists(path) or (part and all(os.path.exists(p) for p in months)):
        return path, "skip"
    cls, datep = KINDS[kind]
    q = QUERY.format(cls=cls, datep=datep, a=a, b=b)
    url = ENDPOINT + "?" + urllib.parse.urlencode({"query": q})
    for attempt in range(8):
        try:
            req = urllib.request.Request(url, headers={"Accept": "application/sparql-results+json", "User-Agent": UA})
            with urllib.request.urlopen(req, timeout=100) as r:
                data = json.load(r)
            rows = data["results"]["bindings"]
            tmp = path + ".tmp"
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(rows, f, ensure_ascii=False)
            os.replace(tmp, path)
            return path, f"{len(rows)}"
        except urllib.error.HTTPError as e:
            time.sleep(int(e.headers.get("Retry-After", "0") or 0) or 10 * (attempt + 1))
        except Exception:
            time.sleep(10 * (attempt + 1))
    return path, "FAIL"


def main():
    out = sys.argv[1]
    y0 = int(sys.argv[2]) if len(sys.argv) > 2 else 1980
    y1 = int(sys.argv[3]) if len(sys.argv) > 3 else 2026
    os.makedirs(out, exist_ok=True)
    jobs = [("films", y, q) for y in range(y0, y1 + 1) for q in (1, 2, 3, 4)]
    jobs += [("series", y, 0) for y in range(y0, y1 + 1)]
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as ex:
        futs = [ex.submit(run, k, y, q, out) for k, y, q in jobs]
        for fu in concurrent.futures.as_completed(futs):
            p, st = fu.result()
            print(os.path.basename(p), st, flush=True)


if __name__ == "__main__":
    main()
