package com.etsy.sbt

import java.util

import sjsonnew.BasicJsonProtocol._
import sbt.Def.Initialize
import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._
import sbt.complete._
import sbt.util.CacheStoreFactory
import xsbti.api.{ClassLike, DependencyContext}
import xsbti.compile.Inputs
import xsbti.{Position, Severity, UseScope}

/**
  * An SBT plugin that allows compiling and packaging a single class
  *
  * @author Andrew Johnson <ajohnson@etsy.com>
  */
object CompileQuick extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[Def.Setting[_]] = defaultSettings

  object autoImport {
    val compileQuick = InputKey[Unit]("compile-quick", "Compiles a single file")
    val scalaSources = TaskKey[Seq[String]]("scala-sources", "List of all Scala source files")
    val packageQuick = TaskKey[File]("package-quick", "Packages a JAR without compiling anything")
    val filesToPackage = TaskKey[Seq[(File, String)]]("files-to-package", "Produces a list of files to be included when running packageQuick")
    val packageQuickOutput = SettingKey[File]("package-quick-output", "Location of the JAR produced by packageQuick")
  }

  import autoImport._

  lazy val defaultSettings: Seq[Setting[_]] = Seq(
    compileQuick in Compile := compileQuickTask(Compile).evaluated,
    compileQuick in Test := compileQuickTask(Test).evaluated,
    scalaSources in Compile := (scalaSourcesTask(Compile) storeAs (scalaSources in Compile) triggeredBy (sources in Compile)).value,
    scalaSources in Test := (scalaSourcesTask(Test) storeAs (scalaSources in Test) triggeredBy (sources in Test)).value,
    filesToPackage := {
      val classDir = (classDirectory in Compile).value
      val baseDir = classDir.getAbsolutePath + "/"
      (classDir ** GlobFilter("*.class")).get.map(f => (f, f.getAbsolutePath.replace(baseDir, "")))
    },
    packageQuick := packageQuickTask(filesToPackage.value, packageQuickOutput.value, streams.value.cacheStoreFactory, streams.value.log),
    packageQuickOutput := (artifactPath in Compile in packageBin).value,
  )

  /**
    * Packages a JAR without compiling anything
    */
  def packageQuickTask(files: Seq[(File, String)], output: File, cacheStoreFactory: CacheStoreFactory, log: Logger): File = {
    IO.delete(output)
    val packageConf = new Package.Configuration(files, output, Seq())
    Package(packageConf, cacheStoreFactory, log)
    output
  }

  /**
    * Parser for compileQuick
    * Supports tab-completing the file name
    */
  val runParser: (State, Seq[String]) => Parser[String] = {
    (state, jobs) => {
      Space ~> token(NotSpace examples jobs.map(_.toString).toSet)
    }
  }

  /**
    * Compiles a single file
    *
    * @param conf The configuration (Compile or Test) in which context to execute the compileQuick command
    */
  def compileQuickTask(conf: Configuration): Initialize[InputTask[Unit]] = {
    val parser = Defaults.loadForParser(scalaSources in conf) { (s, names) =>
      runParser(s, names.getOrElse(Nil))
    }
    Def.inputTask {
      val inputs: Inputs = (compileInputs in(conf, compile)).value
      val classpath: Classpath = (dependencyClasspath in conf).value
      val outputDir: File = (classDirectory in conf).value
      val options: Seq[String] = (scalacOptions in(conf, compileQuick)).value
      val maxErrors = 1000

      val input: String = parser.parsed
      // The tab completion uses full paths.  However, it still
      // supports paths relative to scalaSource
      val baseScalaSourcePath: File = (scalaSource in conf).value
      val baseSrcManagedPath: File = (sourceManaged in conf).value

      val compilers = Keys.compilers.value
      val s = streams.value

      val fileToCompile: File = if (file(input).isAbsolute) {
        file(input)
      } else if ((baseScalaSourcePath / input).exists()) {
        baseScalaSourcePath / input
      } else {
        baseSrcManagedPath / input
      }

      if (fileToCompile.exists) {
        val log = s.log
        IO.createDirectory(outputDir)

        val filesToCompile = Array(fileToCompile)

        log.info(s"Compiling $fileToCompile")

        compilers.scalac() match {
          case c: sbt.internal.inc.AnalyzingCompiler =>
            c.apply(
              filesToCompile,
              noChanges,
              classpath.map(_.data).toArray,
              outputDir,
              options.toArray,
              noopCallback,
              maxErrors,
              inputs.setup().cache(),
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
    val scalaSourceInConf: File = (scalaSource in conf).value
    val sourceManagedInConf: File = (sourceManaged in conf).value
    val scalaSourceFiles: Seq[File] = (scalaSourceInConf ** "*.scala").get
    val baseScalaSourcePath: String = scalaSourceInConf.getAbsolutePath
    val scalaSourceManagedFiles: Seq[File] = (sourceManagedInConf ** "*.scala").get
    val baseScalaSourceManagedPath: String = sourceManagedInConf.getAbsolutePath

    val srcManagedFiles: Seq[String] = scalaSourceManagedFiles.flatMap { file: File =>
      Seq(file.getAbsolutePath, file.getAbsolutePath.replace(baseScalaSourceManagedPath + "/", ""))
    }

    val srcFiles: Seq[String] = scalaSourceFiles.flatMap { file: File =>
      Seq(file.getAbsolutePath, file.getAbsolutePath.replace(baseScalaSourcePath + "/", ""))
    }

    srcFiles ++ srcManagedFiles
  }
}
