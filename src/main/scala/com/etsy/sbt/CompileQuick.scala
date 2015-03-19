package com.etsy.sbt

import sbinary.DefaultProtocol._
import sbt.Cache.seqFormat
import sbt.Def.Initialize
import sbt.Keys._
import sbt._
import sbt.complete._

/**
  * An SBT plugin that allows compiling and packaging a single class
  *
  * @author Andrew Johnson <ajohnson@etsy.com>
  */
object CompileQuick extends Plugin{
  import com.etsy.sbt.CompileQuick.CompileQuickTasks._

  object CompileQuickTasks {
    val compileQuick = InputKey[Unit]("compile-quick", "Compiles a single file")
    val scalaSources = TaskKey[Seq[File]]("scala-sources", "List of all Scala source files")
    val packageQuick = TaskKey[File]("package-quick", "Packages a JAR without compiling anything")
    val filesToPackage = TaskKey[Seq[(File, String)]]("files-to-package", "Produces a list of files to be included when running packageQuick")
    val packageQuickOutput = SettingKey[File]("package-quick-output", "Location of the JAR produced by packageQuick")
  }

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
  def runParser: (State, Seq[File]) => Parser[String] = {
    import sbt.complete.DefaultParsers._
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

    Def.inputTask {
      val input = parser.parsed
      // The tab completion uses full paths.  However, it still
      // supports paths relative to scalaSource
      val fileToCompile = input.charAt(0) match {
        case '/' => file(input)
        case _ => file((scalaSource in conf).value.getAbsolutePath + "/" + input)
      }
      if (fileToCompile.exists) {
        val compilers = Keys.compilers.value
        val log = streams.value.log
        IO.createDirectory(outputDir.value)
        log.info(s"Compiling $fileToCompile")

        compilers.scalac(Seq(fileToCompile), noChanges, classpath.value.map(_.data), outputDir.value, options.value, noopCallback, 1000, inputs.value.incSetup.cache, log)
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
  import xsbti._
  object noopCallback extends xsbti.AnalysisCallback {
    def beginSource(source: File) {}

    def generatedClass(source: File, module: File, name: String) {}

    def api(sourceFile: File, source: xsbti.api.SourceAPI) {}

    def sourceDependency(dependsOn: File, source: File, publicInherited: Boolean) {}

    def binaryDependency(binary: File, name: String, source: File, publicInherited: Boolean) {}

    def endSource(sourcePath: File) {}

    def problem(what: String, pos: Position, msg: String, severity: Severity, reported: Boolean) {}

    def nameHashing(): Boolean = true

    def usedName(sourceFile: File, names: String) {}
  }

  /**
    * Produces a list of Scala files used for tab-completion in compileQuick
    *
    * @param conf The configuration (Compile or Test) in which context to execute the scalaSources task
    */
  def scalaSourcesTask(conf: Configuration): Initialize[Task[Seq[File]]] = Def.task {
    ((scalaSource in conf).value ** "*.scala").get
  }

  val compileQuickSettings: Seq[Def.Setting[_]] = Seq(
    compileQuick in Compile <<= compileQuickTask(Compile),
    compileQuick in Test <<= compileQuickTask(Test),
    scalaSources in Compile <<= scalaSourcesTask(Compile) storeAs (scalaSources in Compile) triggeredBy (sources in Compile),
    scalaSources in Test <<= scalaSourcesTask(Test) storeAs (scalaSources in Test) triggeredBy (sources in Test),
    filesToPackage <<= (classDirectory in Compile).map { (classDir) =>
      val baseDir = classDir.getAbsolutePath + "/"
        (classDir ** GlobFilter("*.class")).get.map(f => (f, f.getAbsolutePath.replace(baseDir, "")))
    },
    packageQuick <<= packageQuickTask(filesToPackage),
    packageQuickOutput <<= artifactPath in Compile in packageBin
  )
}
