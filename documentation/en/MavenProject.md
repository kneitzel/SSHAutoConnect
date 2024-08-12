# Explanation of the Maven Project file (pom.xml)

The Maven Project is described inside a XML document which describes the Project Object Model (pom).

## Start of the pom.xml
The first line defines the format and encoding of the file:
```
<?xml version="1.0" encoding="UTF-8"?> 
```

The top node (+ modelVersion node) of the XML document is the project node should currently always be:
```xml

<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

</project>
```

Maybe in the future the maven project will change the project object model in which case we might want to update
the given xsd and modelVersion.

## Unique identification of project

Each project should identify itself using a groupId, artefactId and version
```xml
    <groupId>de.kneitzel</groupId>
    <artifactId>javafxapp</artifactId>
    <version>1.0-SNAPSHOT</version>
```
**Important**: You should change these values to something that is unique to you (and your organisation) so
that there will never be two projects with the same identification.

**Optional**: You can name your organization if you want to.
```xml
    <organization>
        <name>Java Forum</name>
    </organization>
```
**Please** remove these lines or change them to your organization.

## Properties

Properties are like variables which could be used to have everything, that is quite likely to change, in one location.

In my project, I grouped the properties in multiple areas:

### Application specific properties
In this group I specify everything that is application specific: What names should be used?
Which class is the main class? And so on.

### Dependency versions
All versions of dependencies are stored in this area. That way it is easy to use one version multiple times
and it is easay to change these

### Plugin versions
All plugin versions are stored in this area.

### Other properties
Everything that does not fit in the other areas is placed in here e.g. encoding of files.

The next big areas does not need to be changed to start with the project. Of course: You might want to add dependencies
or other plugins later during development.

The following blocks will be described in detail later:
- **dependencies** with a list of all dependencies.
- **build/plugins** with all plugins used by this project, including their configuration.
- **profiles** We are using a profile. That way, we do not build the image every time we build the project.
