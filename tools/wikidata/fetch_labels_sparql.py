#!/usr/bin/env python3
"""Faza 2 (szybsza): etykiety pl/en dla QID-ów z chunków fetch_films.py przez SPARQL (VALUES po 400 QID-ów).
Użycie: python3 fetch_labels_sparql.py KATALOG_CHUNKS   -> KATALOG_CHUNKS/labels.json (wznawialne, ten sam format
co w wersji przez wbgetentities). Wikidata (CC0).
"""
import concurrent.futures, glob, json, os, sys, time, urllib.parse, urllib.request

ENDPOINT = "https://query.wikidata.org/sparql"
UA = "KetchupPlayerIndexBuilder/1.0 (offline film index for a personal Android TV app)"
BATCH = 400


def query(qids):
    values = " ".join("wd:" + q for q in qids)
    return ("SELECT ?f ?pl ?en WHERE { VALUES ?f { %s } "
            "OPTIONAL { ?f rdfs:label ?pl FILTER(LANG(?pl) = 'pl') } "
            "OPTIONAL { ?f rdfs:label ?en FILTER(LANG(?en) = 'en') } }" % values)


def batch(qids):
    data = urllib.parse.urlencode({"query": query(qids), "format": "json"}).encode()
    for attempt in range(6):
        try:
            req = urllib.request.Request(ENDPOINT, data=data, headers={
                "User-Agent": UA, "Accept": "application/sparql-results+json"})
            with urllib.request.urlopen(req, timeout=90) as r:
                rows = json.load(r)["results"]["bindings"]
            out = {q: {} for q in qids}
            for b in rows:
                qid = b["f"]["value"].rsplit("/", 1)[-1]
                for k in ("pl", "en"):
                    if k in b:
                        out[qid][k] = b[k]["value"]
            return out
        except Exception:
            time.sleep(5 * (attempt + 1))
    return None


def main():
    src = sys.argv[1]
    path = os.path.join(src, "labels.json")
    have = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
    qids = set()
    for p in glob.glob(os.path.join(src, "*_*.json")):
        if os.path.basename(p) == "labels.json":
            continue
        for b in json.load(open(p, encoding="utf-8")):
            qids.add(b["f"]["value"].rsplit("/", 1)[-1])
    todo = sorted(q for q in qids if q not in have)
    chunks = [todo[i:i + BATCH] for i in range(0, len(todo), BATCH)]
    print(len(qids), "QID-ów,", len(todo), "do pobrania,", len(chunks), "partii", flush=True)
    done = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as ex:
        for res in ex.map(batch, chunks):
            if res:
                have.update(res)
            done += 1
            if done % 5 == 0:
                json.dump(have, open(path, "w", encoding="utf-8"), ensure_ascii=False)
                print(done, "/", len(chunks), flush=True)
    json.dump(have, open(path, "w", encoding="utf-8"), ensure_ascii=False)
    print("gotowe", len(have), flush=True)


if __name__ == "__main__":
    main()
