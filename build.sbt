import sbtrelease.ReleaseStateTransformations.{
  checkSnapshotDependencies,
  commitNextVersion,
  commitReleaseVersion,
  inquireVersions,
  publishArtifacts,
  pushChanges,
  runClean,
  runTest,
  setNextVersion,
  setReleaseVersion,
  tagRelease
}

val scala2_12 = "2.12.11"
val scala2_13 = "2.13.2"

lazy val is2_12 = settingKey[Boolean]("Is the scala version 2.12.")

// an ugly work-around for https://github.com/sbt/sbt/issues/3465
// even if a project is 2.12-only, we fake that it's also 2.13-compatible
val only2_12settings = Seq(
  publishArtifact := is2_12.value,
  skip := !is2_12.value,
  skip in publish := !is2_12.value,
  libraryDependencies := (if (is2_12.value) libraryDependencies.value else Nil),
  mimaPreviousArtifacts := (if (is2_12.value) mimaPreviousArtifacts.value else Set.empty)
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
  ),
  mimaPreviousArtifacts := Set.empty, //Set("com.softwaremill.sttp.tapir" %% name.value % "0.12.21")
  // similar to Release.steps, but without setting the next version. That way automatic release notes work w/ github
  // actions, and when we get compiled docs, re-generating them won't cause versions to change to -SNAPSHOT.
  releaseProcess := Seq(
    checkSnapshotDependencies,
    inquireVersions,
    // publishing locally so that the pgp password prompt is displayed early
    // in the process
    releaseStepCommand("publishLocalSigned"),
    runClean,
    runTest,
    setReleaseVersion,
    Release.updateVersionInDocs(organization.value),
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    releaseStepCommand("sonatypeBundleRelease"),
    pushChanges
  )
)

def dependenciesFor(version: String)(deps: (Option[(Long, Long)] => ModuleID)*): Seq[ModuleID] =
  deps.map(_.apply(CrossVersion.partialVersion(version)))

val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest
val scalaCheck = "org.scalacheck" %% "scalacheck" % Versions.scalaCheck

lazy val loggerDependencies = Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "ch.qos.logback" % "logback-core" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
)

lazy val rootProject = (project in file("."))
  .settings(commonSettings)
  .settings(mimaPreviousArtifacts := Set.empty)
  .settings(publishArtifact := false, name := "tapir")
  .aggregate(
    core,
    cats,
    enumeratum,
    refined,
    zio,
    circeJson,
    jsoniterScala,
    playJson,
    sprayJson,
    uPickleJson,
    tethysJson,
    openapiModel,
    openapiCirce,
    openapiCirceYaml,
    openapiDocs,
    swaggerUiAkka,
    redocAkka,
    swaggerUiHttp4s,
    redocHttp4s,
    swaggerUiFinatra,
    swaggerUiPlay,
    redocPlay,
    serverTests,
    akkaHttpServer,
    http4sServer,
    sttpStubServer,
    finatraServer,
    finatraServerCats,
    playServer,
    vertxServer,
    zioServer,
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
      "com.propensive" %% "magnolia" % "0.16.0",
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "com.softwaremill.sttp.model" %% "core" % "1.1.4",
      scalaTest % Test,
      scalaCheck % Test,
      "com.47deg" %% "scalacheck-toolbox-datetime" % "0.3.5" % Test,
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % Test
    ),
    unmanagedSourceDirectories in Compile += {
      val sourceDir = (baseDirectory in Compile).value / "src" / "main"
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n >= 13 => sourceDir / "scala-2.13+"
        case _                       => sourceDir / "scala-2.13-"
      }
    }
  )
  .enablePlugins(spray.boilerplate.BoilerplatePlugin)

lazy val tests: Project = (project in file("tests"))
  .settings(commonSettings)
  .settings(
    name := "tapir-tests",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic" % Versions.circe,
      "com.softwaremill.common" %% "tagging" % "2.2.1",
      scalaTest,
      "com.softwaremill.macwire" %% "macros" % "2.3.7" % "provided"
    ),
    libraryDependencies ++= loggerDependencies
  )
  .dependsOn(core, circeJson)

// integrations

lazy val cats: Project = (project in file("integrations/cats"))
  .settings(commonSettings)
  .settings(
    name := "tapir-cats",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.1.1",
      scalaTest % Test,
      scalaCheck % Test,
      "org.typelevel" %% "discipline-scalatest" % "2.0.0" % Test,
      "org.typelevel" %% "cats-laws" % "2.1.1" % Test
    )
  )
  .dependsOn(core)

lazy val enumeratum: Project = (project in file("integrations/enumeratum"))
  .settings(commonSettings)
  .settings(
    name := "tapir-enumeratum",
    libraryDependencies ++= Seq(
      "com.beachape" %% "enumeratum" % Versions.enumeratum,
      scalaTest % Test
    )
  )
  .dependsOn(core)

lazy val refined: Project = (project in file("integrations/refined"))
  .settings(commonSettings)
  .settings(
    name := "tapir-refined",
    libraryDependencies ++= Seq(
      "eu.timepit" %% "refined" % Versions.refined,
      scalaTest % Test
    )
  )
  .dependsOn(core)

lazy val zio: Project = (project in file("integrations/zio"))
  .settings(commonSettings)
  .settings(
    name := "tapir-zio",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % Versions.zio,
      "dev.zio" %% "zio-streams" % Versions.zio,
      scalaTest % Test
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
      scalaTest % Test
    )
  )
  .dependsOn(core)

lazy val sprayJson: Project = (project in file("json/sprayjson"))
  .settings(commonSettings: _*)
  .settings(
    name := "tapir-json-spray",
    libraryDependencies ++= Seq(
      "io.spray" %% "spray-json" % Versions.sprayJson,
      scalaTest % Test
    )
  )
  .dependsOn(core)

lazy val uPickleJson: Project = (project in file("json/upickle"))
  .settings(commonSettings)
  .settings(
    name := "tapir-json-upickle",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "upickle" % Versions.upickle,
      scalaTest % Test
    )
  )
  .dependsOn(core)

lazy val tethysJson: Project = (project in file("json/tethys"))
  .settings(commonSettings)
  .settings(
    name := "tapir-json-tethys",
    libraryDependencies ++= Seq(
      "com.tethys-json" %% "tethys-core" % Versions.tethys,
      "com.tethys-json" %% "tethys-jackson" % Versions.tethys
    )
  )
  .dependsOn(core)

lazy val jsoniterScala: Project = (project in file("json/jsoniter"))
  .settings(commonSettings)
  .settings(
    name := "tapir-jsoniter-scala",
    libraryDependencies ++= Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "2.6.0",
      scalaTest % Test
    )
  )
  .dependsOn(core)

// openapi

lazy val openapiModel: Project = (project in file("openapi/openapi-model"))
  .settings(commonSettings)
  .settings(
    name := "tapir-openapi-model"
  )
  .settings(libraryDependencies += scalaTest % Test)

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
  .dependsOn(openapiModel, core, tests % Test, openapiCirceYaml % Test)

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

lazy val redocAkka: Project = (project in file("docs/redoc-akka-http"))
  .settings(commonSettings)
  .settings(
    name := "tapir-redoc-akka-http",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
      "com.typesafe.akka" %% "akka-stream" % Versions.akkaStreams
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

lazy val swaggerUiFinatra: Project = (project in file("docs/swagger-ui-finatra"))
  .settings(commonSettings)
  .settings(
    name := "tapir-swagger-ui-finatra",
    libraryDependencies ++= Seq(
      "com.twitter" %% "finatra-http" % Versions.finatra,
      "org.webjars" % "swagger-ui" % Versions.swaggerUi
    )
  )
  .settings(only2_12settings)

lazy val swaggerUiPlay: Project = (project in file("docs/swagger-ui-play"))
  .settings(commonSettings)
  .settings(
    name := "tapir-swagger-ui-play",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play" % Versions.playServer,
      "org.webjars" % "swagger-ui" % Versions.swaggerUi
    )
  )

lazy val redocPlay: Project = (project in file("docs/redoc-play"))
  .enablePlugins(SbtTwirl)
  .settings(commonSettings)
  .settings(
    name := "tapir-redoc-play",
    libraryDependencies += "com.typesafe.play" %% "play" % Versions.playServer
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
  .dependsOn(core, serverTests % Test)

lazy val http4sServer: Project = (project in file("server/http4s-server"))
  .settings(commonSettings)
  .settings(
    name := "tapir-http4s-server",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-server" % Versions.http4s
    )
  )
  .dependsOn(core, serverTests % Test)

lazy val sttpStubServer: Project = (project in file("server/sttp-stub-server"))
  .settings(commonSettings)
  .settings(
    name := "tapir-sttp-stub-server"
  )
  .dependsOn(core, serverTests % "test", sttpClient)

lazy val finatraServer: Project = (project in file("server/finatra-server"))
  .settings(commonSettings: _*)
  .settings(
    name := "tapir-finatra-server",
    libraryDependencies ++= Seq(
      "com.twitter" %% "finatra-http" % Versions.finatra,
      "org.apache.httpcomponents" % "httpmime" % "4.5.12",
      // Testing
      "com.twitter" %% "finatra-http" % Versions.finatra % Test,
      "com.twitter" %% "inject-server" % Versions.finatra % Test,
      "com.twitter" %% "inject-app" % Versions.finatra % Test,
      "com.twitter" %% "inject-core" % Versions.finatra % Test,
      "com.twitter" %% "inject-modules" % Versions.finatra % Test,
      "com.twitter" %% "finatra-http" % Versions.finatra % Test classifier "tests",
      "com.twitter" %% "inject-server" % Versions.finatra % Test classifier "tests",
      "com.twitter" %% "inject-app" % Versions.finatra % Test classifier "tests",
      "com.twitter" %% "inject-core" % Versions.finatra % Test classifier "tests",
      "com.twitter" %% "inject-modules" % Versions.finatra % Test classifier "tests"
    )
  )
  .settings(only2_12settings)
  .dependsOn(core, serverTests % Test)

lazy val finatraServerCats: Project =
  (project in file("server/finatra-server/finatra-server-cats"))
    .settings(commonSettings: _*)
    .settings(
      name := "tapir-finatra-server-cats",
      libraryDependencies ++= Seq(
        "org.typelevel" %% "cats-effect" % Versions.catsEffect,
        "io.catbird" %% "catbird-finagle" % Versions.catbird,
        "io.catbird" %% "catbird-effect" % Versions.catbird
      )
    )
    .settings(only2_12settings)
    .dependsOn(finatraServer % "compile->compile;test->test", serverTests % Test)

lazy val playServer: Project = (project in file("server/play-server"))
  .settings(commonSettings)
  .settings(
    name := "tapir-play-server",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-server" % Versions.playServer,
      "com.typesafe.play" %% "play-akka-http-server" % Versions.playServer,
      "com.typesafe.play" %% "play" % Versions.playServer
    )
  )
  .dependsOn(core, serverTests % Test)

lazy val vertxServer: Project = (project in file("server/vertx"))
  .settings(commonSettings)
  .settings(
    name := "tapir-vertx-server",
    libraryDependencies ++= Seq(
      "io.vertx" %% "vertx-web-scala" % Versions.vertx
    )
  )
  .settings(only2_12settings)
  .dependsOn(core, serverTests % Test)

lazy val zioServer: Project = (project in file("server/zio-http4-server"))
  .settings(commonSettings)
  .settings(
    name := "tapir-zio-http4s-server",
    libraryDependencies += "dev.zio" %% "zio-interop-cats" % Versions.zioInteropCats
  )
  .dependsOn(zio, http4sServer, serverTests % Test)

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
      "com.softwaremill.sttp.client" %% "async-http-client-backend-fs2" % Versions.sttp % Test
    )
  )
  .dependsOn(core, clientTests % Test)

// other

lazy val examples: Project = (project in file("examples"))
  .settings(commonSettings)
  .settings(
    name := "tapir-examples",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-interop-cats" % Versions.zioInteropCats,
      "org.typelevel" %% "cats-effect" % Versions.catsEffect,
      "org.http4s" %% "http4s-dsl" % Versions.http4s,
      "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % Versions.sttp
    ),
    libraryDependencies ++= loggerDependencies,
    publishArtifact := false
  )
  .settings(only2_12settings)
  .dependsOn(akkaHttpServer, http4sServer, sttpClient, openapiCirceYaml, openapiDocs, circeJson, swaggerUiAkka, swaggerUiHttp4s, zioServer)

lazy val playground: Project = (project in file("playground"))
  .settings(commonSettings)
  .settings(
    name := "tapir-playground",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client" %% "akka-http-backend" % Versions.sttp,
      "dev.zio" %% "zio" % Versions.zio,
      "dev.zio" %% "zio-interop-cats" % Versions.zioInteropCats,
      "org.typelevel" %% "cats-effect" % Versions.catsEffect,
      "io.swagger" % "swagger-annotations" % "1.6.2",
      "io.circe" %% "circe-generic-extras" % "0.13.0",
      "com.softwaremill.sttp.client" %% "akka-http-backend" % Versions.sttp
    ),
    libraryDependencies ++= loggerDependencies,
    publishArtifact := false
  )
  .settings(only2_12settings)
  .dependsOn(
    akkaHttpServer,
    http4sServer,
    sttpClient,
    openapiCirceYaml,
    openapiDocs,
    circeJson,
    swaggerUiAkka,
    swaggerUiHttp4s,
    refined,
    cats,
    zioServer
  )

//TODO this should be invoked by compilation process, see #https://github.com/scalameta/mdoc/issues/355
val generateDoc: TaskKey[Unit] = taskKey[Unit]("Compiles doc module throwing away its output")
generateDoc := {
  (generatedDoc / mdoc).toTask(" --out target/tapir-doc").value
}

lazy val generatedDoc: Project = (project in file("generated-doc")) // important: it must not be docs/
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(
    mdocIn := file("doc"),
    moduleName := "tapir-doc",
    mdocVariables := Map(
      "VERSION" -> version.value,
      "PLAY_HTTP_SERVER_VERSION" -> Versions.playServer
    ),
    mdocOut := file("generated-doc/out"),
    publishArtifact := false,
    name := "doc",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-netty-server" % Versions.playServer
    )
  )
  .dependsOn(
    core % "compile->test",
    akkaHttpServer,
    circeJson,
    enumeratum,
    finatraServer,
    finatraServerCats,
    jsoniterScala,
    openapiCirceYaml,
    openapiDocs,
    playJson,
    playServer,
    sprayJson,
    sttpClient,
    swaggerUiAkka,
    tethysJson,
    uPickleJson,
    vertxServer,
    zio,
    zioServer
  )
