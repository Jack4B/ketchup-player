# Antywzorce UI dla TV (Ketchup Player)

Przejdź listę od góry do dołu przy kroku 7. Blokery muszą być zero. Co najmniej 3 trafienia STRONG = ekran jest
„wygenerowany”, nie zaprojektowany: wróć do kroku 1. W nawiasie: czym to wykrywamy.

## BLOKER (zero)
- Element poza ekranem w poziomie albo uciety bez przewijania. (`LayoutAudit`: „poza ekranem”)
- Czcionka < 14 sp. (`LayoutAudit`: „mała czcionka”)
- Cel fokusu/dotyku < 36 dp. (`LayoutAudit`: „mały cel”)
- Teksty nachodzą na siebie albo „sklejają się” (odstęp < 4 dp). (`LayoutAudit`)
- Nie da się dojść D-padem, fokus znika albo ginie po akcji, BACK nie cofa o jeden poziom. (scenariusze fokusu, emulator
  z odczytem `focused` z drzewa UI)
- Brak widocznego fokusu na elemencie interaktywnym. (zrzut + test)
- Kontrast tekstu podstawowego < 4,5:1. (test kontrastu WCAG w designsystemie)
- Informacja tylko kolorem (kropka, pasek stanu bez tekstu lub etykiety). (przegląd zrzutu)
- Wartości poza tokenami: hardkodowany kolor/odstęp, ta sama rola w kilku wartościach. (przegląd diffu, grep po `Color(0x`)
- Zmyślona lub nieopisana treść: placeholder, oceny bez źródła, liczby bez danych. (przegląd)
- Ekran bez stanu pustego, ładowania albo błędu. (scenariusz z brakiem danych)
- Animacja fokusu > 150 ms albo własny czas poza `Motion`. (`Motion`, przegląd)

## STRONG (napraw albo uzasadnij jednym zdaniem)
- Więcej niż 2 akcenty na ekranie; neonowy gradient bez funkcji.
- Więcej niż 2 szklane powierzchnie na widok; szkło bez fallbacku.
- Ten sam promień wszędzie (od pigułki po panel); każda sekcja w karcie; karta w karcie.
- Wszystko wycentrowane; brak jednego pierwszego punktu uwagi.
- Losowe odstępy poza skalą 4/8/12/16/24/32; mniej niż 3 role typograficzne.
- Akapity WIELKIMI LITERAMI (wielkie tylko dla krótkich etykiet).
- Tytuł kafla ucięty do jednej linii, gdy zmieściłby się w dwóch; „…” w nazwie kanału z grupy, którą da się skrócić czyszczeniem.
- Mieszane rodziny ikon; emoji jako ikony funkcji; ikona bez etykiety w pasku, który nie ma opisu dostępności.
- Przejścia dłuższe niż 250 ms; ciągłe pulsowanie lub unoszenie; autoodtwarzany ruch na gęstej treści.
- Treść schowana pod panelem/nakładką, która wygląda jak przezroczysta (prześwity tekstu).
- Układ „telefon rozciągnięty na TV”: małe cele, stos pionowy bez rekompozycji, brak użycia szerokości.

## MINOR (napraw, jeśli tanio)
- Sztywny padding sekcji identyczny wszędzie; dekoracyjne separatory między każdą sekcją.
- Przełącznik motywu, który tylko odwraca tło; miniatura motywu niepodobna do prawdziwego ekranu.
- Licznik/etykieta w kolorze zbyt bliskim tła (kontrast < 3:1 dla tekstu pomocniczego).
- Nierówne wysokości wierszy listy (jedna i dwie linie) bez wyrównania pionowego.

## Dwa pytania kontrolne
1. Czy ten ekran mógłby należeć do innej aplikacji? Jeśli tak, to szablon.
2. Co usunąłeś? Projekt, w którym nic nie usunięto, nie został zredagowany.
