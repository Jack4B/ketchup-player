# Lista testów beta: Ketchup Player (TV)

Wersja: beta 0.2 (Live TV, Filmy, Seriale, wygląd wg makiet v3). Co jest sprawdzane automatycznie w CI, a co trzeba sprawdzić ręcznie na prawdziwym boxie.
Oznaczenia: ✅ pokryte automatycznie, 🖐 do sprawdzenia ręcznie na TV, ⚠ znane ograniczenie.

## Przygotowanie
1. Zainstaluj APK (`adb install -r` albo „wyślij pliki na TV”), zezwól na instalację z nieznanych źródeł.
2. Do testów publicznych: lista `https://iptv-org.github.io/iptv/countries/pl.m3u` (legalne, darmowe kanały).
3. Do testów failoveru: dwie listy z tym samym kanałem (np. dwie różne od jednego dostawcy), jedną można zepsuć
   błędnym adresem.
4. Zgłaszaj: ekran, co kliknąłeś, co zobaczyłeś, czego oczekiwałeś. Zrzut ekranu pomaga.

## A. Start i nawigacja
| # | Scenariusz | Oczekiwany wynik | Auto |
|---|---|---|---|
| A1 | Pierwsze uruchomienie | Splash z logo, potem kreator 3 kroków (Witaj, Dodaj listę, Gotowe); „Pomiń” działa w każdym kroku | ✅ E2E + Roborazzi; 🖐 wygląd splasha na TV |
| A1b | Kolejne uruchomienia | Home z szyną nawigacji po lewej (profil, szukaj, home, na żywo, przewodnik, filmy, seriale, moja lista, ustawienia), fokus na pierwszym kafelku | ✅ E2E + emulator |
| A2 | Pilot: strzałki, OK, BACK po wszystkich ekranach | Wszystko osiągalne, BACK zawsze cofa o jeden ekran | ✅ testy D-pad każdego ekranu |
| A3 | Obrót / uśpienie i wybudzenie TV w środku ekranu | Wracasz na ten sam ekran | ✅ AppStartTest, scenariusz restartu |
| A4 | Overscan: marginesy na Twoim TV | Teksty i ramki nie wychodzą poza ekran | 🖐 |
| A5 | Czytelność z 3 m (rozmiar tekstu, kontrast) | Wszystko czytelne | 🖐 |

## B. Dostawcy (listy)
| # | Scenariusz | Oczekiwany wynik | Auto |
|---|---|---|---|
| B1 | Dostawcy → Dodaj → M3U: nazwa + adres publicznej listy → „Zapisz i pobierz” | Lista zapisana, „Kanały: N” | ✅ scenariusz E2E (lista iptv-org) |
| B2 | To samo na prawdziwym Android Keystore (zapis zaszyfrowanego adresu) | Bez crasha | ✅ emulator Android TV (Enter w polach formularza) |
| B3 | Dodaj konto Xtream (serwer, login, hasło) | Kanały z kategoriami jako grupy | ✅ scenariusz E2E |
| B4 | Złe hasło Xtream | Komunikat „pobranie się nie udało”, aplikacja nie pada | ✅ scenariusz E2E |
| B5 | Nieistniejący adres (404) / brak sieci | Komunikat błędu, dostawca zostaje do poprawienia | ✅ scenariusz E2E |
| B6 | Bardzo duża lista (100 tys. wpisów, filmy i seriale razem z kanałami) | Import w kilka sekund, na liście tylko kanały na żywo | ✅ scenariusz E2E (lista 29 MB); 🖐 pamięć na słabym boxie |
| B7 | Odśwież listę, usuń listę | Kanały odświeżone / znikają razem z listą | ✅ testy repozytorium |
| B8 | Wpisywanie adresu pilotem (klawiatura ekranowa) | Enter/✓ w polu: następne pole, w ostatnim zapis listy | ✅ E2E i emulator; 🖐 Twoja klawiatura ekranowa |
| B9 | Własna lista M3U z kontem użytkownika (hasło w adresie) | Działa; hasło nie jest widoczne na ekranie ani w bazie | ✅ test bajtów bazy; 🖐 na Twojej liście |
| B10 | Filmy i seriale z M3U (`/movie/`, `/series/`, odcinki `S01E02`) i z Xtream | Pojawiają się w Filmy/Seriale, tytuły bez przedrostka języka i roku | ✅ E2E; 🖐 na Twoich listach |
| B11 | Stalker | ⚠ nieobsługiwany | — |

## C. Na żywo
| # | Scenariusz | Oczekiwany wynik | Auto |
|---|---|---|---|
| C1 | Na żywo: grupy po lewej, kanały po prawej | Polskie grupy (`PL| …`, „Polska”) na początku; nazwy bez przedrostków dostawcy | ✅ |
| C2 | Grupa bez nazwy | Pokazana jako „Bez grupy” | ✅ |
| C3 | Wybrana grupa przy 100+ grupach | Lista grup przewija się do wybranej | ✅ |
| C4 | Długie przytrzymanie OK na kanale | Gwiazdka ★; pojawia się grupa „★ Ulubione (n)” | ✅ (długie OK z pilota w teście); 🖐 na Twoim pilocie |
| C5 | Zwykłe OK na kanale | Otwiera odtwarzacz | ✅ |
| C6 | „Teraz leci” z paskiem postępu przy kanałach | Widoczne, gdy EPG ma dane | ✅ scenariusz E2E |
| C7 | Restart aplikacji | Listy, kanały i ulubione zostają | ✅ scenariusz E2E |

## D. Odtwarzacz
| # | Scenariusz | Oczekiwany wynik | Auto |
|---|---|---|---|
| D1 | Otwórz działający kanał HLS | Obraz i dźwięk, nazwa kanału na 4 s | ✅ emulator (HLS z ffmpeg); 🖐 na prawdziwych kanałach |
| D2 | Czas startu kanału, płynność, brak zacięć | Akceptowalne | 🖐 |
| D3 | Kanał nie działa | Po chwili panel „Nie udało się odtworzyć” z „Spróbuj ponownie” i „Wróć” | ✅ scenariusz E2E |
| D4 | BACK z odtwarzacza | Wraca do listy kanałów | ✅ |
| D5 | Dźwięk, napisy, wybór ścieżek audio | ⚠ nieobsługiwane w tej becie | — |
| D6 | Różne formaty (HLS, TS, MPEG-DASH, H.265) na Twoim boxie | Zależnie od sprzętu | 🖐 |

## E. Failover (przełączanie źródeł)
| # | Scenariusz | Oczekiwany wynik | Auto |
|---|---|---|---|
| E1 | Ten sam kanał na dwóch listach, pierwsza zepsuta (404) | Szybkie przełączenie (2 próby, ok. kilka sekund), „Przełączono na Lista 2 · nazwa” i obraz z drugiej | ✅ E2E + emulator z prawdziwym ExoPlayerem; 🖐 czas na Twojej sieci |
| E2 | Wszystkie źródła zepsute | Panel błędu po wyczerpaniu źródeł | ✅ |
| E3 | Ustawienia → Odtwarzanie → wyłącz przełączanie | Zepsuty kanał nie przełącza się na drugą listę | ✅ |
| E4 | Czułość: szybka / standardowa / ostrożna; opóźnienie 5/10/15/25 s | Zapamiętane po restarcie; zmienia czas reakcji | ✅ logika; 🖐 odczucie w praktyce |
| E5 | Kanał zacina się (nie pada) na jednej liście | Po czasie z ustawień przełącza na drugą | ✅ testy na wirtualnym czasie; 🖐 prawdziwa sieć |
| E6 | Zgodność z logiką starej aplikacji (golden master) | Progi, cooldown, ponowienia, opóźnienie TV, przełączniki identyczne ze starym `BkFailoverPolicy` (107 przypadków) | ✅ `BkFailoverPolicyGoldenTest`; pozostałe polityki (§10.2) po resecie |

## F. Przewodnik EPG
| # | Scenariusz | Oczekiwany wynik | Auto |
|---|---|---|---|
| F1 | Przewodnik: siatka 12 godzin | Audycje w blokach, trwające podświetlone | ✅ |
| F2 | Pilot: góra/dół kanał, lewo/prawo audycja, OK otwiera kanał | Działa, oś przewija się wraz z fokusem | ✅ |
| F3 | EPG z listy dostawcy (`url-tvg`) | Audycje widoczne w Na żywo i w przewodniku | ✅ scenariusz E2E |
| F4 | Źródła EPG: epg.ovh, Teleman, własne źródło XMLTV (włącz/wyłącz, dodaj, usuń) | Zmiany zapamiętane | ✅ |
| F5 | Dopasowanie kanałów do EPG po nazwie (bez tvg-id) | Większość popularnych kanałów PL ma audycje | 🖐 na Twojej liście |
| F6 | Kanał bez danych | „Brak danych przewodnika” | ✅ |

## G. Wygląd i ustawienia
| # | Scenariusz | Oczekiwany wynik | Auto |
|---|---|---|---|
| G1 | Wygląd: motyw Granat / Czerń / Bordo | Zmiana na żywo, zapamiętana | ✅ |
| G2 | Kolor akcentu: gotowe i własny (HEX/suwaki) | Kontrast poprawiany automatycznie | ✅ |
| G3 | Czy wygląd odpowiada oczekiwaniom (kolory, logo, ikona, banner na ekranie głównym TV) | Akceptacja brandingu | 🖐 |

## H. Start („Ostatnio oglądane”)
| # | Scenariusz | Oczekiwany wynik | Auto |
|---|---|---|---|
| H1 | Obejrzyj kilka kanałów, wróć na start | Do 5 ostatnich kanałów, OK wznawia | ✅ |


## J. Filmy, Seriale, szczegóły
| # | Scenariusz | Oczekiwany wynik | Auto |
|---|---|---|---|
| J1 | Filmy: siatka plakatów, kategorie po lewej, chipy Najnowsze/A–Z/Ocena/Rok, pole szukania | Fokus na pierwszym plakacie; zmiana sortowania i kategorii działa | ✅ D-pad + ViewModel + E2E |
| J2 | Plakaty z internetu | Obrazy się ładują, bez nich kafel z inicjałem | 🖐 (bez sieci w testach) |
| J3 | Szczegóły filmu: Oglądaj / Wznów / Od początku / Moja lista | Przyciski działają, „Wznów” pokazuje czas | ✅ E2E (czas wznowienia: test repozytorium) |
| J4 | Szczegóły serialu: sezony i odcinki pobierane z serwera, Podobne | Przełączanie sezonów, OK na odcinku gra | ✅ E2E (Xtream i M3U) |
| J5 | Odtwarzacz filmu: LEWO/PRAWO ±10 s, OK pauza, pasek postępu | Pozycja zapisuje się co 5 s i przy wyjściu | ✅ logika; 🖐 przewijanie na prawdziwym pliku |
| J6 | Moja lista i Kontynuuj oglądanie (Home) | Wybór zostaje po restarcie i odświeżeniu listy | ✅ |
| J7 | Szukaj (cały katalog: filmy i seriale) | Wynik po fragmencie tytułu | ✅ E2E |
| J8 | Duży katalog (19 tys. filmów, 76 tys. odcinków) | Import w kilkanaście sekund, UI płynny | ✅ import; 🖐 płynność na słabym boxie |

## K. Ustawienia i wygląd
| # | Scenariusz | Oczekiwany wynik | Auto |
|---|---|---|---|
| K1 | Ustawienia dwupoziomowe: kategorie po lewej, opcje po prawej | PRAWO wchodzi w opcje, BACK wraca do kategorii, potem na szynę | ✅ E2E |
| K2 | Teksty zawijane nie nachodzą na siebie | Czytelne w każdym ekranie | ✅ test regresji + zrzuty; 🖐 Twój TV |
| K3 | Logo: ikona w szynie, ikona launchera, banner TV | Zgodne z dostarczoną grafiką | 🖐 |

## I. Stabilność (tylko na boxie)
| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| I1 | Godzina oglądania jednego kanału | Bez zawieszenia i wycieku pamięci 🖐 |
| I2 | Szybkie przełączanie między kanałami (20 razy) | Bez crasha 🖐 |
| I3 | Utrata sieci i powrót w trakcie oglądania | Przełączenie lub komunikat, aplikacja żyje 🖐 |
| I4 | Zimny start na słabym boxie | Czas do ekranu startowego akceptowalny 🖐 |

## Poza zakresem tej bety (znane luki)
Radar i „Moje zainteresowania” w przewodniku, profile i PIN, nagrywanie, VPN, NAS, migracja z BK Player Next, Stalker,
wybór ścieżek audio i napisów, zwiastuny, podpisane wydanie (APK jest podpisany kluczem debug).

## Jak zgłaszać błędy
Opis w jednym zdaniu, numer scenariusza z tej listy, zrzut ekranu. Przy crashu: kiedy dokładnie (po jakim kliknięciu).
