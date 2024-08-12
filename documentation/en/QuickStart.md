# Quick Start

- Simply download a zip file of this project and unzip it somewhere on your computer
- open the pom.xml and change the settings at the start of the document to fit with your project

## How to use this project

### Clean up

To clean up the project, call
```./mvnw clean```

### build the application (Without building the application image)

To build the application, maven / the maven wrapper can be used. Simply do a
```./mvnw package```
to build the application.
(simply call mvnw instead of ./mvnw on windows!)

### Build the application Image (jpackage)

To build the image with jpackage, the profile image must be used:
```./mvnw -Dimage install```

### Build the native Image (GraalVM)

To build the native image with GraalVM, the profile native must be used:
```./mvnw -Dnative install```

### Build a fat jar file

You can build a fat jar file using the fatjar Image:
```./mvnw -Dfatjar package```

## Static code analysis results

The static code analysis is done during the build of the application. The results can be found in
- ./target/pmd.xml
- ./target/spotbugsXml.xml
