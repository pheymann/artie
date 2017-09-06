import sbt._

object Dependencies {

  val core = Seq(
    "com.chuusai" %% "shapeless" % "2.3.2" % Compile,
    "org.scalaj"  %% "scalaj-http" % "2.3.0" % Compile,

    "org.specs2"  %% "specs2-core" % "3.9.4" % "test;it",

    "com.h2database" % "h2" % "1.4.191" % "it",
    "com.typesafe.akka" %% "akka-actor"  % "2.5.4" % "it",
    "com.typesafe.akka" %% "akka-stream" % "2.5.4" % "it",
    "com.typesafe.akka" %% "akka-http"   % "10.0.10" % "it",

    "com.typesafe.play" %% "play-json" % "2.6.2" % "it",
    "de.heikoseeberger" %% "akka-http-play-json" % "1.17.0" % "it"
  )

  val examples = Seq(
    "com.typesafe.akka" %% "akka-actor"  % "2.5.4" % Compile,
    "com.typesafe.akka" %% "akka-stream" % "2.5.4" % Compile,
    "com.typesafe.akka" %% "akka-http"   % "10.0.10" % Compile,

    "com.typesafe.play" %% "play-json" % "2.6.2" % "compile;it",
    "de.heikoseeberger" %% "akka-http-play-json" % "1.17.0" % "compile;it",

    "com.h2database" % "h2" % "1.4.191" % "it"
  )
}
