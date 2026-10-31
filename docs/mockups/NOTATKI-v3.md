# Ketchup Player — makiety v3: notatki (Lumen)
Data: 31.10.2026. Uwaga do v2: to był jeden szary motyw z różnymi akcentami, a akcent ma być osobnym wyborem w Ustawieniach. W v3 są **3 pełne motywy** (tła, panele, szyna, siatka EPG, nakładki OSD, tony tekstu, promienie zaokrągleń) i **osobny wybór „Kolor akcentu”**. v1 i v2 zostają na Dysku.

> ⚠ **PLAKATY SĄ TYMCZASOWYMI ZAŚLEPKAMI Z WIKIMEDIA COMMONS — TYLKO DO UŻYTKU WEWNĘTRZNEGO, NIE DO WYDANIA.** Tytuły pod plakatami są fikcyjne. Logotypy kanałów (JEDEN, INFO24, SPORT+…) i audycje EPG są fikcyjne.

## 1. Research: jak działają motywy TiViMate
- TiViMate (Premium) ma w *Ustawienia → Wygląd → Motyw kolorystyczny* **trzy niezależne ustawienia**:
  - **kolor tła** (całej aplikacji, m.in. Blue, Indigo, Black, Dark grey, Light grey, Green, Tan),
  - **kolor akcentu**,
  - **kolor zaznaczenia** (focus, np. White, Yellow, Cyan).
  
  Do tego dochodzi suwak **przezroczystości** paneli nad wideo. Zmiana tła przebarwia całość: tło, panele, siatkę EPG i nakładki. Akcent i zaznaczenie wybiera się osobno. Na tym opiera się podział v3 na Motyw i Kolor akcentu. Źródła: [Techkings – wątek o motywach](https://www.techkings.org/threads/regarding-tivimate-app.173153/), [Techkings s. 2](https://www.techkings.org/threads/regarding-tivimate-app.173153/page-2), [r/TiviMate – Sky-like](https://www.reddit.com/r/TiviMate/comments/1h4wnzq/tivimate_background_make_like_sky/).
- **Najpopularniejsze kombinacje:**
  - Blue + Cyan + White ([r/TiviMate: Colour Combinations](https://www.reddit.com/r/TiviMate/comments/1iyd78u/colour_combinations_recommendations/)),
  - Indigo/Blue + Yellow, czyli „jak Sky Q”,
  - Black + Purple/White + 100% przezroczystości,
  - „Blue grey + Cyan”.
  
  Źródło zbiorcze: [r/TiviMate: best background/selection](https://www.reddit.com/r/TiviMate/comments/rz3lap/in_your_opinion_what_is_the_best_looking/).
- **Użytkownicy zgłaszają, że akcenty na TV wyglądają na „sprane”**, a nowsze wersje motywu Blue są ciemniejsze niż starsze ([r/TiviMate: blue theme](https://www.reddit.com/r/TiviMate/comments/1r709i6/how_can_i_get_the_blue_color_theme_like_this/)). Wniosek: akcenty muszą mieć wysoką jasność, a tła głębokie, ale nie płaskie.
- Kolory w TiViMate są dostępne tylko w wersji płatnej (Premium).
- Nie znalazłem oficjalnej dokumentacji z kodami HEX ani zrzutów od twórcy. Palety v3 to interpretacja na podstawie opisów i zrzutów użytkowników, a nie zmierzone wartości. Artykuł dualiptv.com o „skinach” (Dark/Light/AMOLED, import JSON) nie pokrywa się z relacjami użytkowników, więc uznałem go za niewiarygodny.

## 2. Motywy v3
| Motyw | Charakter | Tło | Panele/szyna | Siatka EPG | OSD | Domyślny akcent |
|---|---|---|---|---|---|---|
| **Granat** (domyślny) | jak TiViMate „Blue”: głęboki granat z gradientem | `#0A1A2F` + gradient 160° `#0F2A4A→#07121F` | półprzezroczyste granatowe panele `rgba(22,48,82,.72)`, szyna `rgba(9,24,44,.85)` z obrysem `#24446B`, promień 18 px | komórki `#163152`, focus `#21466F`, linie `#2A4B73` | scrim granatowy `rgb(6,18,36)` | **Turkus `#33B5C4`** |
| **Czerń** (OLED) | czysta czerń, płasko, bez wypełnionych paneli | `#000000` bez gradientu | panele przezroczyste z obrysem 1 px `#262626`, szyna z samą linią po prawej, promień 6 px | komórki `#111111` z obrysem, focus `#262626` | scrim czarny | Biel `#EDEDED` |
| **Bordo** (ciepły) | ciepła, jaśniejsza czerwień wina z winietą pod pomidorowe logo | `#2A1417` + radial `#5A2A2E→#24100F` | pełne panele `#45232A`/`#552E33`, szyna `#4A252A`, promień 26 px | komórki `#552E33`, focus `#6A3A41` | scrim bordowy `rgb(26,10,12)` | Bursztyn `#E3A857` |

- **Tekst:**
  - Granat: `#EAF2FA` / `#A6BCD6`,
  - Czerń: `#F2F2F2` / `#9E9E9E`,
  - Bordo: `#F6ECE6` / `#DDC6BE`.
- **Płytki logo kanałów:**
  - Granat: jasne `#E8EEF6` z granatowym napisem,
  - Czerń: ciemne `#1C1C1C`,
  - Bordo: kremowe `#F1E3D3`.
- **Rozróżnialność w skali szarości:**
  - Czerń jest najciemniejsza i płaska, z obrysami.
  - Granat jest średnio ciemny, z gradientem i półprzezroczystymi panelami.
  - Bordo jest najjaśniejsze, z pełnymi, mocno zaokrąglonymi panelami.
  
  Patrz plik `ketchup-player-v3-porownanie-motywow.png` (górny rząd: Home, dolny: EPG).
- **Dlaczego Bordo:** TiViMate ma ciepłe tła (Tan, Green), a logo jest pomidorowe. Bordo to tło „z rodziny” logo, ale ciemne i bez krzykliwej czerwieni w UI.

## 3. Kolor akcentu (osobny wybór)
- Ustawienia → Wygląd: najpierw **Motyw** (3 karty z miniaturą), pod spodem **Kolor akcentu**: Turkus, Biel, Bursztyn, Żółty, Błękit, Fiolet.
- Akcent obejmuje: ring focusu, przycisk główny (tekst `#0B0B0B`), paski postępu, linię „teraz”, przełączniki i aktywną zakładkę.
- **Bez zieleni** (zarezerwowana dla nowych odcinków, `#3DBE4B`, we wszystkich motywach) i **bez czerwieni** (błędy, nagrywanie).
- Każdy z 6 akcentów przechodzi AA w każdym motywie (tabele poniżej). Makiety pokazują domyślny akcent danego motywu.
- Propozycja: zmiana motywu podpowiada domyślny akcent, chyba że użytkownik wybrał własny.

## 4. Kontrast (WCAG 2.1 AA: tekst ≥ 4,5:1, elementy UI/ring ≥ 3:1)

#### Granat
| para | kontrast | AA |
|---|---|---|
| tekst / tło `#EAF2FA`/`#0A1A2F` | 15.46 | ✅ (≥4.5) |
| tekst / panel s2 `#EAF2FA`/`#163152` | 11.65 | ✅ (≥4.5) |
| tekst / focus s3 `#EAF2FA`/`#21466F` | 8.56 | ✅ (≥4.5) |
| drugorzędny / tło `#A6BCD6`/`#0A1A2F` | 8.98 | ✅ (≥4.5) |
| drugorzędny / s2 `#A6BCD6`/`#163152` | 6.76 | ✅ (≥4.5) |
| drugorzędny / s3 `#A6BCD6`/`#21466F` | 4.97 | ✅ (≥4.5) |
| logo kanału tekst/płytka `#0A1A2F`/`#E8EEF6` | 14.97 | ✅ (≥4.5) |
| zielony #3DBE4B / s2 (UI) `#3DBE4B`/`#163152` | 5.43 | ✅ (≥3) |
| akcent Turkus `#33B5C4`: ring/tło 7.11 · ring/s3 3.94 · tekst #0B0B0B na przycisku 8.01 | min 3.94 | ✅ |
| akcent Biel `#EDEDED`: ring/tło 14.93 · ring/s3 8.27 · tekst #0B0B0B na przycisku 16.81 | min 8.27 | ✅ |
| akcent Bursztyn `#E3A857`: ring/tło 8.33 · ring/s3 4.61 · tekst #0B0B0B na przycisku 9.38 | min 4.61 | ✅ |
| akcent Żółty `#E8C547`: ring/tło 10.41 · ring/s3 5.76 · tekst #0B0B0B na przycisku 11.72 | min 5.76 | ✅ |
| akcent Błękit `#6AA6F2`: ring/tło 6.95 · ring/s3 3.85 · tekst #0B0B0B na przycisku 7.83 | min 3.85 | ✅ |
| akcent Fiolet `#B09AF2`: ring/tło 7.28 · ring/s3 4.03 · tekst #0B0B0B na przycisku 8.20 | min 4.03 | ✅ |

#### Czerń
| para | kontrast | AA |
|---|---|---|
| tekst / tło `#F2F2F2`/`#000000` | 18.76 | ✅ (≥4.5) |
| tekst / panel s2 `#F2F2F2`/`#111111` | 16.87 | ✅ (≥4.5) |
| tekst / focus s3 `#F2F2F2`/`#262626` | 13.52 | ✅ (≥4.5) |
| drugorzędny / tło `#9E9E9E`/`#000000` | 7.84 | ✅ (≥4.5) |
| drugorzędny / s2 `#9E9E9E`/`#111111` | 7.05 | ✅ (≥4.5) |
| drugorzędny / s3 `#9E9E9E`/`#262626` | 5.65 | ✅ (≥4.5) |
| logo kanału tekst/płytka `#F2F2F2`/`#1C1C1C` | 15.22 | ✅ (≥4.5) |
| zielony #3DBE4B / s2 (UI) `#3DBE4B`/`#111111` | 7.79 | ✅ (≥3) |
| akcent Turkus `#33B5C4`: ring/tło 8.55 · ring/s3 6.16 · tekst #0B0B0B na przycisku 8.01 | min 6.16 | ✅ |
| akcent Biel `#EDEDED`: ring/tło 17.94 · ring/s3 12.93 · tekst #0B0B0B na przycisku 16.81 | min 12.93 | ✅ |
| akcent Bursztyn `#E3A857`: ring/tło 10.00 · ring/s3 7.21 · tekst #0B0B0B na przycisku 9.38 | min 7.21 | ✅ |
| akcent Żółty `#E8C547`: ring/tło 12.51 · ring/s3 9.01 · tekst #0B0B0B na przycisku 11.72 | min 9.01 | ✅ |
| akcent Błękit `#6AA6F2`: ring/tło 8.35 · ring/s3 6.02 · tekst #0B0B0B na przycisku 7.83 | min 6.02 | ✅ |
| akcent Fiolet `#B09AF2`: ring/tło 8.75 · ring/s3 6.31 · tekst #0B0B0B na przycisku 8.20 | min 6.31 | ✅ |

#### Bordo
| para | kontrast | AA |
|---|---|---|
| tekst / tło `#F6ECE6`/`#2A1417` | 14.91 | ✅ (≥4.5) |
| tekst / panel s2 `#F6ECE6`/`#552E33` | 9.90 | ✅ (≥4.5) |
| tekst / focus s3 `#F6ECE6`/`#6A3A41` | 7.87 | ✅ (≥4.5) |
| drugorzędny / tło `#DDC6BE`/`#2A1417` | 10.65 | ✅ (≥4.5) |
| drugorzędny / s2 `#DDC6BE`/`#552E33` | 7.07 | ✅ (≥4.5) |
| drugorzędny / s3 `#DDC6BE`/`#6A3A41` | 5.62 | ✅ (≥4.5) |
| logo kanału tekst/płytka `#3A1C20`/`#F1E3D3` | 12.19 | ✅ (≥4.5) |
| zielony #3DBE4B / s2 (UI) `#3DBE4B`/`#552E33` | 4.75 | ✅ (≥3) |
| akcent Turkus `#33B5C4`: ring/tło 7.06 · ring/s3 3.73 · tekst #0B0B0B na przycisku 8.01 | min 3.73 | ✅ |
| akcent Biel `#EDEDED`: ring/tło 14.81 · ring/s3 7.82 · tekst #0B0B0B na przycisku 16.81 | min 7.82 | ✅ |
| akcent Bursztyn `#E3A857`: ring/tło 8.26 · ring/s3 4.36 · tekst #0B0B0B na przycisku 9.38 | min 4.36 | ✅ |
| akcent Żółty `#E8C547`: ring/tło 10.33 · ring/s3 5.45 · tekst #0B0B0B na przycisku 11.72 | min 5.45 | ✅ |
| akcent Błękit `#6AA6F2`: ring/tło 6.90 · ring/s3 3.64 · tekst #0B0B0B na przycisku 7.83 | min 3.64 | ✅ |
| akcent Fiolet `#B09AF2`: ring/tło 7.23 · ring/s3 3.81 · tekst #0B0B0B na przycisku 8.20 | min 3.81 | ✅ |

Wszystkie pary przechodzą AA.

## 5. Mapa focusu / D-pad / Back per ekran
Zasada ogólna: LEWO z pierwszej kolumny treści → szyna (rozwija się, focus na aktywnej pozycji – `focusRestorer`); PRAWO z szyny → ostatni focus w treści; BACK zawsze cofa o jeden poziom, a z szyny Home pyta „Zamknąć aplikację?”. Powrót na ekran przywraca poprzedni focus.

### 1. Pierwsze uruchomienie (`01-onboarding.png`)
Start: pierwszy typ (M3U). LEWO/PRAWO/GÓRA/DÓŁ po kafelkach typów; OK wybiera i przenosi focus do pola URL (DÓŁ = następne pole, IME „Dalej”, LEWO w polu nie zamyka formularza). DÓŁ z ostatniego pola → [Dalej]. Krok 3: pasek postępu Kanały → EPG → VOD (focus na [Przejdź do aplikacji] po zakończeniu, [Pomiń ładowanie VOD] w trakcie). BACK: w polu → zamyka klawiaturę; na kroku → poprzedni krok; krok 1 → dialog „Zamknąć aplikację?”.

### 2. Home (`02-home.png`)
Start: focus na 1. kaflu „Kontynuuj oglądanie” (lub [▶ Oglądaj] w hero, gdy rząd pusty). GÓRA z rzędu → przyciski hero → (GÓRA) nic. LEWO z pierwszego kafla → szyna nawigacji (rozwija się). DÓŁ → kolejne rzędy (Nowe odcinki, Na żywo teraz, Radar…). BACK: z rzędu → przewija do góry/focus na szynę „Home”; z szyny → dialog „Zamknąć aplikację?”.

### 5. Na żywo (`03-live.png`)
Start: focus na ostatnio oglądanym kanale (tu 102). GÓRA/DÓŁ = lista kanałów (CH± = strona). OK = pełny ekran. PRAWO = mini-EPG/szczegóły audycji w prawym panelu. LEWO = kolumna grup; LEWO z grup = szyna. Długie OK/MENU = menu kontekstowe (Ulubione, Ukryj, Nagraj, Przypomnij). BACK: z kanałów → grupy → szyna.

### 4. Przewodnik (EPG) (`04-epg.png`)
Start: komórka audycji trwającej teraz na bieżącym kanale. LEWO/PRAWO = poprzednia/następna audycja (oś czasu przewija się), GÓRA/DÓŁ = kanał (zachowana pozycja czasu), ⏩/⏪ = ±1 h, CH± = strona kanałów. LEWO z pierwszej widocznej komórki w chwili „teraz” → kolumna kanałów → szyna. GÓRA z pierwszego wiersza → zakładki Siatka|Radar|Moje zainteresowania. OK: trwająca = odtwórz; przeszła z archiwum = catch-up; przyszła = Przypomnij/Nagraj. BACK: wraca do „teraz”, drugi BACK → szyna.

### 5. Filmy (`05-movies.png`)
Start: pierwszy plakat siatki (tu pokazany focus na 2.). Siatka 6 kolumn: strzałki po plakatach; GÓRA z 1. rzędu → chipy sortowania → pole wyszukiwania (DÓŁ z pola → wyniki). LEWO z 1. kolumny → pasek kategorii (rozwinięty), LEWO dalej → szyna. OK = Szczegóły. Długie OK = menu kontekstowe. BACK: z siatki → 1. rząd/chipy → kategorie → szyna.

### 6. Seriale (`06-series.png`)
Start: pierwszy plakat siatki (tu pokazany focus na 2.). Siatka 6 kolumn: strzałki po plakatach; GÓRA z 1. rzędu → chipy sortowania → pole wyszukiwania (DÓŁ z pola → wyniki). LEWO z 1. kolumny → pasek kategorii (rozwinięty), LEWO dalej → szyna. OK = Szczegóły. Długie OK = menu kontekstowe. BACK: z siatki → 1. rząd/chipy → kategorie → szyna. Zielona kropka/pigułka = nowe odcinki (jedyne użycie zieleni); filtr „Z nowymi odcinkami” jako chip.

### 7. Szczegóły serialu (`07-details.png`)
Start: [▶ Wznów]/[▶ Oglądaj]. PRAWO/LEWO po przyciskach. DÓŁ → chipy sezonów (zielona kropka przy sezonie z nowymi) → DÓŁ → rząd odcinków (focus na ostatnio oglądanym/pierwszym nowym) → DÓŁ → rząd „Podobne” (pod linią, przewijany). GÓRA wraca o poziom. Film: ten sam układ bez sezonów/odcinków, przyciski [▶ Oglądaj/Wznów][Od początku][Zwiastun][+ Moja lista][Źródła (N)], bez pionowego scrolla. Wejście w szczegóły gasi puls (§7.2.4). BACK → poprzedni ekran z przywróconym focusem na plakacie.

### 8. Odtwarzacz z OSD (`08-player.png`)
OSD ukryty: pierwszy dowolny klawisz tylko budzi OSD (bez akcji). Live bez OSD: GÓRA/DÓŁ = zmiana kanału (konfig.), OK = mini-lista kanałów. OSD widoczny: focus startowo na ⏯; LEWO/PRAWO po kontrolkach i przyciskach Audio/Napisy/Źródło/Info/⋯; GÓRA → pasek przewijania (VOD/catch-up: LEWO/PRAWO = ±10 s, przytrzymanie przyspiesza). MENU/niebieski = menu kontekstowe (Źródło, Audio, Napisy, Bufor, Proporcje, Wyłącznik czasowy, Nagraj). OSD znika po 3 s. Toast failover 4 s, nie zabiera focusu. BACK: OSD widoczny → chowa OSD; ukryty → wyjście z odtwarzacza (VOD zapisuje pozycję).

### 9. Ustawienia (`09-settings.png`)
Start: ostatnio używana kategoria. GÓRA/DÓŁ po kategoriach; PRAWO (lub OK) → opcje; LEWO z opcji → kategorie; LEWO z kategorii → szyna. OK na przełączniku = zmiana; na „›” = 3. poziom w prawym panelu z breadcrumbem (np. „Odtwarzanie › Buforowanie”). GÓRA z 1. kategorii → wyszukiwarka ustawień. BACK: 3. poziom → 2. poziom → kategorie → szyna. „O aplikacji” → 7× OK na numerze wersji = tryb diagnostyczny.


## 6. Pliki
- Podfoldery `v3 - granat`, `v3 - czern`, `v3 - bordo`: w każdym HTML (←/→ przełącza ekrany, F skaluje do okna) i 9 plików PNG 1920×1080.
- Ekran 09 to teraz **Ustawienia › Wygląd** (Motyw + Kolor akcentu). Wcześniejszy widok „Odtwarzanie” jest w v2.
- `ketchup-player-v3-porownanie-motywow.png`: Home + EPG, 3 motywy obok siebie.

## 7. Braki i pytania
- Nadal nie narysowane: stany puste, błędy, ładowanie, PIN.
- Podgląd na żywo i odtwarzacz zamiast wideo pokazują plakat.
- Hero ma ogólny tytuł.
- Nie ma motywu jasnego (TiViMate ma Light grey). Czy jest potrzebny?
- Nie ma osobnego „koloru zaznaczenia” jak w TiViMate: w v3 ring focusu = akcent. Czy rozdzielić?
- Czy suwak przezroczystości nakładek ma zostać (jak w TiViMate)?
