# Zeiterfassung

Eine native Android-App zur persönlichen Arbeitszeiterfassung. Die App ist in Kotlin mit Jetpack Compose umgesetzt und benötigt Android 8.0 (API 26) oder neuer.

## Funktionen

- Arbeitszeit mit einem Start-/Stopp-Timer erfassen
- Laufende Erfassung in einer Benachrichtigung anzeigen und von dort stoppen
- Zeiten lokal speichern, nachtragen, bearbeiten und löschen
- Tagesarbeitszeit von Montag bis Freitag als Diagramm auswerten
- Auswertungen als CSV oder Excel-Datei exportieren

Die Daten bleiben auf dem Gerät. Es gibt keine automatische Cloud- oder Gerätesicherung. Wichtige Zeiten sollten manuell exportiert werden.

## Bauen

Voraussetzungen: Java 17 oder neuer sowie ein installiertes Android SDK mit Plattform 36. Der Gradle-Wrapper lädt die benötigte Gradle-Version selbst herunter.

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Die Debug-APK wird hier erzeugt:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Zum Installieren auf einem verbundenen Android-Gerät mit aktiviertem USB-Debugging:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Projektstruktur

- `app/src/main/java/de/zeiterfassung/domain/` – Erfassungsregeln und Tagesauswertung
- `app/src/main/java/de/zeiterfassung/data/` – lokale SQLite-Speicherung
- `app/src/main/java/de/zeiterfassung/export/` – CSV- und XLSX-Export
- `app/src/main/java/de/zeiterfassung/timer/` – Timer-Benachrichtigung und Wiederherstellung nach Neustart
- `app/src/test/` – Unit-Tests für Erfassung, Auswertung und Export
