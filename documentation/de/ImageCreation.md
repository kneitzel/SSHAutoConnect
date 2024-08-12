# Erstellung von Images

Wenn die Anwendung weiter gegeben werden soll, dann benötigt man das fertig übersetzte Programm sowie
ggf. noch eine Anleitung zur Installation.

Die Erstellung des Images benötigt zwei Schritte:
1. Die Bereitstellung aller notwendigen Dateien. Die umfasst neben den übersetzen Klassen und den Ressourcen der 
Anwendung auch noch alle Abhängigkeiten.
2. Erstellung eines sogenannten Images: Einer Verzeichnisstruktur mit Dateien zusammen mit einem Binary, das gestartet 
werden kann.

## Aufruf

Um das Image zu bauen, ist Maven mit dem Profil image und dem Ziel install zu starten.
Da es teilweise zu Problemen kommt, wenn Dateien überschrieben werden müssen, sollte
als Erstes ein clean durchlaufen.

Dies kann in einem Durchlauf erfolgen:
```shell
mvnw -Pimage clean install
```

## Beschreibung des image Profils

### Das Profil

```xml
        <profile>
            <id>image</id>
            <activation>
                <property>
                    <name>image</name>
                </property>
            </activation>
            <build>
                <plugins>

                <!-- ... -->

                </plugins>
            </build>
        </profile>
```

- Das Profil hat eine Id: image. Damit ist es möglich, das Profil über -Pimage auszuwählen.
- Durch die activation Property ist es zusätzlich möglich, das Profil auch über ein Define auszuwählen: -Dimage
- In dem Profil selbst sind dann Plugins untergebracht.

### maven-dependency-plugin

```xml
                    <plugin>
                        <groupId>org.apache.maven.plugins</groupId>
                        <artifactId>maven-dependency-plugin</artifactId>
                        <version>${maven.dependency.plugin}</version>
                        <executions>
                            <!-- erstmal Abhängigkeiten für den Class-Path kopieren -->
                            <execution>
                                <id>copy-dependencies</id>
                                <phase>package</phase>
                                <goals>
                                    <goal>copy-dependencies</goal>
                                </goals>
                                <configuration>
                                    <outputDirectory>${project.build.directory}/modules</outputDirectory>
                                    <includeScope>runtime</includeScope>
                                    <overWriteReleases>false</overWriteReleases>
                                    <overWriteSnapshots>false</overWriteSnapshots>
                                    <overWriteIfNewer>true</overWriteIfNewer>
                                </configuration>
                            </execution>

                            <!-- dazu noch das Projekt-JAR -->
                            <execution>
                                <id>copy</id>
                                <phase>install</phase>
                                <goals>
                                    <goal>copy</goal>
                                </goals>
                                <configuration>
                                    <outputDirectory>${project.build.directory}/modules</outputDirectory>
                                    <artifactItems>
                                        <artifactItem>
                                            <groupId>${project.groupId}</groupId>
                                            <artifactId>${project.artifactId}</artifactId>
                                            <version>${project.version}</version>
                                            <type>${project.packaging}</type>
                                            <destFileName>${project.build.finalName}.jar</destFileName>
                                        </artifactItem>
                                    </artifactItems>
                                    <overWriteIfNewer>true</overWriteIfNewer>
                                </configuration>
                            </execution>
                        </executions>
                    </plugin>
```

Das Maven Dependency Plugin dient dem Kopieren der benötigten Dateien. Dies erfolgt in zwei Schritten:
1. Es werden alle Abhängigkeiten kopiert
2. Es wird das erzeugte Artefact (das jar File mit dem Code des Projekts) kopiert

#### copy-dependencies
Die Ausführung copy-dependencies innerhalb des maven-dependency-plugins ist darauf ausgelegt, den Prozess des Kopierens 
aller notwendigen Laufzeitabhängigkeiten in ein festgelegtes Ausgabeverzeichnis zu automatisieren. Diese Ausführung ist 
entscheidend dafür, dass alle für die Anwendung erforderlichen Laufzeitkomponenten im Bereitstellungsumfeld verfügbar 
sind. Die Konfiguration stellt sicher, dass Abhängigkeiten effizient während der Package-Phase des Build-Prozesses 
behandelt werden.

Wesentliche Aspekte der Ausführung von copy-dependencies umfassen:

- **phase**: Sie ist so konfiguriert, dass sie während der package-Phase ausgeführt wird, was der ideale Zeitpunkt ist, 
um alle für die verpackte Anwendung benötigten Abhängigkeiten zu sammeln.
- **goal**: Das Ziel copy-dependencies wird genutzt, um das Kopieren der Abhängigkeiten durchzuführen.
- **Konfiguration**: Wichtige Konfigurationseinstellungen beinhalten:
- - **outputDirectory**: Gibt das Verzeichnis an, in das alle Abhängigkeiten kopiert werden, typischerweise ein 
Unterverzeichnis im Build-Verzeichnis wie /modules.
- - **includeScope**: Auf runtime gesetzt, um sicherzustellen, dass nur bei der Laufzeit benötigte Abhängigkeiten 
einbezogen werden, was dazu beiträgt, die Größe und Komplexität der Bereitstellung zu reduzieren.
- - **overWriteReleases, overWriteSnapshots und overWriteIfNewer**: Diese Einstellungen regeln, wie existierende Dateien 
im Zielverzeichnis behandelt werden. Sie verhindern das Überschreiben von stabilen Release-Versionen und 
Snapshot-Versionen, es sei denn, eine neuere Version wird kopiert, was dazu beiträgt, die Integrität und Aktualität der 
Abhängigkeitsdateien zu erhalten.

Diese Konfiguration stellt sicher, dass alle notwendigen Abhängigkeiten vorbereitet und am richtigen Ort verfügbar sind, bevor die Anwendung verpackt wird, was einen reibungslosen Übergang zu den Montage- und Bereitstellungsphasen erleichtert.

#### copy project artefact
Die Ausführung copy project artifact im maven-dependency-plugin ist darauf ausgerichtet, das Kopieren des endgültigen 
Projektartefakts, typischerweise der ausführbaren JAR-Datei, in ein festgelegtes Ausgabeverzeichnis zu erleichtern. 
Dieser Schritt ist entscheidend, da er sicherstellt, dass die aktuellste und überprüfte Version der Anwendung für die 
Verpackung und Bereitstellung verfügbar ist.

Wesentliche Aspekte der Ausführung von copy project artifact umfassen:

- **Phase**: Konfiguriert für die Ausführung während der install-Phase, die auf die Build- und Verifizierungsprozesse 
folgt. Dieser Zeitpunkt ist entscheidend, um sicherzustellen, dass nur vollständig gebaute und getestete Artefakte in 
die Bereitstellungspipeline übernommen werden.
- **Ziele**: Nutzt das Ziel copy, um den Kopiervorgang durchzuführen.
- **Konfiguration**: Wichtige Konfigurationseinstellungen beinhalten:
- - **outputDirectory**: Gibt das Zielverzeichnis an, in dem die JAR-Datei des Projekts platziert wird, typischerweise 
innerhalb des Build-Verzeichnisses unter /modules.
- - **artifactItems**: Eine Liste, die die spezifischen zu kopierenden Projektartefakte detailliert beschreibt. Dies umfasst:
- - - **groupId, artifactId, version und type**: Diese Attribute identifizieren das Projektartefakt eindeutig im 
Maven-Repository und stellen sicher, dass die korrekte Datei ausgewählt wird.
- - - **destFileName**: Gibt den Namen der Datei an, wie sie im Ausgabeverzeichnis erscheinen soll. Dies ermöglicht 
eine Anpassung des Ausgabedateinamens, was besonders nützlich ist, um die Versionskontrolle zu wahren oder zwischen 
verschiedenen Builds zu unterscheiden.
- - **overWriteIfNewer**: Stellt sicher, dass eine vorhandene Datei nur überschrieben wird, wenn die neue Datei aktueller ist, was bei der Verwaltung mehrerer Versionen hilft und sicherstellt, dass bei der Bereitstellung immer das aktuellste Artefakt verwendet wird.

Diese Einrichtung stellt sicher, dass das Projektartefakt korrekt an den geeigneten Ort kopiert wird, wodurch die 
Integrität und Bereitschaft der Anwendung für die Bereitstellung gewahrt bleibt. Sie bietet einen robusten Mechanismus 
zur Verwaltung der Artefakte in einer Build-Pipeline, der sich an den Best Practices für kontinuierliche Integration 
und Auslieferung orientiert.

### jpackage-maven-plugin

```xml
                    <plugin>
                        <groupId>com.github.akman</groupId>
                        <artifactId>jpackage-maven-plugin</artifactId>
                        <version>${jpackage.maven.plugin}</version>
                        <configuration>
                            <name>${appName}</name>
                            <type>IMAGE</type>
                            <modulepath>
                                <dependencysets>
                                    <dependencyset>
                                        <includenames>
                                            <includename>javafx\..*</includename>
                                        </includenames>
                                    </dependencyset>
                                </dependencysets>
                            </modulepath>
                            <addmodules>
                                <addmodule>javafx.controls</addmodule>
                                <addmodule>javafx.graphics</addmodule>
                                <addmodule>javafx.fxml</addmodule>
                                <addmodule>javafx.web</addmodule>
                            </addmodules>
                            <mainclass>${main.class}</mainclass>
                            <input>${project.build.directory}/modules</input>
                            <mainjar>${jar.filename}.jar</mainjar>
                        </configuration>
                        <executions>
                            <execution>
                                <phase>install</phase>
                                <goals>
                                    <goal>jpackage</goal>
                                </goals>
                            </execution>
                        </executions>
                    </plugin>
```
Das jpackage-maven-plugin erleichtert die Erstellung eines eigenständigen Anwendungsimages mithilfe des JPackage-Tools 
von Java, das Teil des JDK ist. Dieses Plugin ist so konfiguriert, dass es während der 'install'-Phase des 
Maven-Build-Lebenszyklus ausgeführt wird. Es bündelt die Anwendung zusammen mit ihren Abhängigkeiten und der 
Java-Laufzeitumgebung in ein plattformspezifisches Paket, was die Distribution und Bereitstellung erleichtert.

Wichtige Konfigurationen für dieses Plugin umfassen:

- **name** und **type**: Gibt den Namen der Anwendung an und setzt den Pakettyp auf 'IMAGE', was darauf hinweist, dass 
ein eigenständiges Installationsimage erstellt wird.
- **modulepath**: Definiert den Pfad zu Java-Modulen und deren Abhängigkeiten, was für Anwendungen, die Java-Module 
verwenden, unerlässlich ist.
- **addmodules**: Listet zusätzliche JavaFX-Module auf, die eingefügt werden sollen, um sicherzustellen, dass 
alle GUI-Komponenten korrekt funktionieren.
- **mainclass**: Legt den Einstiegspunkt der Anwendung fest, der der vollständig qualifizierte Name der Hauptklasse ist.
- **input**: Weist auf das Verzeichnis hin, das die kompilierten Module und Abhängigkeiten enthält.
- **mainjar**: Gibt die Hauptausführbare JAR-Datei der Anwendung an.

Diese Einrichtung ist besonders nützlich, um einen reibungslosen, gestrafften Bereitstellungsprozess für JavaFX-Anwendungen zu schaffen, wodurch die Portabilität und die einfache Installation in verschiedenen Umgebungen verbessert wird.
