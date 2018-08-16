package com.etsy.sbt

import sbinary.DefaultProtocol
import sbt._
import xsbti.{AnalysisCallback, DependencyContext, Position, Severity}
import xsbti.compile.DependencyChanges

private[sbt] object Compatibility {

  def createPackage(config: Package.Configuration, streams: Keys.TaskStreams): Unit = {
    Package(config, streams.cacheDirectory, streams.log)
  }

  def scalac[ScalaCompiler](compilers: Compiler.Compilers,
                            sources: Seq[File],
                            changes: DependencyChanges,
                            classpath: Seq[File],
                            outputDir: File,
                            options: Seq[String],
                            callback: AnalysisCallback,
                            maxErrors: Int,
                            inputs: Compiler.Inputs,
                            log: Logger): Unit = {
    compilers.scalac(
      sources,
      changes,
      classpath,
      outputDir,
      options,
      callback,
      maxErrors,
      inputs.incSetup.cache,
      log
    )
  }

  object Implicits extends DefaultProtocol with BasicCacheImplicits

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

}
