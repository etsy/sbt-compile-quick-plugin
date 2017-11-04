package com.etsy.sbt

import java.util

import sjsonnew.BasicJsonProtocol._
import sbt.Def.Initialize
import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._
import sbt.complete._
import xsbti.api.{ClassLike, DependencyContext}
import xsbti.{Position, Severity, UseScope}

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
    Package(packageConf, s.cacheStoreFactory, s.log)
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
      val input: String = parser.parsed
      // The tab completion uses full paths.  However, it still
      // supports paths relative to scalaSource
      val baseScalaSourcePath: File = (scalaSource in conf).value
      val baseSrcManagedPath: File = (sourceManaged in conf).value

      val fileToCompile: File = if(file(input).isAbsolute) {
        file(input)
      } else if((baseScalaSourcePath / input).exists()) {
        baseScalaSourcePath / input
      } else {
        baseSrcManagedPath / input
      }

      if (fileToCompile.exists) {
        val compilers = Keys.compilers.value
        val s = streams.value
        val log = s.log
        IO.createDirectory(outputDir.value)

        val filesToCompile = Array(fileToCompile)

        log.info(s"Compiling $fileToCompile")

        compilers.scalac() match {
          case c: sbt.internal.inc.AnalyzingCompiler =>
            c.apply(
              filesToCompile,
              noChanges,
              classpath.value.map(_.data).toArray,
              outputDir.value,
              options.value.toArray,
              noopCallback,
              maxErrors,
              inputs.value.setup().cache(),
              log
            )
          case unknown_compiler =>
            log.error("wrong compiler, expected 'sbt.internal.inc.AnalyzingCompiler' got: " + unknown_compiler.getClass.getName)
        }
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
    override def startSource(source: File): Unit = {}

    override def mainClass(sourceFile: File, className: String): Unit = {}

    override def apiPhaseCompleted(): Unit = {}

    override def enabled(): Boolean = false

    override def binaryDependency(onBinaryEntry: File, onBinaryClassName: String, fromClassName: String, fromSourceFile: File, context: DependencyContext): Unit = {}

    override def generatedNonLocalClass(source: File, classFile: File, binaryClassName: String, srcClassName: String): Unit = {}

    override def problem(what: String, pos: Position, msg: String, severity: Severity, reported: Boolean): Unit = {}

    override def dependencyPhaseCompleted(): Unit = {}

    override def classDependency(onClassName: String, sourceClassName: String, context: DependencyContext): Unit = {}

    override def generatedLocalClass(source: File, classFile: File): Unit = {}

    override def api(sourceFile: File, classApi: ClassLike): Unit = {}

    override def usedName(className: String, name: String, useScopes: util.EnumSet[UseScope]): Unit = {}
  }

  /**
    * Produces a list of Scala files used for tab-completion in compileQuick
    * It includes both absolute paths and paths relative to scalaSource
    *
    * @param conf The configuration (Compile or Test) in which context to execute the scalaSources task
    */
  def scalaSourcesTask(conf: Configuration): Initialize[Task[Seq[String]]] = Def.task {
    val scalaSourceFiles: Seq[File] = ((scalaSource in conf).value ** "*.scala").get
    val baseScalaSourcePath: String = (scalaSource in conf).value.getAbsolutePath
    val scalaSourceManagedFiles: Seq[File] = ((sourceManaged in conf).value ** "*.scala").get
    val baseScalaSourceManagedPath: String = (sourceManaged in conf).value.getAbsolutePath

    val srcManagedFiles: Seq[String] = scalaSourceManagedFiles.flatMap { file: File =>
      Seq(file.getAbsolutePath, file.getAbsolutePath.replace(baseScalaSourceManagedPath + "/", ""))
    }

    val srcFiles: Seq[String] = scalaSourceFiles.flatMap { file: File =>
      Seq(file.getAbsolutePath, file.getAbsolutePath.replace(baseScalaSourcePath + "/", ""))
    }

    srcFiles ++ srcManagedFiles
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
