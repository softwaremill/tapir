import java.util.concurrent.atomic.AtomicInteger

import sbtrelease.ReleaseStateTransformations.{
  checkSnapshotDependencies,
  commitReleaseVersion,
  inquireVersions,
  publishArtifacts,
  pushChanges,
  runClean,
  runTest,
  setReleaseVersion,
  tagRelease
}
import sbt.internal.ProjectMatrix

val scala2_12 = "2.12.12"
val scala2_13 = "2.13.3"

val allScalaVersions = List(scala2_12, scala2_13)
val scala2_12Versions = List(scala2_12)
val documentationScalaVersion = scala2_12 // Documentation depends on finatraServer, which is 2.12 only

scalaVersion := scala2_12

// Tests which start servers should use distinct ports to avoid errors. They use different class loaders, hence
// they don't share any objects accessible from the test code, and we have to pass the starting port as test config.
val PortCounterStart = 50000
val portCounter = new AtomicInteger(PortCounterStart)

val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.sttp.tapir",
  libraryDependencies ++= Seq(
    compilerPlugin("com.softwaremill.neme" %% "neme-plugin" % "0.0.5"),
    compilerPlugin("com.github.ghik" % "silencer-plugin" % Versions.silencer cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % Versions.silencer % Provided cross CrossVersion.full
  ),
  mimaPreviousArtifacts := Set.empty, //Set("com.softwaremill.sttp.tapir" %% name.value % "0.12.21")
  // cross-release doesn't work when subprojects have different cross versions
  // work-around from https://github.com/sbt/sbt-release/issues/214,
  releaseCrossBuild := false,
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
    releaseStepInputTask(documentation.jvm(documentationScalaVersion) / mdoc),
    Release.stageChanges("generated-doc/out"),
    Release.updateVersionInDocs(organization.value),
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    releaseStepCommand("sonatypeBundleRelease"),
    pushChanges
  ),
  ideSkipProject := (scalaVersion.value == scala2_13),
  testOptions := {
    val nextPort = portCounter.getAndUpdate((port: Int) => if (port >= 65000) PortCounterStart else port + 500)
    Seq(Tests.Argument(s"-Dport=$nextPort")) ++ testOptions.value
  }
)

def dependenciesFor(version: String)(deps: (Option[(Long, Long)] => ModuleID)*): Seq[ModuleID] =
  deps.map(_.apply(CrossVersion.partialVersion(version)))

val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest
val scalaCheck = "org.scalacheck" %% "scalacheck" % Versions.scalaCheck
val scalaTestPlusScalaCheck = "org.scalatestplus" %% "scalacheck-1-14" % Versions.scalaTestPlusScalaCheck

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
    core.projectRefs ++
      cats.projectRefs ++
      enumeratum.projectRefs ++
      refined.projectRefs ++
      zio.projectRefs ++
      circeJson.projectRefs ++
      jsoniterScala.projectRefs ++
      playJson.projectRefs ++
      sprayJson.projectRefs ++
      uPickleJson.projectRefs ++
      tethysJson.projectRefs ++
      apispecModel.projectRefs ++
      openapiModel.projectRefs ++
      openapiCirce.projectRefs ++
      openapiCirceYaml.projectRefs ++
      asyncapiModel.projectRefs ++
      asyncapiCirce.projectRefs ++
      asyncapiCirceYaml.projectRefs ++
      apispecDocs.projectRefs ++
      openapiDocs.projectRefs ++
      asyncapiDocs.projectRefs ++
      swaggerUiAkka.projectRefs ++
      redocAkka.projectRefs ++
      swaggerUiHttp4s.projectRefs ++
      redocHttp4s.projectRefs ++
      swaggerUiFinatra.projectRefs ++
      swaggerUiPlay.projectRefs ++
      redocPlay.projectRefs ++
      serverTests.projectRefs ++
      akkaHttpServer.projectRefs ++
      http4sServer.projectRefs ++
      sttpStubServer.projectRefs ++
      finatraServer.projectRefs ++
      finatraServerCats.projectRefs ++
      playServer.projectRefs ++
      vertxServer.projectRefs ++
      zioServer.projectRefs ++
      sttpClient.projectRefs ++
      tests.projectRefs ++
      examples.projectRefs ++
      playground.projectRefs ++
      documentation.projectRefs: _*
  )

// core

lazy val core: ProjectMatrix = (projectMatrix in file("core"))
  .settings(commonSettings)
  .settings(
    name := "tapir-core",
    libraryDependencies ++= Seq(
      "com.propensive" %% "magnolia" % "0.17.0",
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "com.softwaremill.sttp.model" %% "core" % Versions.sttpModel,
      "com.softwaremill.sttp.shared" %% "core" % Versions.sttpShared,
      "com.softwaremill.sttp.shared" %% "ws" % Versions.sttpShared,
      scalaTest % Test,
      scalaCheck % Test,
      scalaTestPlusScalaCheck % Test,
      "com.47deg" %% "scalacheck-toolbox-datetime" % "0.3.5" % Test,
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % Test
    ),
    unmanagedSourceDirectories in Compile += {
      val sourceDir = (sourceDirectory in Compile).value
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n >= 13 => sourceDir / "scala-2.13+"
        case _                       => sourceDir / "scala-2.13-"
      }
    }
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .enablePlugins(spray.boilerplate.BoilerplatePlugin)

lazy val tests: ProjectMatrix = (projectMatrix in file("tests"))
  .settings(commonSettings)
  .settings(
    name := "tapir-tests",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-generic" % Versions.circe,
      "com.beachape" %% "enumeratum-circe" % Versions.enumeratum,
      "com.softwaremill.common" %% "tagging" % "2.2.1",
      scalaTest,
      "com.softwaremill.macwire" %% "macros" % "2.3.7" % "provided",
      "org.typelevel" %% "cats-effect" % Versions.catsEffect
    ),
    libraryDependencies ++= loggerDependencies
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core, circeJson, enumeratum, cats)

// integrations

lazy val cats: ProjectMatrix = (projectMatrix in file("integrations/cats"))
  .settings(commonSettings)
  .settings(
    name := "tapir-cats",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "2.2.0",
      scalaTest % Test,
      scalaCheck % Test,
      scalaTestPlusScalaCheck % Test,
      "org.typelevel" %% "discipline-scalatest" % "2.0.1" % Test,
      "org.typelevel" %% "cats-laws" % "2.2.0" % Test
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core)

lazy val enumeratum: ProjectMatrix = (projectMatrix in file("integrations/enumeratum"))
  .settings(commonSettings)
  .settings(
    name := "tapir-enumeratum",
    libraryDependencies ++= Seq(
      "com.beachape" %% "enumeratum" % Versions.enumeratum,
      scalaTest % Test
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core)

lazy val refined: ProjectMatrix = (projectMatrix in file("integrations/refined"))
  .settings(commonSettings)
  .settings(
    name := "tapir-refined",
    libraryDependencies ++= Seq(
      "eu.timepit" %% "refined" % Versions.refined,
      scalaTest % Test,
      "io.circe" %% "circe-refined" % Versions.circe % Test
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core, circeJson % Test)

lazy val zio: ProjectMatrix = (projectMatrix in file("integrations/zio"))
  .settings(commonSettings)
  .settings(
    name := "tapir-zio",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % Versions.zio,
      "dev.zio" %% "zio-streams" % Versions.zio,
      scalaTest % Test,
      "com.softwaremill.sttp.shared" %% "zio" % Versions.sttpShared
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core)

// json

lazy val circeJson: ProjectMatrix = (projectMatrix in file("json/circe"))
  .settings(commonSettings)
  .settings(
    name := "tapir-json-circe",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % Versions.circe,
      "io.circe" %% "circe-parser" % Versions.circe
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core)

lazy val playJson: ProjectMatrix = (projectMatrix in file("json/playjson"))
  .settings(commonSettings: _*)
  .settings(
    name := "tapir-json-play",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-json" % Versions.playJson,
      scalaTest % Test
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core)

lazy val sprayJson: ProjectMatrix = (projectMatrix in file("json/sprayjson"))
  .settings(commonSettings: _*)
  .settings(
    name := "tapir-json-spray",
    libraryDependencies ++= Seq(
      "io.spray" %% "spray-json" % Versions.sprayJson,
      scalaTest % Test
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core)

lazy val uPickleJson: ProjectMatrix = (projectMatrix in file("json/upickle"))
  .settings(commonSettings)
  .settings(
    name := "tapir-json-upickle",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "upickle" % Versions.upickle,
      scalaTest % Test
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core)

lazy val tethysJson: ProjectMatrix = (projectMatrix in file("json/tethys"))
  .settings(commonSettings)
  .settings(
    name := "tapir-json-tethys",
    libraryDependencies ++= Seq(
      "com.tethys-json" %% "tethys-core" % Versions.tethys,
      "com.tethys-json" %% "tethys-jackson" % Versions.tethys
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core)

lazy val jsoniterScala: ProjectMatrix = (projectMatrix in file("json/jsoniter"))
  .settings(commonSettings)
  .settings(
    name := "tapir-jsoniter-scala",
    libraryDependencies ++= Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "2.6.2",
      scalaTest % Test
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core)

// apispec

lazy val apispecModel: ProjectMatrix = (projectMatrix in file("apispec/apispec-model"))
  .settings(commonSettings)
  .settings(
    name := "tapir-apispec-model"
  )
  .settings(libraryDependencies += scalaTest % Test)
  .jvmPlatform(scalaVersions = allScalaVersions)

// openapi

lazy val openapiModel: ProjectMatrix = (projectMatrix in file("apispec/openapi-model"))
  .settings(commonSettings)
  .settings(
    name := "tapir-openapi-model"
  )
  .settings(libraryDependencies += scalaTest % Test)
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(apispecModel)

lazy val openapiCirce: ProjectMatrix = (projectMatrix in file("apispec/openapi-circe"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % Versions.circe,
      "io.circe" %% "circe-parser" % Versions.circe,
      "io.circe" %% "circe-generic" % Versions.circe
    ),
    name := "tapir-openapi-circe"
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(openapiModel)

lazy val openapiCirceYaml: ProjectMatrix = (projectMatrix in file("apispec/openapi-circe-yaml"))
  .settings(commonSettings)
  .settings(
    libraryDependencies += "io.circe" %% "circe-yaml" % Versions.circeYaml,
    name := "tapir-openapi-circe-yaml"
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(openapiCirce)

// asyncapi

lazy val asyncapiModel: ProjectMatrix = (projectMatrix in file("apispec/asyncapi-model"))
  .settings(commonSettings)
  .settings(
    name := "tapir-asyncapi-model"
  )
  .settings(libraryDependencies += scalaTest % Test)
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(apispecModel)

lazy val asyncapiCirce: ProjectMatrix = (projectMatrix in file("apispec/asyncapi-circe"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % Versions.circe,
      "io.circe" %% "circe-parser" % Versions.circe,
      "io.circe" %% "circe-generic" % Versions.circe
    ),
    name := "tapir-asyncapi-circe"
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(asyncapiModel)

lazy val asyncapiCirceYaml: ProjectMatrix = (projectMatrix in file("apispec/asyncapi-circe-yaml"))
  .settings(commonSettings)
  .settings(
    libraryDependencies += "io.circe" %% "circe-yaml" % Versions.circeYaml,
    name := "tapir-asyncapi-circe-yaml"
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(asyncapiCirce)

// docs

lazy val apispecDocs: ProjectMatrix = (projectMatrix in file("docs/apispec-docs"))
  .settings(commonSettings)
  .settings(
    name := "tapir-apispec-docs"
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core, tests % Test, apispecModel)

lazy val openapiDocs: ProjectMatrix = (projectMatrix in file("docs/openapi-docs"))
  .settings(commonSettings)
  .settings(
    name := "tapir-openapi-docs"
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(openapiModel, core, apispecDocs, tests % Test, openapiCirceYaml % Test)

lazy val asyncapiDocs: ProjectMatrix = (projectMatrix in file("docs/asyncapi-docs"))
  .settings(commonSettings)
  .settings(
    name := "tapir-asyncapi-docs",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % Versions.akkaStreams % Test,
      "com.softwaremill.sttp.shared" %% "akka" % Versions.sttpShared % Test
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(asyncapiModel, core, apispecDocs, tests % Test, asyncapiCirceYaml % Test)

lazy val swaggerUiAkka: ProjectMatrix = (projectMatrix in file("docs/swagger-ui-akka-http"))
  .settings(commonSettings)
  .settings(
    name := "tapir-swagger-ui-akka-http",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
      "com.typesafe.akka" %% "akka-stream" % Versions.akkaStreams,
      "org.webjars" % "swagger-ui" % Versions.swaggerUi
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)

lazy val redocAkka: ProjectMatrix = (projectMatrix in file("docs/redoc-akka-http"))
  .settings(commonSettings)
  .settings(
    name := "tapir-redoc-akka-http",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
      "com.typesafe.akka" %% "akka-stream" % Versions.akkaStreams
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)

lazy val swaggerUiHttp4s: ProjectMatrix = (projectMatrix in file("docs/swagger-ui-http4s"))
  .settings(commonSettings)
  .settings(
    name := "tapir-swagger-ui-http4s",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl" % Versions.http4s,
      "org.webjars" % "swagger-ui" % Versions.swaggerUi
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)

lazy val redocHttp4s: ProjectMatrix = (projectMatrix in file("docs/redoc-http4s"))
  .settings(commonSettings)
  .settings(
    name := "tapir-redoc-http4s",
    libraryDependencies += "org.http4s" %% "http4s-dsl" % Versions.http4s
  )
  .jvmPlatform(scalaVersions = allScalaVersions)

lazy val swaggerUiFinatra: ProjectMatrix = (projectMatrix in file("docs/swagger-ui-finatra"))
  .settings(commonSettings)
  .settings(
    name := "tapir-swagger-ui-finatra",
    libraryDependencies ++= Seq(
      "com.twitter" %% "finatra-http" % Versions.finatra,
      "org.webjars" % "swagger-ui" % Versions.swaggerUi
    )
  )
  .jvmPlatform(scalaVersions = scala2_12Versions)

lazy val swaggerUiPlay: ProjectMatrix = (projectMatrix in file("docs/swagger-ui-play"))
  .settings(commonSettings)
  .settings(
    name := "tapir-swagger-ui-play",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play" % Versions.playServer,
      "org.webjars" % "swagger-ui" % Versions.swaggerUi
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)

lazy val redocPlay: ProjectMatrix = (projectMatrix in file("docs/redoc-play"))
  .enablePlugins(SbtTwirl)
  .settings(commonSettings)
  .settings(
    name := "tapir-redoc-play",
    libraryDependencies += "com.typesafe.play" %% "play" % Versions.playServer
  )
  .jvmPlatform(scalaVersions = allScalaVersions)

// server

lazy val serverTests: ProjectMatrix = (projectMatrix in file("server/tests"))
  .settings(commonSettings)
  .settings(
    name := "tapir-server-tests",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2" % Versions.sttp
    )
  )
  .dependsOn(tests)
  .jvmPlatform(scalaVersions = allScalaVersions)

lazy val akkaHttpServer: ProjectMatrix = (projectMatrix in file("server/akka-http-server"))
  .settings(commonSettings)
  .settings(
    name := "tapir-akka-http-server",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
      "com.typesafe.akka" %% "akka-stream" % Versions.akkaStreams,
      "com.softwaremill.sttp.shared" %% "akka" % Versions.sttpShared
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core, serverTests % Test)

lazy val http4sServer: ProjectMatrix = (projectMatrix in file("server/http4s-server"))
  .settings(commonSettings)
  .settings(
    name := "tapir-http4s-server",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-server" % Versions.http4s,
      "com.softwaremill.sttp.shared" %% "fs2" % Versions.sttpShared
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core, serverTests % Test)

lazy val sttpStubServer: ProjectMatrix = (projectMatrix in file("server/sttp-stub-server"))
  .settings(commonSettings)
  .settings(
    name := "tapir-sttp-stub-server"
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core, serverTests % "test", sttpClient)

lazy val finatraServer: ProjectMatrix = (projectMatrix in file("server/finatra-server"))
  .settings(commonSettings: _*)
  .settings(
    name := "tapir-finatra-server",
    libraryDependencies ++= Seq(
      "com.twitter" %% "finatra-http" % Versions.finatra,
      "org.apache.httpcomponents" % "httpmime" % "4.5.13",
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
    ),
    dependencyOverrides += "org.scalatest" %% "scalatest" % "3.1.2" // TODO: finatra testing utilities are not compatible with newer scalatest
  )
  .jvmPlatform(scalaVersions = scala2_12Versions)
  .dependsOn(core, serverTests % Test)

lazy val finatraServerCats: ProjectMatrix =
  (projectMatrix in file("server/finatra-server/finatra-server-cats"))
    .settings(commonSettings: _*)
    .settings(
      name := "tapir-finatra-server-cats",
      libraryDependencies ++= Seq(
        "org.typelevel" %% "cats-effect" % Versions.catsEffect,
        "io.catbird" %% "catbird-finagle" % Versions.catbird,
        "io.catbird" %% "catbird-effect" % Versions.catbird
      )
    )
    .jvmPlatform(scalaVersions = scala2_12Versions)
    .dependsOn(finatraServer % "compile->compile;test->test", serverTests % Test)

lazy val playServer: ProjectMatrix = (projectMatrix in file("server/play-server"))
  .settings(commonSettings)
  .settings(
    name := "tapir-play-server",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-server" % Versions.playServer,
      "com.typesafe.play" %% "play-akka-http-server" % Versions.playServer,
      "com.typesafe.play" %% "play" % Versions.playServer
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core, serverTests % Test)

lazy val vertxServer: ProjectMatrix = (projectMatrix in file("server/vertx"))
  .settings(commonSettings)
  .settings(
    name := "tapir-vertx-server",
    libraryDependencies ++= Seq(
      "io.vertx" %% "vertx-web-scala" % Versions.vertx
    )
  )
  .jvmPlatform(scalaVersions = scala2_12Versions)
  .dependsOn(core, serverTests % Test)

lazy val zioServer: ProjectMatrix = (projectMatrix in file("server/zio-http4-server"))
  .settings(commonSettings)
  .settings(
    name := "tapir-zio-http4s-server",
    libraryDependencies += "dev.zio" %% "zio-interop-cats" % Versions.zioInteropCats
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(zio, http4sServer, serverTests % Test)

// client

lazy val clientTests: ProjectMatrix = (projectMatrix in file("client/tests"))
  .settings(commonSettings)
  .settings(
    name := "tapir-client-tests",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl" % Versions.http4s,
      "org.http4s" %% "http4s-blaze-server" % Versions.http4s,
      "org.http4s" %% "http4s-circe" % Versions.http4s
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(tests)

lazy val sttpClient: ProjectMatrix = (projectMatrix in file("client/sttp-client"))
  .settings(commonSettings)
  .settings(
    name := "tapir-sttp-client",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "core" % Versions.sttp,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2" % Versions.sttp % Test,
      "com.softwaremill.sttp.shared" %% "fs2" % Versions.sttpShared % Optional,
      "com.softwaremill.sttp.shared" %% "akka" % Versions.sttpShared % Optional
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core, clientTests % Test)

// other

lazy val examples: ProjectMatrix = (projectMatrix in file("examples"))
  .settings(commonSettings)
  .settings(
    name := "tapir-examples",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-interop-cats" % Versions.zioInteropCats,
      "org.typelevel" %% "cats-effect" % Versions.catsEffect,
      "org.http4s" %% "http4s-dsl" % Versions.http4s,
      "com.softwaremill.sttp.client3" %% "akka-http-backend" % Versions.sttp,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2" % Versions.sttp,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % Versions.sttp
    ),
    libraryDependencies ++= loggerDependencies,
    publishArtifact := false
  )
  .jvmPlatform(scalaVersions = scala2_12Versions)
  .dependsOn(
    akkaHttpServer,
    http4sServer,
    sttpClient,
    openapiCirceYaml,
    openapiDocs,
    asyncapiCirceYaml,
    asyncapiDocs,
    circeJson,
    swaggerUiAkka,
    swaggerUiHttp4s,
    zioServer
  )

lazy val playground: ProjectMatrix = (projectMatrix in file("playground"))
  .settings(commonSettings)
  .settings(
    name := "tapir-playground",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "akka-http-backend" % Versions.sttp,
      "dev.zio" %% "zio" % Versions.zio,
      "dev.zio" %% "zio-interop-cats" % Versions.zioInteropCats,
      "org.typelevel" %% "cats-effect" % Versions.catsEffect,
      "io.swagger" % "swagger-annotations" % "1.6.2",
      "io.circe" %% "circe-generic-extras" % "0.13.0",
      "com.softwaremill.sttp.client3" %% "akka-http-backend" % Versions.sttp
    ),
    libraryDependencies ++= loggerDependencies,
    publishArtifact := false
  )
  .jvmPlatform(scalaVersions = scala2_12Versions)
  .dependsOn(
    akkaHttpServer,
    http4sServer,
    sttpClient,
    openapiCirceYaml,
    asyncapiCirceYaml,
    openapiDocs,
    circeJson,
    swaggerUiAkka,
    swaggerUiHttp4s,
    refined,
    cats,
    zioServer
  )

//TODO this should be invoked by compilation process, see #https://github.com/scalameta/mdoc/issues/355
val compileDocumentation: TaskKey[Unit] = taskKey[Unit]("Compiles documentation throwing away its output")
compileDocumentation := {
  (documentation.jvm(documentationScalaVersion) / mdoc).toTask(" --out target/tapir-doc").value
}

lazy val documentation: ProjectMatrix = (projectMatrix in file("generated-doc")) // important: it must not be doc/
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
  .jvmPlatform(scalaVersions = List(documentationScalaVersion))
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
    sttpStubServer,
    swaggerUiAkka,
    tethysJson,
    uPickleJson,
    vertxServer,
    zio,
    zioServer
  )
