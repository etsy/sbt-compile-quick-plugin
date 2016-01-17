import SonatypeKeys._

sbtPlugin := true

name := "sbt-compile-quick-plugin"

organization := "com.etsy"

version := "1.0.1-SNAPSHOT"

xerial.sbt.Sonatype.sonatypeSettings

pomExtra := <url>https://github.com/etsy/sbt-checkstyle-plugin</url>
  <licenses>
    <license>
      <name>MIT License</name>
      <url>http://opensource.org/licenses/MIT</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:etsy/sbt-checkstyle-plugin.git</url>
    <connection>scm:git:git@github.com:etsy/sbt-checkstyle-plugin.git</connection>
  </scm>
  <developers>
    <developer>
      <id>ajsquared</id>
      <name>Andrew Johnson</name>
      <url>github.com/ajsquared</url>
    </developer>
  </developers>

scalastyleConfig := file("scalastyle.xml")

scalastyleFailOnError := true