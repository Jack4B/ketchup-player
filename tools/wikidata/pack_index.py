#!/usr/bin/env python3
"""Pakuje wyniki fetch_films.py do SQLite (asset aplikacji): film(id, imdb, tmdb, fw, year, kind, pl, en, orig, plwiki)
i title(norm, film) z indeksem po znormalizowanym tytule. Normalizacja (norm) MUSI być taka sama jak w aplikacji
(FilmIndex.normalize): małe litery, NFD bez znaków łączących, ł->l, tylko a-z0-9.

Użycie: python3 pack_index.py KATALOG_CHUNKS WYJSCIE.db
Źródło danych: Wikidata (CC0).
"""
import glob, json, os, sqlite3, sys, unicodedata, urllib.parse

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


def main():
    src, out = sys.argv[1], sys.argv[2]
    films = {}
    for path in sorted(glob.glob(os.path.join(src, "*.json"))):
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
            for k in ("tmdb", "fw", "pl", "en", "orig"):
                f[k] = f[k] or val(b, k)
            w = val(b, "plwiki")
            if w and not f["plwiki"]:
                f["plwiki"] = urllib.parse.unquote(w.rsplit("/wiki/", 1)[-1])
    if os.path.exists(out):
        os.remove(out)
    db = sqlite3.connect(out)
    db.executescript("""
        CREATE TABLE film(id INTEGER PRIMARY KEY, imdb TEXT NOT NULL, tmdb INTEGER, fw TEXT, year INTEGER NOT NULL,
                          kind INTEGER NOT NULL, pl TEXT, en TEXT, orig TEXT, plwiki TEXT);
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
        db.execute("INSERT INTO film VALUES(?,?,?,?,?,?,?,?,?,?)",
                   (n, f["imdb"], tmdb, f["fw"], f["year"], f["kind"], f["pl"], f["en"] if f["en"] != f["pl"] else None,
                    f["orig"] if f["orig"] not in (f["pl"], f["en"]) else None, f["plwiki"]))
        db.executemany("INSERT INTO title VALUES(?,?)", [(x, n) for x in norms])
    db.execute("CREATE INDEX title_norm ON title(norm)")
    db.execute("INSERT INTO meta VALUES('version','1')")
    db.execute("INSERT INTO meta VALUES('source','Wikidata (CC0), filmy i seriale od 1980 z identyfikatorem IMDb')")
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(n, "pozycji,", os.path.getsize(out) // 1024, "KB")


if __name__ == "__main__":
    main()
