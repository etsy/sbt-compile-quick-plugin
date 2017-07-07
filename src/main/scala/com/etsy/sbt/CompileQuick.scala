package com.etsy.sbt

import sbinary.DefaultProtocol._
import sbt.Cache.seqFormat
import sbt.Def.Initialize
import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._
import sbt.complete._
import xsbti.{DependencyContext, Severity, Position}

/**
  * An SBT plugin that allows compiling and packaging a single class
  *
  * @author Andrew Johnson <ajohnson@etsy.com>
  */
object CompileQuick extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  val compileQuick = InputKey[Unit]("compile-quick", "Compiles a single file")
  val scalaSources = TaskKey[Seq[String]]("scala-sources", "List of all Scala source files")
  val packageQuick = TaskKey[File]("package-quick", "Packages a JAR without compiling anything")
  val filesToPackage = TaskKey[Seq[(File, String)]]("files-to-package", "Produces a list of files to be included when running packageQuick")
  val packageQuickOutput = SettingKey[File]("package-quick-output", "Location of the JAR produced by packageQuick")

  /**
    * Packages a JAR without compiling anything
    */
  def packageQuickTask(files: TaskKey[Seq[(File, String)]]): Initialize[Task[File]] = Def.task {
    val output = packageQuickOutput.value
    IO.delete(output)
    val s = streams.value
    val packageConf = new Package.Configuration(files.value, output, Seq())
    Package(packageConf, s.cacheDirectory, s.log)
    output
  }

  /**
    * Parser for compileQuick
    * Supports tab-completing the file name
    */
  def runParser: (State, Seq[String]) => Parser[String] = {
    (state, jobs) => {
      Space ~> token(NotSpace examples jobs.map(_.toString).toSet)
    }
  }

  /**
    * Compiles a single file
    *
    * @param conf The configuration (Compile or Test) in which context to execute the compileQuick command
    */
  def compileQuickTask(conf: Configuration) : Initialize[InputTask[Unit]] = {
    val parser = Defaults.loadForParser(scalaSources in conf) { (s, names) =>
      runParser(s, names.getOrElse(Nil))
    }
    val inputs = compileInputs in (conf, compile)
    val classpath = dependencyClasspath in conf
    val outputDir = classDirectory in conf
    val options = scalacOptions in (conf, compileQuick)
    val maxErrors = 1000

    Def.inputTask {
      val input = parser.parsed
      // The tab completion uses full paths.  However, it still
      // supports paths relative to scalaSource
      val baseScalaSourcePath = (scalaSource in conf).value.getAbsolutePath
      val fileToCompile = file(input).isAbsolute match {
        case true => file(input)
        case false => file(s"$baseScalaSourcePath/$input")
      }

      if (fileToCompile.exists) {
        val compilers = Keys.compilers.value
        val log = streams.value.log
        IO.createDirectory(outputDir.value)
        log.info(s"Compiling $fileToCompile")

        compilers.scalac(Seq(fileToCompile), noChanges, classpath.value.map(_.data), outputDir.value, options.value, noopCallback, maxErrors, inputs.value.incSetup.cache, log)
      }
    }
  }

  // Indicates to the compiler that no files or dependencies have changed
  // This prevents compiling anything other than the requested file
  val noChanges = new xsbti.compile.DependencyChanges {
    def isEmpty = true
    def modifiedBinaries = Array()
    def modifiedClasses = Array()
  }

  // This discards the analysis produced by compiling one file, as it
  // isn't that useful
  object noopCallback extends xsbti.AnalysisCallback {
    def beginSource(source: File) {}

    def generatedClass(source: File, module: File, name: String) {}

    def api(sourceFile: File, source: xsbti.api.SourceAPI) {}

    def sourceDependency(dependsOn: File, source: File, publicInherited: Boolean) {}

    def binaryDependency(binary: File, name: String, source: File, publicInherited: Boolean) {}

    def endSource(sourcePath: File) {}

    def problem(what: String, pos: Position, msg: String, severity: Severity, reported: Boolean) {}

    def nameHashing(): Boolean = true

    override def includeSynthToNameHashing(): Boolean = true

    def usedName(sourceFile: File, names: String) {}

    override def binaryDependency(file: File, s: String, file1: File, dependencyContext: DependencyContext): Unit = {}

    override def sourceDependency(file: File, file1: File, dependencyContext: DependencyContext): Unit = {}
  }

  /**
    * Produces a list of Scala files used for tab-completion in compileQuick
    * It includes both absolute paths and paths relative to scalaSource
    *
    * @param conf The configuration (Compile or Test) in which context to execute the scalaSources task
    */
  def scalaSourcesTask(conf: Configuration): Initialize[Task[Seq[String]]] = Def.task {
    val scalaSourceFiles = ((scalaSource in conf).value ** "*.scala").get
    val baseScalaSourcePath = (scalaSource in conf).value.getAbsolutePath

    scalaSourceFiles flatMap { file: File =>
      Seq(file.getAbsolutePath, file.getAbsolutePath.replace(baseScalaSourcePath + "/", ""))
    }
  }


  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    compileQuick in Compile := compileQuickTask(Compile).evaluated,
    compileQuick in Test := compileQuickTask(Test).evaluated,
    scalaSources in Compile := (scalaSourcesTask(Compile) storeAs (scalaSources in Compile) triggeredBy (sources in Compile)).value,
    scalaSources in Test := (scalaSourcesTask(Test) storeAs (scalaSources in Test) triggeredBy (sources in Test)).value,
    filesToPackage := {
      val classDir = (classDirectory in Compile).value
      val baseDir = classDir.getAbsolutePath + "/"
      (classDir ** GlobFilter("*.class")).get.map(f => (f, f.getAbsolutePath.replace(baseDir, "")))
    },
    packageQuick := packageQuickTask(filesToPackage).value,
    packageQuickOutput := (artifactPath in Compile in packageBin).value
  )
}
