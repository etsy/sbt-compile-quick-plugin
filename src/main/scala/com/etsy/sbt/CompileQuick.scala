package com.etsy.sbt

import sbt._
import Keys._
import sbt.Defaults._
import Def.{Initialize}
import sbt.complete._
import sbinary.DefaultProtocol._
import Cache.seqFormat
import Compiler._

// This plugin allows compiling and packaging a single class
object CompileQuick extends Plugin{
  import CompileQuickTasks._

  object CompileQuickTasks {
    lazy val compileQuick = InputKey[Unit]("compile-quick")
    lazy val scaldingJobs = TaskKey[Seq[File]]("scalding-jobs")
    lazy val packageQuick = TaskKey[File]("package-quick")
    lazy val filesToPackage = TaskKey[Seq[(File, String)]]("files-to-package")
    lazy val packageQuickOutput = SettingKey[File]("package-quick-output")
  }

  def packageQuickTask(files: TaskKey[Seq[(File, String)]]) : Initialize[Task[File]] = Def.task {
    val output = packageQuickOutput.value
    IO.delete(output)
    val s = streams.value
    val packageConf = new Package.Configuration(files.value, output, Seq())
    Package(packageConf, s.cacheDirectory, s.log)
    output
  }

  def runParser: (State, Seq[File]) => Parser[String] = {
    import DefaultParsers._
      (state, jobs) => {
      Space ~> token(NotSpace examples jobs.map(_.toString).toSet)
    }
  }

  def compileQuickTask : Initialize[InputTask[Unit]] = {
    val parser = Defaults.loadForParser(scaldingJobs) { (s, names) =>
      runParser(s, names.getOrElse(Nil))
    }
    val inputs = compileInputs in (Compile, compile)
    val classpath = dependencyClasspath in Compile
    val outputDir = classDirectory in Compile
    val options = scalacOptions in compileQuick

    Def.inputTask {
      val input = parser.parsed
      val fileToCompile = input.charAt(0) match {
        case '/' => file(input)
        case _ => file((scalaSource in Compile).value.getAbsolutePath + "/" + input)
      }
      if (fileToCompile.exists) {
        val compilers = Keys.compilers.value
        val log = streams.value.log
        IO.createDirectory(outputDir.value)

        compilers.scalac(Seq(fileToCompile), noChanges, classpath.value.map(_.data), outputDir.value, options.value, noopCallback, 1000, inputs.value.incSetup.cache, log)
      }
    }
  }

  val noChanges = new xsbti.compile.DependencyChanges {
    def isEmpty = true
    def modifiedBinaries = Array()
    def modifiedClasses = Array()
  }

  import xsbti._
  object noopCallback extends xsbti.AnalysisCallback {
    def beginSource(source: File) {}

    def generatedClass(source: File, module: File, name: String) {}

    def api(sourceFile: File, source: xsbti.api.SourceAPI) {}

    def sourceDependency(dependsOn: File, source: File, publicInherited: Boolean) {}

    def binaryDependency(binary: File, name: String, source: File, publicInherited: Boolean) {}

    def endSource(sourcePath: File) {}

    def problem(what: String, pos: Position, msg: String, severity: Severity, reported: Boolean) {}
  }

  val compileQuickSettings = Seq(
    compileQuick <<= compileQuickTask,
    scaldingJobs <<= (scalaSource in Compile).map {
      (scalaSource) => (scalaSource ** GlobFilter("*.scala")).get
    } storeAs scaldingJobs,
    filesToPackage <<= (classDirectory in Compile).map { (classDir) =>
      val baseDir = classDir.getAbsolutePath + "/"
      (classDir ** GlobFilter("*.class")).get.map(f => (f, f.getAbsolutePath.replace(baseDir, "")))
    },
    packageQuick <<= packageQuickTask(filesToPackage),
    packageQuickOutput <<= baseDirectory(_ / ".." / ".." / "jar" / "scalding-jobs.jar")
  )
}
