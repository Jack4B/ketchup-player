#!/usr/bin/env python3
"""Faza 2: etykiety (pl, en) dla QID-ów z chunków fetch_films.py, partiami po 50 przez wbgetentities.
Użycie: python3 fetch_labels.py KATALOG_CHUNKS   -> KATALOG_CHUNKS/labels.json (wznawialne)
"""
import concurrent.futures, glob, json, os, sys, time, urllib.parse, urllib.request

API = "https://www.wikidata.org/w/api.php"
UA = "KetchupPlayerIndexBuilder/1.0 (offline film index for a personal Android TV app)"


def batch(ids):
    q = urllib.parse.urlencode({"action": "wbgetentities", "ids": "|".join(ids), "props": "labels",
                                "languages": "pl|en", "format": "json"})
    for attempt in range(8):
        try:
            req = urllib.request.Request(API + "?" + q, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                data = json.load(r)
            out = {}
            for qid, e in data.get("entities", {}).items():
                lab = e.get("labels", {})
                out[qid] = {k: lab[k]["value"] for k in ("pl", "en") if k in lab}
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
    chunks = [todo[i:i + 50] for i in range(0, len(todo), 50)]
    print(len(qids), "QID-ów,", len(todo), "do pobrania,", len(chunks), "partii", flush=True)
    done = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as ex:
        for res in ex.map(batch, chunks):
            if res:
                have.update(res)
            done += 1
            if done % 100 == 0:
                json.dump(have, open(path, "w", encoding="utf-8"), ensure_ascii=False)
                print(done, "/", len(chunks), flush=True)
    json.dump(have, open(path, "w", encoding="utf-8"), ensure_ascii=False)
    print("gotowe", len(have), flush=True)


if __name__ == "__main__":
    main()
