# sbt-compile-quick-plugin

This project provides an SBT 0.13+ plugin for compiling and packaging
a single file.

The implementation was based on a previous, non-functional
implementation found [here](https://github.com/sbt/sbt/issues/240).

## Setup

Add the following lines to `project/plugins.sbt`

```scala
addSbtPlugin("com.etsy" % "sbt-compile-quick-plugin" % "0.4.0")

resolvers += "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"	
```

Then add the following line to `build.sbt`:

```scala
com.etsy.sbt.CompileQuick.compileQuickSettings
```

## Usage

You can compile a single file with the `compileQuick` command.  It
supports tab-completing the file name.  You can also compile a single
test file with the `test:compileQuick` command.

You can produce a JAR without compiling any additional files with the
`packageQuick` command.  By default this will include all class files
located in `compile:classDirectory`.  You can add additional files to
be packaged by `packageQuick` by appending to the value of
`filesToPackage`.
