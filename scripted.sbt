scriptedLaunchOpts := {
  scriptedLaunchOpts.value ++
    Seq("-Xmx1024M", "-XX:MaxPermSize=256M", "-Dplugin.version=" + version.value)
}
scriptedBufferLog := false

sbtTestDirectory := {
  val sourceDir = sourceDirectory.value
  val sbtBinVersion = (sbtBinaryVersion in pluginCrossBuild).value
  sourceDir / s"sbt-test-$sbtBinVersion"
}
