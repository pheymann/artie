lazy val commonSettings = Seq(
  version       := "0.1.0",
  scalaVersion  := "2.12.3",
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
    "-Ywarn-inaccessible",
    "-Ywarn-infer-any",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused:implicits",
    "-Ywarn-unused:imports",
    "-Ywarn-unused:locals",
    "-Ywarn-unused:patvars",
    "-Ywarn-unused:privates"
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
