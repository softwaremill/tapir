val scala2_12 = "2.12.10"
val scala2_13 = "2.13.1"

lazy val is2_12 = settingKey[Boolean]("Is the scala version 2.12.")

// an ugly work-around for https://github.com/sbt/sbt/issues/3465
// even if a project is 2.12-only, we fake that it's also 2.13-compatible
val only2_12settings = Seq(
  publishArtifact := is2_12.value,
  skip := !is2_12.value,
  skip in publish := !is2_12.value,
  libraryDependencies := (if (is2_12.value) libraryDependencies.value else Nil)
)

val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.sttp.tapir",
  scalaVersion := scala2_12,
  crossScalaVersions := Seq(scala2_12, scala2_13),
  is2_12 := scalaVersion.value.startsWith("2.12."),
  libraryDependencies ++= Seq(
    compilerPlugin("com.softwaremill.neme" %% "neme-plugin" % "0.0.5"),
    compilerPlugin("com.github.ghik" % "silencer-plugin" % Versions.silencer cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % Versions.silencer % Provided cross CrossVersion.full
  )
)

def dependenciesFor(version: String)(deps: (Option[(Long, Long)] => ModuleID)*): Seq[ModuleID] =
  deps.map(_.apply(CrossVersion.partialVersion(version)))

val scalaTest = "org.scalatest" %% "scalatest" % "3.0.8"

lazy val loggerDependencies = Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "ch.qos.logback" % "logback-core" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
)

lazy val rootProject = (project in file("."))
  .settings(commonSettings)
  .settings(publishArtifact := false, name := "tapir")
  .aggregate(
    core,
    tapirCats,
    circeJson,
    playJson,
    uPickleJson,
    openapiModel,
    openapiCirce,
    openapiCirceYaml,
    openapiDocs,
    swaggerUiAkka,
    swaggerUiHttp4s,
    redocHttp4s,
    serverTests,
    akkaHttpServer,
    http4sServer,
    finatraServer,
    sttpClient,
    tests,
    examples,
    playground
  )

// core

lazy val core: Project = (project in file("core"))
  .settings(commonSettings)
  .settings(
    name := "tapir-core",
    libraryDependencies ++= Seq(
      "com.propensive" %% "magnolia" % "0.12.0",
      "com.softwaremill.sttp.client" %% "model" % Versions.sttp,
      scalaTest % "test"
    )
  )
  .enablePlugins(spray.boilerplate.BoilerplatePlugin)

lazy val tests: Project = (project in file("tests"))
  .settings(commonSettings)
  .settings(
    name := "tapir-tests",
    libraryDependencies ++= Seq(
      "com.softwaremill.common" %% "tagging" % "2.2.1",
      scalaTest,
      "com.softwaremill.macwire" %% "macros" % "2.3.3" % "provided"
    ),
    libraryDependencies ++= loggerDependencies
  )
  .dependsOn(core, circeJson)

// cats

lazy val tapirCats: Project = (project in file("cats"))
  .settings(commonSettings)
  .settings(
    name := "tapir-cats",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % Versions.cats,
      scalaTest % "test"
    )
  )
  .dependsOn(core)

// json

lazy val circeJson: Project = (project in file("json/circe"))
  .settings(commonSettings)
  .settings(
    name := "tapir-json-circe",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % Versions.circe,
      "io.circe" %% "circe-generic" % Versions.circe,
      "io.circe" %% "circe-parser" % Versions.circe
    )
  )
  .dependsOn(core)

lazy val playJson: Project = (project in file("json/playjson"))
  .settings(commonSettings: _*)
  .settings(
    name := "tapir-json-play",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % Versions.playJson,
      scalaTest % "test"
    )
  )
  .dependsOn(core)

lazy val uPickleJson: Project = (project in file("json/upickle"))
  .settings(commonSettings)
  .settings(
    name := "tapir-json-upickle",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "upickle" % Versions.upickle,
      scalaTest % "test"
    )
  )
  .dependsOn(core)

// openapi

lazy val openapiModel: Project = (project in file("openapi/openapi-model"))
  .settings(commonSettings)
  .settings(
    name := "tapir-openapi-model"
  )

lazy val openapiCirce: Project = (project in file("openapi/openapi-circe"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % Versions.circe,
      "io.circe" %% "circe-parser" % Versions.circe,
      "io.circe" %% "circe-generic" % Versions.circe
    ),
    name := "tapir-openapi-circe"
  )
  .dependsOn(openapiModel)

lazy val openapiCirceYaml: Project = (project in file("openapi/openapi-circe-yaml"))
  .settings(commonSettings)
  .settings(
    libraryDependencies += "io.circe" %% "circe-yaml" % Versions.circeYaml,
    name := "tapir-openapi-circe-yaml"
  )
  .dependsOn(openapiCirce)

// docs

lazy val openapiDocs: Project = (project in file("docs/openapi-docs"))
  .settings(commonSettings)
  .settings(
    name := "tapir-openapi-docs"
  )
  .dependsOn(openapiModel, core, tests % "test", openapiCirceYaml % "test")

lazy val swaggerUiAkka: Project = (project in file("docs/swagger-ui-akka-http"))
  .settings(commonSettings)
  .settings(
    name := "tapir-swagger-ui-akka-http",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
      "com.typesafe.akka" %% "akka-stream" % Versions.akkaStreams,
      "org.webjars" % "swagger-ui" % Versions.swaggerUi
    )
  )

lazy val swaggerUiHttp4s: Project = (project in file("docs/swagger-ui-http4s"))
  .settings(commonSettings)
  .settings(
    name := "tapir-swagger-ui-http4s",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl" % Versions.http4s,
      "org.webjars" % "swagger-ui" % Versions.swaggerUi
    )
  )

lazy val redocHttp4s: Project = (project in file("docs/redoc-http4s"))
  .settings(commonSettings)
  .settings(
    name := "tapir-redoc-http4s",
    libraryDependencies += "org.http4s" %% "http4s-dsl" % Versions.http4s
  )

// server

lazy val serverTests: Project = (project in file("server/tests"))
  .settings(commonSettings)
  .settings(
    name := "tapir-server-tests",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client" %% "async-http-client-backend-cats" % Versions.sttp
    )
  )
  .dependsOn(tests)

lazy val akkaHttpServer: Project = (project in file("server/akka-http-server"))
  .settings(commonSettings)
  .settings(
    name := "tapir-akka-http-server",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
      "com.typesafe.akka" %% "akka-stream" % Versions.akkaStreams
    )
  )
  .dependsOn(core, serverTests % "test")

lazy val http4sServer: Project = (project in file("server/http4s-server"))
  .settings(commonSettings)
  .settings(
    name := "tapir-http4s-server",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-server" % Versions.http4s
    )
  )
  .dependsOn(core, serverTests % "test")

lazy val finatraServer: Project = (project in file("server/finatra-server"))
  .settings(commonSettings: _*)
  .settings(
    name := "tapir-finatra-server",
    libraryDependencies ++= Seq(
      "com.twitter" %% "finatra-http" % Versions.finatra,
      "org.apache.httpcomponents" % "httpmime" % "4.5.10",
      // Testing
      "com.twitter" %% "finatra-http" % Versions.finatra % "test",
      "com.twitter" %% "inject-server" % Versions.finatra % "test",
      "com.twitter" %% "inject-app" % Versions.finatra % "test",
      "com.twitter" %% "inject-core" % Versions.finatra % "test",
      "com.twitter" %% "inject-modules" % Versions.finatra % "test",
      "com.twitter" %% "finatra-http" % Versions.finatra % "test" classifier "tests",
      "com.twitter" %% "inject-server" % Versions.finatra % "test" classifier "tests",
      "com.twitter" %% "inject-app" % Versions.finatra % "test" classifier "tests",
      "com.twitter" %% "inject-core" % Versions.finatra % "test" classifier "tests",
      "com.twitter" %% "inject-modules" % Versions.finatra % "test" classifier "tests"
    )
  )
  .settings(only2_12settings)
  .dependsOn(core, serverTests % "test")

// client

lazy val clientTests: Project = (project in file("client/tests"))
  .settings(commonSettings)
  .settings(
    name := "tapir-client-tests",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl" % Versions.http4s,
      "org.http4s" %% "http4s-blaze-server" % Versions.http4s,
      "org.http4s" %% "http4s-circe" % Versions.http4s
    )
  )
  .dependsOn(tests)

lazy val sttpClient: Project = (project in file("client/sttp-client"))
  .settings(commonSettings)
  .settings(
    name := "tapir-sttp-client",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client" %% "core" % Versions.sttp,
      "com.softwaremill.sttp.client" %% "async-http-client-backend-fs2" % Versions.sttp % "test"
    )
  )
  .dependsOn(core, clientTests % "test")

// other

lazy val examples: Project = (project in file("examples"))
  .settings(commonSettings)
  .settings(
    name := "tapir-examples",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "1.0.0-RC16",
      "dev.zio" %% "zio-interop-cats" % "2.0.0.0-RC7",
      "org.typelevel" %% "cats-effect" % "2.0.0",
      "org.http4s" %% "http4s-dsl" % Versions.http4s
    ),
    libraryDependencies ++= loggerDependencies,
    publishArtifact := false
  )
  .settings(only2_12settings)
  .dependsOn(akkaHttpServer, http4sServer, sttpClient, openapiCirceYaml, openapiDocs, circeJson, swaggerUiAkka, swaggerUiHttp4s)

lazy val playground: Project = (project in file("playground"))
  .settings(commonSettings)
  .settings(
    name := "tapir-playground",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client" %% "akka-http-backend" % Versions.sttp,
      "dev.zio" %% "zio" % "1.0.0-RC16",
      "dev.zio" %% "zio-interop-cats" % "2.0.0.0-RC7",
      "org.typelevel" %% "cats-effect" % "2.0.0",
      "io.swagger" % "swagger-annotations" % "1.5.24"
    ),
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client" %% "akka-http-backend" % Versions.sttp
    ),
    libraryDependencies ++= loggerDependencies,
    publishArtifact := false
  )
  .settings(only2_12settings)
  .dependsOn(akkaHttpServer, http4sServer, sttpClient, openapiCirceYaml, openapiDocs, circeJson, swaggerUiAkka, swaggerUiHttp4s)
