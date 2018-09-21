lazy val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.swagger",
  scalaVersion := "2.12.6",
  scalafmtOnCompile := true
)

val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5" % "test"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publishArtifact := false, name := "scala-swagger")
  .aggregate(core, typedSchema, typedapi)

lazy val core: Project = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "com.chuusai" %% "shapeless" % "2.3.3",
      "com.softwaremill.sttp" %% "akka-http-backend" % "1.3.1",
      "com.typesafe.akka" %% "akka-stream" % "2.5.16",
      scalaTest
    )
  )

lazy val typedSchema: Project = (project in file("typed-schema"))
  .settings(commonSettings: _*)
  .settings(
    name := "typed-schema",
    libraryDependencies ++= Seq(
      "ru.tinkoff" %% "typed-schema" % "0.10.5"
    )
  )

lazy val typedapi: Project = (project in file("typedapi"))
  .settings(commonSettings: _*)
  .settings(
    name := "typedapi",
    libraryDependencies ++= Seq(
      "com.github.pheymann" %% "typedapi-http4s-server" % "0.1.0" exclude ("org.ensime", "sbt-ensime")
    )
  )
