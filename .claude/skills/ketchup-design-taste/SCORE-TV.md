# Skala oceny ekranu TV (0-100)

Każda kategoria 0-5. Wynik cząstkowy = ocena × waga ÷ 5. Strata ważona = (5 − ocena) × waga ÷ 5.
Dowody: wynik `LayoutAudit`, zrzut (Roborazzi/emulator), scenariusz D-pad, test kontrastu.

| # | Kategoria | Waga | Co oceniamy |
|---|---|---|---|
| 1 | Hierarchia | 10 | jedno pierwsze miejsce uwagi, rozmiar i grubość prowadzą wzrok |
| 2 | Typografia | 9 | role (>= 3), min. 14 sp, czytelność z 3 m, brak dziwnych skrótów „…” |
| 3 | Odstępy i rytm | 7 | skala 4/8/12/16/24/32, równe odstępy, wyrównanie do siatki |
| 4 | Kompozycja | 8 | wzorzec nazwany, użycie szerokości TV, wyśrodkowanie tam, gdzie ma sens |
| 5 | Spójność tokenów | 8 | kolor, odstęp, promień, ruch z designsystemu; zero hardkodów |
| 6 | Kontrast i dostępność | 10 | >= 4,5:1, informacja nie tylko kolorem, opisy dostępności |
| 7 | Fokus i nawigacja pilotem | 14 | fokus widoczny i zawsze gdzieś, LEWO/PRAWO/BACK przewidywalne, powrót na ten sam element |
| 8 | Jakość komponentów | 7 | stany (fokus, wybrany, wyłączony), cele >= 36 dp |
| 9 | Zgodność z marką | 8 | paleta Morski/Grafit/Czerń, akcent z sensem, nie „przemalowane” |
| 10 | Czytelność z odległości | 8 | zrzut w 50% skali dalej czytelny, brak drobnych tekstów |
| 11 | Stany i uczciwość treści | 6 | pusty/ładowanie/błąd, dane prawdziwe i podpisane |
| 12 | Ruch | 5 | `Motion`, bez zbędnego ruchu, płynność wsuwania paneli |
|   | **Suma** | **100** | |

## Werdykt
| Wynik | Decyzja |
|---|---|
| 85-100 | Wdrażamy. |
| 75-84 | Poprawiamy dwie kategorie o największej stracie, potem wdrażamy. |
| 60-74 | Przeprojektować dwie najsłabsze kategorie, ocenić ponownie, nie wdrażać. |
| < 60 | Problem w koncepcji: wróć do kroku 1 (rozpoznanie). |

Bloker z `ANTI-SLOP-TV.md` oznacza „nie przechodzi” niezależnie od wyniku.
