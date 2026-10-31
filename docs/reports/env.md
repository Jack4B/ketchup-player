# Raport środowiska (sesja chmurowa)

Data: 2026-10-31.

| Element | Stan | Działanie |
|---|---|---|
| JDK | domyślnie 21 | doinstalowano `openjdk-17-jdk-headless` (apt); build używa `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` |
| Android SDK | brak | zainstalowano cmdline-tools, `platforms;android-35`, `build-tools;35.0.0`, `platform-tools` w `/opt/android-sdk`; `ANDROID_HOME=/opt/android-sdk`, `local.properties` (poza repo) |
| Internet | `google()`, plugin portal, npm OK | `repo.maven.apache.org` zwraca **429** przez proxy sesji |
| Maven Central | obejście | lokalny init script `~/.gradle/init.d/mirror.gradle` przekierowuje na mirror Google (`maven-central.storage-download.googleapis.com`); poza repo, CI używa normalnego `mavenCentral()` |
| Skille repo | `android-tv-ui`, `animation-performance`, `design-taste-frontend`, `emil-design-eng`, `improve-animations`, `review-animations` | wczytane |
| MCP `.mcp.json` | `context7`, `mobile` | oba połączone (context7 wystawia `resolve-library-id`, `query-docs`) |
| Emulator / urządzenie | brak (chmura, bez KVM) | UI weryfikowane Roborazzi (Robolectric) w CI; DoD „start na emulatorze API 34” do potwierdzenia lokalnie przez właściciela |

## Zasada oszczędzania tokenów
Testy, lint, Roborazzi i R8 release uruchamia GitHub Actions; lokalnie tylko `assembleDebug` jako szybka kontrola kompilacji.
