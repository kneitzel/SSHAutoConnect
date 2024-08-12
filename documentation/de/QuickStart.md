# Schnellstart

- Lade einfach das Projekt als ZIP Datei von GitHub und entpacke es an einer 
Stelle Deiner Wahl auf dem Computer.
- Öffne die pom.xl und ändere die Einstellungen am Anfang des Dokumentes.

**Hinweis**: Im folgenden werden die Befehle für Unix artige Systeme / macos
angegeben. Unter Windows kannst Du am Anfang des Befehles das ./mvnw durch mvnw
ersetzen.

## Wie kannst Du das Projekt nutzen

### Projekt bereinigen

Um das Projekt zu bereinigen, kannst Du
```./mvnw clean```
aufrufen.

### Übersetzen der Anwendung (Ohne ein Image zur Weitergabe zu bauen)

Um die Anwendung zu übersetzen kannst Du aufrufen:
```./mvnw package```

### Bau des Application Images zur Weitergabe (JPackage)

Um das Image mit JPackage zu bauen, rufst du einfach Maven mit dem Profil Image und dem
Ziel install auf:
```./mvnw -Dimage install```

### Bau des native Images zur Weitergabe (GraalVM)

Um das Image mit NativeImage / GraalVM zu bauen, rufst du einfach Maven mit dem Profil Image und dem
Ziel install auf:
```./mvnw -Dnative install```

## Bau einer fat jar Datei

Über das Profil fatjar läßt sich auch ein fat jar File bauen, welches alle Abhängigkeiten enthält.
```./mvnw -Dimage install```

## Ergebnisse der statischen Codeanalyse

Die Codeanalyse läuft automatisch beim Bau des Projektes und die Ergebnisse
finden sich in:
- ./target/pmd.xml
- ./target/spotbugsXml.xml
