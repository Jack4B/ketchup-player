# Ketchup Player — makiety v4

Data: 6.11.2026 (Europe/Warsaw). Obowiązujące makiety do akceptacji właściciela. v3 zostaje w `docs/mockups/v3-*` jako historia (z plakatami Wikimedia — nie używać w implementacji ani w materiałach sklepowych).

## Decyzja o palecie (jedno źródło prawdy)

Wdrożenie i te makiety używają **jednego** zestawu: motyw **Granat** + akcent **Turkus**. To domyślny zestaw v3, wybrany jako jedyny obowiązujący, żeby usunąć konflikt logo / appendix v2 / trzy motywy v3.

Czerń i Bordo oraz pozostałe próbki akcentu zostają w backlogu wyglądu (widać je wyszarzone na ekranie Ustawień). Nie implementować ich, dopóki v4 nie zostanie zaakceptowane i nie zapadnie osobna decyzja.

### Tokeny

| Token | Wartość | Użycie |
|---|---|---|
| `--bg` | `#0A1A2F` | tło |
| `--bgimg` | `linear-gradient(160deg, #0F2A4A, #0A1A2F 45%, #07121F)` | tło aplikacji |
| `--s1` | `rgba(22,48,82,.72)` | panele |
| `--s2` | `#163152` | powierzchnie, komórki |
| `--s3` | `#21466F` | powierzchnia w focusie / aktywna |
| `--ln` | `#2A4B73` | linie siatki |
| `--tx` | `#EAF2FA` | tekst główny |
| `--tx2` | `#A6BCD6` | tekst drugorzędny |
| `--acc` / `--accf` | `#33B5C4` | focus, CTA, paski, linia „teraz” |
| `--onacc` | `#0B0B0B` | tekst na przycisku akcentu |
| `--grn` | `#3DBE4B` | wyłącznie nowe odcinki |
| `--danger` | `#D4534A` | błędy (nie jako akcent motywu) |
| `--rad` | `18px` | promień paneli |
| `--chlbg` / `--chltx` | `#E8EEF6` / `#0A1A2F` | płytki logo kanałów |

Kontrasty AA jak w `docs/mockups/NOTATKI-v3.md` §4 dla Granat + Turkus (sprawdzone przy review v3).

## Katalog danych (wspólny)

Godzina systemowa na wszystkich ekranach: **19:42**, sobota 31.10.2026.

Kanał bieżący (ostatnio oglądany, focus na Na żywo i EPG): **102 Info 24**, audycja **Rozmowa dnia, 19:15–20:15**, następnie **20:15 Serwis informacyjny**. Pasek postępu 45% (27 z 60 min). To samo w Live, EPG, Playerze, Radarze i wyszukiwarce.

Filmy w siatce „Najnowsze” są posortowane malejąco po roku (Złote miasto 2024 … Most nad rzeką / Burza 2018). Hero Home = **Złote miasto** (ten sam tytuł co plakat-placeholder). Kontynuuj: Komisariat 7, Czerwona glina, Natura z bliska, Kanał Jeden.

Placeholdery graficzne to gradient + monogram z tytułu. Żadnych kadrów filmów ani plików JPEG/PNG z plakatami.

## Ekrany

HTML: `ketchup-player-makiety-v4.html` (←/→, F = skala; działa też wielkie F). Generator: `generate.py`. Zrzuty: `screenshot.py` (Playwright + Chrome).

### TV 1080 (`screenshots/tv-1080/`, 1920×1080)

Onboarding (krok 2), ładowanie listy (krok 3), Home, Na żywo, EPG, Radar, Filmy, Seriale, Szczegóły, Odtwarzacz (ikony SVG, nie emoji), Ustawienia, Szukaj, Moja lista, PIN, pusty stan, błąd sieci, szkielet ładowania, stany focusu D-pad.

### TV 720 (`screenshots/tv-720/`, 1280×720)

Home, Na żywo, EPG, Odtwarzacz.

### Telefon (`screenshots/phone/`, 1080×1920)

Home, Na żywo, Filmy, Szukaj, Odtwarzacz. Nawigacja: dolny pasek Home / Na żywo / Filmy / Seriale / Więcej (zgodnie z konspektem §6.3).

## Regeneracja

```bash
python3 docs/mockups/v4/generate.py
python3 docs/mockups/v4/screenshot.py
```

Status: **oczekuje na akceptację właściciela**. Po akceptacji te PNG są referencją dla Roborazzi i Fazy F.
