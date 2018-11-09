import Resolvers._
import Dependencies._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import sbt.Keys._

name := "nosqlmark"

lazy val common_settings = multiJvmSettings ++ Seq(
  organization := "de.unihamburg.informatik",
  version := "1.0.2",
  scalaVersion := "2.11.8",
  resolvers ++= my_resolvers,
  javaOptions += "-Xmx4G",
  javaOptions in Test += "-Dlogback.configurationFile=config/logback​.xml"
)


lazy val multiJvmSettings = SbtMultiJvm.multiJvmSettings ++ Seq(
  // make sure that MultiJvm test are compiled by the default test compilation
  compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
  // disable parallel tests
  parallelExecution in Test := true,
  // make sure that MultiJvm tests are executed by the default test target
  executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
    case (testResults, multiNodeResults)  =>
      val overall =
        if (testResults.overall.id < multiNodeResults.overall.id)
          multiNodeResults.overall
        else
          testResults.overall
      Tests.Output(overall,
        testResults.events ++ multiNodeResults.events,
        testResults.summaries ++ multiNodeResults.summaries)
  } ,
  fork in test := true
)
common_settings

/**
 * Sub projects
 */

//lazy val ycsb = ProjectRef(file("../ycsb"), "ycsb")
//lazy val ycsb = RootProject(uri("https://github.com/steffenfriedrich/YCSB.git"))

lazy val common = project.settings(common_settings: _*).settings(
  libraryDependencies ++= common_deps)

lazy val sickstore_binding = project.settings(common_settings: _*).settings(
  libraryDependencies ++= sickstore)

lazy val redis_binding = project.settings(common_settings: _*).settings(
  libraryDependencies ++= redis)

lazy val backbench = project.settings(common_settings: _*).settings(
  libraryDependencies ++= (common_deps ++ ycsb),
  // this enables custom javaOptions
  fork in run := true
).dependsOn(common, sickstore_binding, redis_binding) configs (MultiJvm)

lazy val repl = project.settings(common_settings: _*).settings(
  libraryDependencies ++= (common_deps),
  // this enables custom javaOptions
  fork in run := true
).dependsOn(common, backbench)

// lazy val frontbench = project.enablePlugins(PlayScala).settings(common_settings: _*).settings( libraryDependencies ++= (common_deps ++ webjars ++ Seq(filters, cache))).dependsOn(common)


/**
 * Scala Compiler Options If this project is only a subproject,
 * add these to a common project setting.
 */
scalacOptions in ThisBuild ++= Seq(
  "-target:jvm-1.8",
  "-encoding", "UTF-8",
  "-deprecation", // warning and location for usages of deprecated APIs
  "-feature", // warning and location for usages of features that should be imported explicitly
  "-unchecked", // additional warnings where g
  // enerated code depends on assumptions
  "-Xlint", // recommended additional warnings
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
  "-Ywarn-inaccessible",
  "-Ywarn-dead-code"
)

publishMavenStyle := true
//lazy val publishTo = Resolver.sftp("NoSQLMark", "nosqlmark.informatik.uni-hamburg.de", "/var/www/maven2")


/**
  * http://scalameter.github.io
  *
  */
testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework")
logBuffered := false


/**
 * see https://github.com/xerial/sbt-pack
 */
packSettings
packMain := Map(
  ("backbench" -> "de.unihamburg.informatik.nosqlmark.BackBench"),
  ("repl" -> "de.unihamburg.informatik.nosqlmark.repl.REPL")
)
packExtraClasspath := Map(
  ("backbench" -> Seq("config")),
  ("repl" -> Seq("config"))
)

packJvmOpts := Map(
  ("backbench" -> Seq("-Dlogback.configurationFile=config/logback.xml")),
  ("repl" -> Seq("-Dlogback.configurationFile=config/logback.xml", "-Djline.WindowsTerminal.directConsole"))
)
packGenerateWindowsBatFile := true
packExpandedClasspath := false
packResourceDir += (baseDirectory.value / "workloads" -> "workloads")
packResourceDir += (baseDirectory.value / "config" -> "config")

fork in run := true