lazy val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.sapi",
  scalaVersion := "2.12.7",
  scalafmtOnCompile := true
)

val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5" % "test"
val circeVersion = "0.10.0"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publishArtifact := false, name := "sapi")
  .aggregate(core, openapiModel, openapiDocs, akkaHttpServer, http4sServer, sttpClient)

lazy val core: Project = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "com.chuusai" %% "shapeless" % "2.3.3",
      "com.softwaremill.sttp" %% "akka-http-backend" % "1.3.5",
      "com.typesafe.akka" %% "akka-stream" % "2.5.17",
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "com.propensive" %% "magnolia" % "0.10.0",
      scalaTest
    )
  )

lazy val openapiModel: Project = (project in file("openapi-model"))
  .settings(commonSettings: _*)
  .settings(
    name := "openapi-model"
  )

lazy val openapiDocs: Project = (project in file("docs/openapi-docs"))
  .settings(commonSettings: _*)
  .settings(
    name := "openapi-docs"
  )
  .dependsOn(openapiModel, core)

lazy val akkaHttpServer: Project = (project in file("server/akka-http-server"))
  .settings(commonSettings: _*)
  .settings(
    name := "akka-http-server",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % "10.1.5"
    )
  )
  .dependsOn(core)

lazy val http4sServer: Project = (project in file("server/http4s-server"))
  .settings(commonSettings: _*)
  .settings(
    name := "http4s-server",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-client" % "0.19.0"
    )
  )
  .dependsOn(core)

lazy val sttpClient: Project = (project in file("client/sttp-client"))
  .settings(commonSettings: _*)
  .settings(
    name := "sttp-client",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp" %% "core" % "1.3.8"
    )
  )
  .dependsOn(core)

lazy val tests: Project = (project in file("tests"))
  .settings(commonSettings: _*)
  .settings(
    name := "tests"
  )
  .dependsOn(akkaHttpServer, sttpClient)
