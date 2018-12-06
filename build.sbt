lazy val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.tapir",
  scalaVersion := "2.12.7",
  scalafmtOnCompile := true
)

val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"

val http4sVersion = "0.18.21"
val circeVersion = "0.10.1"
val sttpVersion = "1.5.0"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publishArtifact := false, name := "tapir")
  .aggregate(core, openapiModel, openapiCirce, openapiCirceYaml, openapiDocs, serverTests, akkaHttpServer, http4sServer, sttpClient, tests)

// core

lazy val core: Project = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "com.propensive" %% "magnolia" % "0.10.0",
      scalaTest % "test"
    )
  )

lazy val tests: Project = (project in file("tests"))
  .settings(commonSettings: _*)
  .settings(
    name := "tests",
    publishArtifact := false,
    libraryDependencies ++= Seq(scalaTest, "ch.qos.logback" % "logback-classic" % "1.2.3")
  )
  .dependsOn(core, circeJson)

// json

lazy val circeJson: Project = (project in file("json/circe"))
  .settings(commonSettings: _*)
  .settings(
    name := "json-circe",
    publishArtifact := false,
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
    )
  )
  .dependsOn(core)

// openapi

lazy val openapiModel: Project = (project in file("openapi/openapi-model"))
  .settings(commonSettings: _*)
  .settings(
    name := "openapi-model"
  )

lazy val openapiCirce: Project = (project in file("openapi/openapi-circe"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-magnolia-derivation" % "0.3.0"
    ),
    name := "openapi-circe"
  )
  .dependsOn(openapiModel)

lazy val openapiCirceYaml: Project = (project in file("openapi/openapi-circe-yaml"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-yaml" % "0.9.0"
    ),
    name := "openapi-circe-yaml"
  )
  .dependsOn(openapiCirce)

// docs

lazy val openapiDocs: Project = (project in file("docs/openapi-docs"))
  .settings(commonSettings: _*)
  .settings(
    name := "openapi-docs"
  )
  .dependsOn(openapiModel, core, tests % "test")

// server

lazy val serverTests: Project = (project in file("server/tests"))
  .settings(commonSettings: _*)
  .settings(
    name := "server-tests",
    publishArtifact := false,
    libraryDependencies ++= Seq("com.softwaremill.sttp" %% "async-http-client-backend-cats" % sttpVersion)
  )
  .dependsOn(tests)

lazy val akkaHttpServer: Project = (project in file("server/akka-http-server"))
  .settings(commonSettings: _*)
  .settings(
    name := "akka-http-server",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % "10.1.5",
      "com.typesafe.akka" %% "akka-stream" % "2.5.18"
    )
  )
  .dependsOn(core, serverTests % "test")

lazy val http4sServer: Project = (project in file("server/http4s-server"))
  .settings(commonSettings: _*)
  .settings(
    name := "http4s-server",
    libraryDependencies ++= Seq("org.http4s" %% "http4s-blaze-server" % http4sVersion)
  )
  .dependsOn(core, serverTests % "test")

// client

lazy val clientTests: Project = (project in file("client/tests"))
  .settings(commonSettings: _*)
  .settings(
    name := "client-tests",
    publishArtifact := false,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion
    )
  )
  .dependsOn(tests)

lazy val sttpClient: Project = (project in file("client/sttp-client"))
  .settings(commonSettings: _*)
  .settings(
    name := "sttp-client",
    libraryDependencies ++= Seq("com.softwaremill.sttp" %% "core" % sttpVersion)
  )
  .dependsOn(core, clientTests % "test")

// other

lazy val playground: Project = (project in file("playground"))
  .settings(commonSettings: _*)
  .settings(
    name := "tests",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp" %% "akka-http-backend" % sttpVersion
    )
  )
  .dependsOn(akkaHttpServer, sttpClient, openapiCirceYaml, openapiDocs, circeJson)
