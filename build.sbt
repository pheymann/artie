lazy val commonSettings = Seq(
  version       := "0.1.0",
  scalaVersion  := "2.11.11",
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "utf-8",
    "-explaintypes",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xfuture",
    "-Xlint:inaccessible",
    "-Xlint:infer-any",
    "-Xlint:missing-interpolator",
    "-Xlint:option-implicit",
    "-Xlint:type-parameter-shadow",
    "-Xlint:unsound-match",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused",
    "-Ywarn-inaccessible",
    "-Ywarn-infer-any"
  )
)

lazy val artie = project
  .in(file("."))
  .settings(commonSettings: _*)
  .aggregate(core, framework, examples)

lazy val core = project
  .in(file("core"))
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    Defaults.itSettings,
    name := "artie-core",
    libraryDependencies ++= Dependencies.core
  )

lazy val framework = project
  .in(file("framework"))
  .settings(
    commonSettings,
    name := "artie"
  )
  .dependsOn(core)

lazy val examples = project
  .in(file("examples"))
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    Defaults.itSettings,
    libraryDependencies ++= Dependencies.examples
  )
  .dependsOn(framework)
