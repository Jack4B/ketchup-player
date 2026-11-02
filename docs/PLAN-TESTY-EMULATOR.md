# Plan: testy na emulatorze (bramka wydania) i silnik odtwarzania

Decyzja użytkownika (2.11.2026): testować zawsze na GitHubie, na emulatorze, klikaniem (dotyk i D-pad), a nie tylko statycznie.
Przed każdą wersją dla użytkownika uruchamiamy inne testy na emulatorze niż te, które już przeszły.

## 1. Zasada wydania (obowiązuje od rc0.9.6)

APK trafia do użytkownika dopiero, gdy **dla tego samego SHA** zielone są wszystkie joby CI, w tym `emulator`.
Kolejność: commit → push → CI (spotless, detekt, lint, testy, Roborazzi, build debug/release, **emulator**, gitleaks) →
dopiero potem `SendUserFile`. W wiadomości z APK podaję numer biegu CI i wynik emulatora; brak biegu = brak wysyłki.

Dlaczego: rc0.9.5 wyszło z lokalnymi testami, ale bez CI. CI reagowało tylko na `main` i `faza-*`, a gałąź robocza sesji to
`main-azhd2v`, więc dla `ac089f5` nie uruchomiono ani jednego testu na GitHubie. Poprawione: CI obejmuje też `main-*`.
Błędy z telefonu (odtwarzacz w tle po BACK, sortowanie bez efektu) były dokładnie z klasy, którą emulator łapie, a testy
jednostkowe nie: czas życia ViewModeli, prawdziwy dotyk, prawdziwy ExoPlayer i dźwięk systemowy.

## 2. Co dziś sprawdza emulator (`.github/scripts/emulator-smoke.sh`)

Android TV API 34, prawdziwy Keystore, ExoPlayer, serwer HTTP z HLS z ffmpeg, D-pad i `input tap`:
kreator, Home, szyna, Ustawienia, dodanie dwóch list przez formularz, failover z 404 na działającą listę, odtwarzanie,
crash/ANR w logcat.

Dodane w tej paczce (regresje z rc0.9.4):
- **BACK z odtwarzacza**: `dumpsys audio` musi pokazywać działający odtwarzacz aplikacji podczas grania (kontrola
  pozytywna), a po BACK żadnego.
- **Sortowanie Filmów dotykiem**: 60 filmów bez metadanych, przewinięcie palcem, „A–Z”; pierwszy widoczny film w drzewie UI
  musi się zmienić z `Film 60` (kolejność dostawcy) na `Film 01`.

## 3. Kolejne scenariusze emulatora (kolejność = priorytet)

Każdy: osobna funkcja w skrypcie (albo osobny plik `.github/scripts/scenarios/NN-nazwa.sh`), zrzuty ekranu do artefaktu,
twarda asercja z drzewa UI lub logu, nie sam zrzut.

| # | Scenariusz | Asercja |
|---|-----------|---------|
| E1 | Film VOD (MP4/HLS z ffmpeg): Szczegóły → Oglądaj → pauza OK → przewijanie PRAWO przytrzymane → BACK | pozycja po przewinięciu > start; po BACK zapis wznowienia; brak dźwięku w tle |
| E2 | Wznawianie: film do 50%, wyjście, wejście ponownie | start z zapamiętanej pozycji (log playera / pozycja w OSD) |
| E3 | Wiele ścieżek audio i napisów (HLS z ffmpeg: 2×audio, WebVTT) | zmiana ścieżki pilotem zmienia wybrany track (ten sam mechanizm co 1.7) |
| E4 | Zapping: lista kanałów w odtwarzaczu, CH+/CH− | serwer widzi żądania kolejnych kanałów, jeden aktywny odtwarzacz naraz |
| E5 | Wyjście z aplikacji (HOME) podczas grania i powrót | pauza w tle (`dumpsys audio` = 0), wznowienie po powrocie |
| E6 | Motywy i szkło: zmiana motywu, Własny z suwakiem, poziom szkła | zrzuty różnią się; brak crasha; czas klatek z `dumpsys gfxinfo` poniżej progu |
| E7 | Duża lista (50 tys. kanałów + 20 tys. filmów, wygenerowana) | import kończy się w limicie; przewijanie Filmów bez ANR |
| E8 | Obrót/zmiana konfiguracji w odtwarzaczu | odtwarzanie nie restartuje się (ten sam adres, jedno żądanie) |
| E9 | Sieć: serwer zwraca 404/timeout/zrywa połączenie w trakcie | komunikat błędu, ponowienie, brak pętli żądań |
| E10 | Telefon (dotyk, portret) | poza 1.0 (ADR tv-first), zostaje na 2.0 |

Pomiary na emulatorze nie zastępują boxu (inny SoC, brak hardware'owych dekoderów), więc skrypt `ketchup_tvbox_test.sh`
zostaje drugą bramką: użytkownik uruchamia go na Leelbox przed 1.0.

## 4. Praca nad pułapkami emulatora

- Emulator CI jest wolny: wpisywanie przez `type_checked`, czekanie na stan w drzewie UI zamiast `sleep` (zamienić stałe
  `sleep` na pętlę `wait_for_text` z limitem).
- Przy każdej czerwonej asercji skrypt zapisuje dump UI, `logcat -d`, `dumpsys audio`, log serwera; wszystko w artefakcie.
- Skrypt ma być uruchamialny lokalnie na urządzeniu użytkownika (adb po sieci) tą samą ścieżką co w CI.

## 5. Silnik odtwarzania: Media3 ExoPlayer czy własny?

Ocena: **nie piszemy własnego odtwarzacza**. Media3 ExoPlayer to silnik, na którym stoi większość aplikacji na Androida;
własny dekoder/demuxer to miesiące pracy i gorsza zgodność niż to, co mamy. Wąskie gardła IPTV rozwiązujemy **rozbudową
wokół ExoPlayera**; interfejs `PlayerController` już oddziela aplikację od silnika, więc drugi silnik da się dołożyć.

Stan: OkHttp jako źródło, HLS, profil bufora (`DefaultLoadControl`), szybkie odrzucanie martwych źródeł
(`FailFastLoadPolicy`), opóźnienie audio (`DelayingAudioSink`), ścieżki audio/napisów, tempo. Brakuje:

| Pozycja | Co | Kiedy / bramka |
|---------|----|----------------|
| P1 | `setEnableDecoderFallback(true)` w fabryce rendererów: gdy sprzętowy dekoder odmawia, próbuje następny | od razu, tanie; test E3 + box |
| P2 | Flagi TS: `FLAG_ALLOW_NON_IDR_KEYFRAMES`, `FLAG_DETECT_ACCESS_UNITS` (strumienie z uszkodzonym PES, częste w IPTV) | po danych z boxa (czarny ekran z dźwiękiem) |
| P3 | Programowy dekoder audio (AC3/E-AC3/DTS), gdy box nie ma sprzętowego: rozszerzenie FFmpeg dla Media3 (trzeba budować z źródeł, sprawdzić licencję LGPL) | tylko jeśli raport z boxa pokaże brak dźwięku dla konkretnego kodeka (1.7) |
| P4 | Statystyki odtwarzania (`AnalyticsListener`): bitrate, utracone klatki, rebuffery, w OSD INFO i w raporcie testowym | przy 1.8 (wydajność) |
| P5 | Drugi silnik (libVLC) za `PlayerController`, wybierany w Ustawieniach lub automatycznie przy błędzie formatu | wyłącznie, gdy P1–P3 nie wystarczą na realnych listach; osobny ADR |
| P6 | Własna polityka ponowień i przełączania (już jest failover), dodać test E9 | razem z E9 |

Reguła: zmiana silnika wchodzi z testem (E-scenariusz na emulatorze + test jednostkowy z `FakePlayerController`)
i weryfikacją na boxie; bez danych z boxa nie dodajemy ciężkich komponentów (P3, P5).

## 6. Harmonogram

1. (ta paczka) CI na `main-*`, emulator: BACK i sortowanie; reguła wydania w `CLAUDE.md`.
2. E1, E2, E5 (odtwarzanie VOD, wznawianie, HOME), P1.
3. E3, E4, E8 (ścieżki, zapping, obrót), P4.
4. E6, E7, E9 (motywy/wydajność, duże listy, sieć), P2 po danych z boxa.
5. Przed 1.0: pełny przebieg emulatora + skrypt na boxie bez FAIL + checklista ręczna (plan 2.5).
