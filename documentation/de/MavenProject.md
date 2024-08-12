# Erläuterung der Maven Projekt Datei (pom.xml)

Das Maven Projekt wird in einem XML Dokument beschrieben welches das Projekt Objekt Modell (pom) beschreibt.

## Rahmen der pom.xml
Der Anfang und die letzte Zeile eines jeden Maven Projekts ist eigentlich immer gleich.

In der ersten Zeile wird die Formatierung und das Encoding der Datei angegeben:
```
<?xml version="1.0" encoding="UTF-8"?> 
```

Der oberste Knoten (und der modelVersion Knoten darunter) ist immer wie folgt:
```xml

<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

</project>
```

In der Zukunft ändern die Entwickler ggf. etwas an dem Format, so dass sich die Modellversion ändert, aber bis dahin
bleiben diese Elemente unverändert.

## Eindeutige Identifizierung eines Projektes

Jedes Projekt sollte sich eindeutig über eine groupId, artefactId und version identifizieren lassen.
```xml
    <groupId>de.kneitzel</groupId>
    <artifactId>javafxapp</artifactId>
    <version>1.0-SNAPSHOT</version>
```
**Wichtig**: Wenn Du das Template nutzt, solltest Du die bereits gesetzten Daten anpassen.

**Optional**: In dem Projekt kann auch die Organisation angegeben werden.
```xml
    <organization>
        <name>Java Forum</name>
    </organization>
```
**Wichtig**: Bitte entferne diese Zeilen oder passe die Organisation an.

## Properties

Properties sind vergleichbar zu Variablen und können dazu genutzt werden, Dinge zusammen zu fassen, die sich
öfters ändern könnten.

Ich dem Template sind die Properties in mehrere Abschnitte unterteilt.

### Application properties
In diesem Abschnitt sind die Eigenschaften der Anwendung selbst zu finden. Dies können vorgegebene Namen sein
oder einfach der Klassenname der Hauptklasse zum Starten der Anwendung.

### Dependency versions
Alle Versionen von Abhängigkeiten der Anwendung werden in diesem Abschnitt gesammelt.

### Plugin versions
Die Versionen von Plugin finden sich in diesem Abschnitt.

### Other properties
Alles, was in keinen der bereits genannten Abschnitte fällt, findet sich dann in diesem Abschnitt.

Die Abschnitte, die danach kommen, werden in eigenständigen Dokumenten beschrieben und wären:
- **dependencies** Alle Abhängigkeiten der Anwendung werden hier aufgeführt.
- **build/plugins** Die Plugins mit ihrer Konfiguration finden sich in diesem Bereich
- **profiles** In dem Projekt nutze ich auch ein Profil zur Erstellung von Images.
