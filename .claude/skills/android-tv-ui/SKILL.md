---
name: android-tv-ui
description: Zasady 10-foot UI dla Compose for TV w Ketchup Player (focus, overscan, D-pad, Back)
---
- Focus zawsze widoczny: skala 1.05-1.08 + obrys 3 dp + poświata; animacja <= 150 ms.
- Overscan: 48 dp poziomo, 27 dp pionowo. Tekst body >= 18 sp. Hit-area >= 48 dp.
- Po wejściu na ekran focus ma sensowny element; po BACK wraca na ten sam (klucz ID, focusRestorer).
- BACK zawsze cofa o jeden poziom. LEWO z pierwszej kolumny otwiera szynę.
- Komponenty z androidx.tv.material3; brak pułapek focusu.
- Kolory z tokenów motywu (Granat/Czerń/Bordo + akcent), nigdy hardkody.
