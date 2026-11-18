# Entwurf: Android-App zur Zeiterfassung

## Ziel

Eine möglichst einfache, persönliche Android-App zur Erfassung und Auswertung tatsächlicher Arbeitszeiten. Die App hat keine Team-, Planungs- oder Abrechnungsfunktionen.

## Plattform und Datenspeicherung

- Native Android-App mit Kotlin und Jetpack Compose
- Deutsche Benutzeroberfläche
- Speicherung ausschließlich lokal auf dem Gerät
- Kein Benutzerkonto, kein Server, kein Cloud-Sync und keine automatische Gerätesicherung
- Exporte sind die einzige manuelle Möglichkeit, Daten aus der App herauszusichern

## Navigation

Drei Tabs am unteren Bildschirmrand:

1. **Timer**
2. **Verlauf**
3. **Auswertung**

Beim Öffnen der App wird immer der Timer-Tab angezeigt.

## Timer und Erfassung

- Ein Start-/Stopp-Schalter und höchstens eine gleichzeitig laufende Erfassung
- Eine Erfassung zählt vom Einschalten bis zum Ausschalten; für Pausen, etwa die Mittagspause, wird der Schalter ausgeschaltet
- Es gibt keine automatischen Pausenabzüge oder separaten Pausenfunktionen
- Eine laufende Erfassung übersteht das Schließen oder den Neustart der App sowie einen Neustart des Telefons
- Der aktive Eintrag erscheint im Verlauf als laufend und ist dort erst nach dem Stoppen bearbeitbar
- Stoppen über den Schalter oder die Benachrichtigung speichert sofort und benötigt keine Bestätigung
- Während der Erfassung zeigt eine fortlaufend aktualisierte Benachrichtigung den Status und die bisherige Dauer. Antippen öffnet die App; eine Aktion stoppt die Erfassung
- Die Erfassung funktioniert auch, wenn Android-Benachrichtigungen nicht erlaubt oder deaktiviert sind

## Verlauf und Korrekturen

- Einträge enthalten ausschließlich Start und Ende, ohne Projekt, Tätigkeit oder Notiz
- Einträge können manuell ergänzt, bearbeitet und gelöscht werden
- Löschen erfordert eine Bestätigung
- Überlappende Einträge und Einträge in der Zukunft sind nicht erlaubt
- Einträge werden nach lokalem Datum gruppiert, innerhalb eines Tages neueste zuerst
- Arbeitszeiten können an allen Wochentagen erfasst werden
- Start- und Endzeiten werden minutengenau angezeigt und bearbeitet; Dauern werden auf die nächste volle Minute gerundet

## Zeitberechnung

- Zeitangaben folgen der lokalen Gerätezeit
- Es zählt die tatsächlich verstrichene Zeit, auch bei einem Sommerzeitwechsel
- Erfassungen über Mitternacht werden für Tagesauswertungen auf die jeweiligen lokalen Kalendertage aufgeteilt

## Auswertung

- Balkendiagramm mit einem Tageswert pro Berichtstag
- Anzeige der Gesamtdauer für den ausgewählten Zeitraum
- Standardzeitraum ist die aktuelle Arbeitswoche von Montag bis Freitag
- Schnellwahl für aktuelle, vorherige und nächste Woche
- Zusätzlich ist ein frei wählbarer Von-bis-Zeitraum möglich; beide Grenzen sind eingeschlossen
- Berichte enthalten ausschließlich Montag bis Freitag. Wochenendtage werden aus Diagramm und Bericht ausgelassen, auch wenn dort Arbeitszeit erfasst wurde
- Wochentage ohne Arbeitszeit werden mit 0 Stunden angezeigt
- Anzeige der Arbeitsdauer im Format Stunden:Minuten, zum Beispiel `7:30`
- Keine Berechnung von Sollstunden, Überstunden, Urlaub oder Feiertagen

## Export

- Export wahlweise als CSV oder echtes Excel-Format (`.xlsx`)
- Der Export verwendet den aktuell in der Auswertung gewählten Zeitraum
- Öffnen des Android-Dateidialogs zur Auswahl von Speicherort und Dateiname
- Exportiert werden ausschließlich Tagessummen, keine einzelnen Arbeitsintervalle
- Enthalten sind alle Berichtstage (Montag bis Freitag), auch Tage mit 0 Stunden
- Deutsche Spaltenüberschriften: `Datum` und `Arbeitszeit`; zusätzlich eine Zeile `Gesamt`
- Tagessummen und Zeitraum-Gesamtsumme werden als Dezimalstunden mit zwei Nachkommastellen ausgegeben
- Die Gesamtsumme wird aus den ungerundeten Minuten berechnet; dadurch kann sie geringfügig von der Summe der gerundeten Tageswerte abweichen
- CSV ist für eine deutsche Excel-Installation formatiert
