# PMD

## Rules for PMD

In this project, we use PMD to ensure code quality and prevent common 
mistakes. The rules for code analysis are defined in the pmd-ruleset.xml 
file located in the main directory of the project.

This file contains specific rule sets tailored to the project requirements. 
Each rule set within the pmd-ruleset.xml specifies what issues PMD should 
look for and report in the code. The configuration includes various 
categories such as error prevention, code style, optimization, and many 
others.

A typical entry in this file looks like this:

```xml
<rule ref="category/java/bestpractices.xml/UnusedImports">
    <priority>5</priority>
</rule>
```

In this example, the UnusedImports rule from the best practices category is 
used to identify unused imports in the code. The priority of this rule is set
to 5, which indicates a low priority.

To address specific needs, we can also add custom rules or modify existing
ones. This allows us a flexible adjustment of the code analysis, which helps
to keep our code clean, efficient, and error-free.

For effective use of PMD, it is important that all team members understand
how the pmd-ruleset.xml is structured and how it contributes to improving
code quality. Therefore, every developer should be familiar with this
configuration file and know how to make changes to it.

## Stop build process on rule violations

In the Maven project, we can control whether the build process should be
aborted upon violations of specified rules by setting specific goals (Goals)
of the Maven PMD or Checkstyle plugins. This function is particularly useful
for ensuring code quality and detecting errors early in the development
process.

Depending on the project configuration, these goals can be selected for PMD:

- **pmd**: Violations do not cause the build process to be aborted
- **check**: Stricter verification and the build process is aborted upon
violations.

To enable configuration directly in the properties, the pmd.target property is used:

```xml
<pmd.target>pmd</pmd.target>
```


## Result of Analysis

The result can be found inside target/pmd.xml.

Formatting the file will make it much easier to read.