val scala2_12 = "2.12.8"
val scala2_13 = "2.13.0"
def commonSettings(scalaVersions: Seq[String] = Seq(scala2_12, scala2_13)) =
  commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
    organization := "com.softwaremill.tapir",
    scalaVersion := scala2_12,
    scalafmtOnCompile := true,
    crossScalaVersions := scalaVersions
  )

def dependenciesFor(version: String)(deps: (Option[(Long, Long)] => ModuleID) *): Seq[ModuleID] =
  deps.map(_.apply(CrossVersion.partialVersion(version)))

val scalaTest = "org.scalatest" %% "scalatest" % "3.0.8"

lazy val loggerDependencies = Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "ch.qos.logback" % "logback-core" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
)

lazy val rootProject = (project in file("."))
  .settings(commonSettings(Nil): _*)
  .settings(publishArtifact := false, name := "tapir")
  .aggregate(
    core,
    circeJson,
    openapiModel,
    openapiCirce,
    openapiCirceYaml,
    openapiDocs,
    swaggerUiAkka,
    swaggerUiHttp4s,
    serverTests,
    akkaHttpServer,
    http4sServer,
    sttpClient,
    tests,
    examples,
    playground
  )

// core

lazy val core: Project = (project in file("core"))
  .settings(commonSettings(): _*)
  .settings(
    name := "tapir-core",
    libraryDependencies ++= Seq(
      "com.propensive" %% "magnolia" % "0.11.0",
      scalaTest % "test"
    )
  )
  .enablePlugins(spray.boilerplate.BoilerplatePlugin)

lazy val tests: Project = (project in file("tests"))
  .settings(commonSettings(): _*)
  .settings(
    name := "tapir-tests",
    publishArtifact := false,
    libraryDependencies ++= Seq(
      scalaTest,
      "com.softwaremill.macwire" %% "macros" % "2.3.3" % "provided"
    ),
    libraryDependencies ++= loggerDependencies
  )
  .dependsOn(core, circeJson)

// json

lazy val circeJson: Project = (project in file("json/circe"))
  .settings(commonSettings(): _*)
  .settings(
    name := "tapir-json-circe",
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "io.circe" %% "circe-core" % Versions.circe(_),
      "io.circe" %% "circe-generic" % Versions.circe(_),
      "io.circe" %% "circe-parser" % Versions.circe(_)
    )
  )
  .dependsOn(core)

// openapi

lazy val openapiModel: Project = (project in file("openapi/openapi-model"))
  .settings(commonSettings(): _*)
  .settings(
    name := "tapir-openapi-model"
  )

lazy val openapiCirce: Project = (project in file("openapi/openapi-circe"))
  .settings(commonSettings(): _*)
  .settings(
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "io.circe" %% "circe-core" % Versions.circe(_),
      "io.circe" %% "circe-parser" % Versions.circe(_),
      "io.circe" %% "circe-generic" % Versions.circe(_)
    ),
    name := "tapir-openapi-circe"
  )
  .dependsOn(openapiModel)

lazy val openapiCirceYaml: Project = (project in file("openapi/openapi-circe-yaml"))
  .settings(commonSettings(): _*)
  .settings(
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "io.circe" %% "circe-yaml" % Versions.circeYaml(_)
    ),
    name := "tapir-openapi-circe-yaml"
  )
  .dependsOn(openapiCirce)

// docs

lazy val openapiDocs: Project = (project in file("docs/openapi-docs"))
  .settings(commonSettings(): _*)
  .settings(
    name := "tapir-openapi-docs"
  )
  .dependsOn(openapiModel, core, tests % "test", openapiCirceYaml % "test")

lazy val swaggerUiAkka: Project = (project in file("docs/swagger-ui-akka-http"))
  .settings(commonSettings(): _*)
  .settings(
    name := "tapir-swagger-ui-akka-http",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
      "com.typesafe.akka" %% "akka-stream" % Versions.akkaStreams,
      "org.webjars" % "swagger-ui" % Versions.swaggerUi
    )
  )

lazy val swaggerUiHttp4s: Project = (project in file("docs/swagger-ui-http4s"))
  .settings(commonSettings(): _*)
  .settings(
    name := "tapir-swagger-ui-http4s",
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "org.http4s" %% "http4s-dsl" % Versions.http4s(_),
      _ => "org.webjars" % "swagger-ui" % Versions.swaggerUi
    )
  )

// server

lazy val serverTests: Project = (project in file("server/tests"))
  .settings(commonSettings(): _*)
  .settings(
    name := "tapir-server-tests",
    publishArtifact := false,
    libraryDependencies ++=
      Seq("com.softwaremill.sttp" %% "async-http-client-backend-cats" % Versions.sttp)
  )
  .dependsOn(tests)

lazy val akkaHttpServer: Project = (project in file("server/akka-http-server"))
  .settings(commonSettings(): _*)
  .settings(
    name := "tapir-akka-http-server",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
      "com.typesafe.akka" %% "akka-stream" % Versions.akkaStreams
    )
  )
  .dependsOn(core, serverTests % "test")

lazy val http4sServer: Project = (project in file("server/http4s-server"))
  .settings(commonSettings(): _*)
  .settings(
    name := "tapir-http4s-server",
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "org.http4s" %% "http4s-blaze-server" % Versions.http4s(_)
    )
  )
  .dependsOn(core, serverTests % "test")

// client

lazy val clientTests: Project = (project in file("client/tests"))
  .settings(commonSettings(): _*)
  .settings(
    name := "tapir-client-tests",
    publishArtifact := false,
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      "org.http4s" %% "http4s-dsl" % Versions.http4s(_),
      "org.http4s" %% "http4s-blaze-server" % Versions.http4s(_),
      "org.http4s" %% "http4s-circe" % Versions.http4s(_)
    )
  )
  .dependsOn(tests)

lazy val sttpClient: Project = (project in file("client/sttp-client"))
  .settings(commonSettings(): _*)
  .settings(
    name := "tapir-sttp-client",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp" %% "core" % Versions.sttp,
      "com.softwaremill.sttp" %% "async-http-client-backend-fs2" % Versions.sttp % "test"
    )
  )
  .dependsOn(core, clientTests % "test")

// other

lazy val examples: Project = (project in file("examples"))
  .settings(commonSettings(Seq(scala2_12)): _*)
  .settings(
    name := "tapir-examples",
    libraryDependencies ++= dependenciesFor(scalaVersion.value)(
      _ => "dev.zio" %% "zio" % "1.0.0-RC10-1",
      _ => "dev.zio" %% "zio-interop-cats" % "1.3.1.0-RC3",
      _ => "org.typelevel" %% "cats-effect" % "1.3.1",
      "org.http4s" %% "http4s-dsl" % Versions.http4s(_)
    ),
    libraryDependencies ++= loggerDependencies,
    publishArtifact := false
  )
  .dependsOn(akkaHttpServer, http4sServer, sttpClient, openapiCirceYaml, openapiDocs, circeJson, swaggerUiAkka, swaggerUiHttp4s)

lazy val playground: Project = (project in file("playground"))
  .settings(commonSettings(Seq(scala2_12)): _*)
  .settings(
    name := "tapir-playground",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp" %% "akka-http-backend" % Versions.sttp,
      "dev.zio" %% "zio" % "1.0.0-RC10-1",
      "dev.zio" %% "zio-interop-cats" % "1.3.1.0-RC3",
      "org.typelevel" %% "cats-effect" % "1.3.1",
      "io.swagger" % "swagger-annotations" % "1.5.22"
    ),
    libraryDependencies ++= loggerDependencies,
    publishArtifact := false
  )
  .dependsOn(akkaHttpServer, http4sServer, sttpClient, openapiCirceYaml, openapiDocs, circeJson, swaggerUiAkka, swaggerUiHttp4s)
