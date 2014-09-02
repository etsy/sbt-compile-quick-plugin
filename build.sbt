sbtPlugin := true

name := "sbt-compile-quick-plugin"

organization := "com.etsy"

version := "0.3.2-SNAPSHOT"

publishTo <<= version { (v: String) =>
  val archivaURL = "http://ivy.etsycorp.com/repository"
  if (v.trim.endsWith("SNAPSHOT")) {
    Some("snapshots" at (archivaURL + "/snapshots"))
  } else {
    Some("releases"  at (archivaURL + "/internal"))
  }
}
