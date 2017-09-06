lazy val commonSettings = Seq(
  version       := "0.1.0",
  scalaVersion  := "2.12.3"
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
