# ADR: polityki §10.2: przepisanie wybranych zachowań zamiast portu 1:1

Status: **zdecydowane** (właściciel zlecił samodzielną decyzję, 2026-11-02). Uzupełnia `legacy-policy-port.md`.

## Kontekst
Stary kod (BK Player Next, poza repo) jest dostępny do czytania. Klasy z §10.2, o które pyta plan (1.9), są małe (13 do 86
linii): to głównie stałe i proste warunki dopasowane do architektury starego odtwarzacza (własne pętle nadzoru,
współdzielenie dekodera między kanałami). Nowy odtwarzacz stoi na Media3 + `FailoverCoordinator`, więc mechanika inna.
Golden masterów od właściciela nie ma, a „BkTvFailoverDeadline” nie istnieje w starym kodzie (rolę terminu przełączenia
pełni `BkFailoverPolicy.tvSwitchDelaySeconds`, już w `:core:policy`).

## Decyzja: przepisujemy ZACHOWANIA, nie klasy; stałe zachowujemy, mechanikę nie
Port 1:1 dałby martwy kod pod architekturę, której nie mamy. Wchodzi tylko to, co ma wartość dla użytkownika i czego
nowa aplikacja jeszcze nie robi. Każda wartość ma w KDoc źródło w starej klasie.

| Stara klasa | Decyzja | Co w nowej aplikacji |
|---|---|---|
| `BkSmartBufferPolicy` (profile 15/30/50/90 s) | WCHODZI | `LiveBufferPolicy` + ustawienie „Bufor odtwarzania” → `DefaultLoadControl` |
| `CinemaResumePolicy`, `SeriesEpisodePolicy.watchBadge` (progi 1 s/97%, 30 s/95%) | WCHODZI | `ResumeRules` (wznowienie, „Kontynuuj”, „obejrzany”) zamiast rozrzuconego 0,9 |
| `PlaybackStabilityPolicy.retryHttpStatus` | MAMY | `FailFastLoadPolicy` (4xx kończy od razu, wyjątki 408/429): świadomie szybsze niż stare ponawianie |
| `BkTvFailoverMatch`, `BkVodFailoverMatch` | MAMY | `catalog.equivalents` po znormalizowanej nazwie + `FailoverCoordinator`; do porównania na prawdziwych listach |
| `BkFailoverPolicy`, termin przełączenia TV | MAMY | `core:policy` (`BkFailoverPolicy`), opóźnienie 5/10/15/25 s w Ustawieniach |
| `StreamUserAgent`, `NetworkRoutePolicy` (tylko UA) | MAMY | profile User-Agent z wyborem; domyślny okhttp/4.10.0 (import), VLC (odtwarzacz) |
| `StreamRecoveryPolicy` (klasyfikacja zacięcia: sieć/dekoder, progi 12/19/28 s) | ZOSTAWIAMY | Media3 + ponowienia failoveru pokrywają przypadek; bez danych z boxa brak dowodu, że klasyfikacja coś poprawi |
| `NetworkRoutePolicy` (miękkie/twarde ponowne łączenie po zmianie Wi-Fi/VPN) | ZOSTAWIAMY | VPN jest w planie 1.1; wymaga testu na urządzeniu |
| `TvBufferSessionPolicy`, `PlaybackStabilityPolicy.canReuse/currentOwner` | NIE | zależą od współdzielenia dekodera starego odtwarzacza; nowy tworzy kontroler per ekran |
| `PlaybackStabilityPolicy.refreshStalker…` | NIE | Stalker poza zakresem 1.0 |
| `SeriesEpisodePolicy` (paginacja 80 odcinków, etykiety) | NIE | Szczegóły pokazują cały sezon w `LazyColumn`; kod odcinka już jest |
| `Radar*`, `Memory*`, `Home*Policy`, `ArtLoadPolicy` itd. | POZA TYM PUNKTEM | osobne pozycje planu |

## Konsekwencje
- Pokrycie: testy wartości granicznych w `:core:policy` (stałe ze starej aplikacji są przypięte testami), testy ustawienia i
  wiązania z silnikiem, scenariusz E2E wyboru bufora.
- Zmiana progów wznowienia z 90% na 95%/97% jest świadoma (zgodność ze starą aplikacją); „Kontynuuj” wymaga co najmniej 30 s.
- Jeśli raport z boxa pokaże zacięcia, których Media3 + failover nie łapią, wracamy do `StreamRecoveryPolicy` z danymi.
