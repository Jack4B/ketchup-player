#!/usr/bin/env python3
"""Pakuje wyniki fetch_films.py do SQLite (asset aplikacji): film(id, imdb, tmdb, fw, year, kind, pl, en, orig, plwiki)
i title(norm, film) z indeksem po znormalizowanym tytule. Normalizacja (norm) MUSI być taka sama jak w aplikacji
(FilmIndex.normalize): małe litery, NFD bez znaków łączących, ł->l, tylko a-z0-9.

Użycie: python3 pack_index.py KATALOG_CHUNKS WYJSCIE.db [title.ratings.tsv.gz]
Źródło danych: Wikidata (CC0). Trzeci argument (opcjonalny): publiczny plik ocen IMDb
(https://datasets.imdbws.com/title.ratings.tsv.gz, do użytku osobistego i niekomercyjnego); oceny trafiają do kolumn
rating (dziesiąte części: 74 = 7,4) i votes.
"""
import glob, gzip, json, os, re, sqlite3, sys, unicodedata, urllib.parse

MARKS = {"ł": "l", "đ": "d", "ø": "o", "ß": "ss", "æ": "ae", "œ": "oe", "ı": "i"}


def normalize(s):
    s = s.lower()
    for k, v in MARKS.items():
        s = s.replace(k, v)
    s = unicodedata.normalize("NFD", s)
    return "".join(c for c in s if c.isascii() and c.isalnum())


def val(b, k):
    v = b.get(k)
    return v["value"] if v else None


def load_ratings(path):
    """tconst -> (ocena w dziesiątych częściach, liczba głosów) z title.ratings.tsv.gz."""
    ratings = {}
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        next(fh)  # nagłówek: tconst, averageRating, numVotes
        for line in fh:
            t, r, v = line.rstrip("\n").split("\t")
            ratings[t] = (round(float(r) * 10), int(v))
    return ratings


def main():
    src, out = sys.argv[1], sys.argv[2]
    ratings = load_ratings(sys.argv[3]) if len(sys.argv) > 3 else {}
    labels_path = os.path.join(src, "labels.json")
    labels = json.load(open(labels_path, encoding="utf-8")) if os.path.exists(labels_path) else {}
    films = {}
    for path in sorted(glob.glob(os.path.join(src, "*_*.json"))):
        if os.path.basename(path) == "labels.json":
            continue
        kind = 1 if os.path.basename(path).startswith("series") else 0
        for b in json.load(open(path, encoding="utf-8")):
            imdb = val(b, "imdb")
            d = val(b, "d")
            if not imdb or not d:
                continue
            year = int(d[:4]) if d[:1].isdigit() else None
            if year is None:
                continue
            f = films.setdefault(imdb, {"imdb": imdb, "kind": kind, "year": year, "tmdb": None, "fw": None,
                                        "pl": None, "en": None, "orig": None, "plwiki": None})
            f["year"] = min(f["year"], year)
            lab = labels.get(val(b, "f").rsplit("/", 1)[-1], {})
            f["pl"] = f["pl"] or lab.get("pl") or val(b, "pl")
            f["en"] = f["en"] or lab.get("en") or val(b, "en")
            for k in ("tmdb", "fw", "orig"):
                f[k] = f[k] or val(b, k)
            w = val(b, "plwiki")
            if w and not f["plwiki"]:
                f["plwiki"] = urllib.parse.unquote(w.rsplit("/wiki/", 1)[-1])
    for f in films.values():
        # Bez polskiej etykiety: tytuł ze strony plwiki (bez dopisku w nawiasie, np. „(film 2005)”).
        if not f["pl"] and f["plwiki"]:
            f["pl"] = re.sub(r"\s*\(.*\)$", "", f["plwiki"].replace("_", " ")) or None
    if os.path.exists(out):
        os.remove(out)
    db = sqlite3.connect(out)
    db.executescript("""
        CREATE TABLE film(id INTEGER PRIMARY KEY, imdb TEXT NOT NULL, tmdb INTEGER, fw TEXT, year INTEGER NOT NULL,
                          kind INTEGER NOT NULL, pl TEXT, en TEXT, orig TEXT, plwiki TEXT, rating INTEGER, votes INTEGER);
        CREATE TABLE title(norm TEXT NOT NULL, film INTEGER NOT NULL);
        CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT);
    """)
    n = 0
    for f in films.values():
        names = [t for t in (f["pl"], f["en"], f["orig"]) if t]
        norms = {normalize(t) for t in names}
        norms.discard("")
        if not norms:
            continue
        n += 1
        tmdb = int(f["tmdb"]) if f["tmdb"] and f["tmdb"].isdigit() else None
        rating, votes = ratings.get(f["imdb"], (None, None))
        db.execute("INSERT INTO film VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                   (n, f["imdb"], tmdb, f["fw"], f["year"], f["kind"], f["pl"], f["en"] if f["en"] != f["pl"] else None,
                    f["orig"] if f["orig"] not in (f["pl"], f["en"]) else None, f["plwiki"], rating, votes))
        db.executemany("INSERT INTO title VALUES(?,?)", [(x, n) for x in norms])
    db.execute("CREATE INDEX title_norm ON title(norm)")
    db.execute("INSERT INTO meta VALUES('version','2')")
    if ratings:
        db.execute("INSERT INTO meta VALUES('ratings','IMDb, publiczne dane (datasets.imdbws.com), uzytek osobisty')")
    db.execute("INSERT INTO meta VALUES('source','Wikidata (CC0), filmy i seriale od 1980 z identyfikatorem IMDb')")
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(n, "pozycji,", os.path.getsize(out) // 1024, "KB")


if __name__ == "__main__":
    main()
