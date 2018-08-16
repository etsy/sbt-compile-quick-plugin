sbtPlugin := true

name := "sbt-compile-quick-plugin"

organization := "com.etsy"

version := "1.5.0-SNAPSHOT"

crossSbtVersions := Seq("0.13.17", "1.1.6")

scalaVersion := {
  (sbtBinaryVersion in pluginCrossBuild).value match {
    case "0.13" => "2.10.7"
    case _      => "2.12.6"
  }
}

libraryDependencies ++= {
  (sbtBinaryVersion in pluginCrossBuild).value match {
    case "0.13" => Seq.empty
    case _      => Seq("com.eed3si9n" %% "sjson-new-core" % "0.8.2")
  }
}

xerial.sbt.Sonatype.sonatypeSettings
publishTo := sonatypePublishTo.value

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
