# Wydanie 1.0: jak podpisać i opublikować APK

APK wydania jest podpisywany **twoim własnym kluczem**. Klucz i hasła nie trafiają do repozytorium, tylko do sekretów GitHub.

## 1. Jednorazowo: wygeneruj klucz (na komputerze albo w Termux z `pkg install openjdk-17`)
```
keytool -genkeypair -v -keystore ketchup-release.jks -alias ketchup -keyalg RSA -keysize 4096 -validity 10000
```
Zapisz hasła i **zrób kopię pliku `ketchup-release.jks` w bezpiecznym miejscu**. Bez niego nie wydasz aktualizacji tej samej aplikacji.

## 2. Wgraj sekrety do repozytorium (Settings → Secrets and variables → Actions)
| Sekret | Wartość |
|---|---|
| `KETCHUP_KEYSTORE_BASE64` | `base64 -w0 ketchup-release.jks` (cała zawartość jako jedna linia) |
| `KETCHUP_KEYSTORE_PASSWORD` | hasło magazynu kluczy |
| `KETCHUP_KEY_ALIAS` | `ketchup` |
| `KETCHUP_KEY_PASSWORD` | hasło klucza |

## 3. Wydaj
- Tag: `git tag v1.0.0 && git push origin v1.0.0`, albo Actions → Release → Run workflow z wersją `1.0.0`.
- Wynik: podpisany `KetchupPlayer-1.0.0.apk` w artefaktach i w GitHub Release.
- `versionCode` liczony jest z wersji: `1.0.0` → `10000`, `1.2.3` → `10203` (zawsze rośnie).

## 4. Przed wydaniem (lista kontrolna)
1. CI zielony na gałęzi wydania (build, detekt, testy, Roborazzi, lint, emulator).
2. `~/ketchup_tvbox_test.sh` na boxie: 0 FAIL, bez wyjścia na pulpit, bez crashy w logach.
3. Ręcznie: kreator → import własnej listy → Na żywo → grupy → Przewodnik (Siatka, Radar, Zainteresowania) → Filmy → szczegóły → odtwarzanie → OSD (audio, napisy, synchronizacja) → BACK po wszystkich ekranach.
4. `docs/TEST-CHECKLIST.md` zaktualizowana do wersji wydania.

## Uwagi
- Debug (`pl.ketchupplayer.tv.debug`) i wydanie (`pl.ketchupplayer.tv`) mają różne pakiety, więc mogą stać obok siebie.
- Zmiana klucza podpisu = nowa aplikacja dla systemu (trzeba odinstalować starą).
