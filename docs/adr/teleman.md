# ADR: Teleman i źródła EPG

Status: **zdecydowane przez właściciela (2026-10-31): Teleman domyślnie WŁ.** Ryzyko prawne z §13.3 pozostaje; flaga w Ustawieniach pozwala go wyłączyć. Dla dystrybucji publicznej rozważyć ponownie.

- Teleman (scraping) jest za flagą `epg_teleman_enabled`, **domyślnie WŁ.** (decyzja właściciela; §13.3 zalecał WYŁ. dla dystrybucji publicznej). Aplikacja działa w pełni bez niego.
- epg.ovh (`https://epg.ovh/pltv.xml.gz`) jest wbudowany, domyślnie WŁ., da się wyłączyć.
- Użytkownik może dodać własne źródło XMLTV (http/https) w Ustawienia → Źródła EPG; wbudowane można wyłączyć, własne także usunąć.
- Flagi i lista własnych źródeł: DataStore (`DataStoreEpgSourcesRepository`); migracja do Room (`EpgSourceEntity`) w Fazie D.
- Zdalne przełączanie flagi przez `update.json` (§13.3) — Faza OTA.
- Logika pobierania XMLTV i scrapingu Telemana: Faza E (`:epg:xmltv`, `:epg:teleman`). Na razie wbudowane są model, walidacja, ustawienia i ekran.
