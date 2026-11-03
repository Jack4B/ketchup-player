---
name: android-tv-ui
description: Zasady 10-foot UI dla Compose for TV w Ketchup Player (focus, overscan, D-pad, Back)
---
- Focus zawsze widoczny: skala 1.05-1.08 + obrys 3 dp + poświata; animacja <= 150 ms.
- Marginesy: małe (16 dp poziomo, 10 dp pionowo; overscan wyłączony na boxach). Czcionka: min. 14 sp (twardo, `LayoutAudit`), treść 16-18 sp. Cel fokusu min. 36 dp (twardo), docelowo 48 dp.
- Po wejściu na ekran focus ma sensowny element; po BACK wraca na ten sam (klucz ID, focusRestorer).
- BACK zawsze cofa o jeden poziom. LEWO z pierwszej kolumny otwiera szynę.
- Komponenty z androidx.tv.material3; brak pułapek focusu.
- Kolory z tokenów motywu (Morski/Grafit/Czerń/Własny + akcent), nigdy hardkody.
- Pełny proces, antywzorce i ocenę ekranu ma skill `ketchup-design-taste`; ekran dopisz do `LayoutAudit`.
