# PMD

## Regeln für PMD konfigurieren
In dem Projekt verwenden wir PMD, um die Code-Qualität sicherzustellen und 
um allgemeine Fehler zu vermeiden. Die Regeln für die Codeanalyse sind in 
der Datei pmd-ruleset.xml definiert, die im Hauptverzeichnis des Projekts 
liegt.

Diese Datei enthält spezifische Regelsätze, die an die Projektanforderungen 
angepasst sind. Jeder Regelsatz innerhalb der pmd-ruleset.xml spezifiziert, 
welche Probleme PMD im Code suchen und melden soll. Die Konfiguration umfasst
verschiedene Kategorien wie Fehlerprävention, Code-Stil, Optimierung und 
viele andere.

Ein typischer Eintrag in dieser Datei sieht wie folgt aus:

```xml
<rule ref="category/java/bestpractices.xml/UnusedImports">
    <priority>5</priority>
</rule>
```
In diesem Beispiel wird die Regel UnusedImports aus dem Bereich der Best 
Practices verwendet, um ungenutzte Importe im Code zu identifizieren. 
Die Priorität dieser Regel ist auf 5 gesetzt, was einer niedrigen Priorität 
entspricht.

Um spezifische Bedürfnisse anzusprechen, können wir auch benutzerdefinierte 
Regeln hinzufügen oder bestehende Regeln anpassen. Dies ermöglicht uns eine 
flexible Anpassung der Codeanalyse, die dazu beiträgt, unseren Code sauber, 
effizient und fehlerfrei zu halten.

Für die effektive Nutzung von PMD ist es wichtig, dass alle Teammitglieder 
verstehen, wie die pmd-ruleset.xml aufgebaut ist und wie sie zur Verbesserung
der Codequalität beiträgt. Daher sollte jeder Entwickler mit dieser 
Konfigurationsdatei vertraut sein und wissen, wie Änderungen daran vorgenommen
werden.

## Abbruch bei Regelverstößen

In dem Maven-Projekt können wir durch das Festlegen spezifischer Ziele (Goals)
des Maven PMD oder Checkstyle Plugins steuern, ob der Build-Prozess bei 
Verstößen gegen festgelegte Regeln abgebrochen werden soll. Diese Funktion 
ist besonders nützlich, um die Code-Qualität sicherzustellen und Fehler 
frühzeitig im Entwicklungsprozess zu erkennen.

Je nach Konfiguration des Projekts können diese Ziele bei PMD gewählt werden:
- **pmd**: Bei Verstößen wird der weitere Build Prozess nicht abgebrochen
- **check**: Strengere Überprüfung und der Build Prozess bricht bei Vertsößen ab.

Damit dies direkt in den Properties konfiguriert werden kann, wird die
Property pmd.target verwendet:
```xml
<pmd.target>pmd</pmd.target>
```

## Ergebnis der Analyse

Das Ergebnis der Analyse findet sich nach der Überprüfung in target/pmd.xml.

Um die Datei besser lesen zu können, sollte man sich die Datei einfach
von der Entwicklungsumgebung formatieren lassen.
