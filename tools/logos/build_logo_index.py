#!/usr/bin/env python3
"""Buduje zwarty indeks logo stacji z publicznej bazy iptv-org (dane CC0).

Wejście:  https://iptv-org.github.io/api/channels.json i logos.json
Wyjście:  core/data/src/main/assets/logos/iptv-org.tsv, wiersz: id <TAB> nazwy (znormalizowane, rozdzielone '|') <TAB> url
Użycie:   python3 tools/logos/build_logo_index.py [PL GB DE ...]
Wybieramy tylko PNG (Coil bez dekodera SVG), a z kilku wariantów największy do 1000 px szerokości.
"""
import json, re, sys, unicodedata, urllib.request, pathlib

API = "https://iptv-org.github.io/api/"


def norm(s: str) -> str:
    s = unicodedata.normalize("NFKD", s.lower())
    s = "".join(c for c in s if not unicodedata.combining(c))
    s = s.replace("ł", "l")
    s = re.sub(r"\b(hd|fhd|uhd|4k|sd|hevc|h265|tv)\b", " ", s)  # sufiksy jakości i „tv” nie rozróżniają stacji
    s = re.sub(r"[^a-z0-9]+", "", s)
    return s


def get(name):
    with urllib.request.urlopen(API + name, timeout=120) as r:
        return json.load(r)


def main():
    countries = set(a.upper() for a in sys.argv[1:]) or {"PL"}
    channels = [c for c in get("channels.json") if c.get("country") in countries and not c.get("closed")]
    best = {}
    for l in get("logos.json"):
        if l.get("format") != "PNG" or not l.get("url", "").startswith("https://"):
            continue
        w = l.get("width") or 0
        cur = best.get(l["channel"])
        if w <= 1000 and (cur is None or w > cur[0]):
            best[l["channel"]] = (w, l["url"])
    out = []
    for c in channels:
        if c["id"] not in best:
            continue
        names = {norm(n) for n in [c["name"], *c.get("alt_names", [])] if norm(n)}
        names.add(norm(c["id"].rsplit(".", 1)[0]))
        names.discard("")
        out.append(f"{c['id']}\t{'|'.join(sorted(names))}\t{best[c['id']][1]}")
    dest = pathlib.Path(__file__).resolve().parents[2] / "core/data/src/main/assets/logos/iptv-org.tsv"
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text("\n".join(sorted(out)) + "\n", encoding="utf-8")
    print(f"{len(out)} stacji, {dest.stat().st_size} B -> {dest}")


main()
