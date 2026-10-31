# Inwentarz portu logiki (KONSPEKT §10)

Decyzja o sposobie portu: [`docs/adr/legacy-policy-port.md`](adr/legacy-policy-port.md).
Legenda statusu: ✅ ze spec + testy · 🟡 częściowo (spec opisuje tylko część) · ⏳ czeka na fixture'y/opis · ➖ nie portować.

| Klasa (stara) | Kat. | Moduł docelowy | Status | Uwagi |
|---|---|---|---|---|
| `BkFailoverPolicy` | L | `:core:policy` | 🟡 | stałe z §10.1 pkt 4 (progi, cooldown, retry, opóźnienie TV); `enabled()` odtworzone z kluczy ustawień §9 — do weryfikacji |
| `BkTvFailoverMatch`, `BkVodFailoverMatch` | L | `:core:policy` | ⏳ | spec opisuje tylko cel (§7.1.1) |
| `BkSmartBufferPolicy` | L | `:core:policy` | ⏳ | parametry profili nieopisane w spec |
| `StreamRecoveryPolicy`, `TvBufferSessionPolicy`, `PlaybackStabilityPolicy` | L | `:core:policy` | ⏳ | |
| `RadarGuidePolicy`, `RadarTodayFilmPolicy`, `Radar*Policy` | L | `:epg:radar` / `:core:policy` | ⏳ | golden master na `pltv.xml.gz` + HTML Teleman |
| `StreamUserAgent`, `NetworkRoutePolicy`, `CinemaResumePolicy`, `SeriesEpisode*`, `CatalogOrder`, `CategoryMetadata` | L | `:core:policy` | ⏳ | |
| `TvPremiumContentPolicy`, `TvCarouselPolicy`, `BkLiveHomePolicy` | L/M | `:core:policy` | ⏳ | |
| `MemoryStabilityPolicy`, `HomeSmartMemoryPolicy`, `DiskCacheSafety`, `ArtLoadPolicy` | L/M | `:core:policy` | ⏳ | |
| Parsery `Importers`, `Teleman`, `Captions`/`CaptionFile` | L | `:provider:*`, `:epg:*`, `:core:data` | ⏳ | Faza D/E |
| Testy „tekstowe” (~146) | — | — | ➖ | zachowanie zapisać w `docs/KNOWN-EDGE-CASES.md` (§10.1 pkt 5) |

## Brakujące wejście od właściciela
Stary kod nie jest dostępny w tym środowisku. Do zamknięcia fazy P potrzebne są fixture'y golden-master wygenerowane lokalnie (format: JSON `{ "case": "...", "input": {...}, "expected": {...} }`).
