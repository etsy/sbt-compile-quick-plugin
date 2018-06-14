package com.etsy.sbt

import java.util

import sbt._
import sbt.internal.util.ManagedLogger
import sjsonnew.BasicJsonProtocol
import xsbti.{AnalysisCallback, Position, Severity, UseScope}
import xsbti.api.{ClassLike, DependencyContext}
import xsbti.compile.{Compilers, DependencyChanges, Inputs}

private[sbt] object Compatibility {

  def createPackage(config: Package.Configuration, streams: Keys.TaskStreams): Unit = {
    Package(config, streams.cacheStoreFactory, streams.log)
  }

  def scalac(compilers: Compilers,
             sources: Seq[File],
             changes: DependencyChanges,
             classpath: Seq[File],
             outputDir: File,
             options: Seq[String],
             callback: AnalysisCallback,
             maxErrors: Int,
             inputs: Inputs,
             log: ManagedLogger): Unit = {
    compilers.scalac() match {
      case c: sbt.internal.inc.AnalyzingCompiler =>
        c.apply(
          sources.toArray,
          changes,
          classpath.toArray,
          outputDir,
          options.toArray,
          callback,
          maxErrors,
          inputs.setup().cache(),
          log
        )
      case unknown_compiler =>
        log.error("wrong compiler, expected 'sbt.internal.inc.AnalyzingCompiler' got: " + unknown_compiler.getClass.getName)
    }
  }

  object Implicits extends BasicJsonProtocol

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

}
