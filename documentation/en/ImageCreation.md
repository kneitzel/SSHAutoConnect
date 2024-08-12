# Image Creation

If the application is to be distributed, the translated program and possibly installation instructions are required.

Creating the image involves two steps:

Provision of all necessary files. This includes the translated classes and the application's resources, as well as all dependencies.
Creation of an "image": A directory structure with files along with a binary that can be executed.

## Invocation

To build the image, start Maven with the image profile and the install goal.
Since there are sometimes problems when files need to be overwritten, a clean should be run first.

This can be done in one run:
```shell
mvnw -Pimage clean install
```

## Description of theImage Profile

### The Profile

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

- The profile has an ID: image. This allows the profile to be selected using -Pimage.
- Additionally, the profile can be selected using a define: -Dimage
- The profile itself then contains plugins.

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

The Maven Dependency Plugin is used to copy the required files. This is done in two steps:

1. All dependencies are copied.
2. The produced artifact (the jar file with the project code) is copied.

#### copy-dependencies
The copy-dependencies execution within the maven-dependency-plugin is designed to automate the process of copying all 
necessary runtime dependencies into a specified output directory. This execution is vital for ensuring that all runtime 
components required by the application are available in the deployment environment. The configuration ensures that 
dependencies are handled efficiently during the package phase of the build process.

Key aspects of the copy-dependencies execution include:

- **Phase**: It is configured to run during the package phase, which is the ideal time to gather all dependencies needed 
for the packaged application.
- **Goals**: The copy-dependencies goal is utilized to perform the action of copying dependencies.
- **Configuration**: Important configuration settings include:
- - **outputDirectory**: Specifies the directory where all dependencies will be copied, typically a subdirectory in the 
build directory such as /modules.
- - **includeScope**: Set to runtime to ensure that only dependencies needed at runtime are included, which helps in 
reducing the size and complexity of the deployment.
- - **overWriteReleases, overWriteSnapshots, and overWriteIfNewer**: These settings manage how existing files in the 
target directory are handled. They prevent overwriting of stable release versions and snapshot versions unless a newer 
version is being copied, which helps in maintaining the integrity and up-to-dateness of the dependency files.

This configuration ensures that all necessary dependencies are prepared and available in the correct location before the application is packaged, facilitating a smooth transition to the assembly and deployment stages.

#### copy project artefact
The copy project artifact execution in the maven-dependency-plugin is tailored to facilitate the copying of the final 
project artifact, typically the executable JAR file, into a designated output directory. This step is critical as it 
ensures that the most current and verified version of the application is available for packaging and deployment.

Key aspects of the copy project artifact execution include:

- **Phase**: Configured to execute during the install phase, which follows the build and verification processes. 
This timing is crucial to ensure that only fully built and tested artifacts are moved forward in the deployment pipeline.
- **Goals**: Utilizes the copy goal to perform the file copying operation.
- **Configuration**: Important configuration settings include:
- - **outputDirectory**: Specifies the destination directory where the project's JAR file will be placed, typically 
within the build directory under /modules.
- - **artifactItems**: A list detailing the specific project artifacts to copy. This includes:
- - - **groupId, artifactId, version, and type**: These attributes uniquely identify the project artifact within the Maven 
repository, ensuring the correct file is selected.
- - - **destFileName**: Specifies the name of the file as it should appear in the output directory. This allows for 
customization of the output filename, which can be particularly useful in maintaining version control or distinguishing 
between different builds.
- - **overWriteIfNewer**: Ensures that an existing file is only overwritten if the new file is more recent, which helps 
in managing multiple versions and ensuring that the deployment always uses the most up-to-date artifact.

This setup ensures that the project artifact is correctly copied to the appropriate location, maintaining the integrity and readiness of the application for deployment. It provides a robust mechanism for managing the artifacts in a build pipeline, aligning with best practices for continuous integration and delivery.

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
The jpackage-maven-plugin facilitates the creation of a self-contained application image using Java's JPackage 
tool, which is part of the JDK. This plugin is configured to run during the 'install' phase of the Maven build 
lifecycle. It bundles the application along with its dependencies and the Java runtime into a platform-specific 
package, making it easier to distribute and deploy.

Key configurations for this plugin include:

- **Name and Type**: Specifies the application's name and sets the package type to 'IMAGE', indicating a standalone 
installation image is created.
- **Module Path**: Defines the path to Java modules and their dependencies, essential for applications that use Java modules.
- **Additional Modules**: Lists extra JavaFX modules to include, ensuring all GUI components function correctly.
- **Main Class**: Sets the entry point of the application, which is the fully qualified name of the main class.
- **Input Directory**: Points to the directory containing the compiled modules and dependencies.
- **Main JAR**: Specifies the main executable JAR file of the application.

This setup is particularly useful for creating a smooth, streamlined deployment process for JavaFX applications, 
enhancing portability and ease of installation across different environments.
