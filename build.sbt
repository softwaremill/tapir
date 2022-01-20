import com.softwaremill.SbtSoftwareMillBrowserTestJS._
import com.softwaremill.SbtSoftwareMillCommon.commonSmlBuildSettings
import com.softwaremill.Publish.{ossPublishSettings, updateDocs}
import com.softwaremill.UpdateVersionInDocs
import com.typesafe.tools.mima.core.{Problem, ProblemFilters}
import sbt.Reference.display
import sbt.internal.ProjectMatrix

import java.net.URL
import scala.concurrent.duration.DurationInt
import scala.sys.process.Process

val scala2_12 = "2.12.15"
val scala2_13 = "2.13.8"
val scala3 = "3.1.0"

val scala2Versions = List(scala2_12, scala2_13)
val scala2And3Versions = scala2Versions ++ List(scala3)
val codegenScalaVersions = List(scala2_12)
val examplesScalaVersions = List(scala2_13)
val documentationScalaVersion = scala2_13

lazy val clientTestServerPort = settingKey[Int]("Port to run the client interpreter test server on")
lazy val startClientTestServer = taskKey[Unit]("Start a http server used by client interpreter tests")
lazy val generateMimeByExtensionDB = taskKey[Unit]("Generate the mime by extension DB")

concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

excludeLintKeys in Global ++= Set(ideSkipProject, reStartArgs)

def versionedScalaSourceDirectories(sourceDir: File, scalaVersion: String): List[File] =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((3, _))            => List(sourceDir / "scala-3")
    case Some((2, n)) if n >= 13 => List(sourceDir / "scala-2", sourceDir / "scala-2.13+")
    case _                       => List(sourceDir / "scala-2", sourceDir / "scala-2.13-")
  }

def versionedScalaJvmSourceDirectories(sourceDir: File, scalaVersion: String): List[File] =
  CrossVersion.partialVersion(scalaVersion) match {
    case Some((3, _)) => List(sourceDir / "scalajvm-3")
    case _            => List(sourceDir / "scalajvm-2")
  }

val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.sttp.tapir",
  Compile / unmanagedSourceDirectories ++= versionedScalaSourceDirectories((Compile / sourceDirectory).value, scalaVersion.value),
  Test / unmanagedSourceDirectories ++= versionedScalaSourceDirectories((Test / sourceDirectory).value, scalaVersion.value),
  updateDocs := Def.taskDyn {
    val files1 = UpdateVersionInDocs(sLog.value, organization.value, version.value)
    Def.task {
      (documentation.jvm(documentationScalaVersion) / mdoc).toTask("").value
      files1 ++ Seq(file("generated-doc/out"))
    }
  }.value,
  mimaPreviousArtifacts := Set.empty, // we only use MiMa for `core` for now, using enableMimaSettings
  ideSkipProject := (scalaVersion.value == scala2_12) || (scalaVersion.value == scala3) || thisProjectRef.value.project.contains("JS"),
  // slow down for CI
  Test / parallelExecution := false,
  // remove false alarms about unused implicit definitions in macros
  scalacOptions += "-Ywarn-macros:after",
  evictionErrorLevel := Level.Info
)

val versioningSchemeSettings = Seq(versionScheme := Some("early-semver"))

val enableMimaSettings = Seq(
  mimaPreviousArtifacts := {
    val current = version.value
    val isRcOrMilestone = current.contains("M") || current.contains("RC")
    if (!isRcOrMilestone) {
      val previous = previousStableVersion.value
      println(s"[info] Not a M or RC version, using previous version for MiMa check: $previous")
      previousStableVersion.value.map(organization.value %% moduleName.value % _).toSet
    } else {
      println(s"[info] $current is an M or RC version, no previous version to check with MiMa")
      Set.empty
    }
  },
  mimaBinaryIssueFilters ++= Seq(
    ProblemFilters.exclude[Problem]("sttp.tapir.internal.*"),
    ProblemFilters.exclude[Problem]("sttp.tapir.generic.internal.*"),
    ProblemFilters.exclude[Problem]("sttp.tapir.typelevel.internal.*"),
    ProblemFilters.exclude[Problem]("sttp.tapir.server.interpreter.*") // for 0.19
  )
)

val commonJvmSettings: Seq[Def.Setting[_]] = commonSettings ++ Seq(
  Compile / unmanagedSourceDirectories ++= versionedScalaJvmSourceDirectories((Compile / sourceDirectory).value, scalaVersion.value),
  Test / unmanagedSourceDirectories ++= versionedScalaJvmSourceDirectories((Test / sourceDirectory).value, scalaVersion.value),
  Test / testOptions += Tests.Argument("-oD") // js has other options which conflict with timings
)

// run JS tests inside Gecko, due to jsdom not supporting fetch and to avoid having to install node
val commonJsSettings = commonSettings ++ browserGeckoTestSettings

def dependenciesFor(version: String)(deps: (Option[(Long, Long)] => ModuleID)*): Seq[ModuleID] =
  deps.map(_.apply(CrossVersion.partialVersion(version)))

val scalaTest = Def.setting("org.scalatest" %%% "scalatest" % Versions.scalaTest)
val scalaCheck = Def.setting("org.scalacheck" %%% "scalacheck" % Versions.scalaCheck)
val scalaTestPlusScalaCheck = Def.setting("org.scalatestplus" %%% "scalacheck-1-15" % Versions.scalaTestPlusScalaCheck)

lazy val loggerDependencies = Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.10",
  "ch.qos.logback" % "logback-core" % "1.2.10",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4"
)

lazy val allAggregates = core.projectRefs ++
  cats.projectRefs ++
  enumeratum.projectRefs ++
  refined.projectRefs ++
  zio.projectRefs ++
  newtype.projectRefs ++
  circeJson.projectRefs ++
  jsoniterScala.projectRefs ++
  prometheusMetrics.projectRefs ++
  opentelemetryMetrics.projectRefs ++
  json4s.projectRefs ++
  playJson.projectRefs ++
  sprayJson.projectRefs ++
  uPickleJson.projectRefs ++
  tethysJson.projectRefs ++
  zioJson.projectRefs ++
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
  swaggerUi.projectRefs ++
  swaggerUiBundle.projectRefs ++
  redoc.projectRefs ++
  redocBundle.projectRefs ++
  serverTests.projectRefs ++
  akkaHttpServer.projectRefs ++
  http4sServer.projectRefs ++
  sttpStubServer.projectRefs ++
  sttpMockServer.projectRefs ++
  finatraServer.projectRefs ++
  finatraServerCats.projectRefs ++
  playServer.projectRefs ++
  vertxServer.projectRefs ++
  nettyServer.projectRefs ++
  zioHttp4sServer.projectRefs ++
  zioHttpServer.projectRefs ++
  awsLambda.projectRefs ++
  awsLambdaTests.projectRefs ++
  awsSam.projectRefs ++
  awsTerraform.projectRefs ++
  awsExamples.projectRefs ++
  http4sClient.projectRefs ++
  sttpClient.projectRefs ++
  playClient.projectRefs ++
  tests.projectRefs ++
  examples.projectRefs ++
  documentation.projectRefs ++
  openapiCodegen.projectRefs ++
  clientTestServer.projectRefs ++
  derevo.projectRefs

val testJVM = taskKey[Unit]("Test JVM projects, without Finatra")
val testJS = taskKey[Unit]("Test JS projects")
val testDocs = taskKey[Unit]("Test docs projects")
val testServers = taskKey[Unit]("Test server projects")
val testClients = taskKey[Unit]("Test client projects")
val testOther = taskKey[Unit]("Test other projects")
val testFinatra = taskKey[Unit]("Test Finatra projects")

def filterProject(p: String => Boolean) =
  ScopeFilter(inProjects(allAggregates.filter(pr => p(display(pr.project))): _*))

lazy val macros = Seq(
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11 | 12)) => List(compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.patch))
      case _                  => List()
    }
  },
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, y)) if y == 11 => Seq("-Xexperimental")
      case Some((2, y)) if y == 13 => Seq("-Ymacro-annotations")
      case _                       => Seq.empty[String]
    }
  },
  // remove false alarms about unused implicit definitions in macros
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq("-Ywarn-macros:after")
      case _            => Seq.empty[String]
    }
  }
)

lazy val rootProject = (project in file("."))
  .settings(commonSettings)
  .settings(mimaPreviousArtifacts := Set.empty)
  .settings(
    publishArtifact := false,
    name := "tapir",
    testJVM := (Test / test).all(filterProject(p => !p.contains("JS") && !p.contains("finatra"))).value,
    testJS := (Test / test).all(filterProject(_.contains("JS"))).value,
    testDocs := (Test / test).all(filterProject(p => p.contains("Docs") || p.contains("openapi") || p.contains("asyncapi"))).value,
    testServers := (Test / test).all(filterProject(p => p.contains("Server"))).value,
    testClients := (Test / test).all(filterProject(p => p.contains("Client"))).value,
    testOther := (Test / test)
      .all(
        filterProject(p =>
          !p.contains("Server") && !p.contains("Client") && !p.contains("Docs") && !p.contains("openapi") && !p.contains("asyncapi")
        )
      )
      .value,
    testFinatra := (Test / test).all(filterProject(p => p.contains("finatra"))).value,
    ideSkipProject := false,
    generateMimeByExtensionDB := GenerateMimeByExtensionDB()
  )
  .aggregate(allAggregates: _*)

// start a test server before running tests of a client interpreter; this is required both for JS tests run inside a
// nodejs/browser environment, as well as for JVM tests where akka-http isn't available (e.g. dotty).
val clientTestServerSettings = Seq(
  Test / test := (Test / test)
    .dependsOn(clientTestServer2_13 / startClientTestServer)
    .value,
  Test / testOnly := (Test / testOnly)
    .dependsOn(clientTestServer2_13 / startClientTestServer)
    .evaluated,
  Test / testOptions += Tests.Setup(() => {
    val port = (clientTestServer2_13 / clientTestServerPort).value
    PollingUtils.waitUntilServerAvailable(new URL(s"http://localhost:$port"))
  })
)

lazy val clientTestServer = (projectMatrix in file("client/testserver"))
  .settings(commonJvmSettings)
  .settings(
    name := "testing-server",
    publish / skip := true,
    libraryDependencies ++= loggerDependencies ++ Seq(
      "org.http4s" %% "http4s-dsl" % Versions.http4s,
      "org.http4s" %% "http4s-blaze-server" % Versions.http4s,
      "org.http4s" %% "http4s-circe" % Versions.http4s
    ),
    // the test server needs to be started before running any client tests
    reStart / mainClass := Some("sttp.tapir.client.tests.HttpServer"),
    reStart / reStartArgs := Seq(s"${(Test / clientTestServerPort).value}"),
    reStart / fullClasspath := (Test / fullClasspath).value,
    clientTestServerPort := 51823,
    startClientTestServer := reStart.toTask("").value
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)

lazy val clientTestServer2_13 = clientTestServer.jvm(scala2_13)

// core

lazy val core: ProjectMatrix = (projectMatrix in file("core"))
  .settings(commonSettings)
  .settings(versioningSchemeSettings)
  .settings(
    name := "tapir-core",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.model" %%% "core" % Versions.sttpModel,
      "com.softwaremill.sttp.shared" %%% "core" % Versions.sttpShared,
      "com.softwaremill.sttp.shared" %%% "ws" % Versions.sttpShared,
      scalaTest.value % Test,
      scalaCheck.value % Test,
      scalaTestPlusScalaCheck.value % Test,
      "com.47deg" %%% "scalacheck-toolbox-datetime" % "0.6.0" % Test
    ),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) =>
          Seq("com.softwaremill.magnolia1_3" %%% "magnolia" % "1.0.0-M7")
        case _ =>
          Seq(
            "com.softwaremill.magnolia1_2" %%% "magnolia" % "1.0.0",
            "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided
          )
      }
    },
    // Until https://youtrack.jetbrains.com/issue/SCL-18636 is fixed and IntelliJ properly imports projects with
    // generated sources, they are explicitly added to git. See also below: commented out plugin.
    Compile / unmanagedSourceDirectories += {
      (Compile / sourceDirectory).value / "boilerplate-gen"
    }
  )
  .jvmPlatform(
    scalaVersions = scala2And3Versions,
    settings = commonJvmSettings ++ enableMimaSettings
  )
  .jsPlatform(
    scalaVersions = scala2And3Versions,
    settings = commonJsSettings ++ Seq(
      libraryDependencies ++= Seq(
        "org.scala-js" %%% "scalajs-dom" % "2.1.0",
        "io.github.cquiroz" %%% "scala-java-time" % Versions.jsScalaJavaTime % Test,
        "io.github.cquiroz" %%% "scala-java-time-tzdb" % Versions.jsScalaJavaTime % Test
      )
    )
  )
//.enablePlugins(spray.boilerplate.BoilerplatePlugin)

lazy val tests: ProjectMatrix = (projectMatrix in file("tests"))
  .settings(commonSettings)
  .settings(
    name := "tapir-tests",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-generic" % Versions.circe,
      "com.softwaremill.common" %%% "tagging" % "2.3.2",
      scalaTest.value,
      "org.typelevel" %%% "cats-effect" % Versions.catsEffect
    ) ++ loggerDependencies
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .jsPlatform(
    scalaVersions = scala2And3Versions,
    settings = commonJsSettings
  )
  .dependsOn(core, circeJson, cats)

// integrations

lazy val cats: ProjectMatrix = (projectMatrix in file("integrations/cats"))
  .settings(commonSettings)
  .settings(
    name := "tapir-cats",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % "2.7.0",
      "org.typelevel" %%% "cats-effect" % Versions.catsEffect,
      scalaTest.value % Test,
      scalaCheck.value % Test,
      scalaTestPlusScalaCheck.value % Test,
      "org.typelevel" %%% "discipline-scalatest" % "2.1.5" % Test,
      "org.typelevel" %%% "cats-laws" % "2.7.0" % Test
    )
  )
  .jvmPlatform(
    scalaVersions = scala2And3Versions,
    // tests for cats3 are disable until https://github.com/lampepfl/dotty/issues/12849 is fixed
    settings = Seq(
      Test / skip := scalaVersion.value == scala3,
      Test / test := {
        if (scalaVersion.value == scala3) () else (Test / test).value
      }
    )
  )
  .jsPlatform(
    scalaVersions = scala2And3Versions,
    settings = commonJsSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % Versions.jsScalaJavaTime % Test
      )
    )
  )
  .dependsOn(core)

lazy val enumeratum: ProjectMatrix = (projectMatrix in file("integrations/enumeratum"))
  .settings(commonSettings)
  .settings(
    name := "tapir-enumeratum",
    libraryDependencies ++= Seq(
      "com.beachape" %%% "enumeratum" % Versions.enumeratum,
      scalaTest.value % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .jsPlatform(
    scalaVersions = scala2Versions,
    settings = commonJsSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % Versions.jsScalaJavaTime % Test
      )
    )
  )
  .dependsOn(core)

lazy val refined: ProjectMatrix = (projectMatrix in file("integrations/refined"))
  .settings(commonSettings)
  .settings(
    name := "tapir-refined",
    libraryDependencies ++= Seq(
      "eu.timepit" %%% "refined" % Versions.refined,
      scalaTest.value % Test,
      "io.circe" %%% "circe-refined" % Versions.circe % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .jsPlatform(
    scalaVersions = scala2Versions,
    settings = commonJsSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % Versions.jsScalaJavaTime % Test
      )
    )
  )
  .dependsOn(core, circeJson % Test)

lazy val zio: ProjectMatrix = (projectMatrix in file("integrations/zio"))
  .settings(commonSettings)
  .settings(
    name := "tapir-zio",
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % Versions.zio,
      "dev.zio" %% "zio-streams" % Versions.zio,
      "dev.zio" %% "zio-test" % Versions.zio % Test,
      "dev.zio" %% "zio-test-sbt" % Versions.zio % Test,
      "com.softwaremill.sttp.shared" %% "zio1" % Versions.sttpShared
    )
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(core)

lazy val derevo: ProjectMatrix = (projectMatrix in file("integrations/derevo"))
  .settings(commonSettings)
  .settings(macros)
  .settings(
    name := "tapir-derevo",
    libraryDependencies ++= Seq(
      "tf.tofu" %% "derevo-core" % Versions.derevo,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % Provided,
      scalaTest.value % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .dependsOn(core, newtype)

lazy val newtype: ProjectMatrix = (projectMatrix in file("integrations/newtype"))
  .settings(commonSettings)
  .settings(macros)
  .settings(
    name := "tapir-newtype",
    libraryDependencies ++= Seq(
      "io.estatico" %%% "newtype" % Versions.newtype,
      scalaTest.value % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .jsPlatform(
    scalaVersions = scala2Versions,
    settings = commonJsSettings
  )
  .dependsOn(core)

// json

lazy val circeJson: ProjectMatrix = (projectMatrix in file("json/circe"))
  .settings(commonSettings)
  .settings(
    name := "tapir-json-circe",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core" % Versions.circe,
      "io.circe" %%% "circe-parser" % Versions.circe,
      "io.circe" %%% "circe-generic" % Versions.circe,
      scalaTest.value % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .jsPlatform(
    scalaVersions = scala2And3Versions,
    settings = commonJsSettings
  )
  .dependsOn(core)

lazy val json4s: ProjectMatrix = (projectMatrix in file("json/json4s"))
  .settings(commonSettings)
  .settings(
    name := "tapir-json-json4s",
    libraryDependencies ++= Seq(
      "org.json4s" %%% "json4s-core" % Versions.json4s,
      "org.json4s" %%% "json4s-jackson" % Versions.json4s % Test,
      scalaTest.value % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .dependsOn(core)

lazy val playJson: ProjectMatrix = (projectMatrix in file("json/playjson"))
  .settings(commonSettings: _*)
  .settings(
    name := "tapir-json-play",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %%% "play-json" % Versions.playJson,
      scalaTest.value % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .jsPlatform(
    scalaVersions = scala2Versions,
    settings = commonJsSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % Versions.jsScalaJavaTime % Test
      )
    )
  )
  .dependsOn(core)

lazy val sprayJson: ProjectMatrix = (projectMatrix in file("json/sprayjson"))
  .settings(commonSettings: _*)
  .settings(
    name := "tapir-json-spray",
    libraryDependencies ++= Seq(
      "io.spray" %% "spray-json" % Versions.sprayJson,
      scalaTest.value % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .dependsOn(core)

lazy val uPickleJson: ProjectMatrix = (projectMatrix in file("json/upickle"))
  .settings(commonSettings)
  .settings(
    name := "tapir-json-upickle",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "upickle" % Versions.upickle,
      scalaTest.value % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .jsPlatform(
    scalaVersions = scala2Versions,
    settings = commonJsSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % Versions.jsScalaJavaTime % Test
      )
    )
  )
  .dependsOn(core)

lazy val tethysJson: ProjectMatrix = (projectMatrix in file("json/tethys"))
  .settings(commonSettings)
  .settings(
    name := "tapir-json-tethys",
    libraryDependencies ++= Seq(
      "com.tethys-json" %% "tethys-core" % Versions.tethys,
      "com.tethys-json" %% "tethys-jackson" % Versions.tethys,
      scalaTest.value % Test,
      "com.tethys-json" %% "tethys-derivation" % Versions.tethys % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .dependsOn(core)

lazy val jsoniterScala: ProjectMatrix = (projectMatrix in file("json/jsoniter"))
  .settings(commonSettings)
  .settings(
    name := "tapir-jsoniter-scala",
    libraryDependencies ++= Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-core" % "2.12.1",
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-macros" % "2.12.1" % Test,
      scalaTest.value % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .jsPlatform(
    scalaVersions = scala2Versions,
    settings = commonJsSettings
  )
  .dependsOn(core)

lazy val zioJson: ProjectMatrix = (projectMatrix in file("json/zio"))
  .settings(commonSettings)
  .settings(
    name := "tapir-json-zio",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-json" % Versions.zioJson,
      scalaTest.value % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .jsPlatform(
    scalaVersions = scala2Versions,
    settings = commonJsSettings
  )
  .dependsOn(core)

// metrics

lazy val prometheusMetrics: ProjectMatrix = (projectMatrix in file("metrics/prometheus-metrics"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-prometheus-metrics",
    libraryDependencies ++= Seq(
      "io.prometheus" % "simpleclient_common" % "0.14.1",
      scalaTest.value % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(core % "compile->compile;test->test")

lazy val opentelemetryMetrics: ProjectMatrix = (projectMatrix in file("metrics/opentelemetry-metrics"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-opentelemetry-metrics",
    libraryDependencies ++= Seq(
      "io.opentelemetry" % "opentelemetry-api" % "1.9.1",
      "io.opentelemetry" % "opentelemetry-sdk" % "1.9.1",
      "io.opentelemetry" % "opentelemetry-sdk-metrics" % "1.5.0-alpha" % Test,
      scalaTest.value % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(core % "compile->compile;test->test")

// apispec

lazy val apispecModel: ProjectMatrix = (projectMatrix in file("apispec/apispec-model"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-apispec-model"
  )
  .settings(libraryDependencies += scalaTest.value % Test)
  .jvmPlatform(scalaVersions = scala2And3Versions)

// openapi

lazy val openapiModel: ProjectMatrix = (projectMatrix in file("apispec/openapi-model"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-openapi-model"
  )
  .settings(libraryDependencies += scalaTest.value % Test)
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(apispecModel)

lazy val openapiCirce: ProjectMatrix = (projectMatrix in file("apispec/openapi-circe"))
  .settings(commonJvmSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % Versions.circe,
      "io.circe" %% "circe-parser" % Versions.circe,
      "io.circe" %% "circe-generic" % Versions.circe
    ),
    name := "tapir-openapi-circe"
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(openapiModel)

lazy val openapiCirceYaml: ProjectMatrix = (projectMatrix in file("apispec/openapi-circe-yaml"))
  .settings(commonJvmSettings)
  .settings(
    libraryDependencies += "io.circe" %% "circe-yaml" % Versions.circeYaml,
    name := "tapir-openapi-circe-yaml"
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(openapiCirce)

// asyncapi

lazy val asyncapiModel: ProjectMatrix = (projectMatrix in file("apispec/asyncapi-model"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-asyncapi-model"
  )
  .settings(libraryDependencies += scalaTest.value % Test)
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(apispecModel)

lazy val asyncapiCirce: ProjectMatrix = (projectMatrix in file("apispec/asyncapi-circe"))
  .settings(commonJvmSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % Versions.circe,
      "io.circe" %% "circe-parser" % Versions.circe,
      "io.circe" %% "circe-generic" % Versions.circe
    ),
    name := "tapir-asyncapi-circe"
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(asyncapiModel)

lazy val asyncapiCirceYaml: ProjectMatrix = (projectMatrix in file("apispec/asyncapi-circe-yaml"))
  .settings(commonJvmSettings)
  .settings(
    libraryDependencies += "io.circe" %% "circe-yaml" % Versions.circeYaml,
    name := "tapir-asyncapi-circe-yaml"
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(asyncapiCirce)

// docs

lazy val apispecDocs: ProjectMatrix = (projectMatrix in file("docs/apispec-docs"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-apispec-docs"
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(core, tests % Test, apispecModel)

lazy val openapiDocs: ProjectMatrix = (projectMatrix in file("docs/openapi-docs"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-openapi-docs"
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(openapiModel, core, apispecDocs, tests % Test, openapiCirceYaml % Test)

lazy val openapiDocs2_13 = openapiDocs.jvm(scala2_13).dependsOn(enumeratum.jvm(scala2_13))
lazy val openapiDocs2_12 = openapiDocs.jvm(scala2_12).dependsOn(enumeratum.jvm(scala2_12))

lazy val asyncapiDocs: ProjectMatrix = (projectMatrix in file("docs/asyncapi-docs"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-asyncapi-docs",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % Versions.akkaStreams % Test,
      "com.softwaremill.sttp.shared" %% "akka" % Versions.sttpShared % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .dependsOn(asyncapiModel, core, apispecDocs, tests % Test, asyncapiCirceYaml % Test)

lazy val swaggerUi: ProjectMatrix = (projectMatrix in file("docs/swagger-ui"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-swagger-ui",
    libraryDependencies ++= Seq("org.webjars" % "swagger-ui" % Versions.swaggerUi)
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(core)

lazy val swaggerUiBundle: ProjectMatrix = (projectMatrix in file("docs/swagger-ui-bundle"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-swagger-ui-bundle",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-server" % Versions.http4s % Test,
      scalaTest.value % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(swaggerUi, openapiDocs, openapiCirceYaml, sttpClient % Test, http4sServer % Test)

lazy val redoc: ProjectMatrix = (projectMatrix in file("docs/redoc"))
  .settings(commonJvmSettings)
  .settings(name := "tapir-redoc")
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(core)

lazy val redocBundle: ProjectMatrix = (projectMatrix in file("docs/redoc-bundle"))
  .settings(commonJvmSettings)
  .settings(name := "tapir-redoc-bundle")
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(redoc, openapiDocs, openapiCirceYaml)

// server

lazy val serverTests: ProjectMatrix = (projectMatrix in file("server/tests"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-server-tests",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "httpclient-backend-fs2" % Versions.sttp
    )
  )
  .dependsOn(tests)
  .jvmPlatform(scalaVersions = scala2And3Versions)

lazy val akkaHttpServer: ProjectMatrix = (projectMatrix in file("server/akka-http-server"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-akka-http-server",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
      "com.typesafe.akka" %% "akka-stream" % Versions.akkaStreams,
      "com.softwaremill.sttp.shared" %% "akka" % Versions.sttpShared,
      "com.softwaremill.sttp.client3" %% "akka-http-backend" % Versions.sttp % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .dependsOn(core, serverTests % Test)

lazy val http4sServer: ProjectMatrix = (projectMatrix in file("server/http4s-server"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-http4s-server",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-server" % Versions.http4s,
      "com.softwaremill.sttp.shared" %% "fs2" % Versions.sttpShared,
      "org.http4s" %% "http4s-blaze-server" % Versions.http4s % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(core, cats, serverTests % Test)

lazy val sttpStubServer: ProjectMatrix = (projectMatrix in file("server/sttp-stub-server"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-sttp-stub-server"
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(core, serverTests % "test", sttpClient)

lazy val sttpMockServer: ProjectMatrix = (projectMatrix in file("server/sttp-mock-server"))
  .settings(commonJvmSettings)
  .settings(
    name := "sttp-mock-server",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %%% "core" % Versions.sttp,
      "io.circe" %% "circe-core" % Versions.circe,
      "io.circe" %% "circe-parser" % Versions.circe,
      "io.circe" %% "circe-generic" % Versions.circe,
      // test libs
      "io.circe" %% "circe-literal" % Versions.circe % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .dependsOn(core, serverTests % "test", sttpClient)

lazy val finatraServer: ProjectMatrix = (projectMatrix in file("server/finatra-server"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-finatra-server",
    libraryDependencies ++= Seq(
      "com.twitter" %% "finatra-http-server" % Versions.finatra,
      "org.apache.httpcomponents" % "httpmime" % "4.5.13",
      // Testing
      "com.twitter" %% "inject-server" % Versions.finatra % Test,
      "com.twitter" %% "inject-app" % Versions.finatra % Test,
      "com.twitter" %% "inject-core" % Versions.finatra % Test,
      "com.twitter" %% "inject-modules" % Versions.finatra % Test,
      "com.twitter" %% "finatra-http-server" % Versions.finatra % Test classifier "tests",
      "com.twitter" %% "inject-server" % Versions.finatra % Test classifier "tests",
      "com.twitter" %% "inject-app" % Versions.finatra % Test classifier "tests",
      "com.twitter" %% "inject-core" % Versions.finatra % Test classifier "tests",
      "com.twitter" %% "inject-modules" % Versions.finatra % Test classifier "tests"
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .dependsOn(core, serverTests % Test)

lazy val finatraServerCats: ProjectMatrix =
  (projectMatrix in file("server/finatra-server/finatra-server-cats"))
    .settings(commonJvmSettings)
    .settings(
      name := "tapir-finatra-server-cats",
      libraryDependencies ++= Seq("org.typelevel" %% "cats-effect" % Versions.catsEffect)
    )
    .jvmPlatform(scalaVersions = scala2Versions)
    .dependsOn(finatraServer % "compile->compile;test->test", serverTests % Test)

lazy val playServer: ProjectMatrix = (projectMatrix in file("server/play-server"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-play-server",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-server" % Versions.playServer,
      "com.typesafe.play" %% "play-akka-http-server" % Versions.playServer,
      "com.typesafe.play" %% "play" % Versions.playServer,
      "com.softwaremill.sttp.shared" %% "akka" % Versions.sttpShared
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .dependsOn(core, serverTests % Test)

lazy val nettyServer: ProjectMatrix = (projectMatrix in file("server/netty-server"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-netty-server",
    libraryDependencies ++= Seq(
      "io.netty" % "netty-all" % "4.1.73.Final",
      "com.softwaremill.sttp.shared" %% "fs2" % Versions.sttpShared % Optional
    ) ++ loggerDependencies,
    // needed because of https://github.com/coursier/coursier/issues/2016
    useCoursier := false
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(core, serverTests % Test)

lazy val vertxServer: ProjectMatrix = (projectMatrix in file("server/vertx"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-vertx-server",
    libraryDependencies ++= Seq(
      "io.vertx" % "vertx-web" % Versions.vertx,
      "com.softwaremill.sttp.shared" %% "fs2" % Versions.sttpShared % Optional,
      "com.softwaremill.sttp.shared" %% "zio" % Versions.sttpShared % Optional,
      "dev.zio" %% "zio-interop-cats" % Versions.zioInteropCats % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(core, serverTests % Test)

lazy val zioHttp4sServer: ProjectMatrix = (projectMatrix in file("server/zio-http4s-server"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-zio-http4s-server",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-interop-cats" % Versions.zioInteropCats,
      "org.http4s" %% "http4s-blaze-server" % Versions.http4s % Test
    )
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(zio, http4sServer, serverTests % Test)

lazy val zioHttpServer: ProjectMatrix = (projectMatrix in file("server/zio-http-server"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-zio-http-server",
    libraryDependencies ++= Seq("dev.zio" %% "zio-interop-cats" % Versions.zioInteropCats % Test, "io.d11" %% "zhttp" % "2.0.0-RC2")
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(zio, serverTests % Test)

// serverless

lazy val awsLambda: ProjectMatrix = (projectMatrix in file("serverless/aws/lambda"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-aws-lambda",
    libraryDependencies ++= loggerDependencies,
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "httpclient-backend-fs2" % Versions.sttp
    )
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .jsPlatform(scalaVersions = scala2Versions)
  .dependsOn(core, cats, circeJson, tests % "test")

// integration tests for lambda interpreter
// it's a separate project since it needs a fat jar with lambda code which cannot be build from tests sources
// runs sam local cmd line tool to start AWS Api Gateway with lambda proxy
lazy val awsLambdaTests: ProjectMatrix = (projectMatrix in file("serverless/aws/lambda-tests"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-aws-lambda-tests",
    libraryDependencies += "com.amazonaws" % "aws-lambda-java-runtime-interface-client" % Versions.awsLambdaInterface,
    assembly / assemblyJarName := "tapir-aws-lambda-tests.jar",
    assembly / test := {}, // no tests before building jar
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "io.netty.versions.properties")                    => MergeStrategy.first
      case PathList(ps @ _*) if ps.last contains "FlowAdapters"                    => MergeStrategy.first
      case _ @("scala/annotation/nowarn.class" | "scala/annotation/nowarn$.class") => MergeStrategy.first
      case x                                                                       => (assembly / assemblyMergeStrategy).value(x)
    },
    Test / test := {
      if (scalaVersion.value == scala2_13) { // only one test can run concurrently, as it starts a local sam instance
        (Test / test)
          .dependsOn(
            Def.sequential(
              (Compile / runMain).toTask(" sttp.tapir.serverless.aws.lambda.tests.LambdaSamTemplate"),
              assembly
            )
          )
          .value
      }
    },
    Test / testOptions ++= {
      val log = sLog.value
      // process uses template.yaml which is generated by `LambdaSamTemplate` called above
      lazy val sam = Process("sam local start-api --warm-containers EAGER").run()
      Seq(
        Tests.Setup(() => {
          val samReady = PollingUtils.poll(60.seconds, 1.second) {
            sam.isAlive() && PollingUtils.urlConnectionAvailable(new URL(s"http://127.0.0.1:3000/health"))
          }
          if (!samReady) {
            sam.destroy()
            val exit = sam.exitValue()
            log.error(s"failed to start sam local within 60 seconds (exit code: $exit")
          }
        }),
        Tests.Cleanup(() => {
          sam.destroy()
          val exit = sam.exitValue()
          log.info(s"stopped sam local (exit code: $exit")
        })
      )
    },
    Test / parallelExecution := false
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .dependsOn(core, cats, circeJson, awsLambda, awsSam, sttpStubServer, serverTests)

lazy val awsSam: ProjectMatrix = (projectMatrix in file("serverless/aws/sam"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-aws-sam",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-yaml" % Versions.circeYaml,
      "io.circe" %% "circe-generic" % Versions.circe
    )
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(core, tests % Test)

lazy val awsTerraform: ProjectMatrix = (projectMatrix in file("serverless/aws/terraform"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-aws-terraform",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-yaml" % Versions.circeYaml,
      "io.circe" %% "circe-generic" % Versions.circe,
      "io.circe" %% "circe-literal" % Versions.circe,
      "org.typelevel" %% "jawn-parser" % "1.3.2"
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .dependsOn(core, tests % Test)

lazy val awsExamples: ProjectMatrix = (projectMatrix in file("serverless/aws/examples"))
  .settings(commonSettings)
  .settings(
    name := "tapir-aws-examples",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %%% "cats" % Versions.sttp
    )
  )
  .jvmPlatform(
    scalaVersions = scala2Versions,
    settings = commonJvmSettings ++ Seq(
      assembly / assemblyJarName := "tapir-aws-examples.jar",
      assembly / assemblyMergeStrategy := {
        case PathList("META-INF", "io.netty.versions.properties")                    => MergeStrategy.first
        case PathList(ps @ _*) if ps.last contains "FlowAdapters"                    => MergeStrategy.first
        case _ @("scala/annotation/nowarn.class" | "scala/annotation/nowarn$.class") => MergeStrategy.first
        case x                                                                       => (assembly / assemblyMergeStrategy).value(x)
      },
      libraryDependencies += "com.amazonaws" % "aws-lambda-java-runtime-interface-client" % Versions.awsLambdaInterface
    )
  )
  .jsPlatform(
    scalaVersions = scala2Versions,
    settings = commonJsSettings ++ Seq(
      scalaJSUseMainModuleInitializer := false,
      scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
    )
  )
  .dependsOn(awsLambda)

lazy val awsExamples2_12 = awsExamples.jvm(scala2_12).dependsOn(awsSam.jvm(scala2_12), awsTerraform.jvm(scala2_12))
lazy val awsExamples2_13 = awsExamples.jvm(scala2_13).dependsOn(awsSam.jvm(scala2_13), awsTerraform.jvm(scala2_13))

// client

lazy val clientTests: ProjectMatrix = (projectMatrix in file("client/tests"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-client-tests",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl" % Versions.http4s,
      "org.http4s" %% "http4s-blaze-server" % Versions.http4s,
      "org.http4s" %% "http4s-circe" % Versions.http4s
    )
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .jsPlatform(
    scalaVersions = scala2And3Versions,
    settings = commonJsSettings
  )
  .dependsOn(tests)

lazy val http4sClient: ProjectMatrix = (projectMatrix in file("client/http4s-client"))
  .settings(clientTestServerSettings)
  .settings(commonSettings)
  .settings(
    name := "tapir-http4s-client",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-core" % Versions.http4s,
      "org.http4s" %% "http4s-blaze-client" % Versions.http4s % Test,
      "com.softwaremill.sttp.shared" %% "fs2" % Versions.sttpShared % Optional
    )
  )
  .jvmPlatform(scalaVersions = scala2And3Versions)
  .dependsOn(core, clientTests % Test)

lazy val sttpClient: ProjectMatrix = (projectMatrix in file("client/sttp-client"))
  .settings(clientTestServerSettings)
  .settings(
    name := "tapir-sttp-client",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %%% "core" % Versions.sttp
    )
  )
  .jvmPlatform(
    scalaVersions = scala2And3Versions,
    settings = commonJvmSettings ++ Seq(
      libraryDependencies ++= Seq(
        "com.softwaremill.sttp.client3" %% "httpclient-backend-fs2" % Versions.sttp % Test,
        "com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % Versions.sttp % Test,
        "com.softwaremill.sttp.shared" %% "fs2" % Versions.sttpShared % Optional,
        "com.softwaremill.sttp.shared" %% "zio1" % Versions.sttpShared % Optional
      ),
      libraryDependencies ++= {
        CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((3, _)) => Nil
          case _ =>
            Seq(
              "com.softwaremill.sttp.shared" %% "akka" % Versions.sttpShared % Optional,
              "com.softwaremill.sttp.client3" %% "akka-http-backend" % Versions.sttp % Test,
              "com.typesafe.akka" %% "akka-stream" % Versions.akkaStreams % Optional
            )
        }
      }
    )
  )
  .jsPlatform(
    scalaVersions = scala2And3Versions,
    settings = commonJsSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % Versions.jsScalaJavaTime % Test
      )
    )
  )
  .dependsOn(core, clientTests % Test)

lazy val playClient: ProjectMatrix = (projectMatrix in file("client/play-client"))
  .settings(clientTestServerSettings)
  .settings(commonSettings)
  .settings(
    name := "tapir-play-client",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-ahc-ws-standalone" % Versions.playClient,
      "com.softwaremill.sttp.shared" %% "akka" % Versions.sttpShared % Optional,
      "com.typesafe.akka" %% "akka-stream" % Versions.akkaStreams % Optional
    )
  )
  .jvmPlatform(scalaVersions = scala2Versions)
  .dependsOn(core, clientTests % Test)

import scala.collection.JavaConverters._

lazy val openapiCodegen = (projectMatrix in file("sbt/sbt-openapi-codegen"))
  .enablePlugins(SbtPlugin)
  .settings(commonSettings)
  .jvmPlatform(scalaVersions = codegenScalaVersions)
  .settings(
    name := "sbt-openapi-codegen",
    organization := "com.softwaremill.sttp.tapir",
    sbtPlugin := true,
    scriptedLaunchOpts += ("-Dplugin.version=" + version.value),
    scriptedLaunchOpts ++= java.lang.management.ManagementFactory.getRuntimeMXBean.getInputArguments.asScala
      .filter(a => Seq("-Xmx", "-Xms", "-XX", "-Dfile").exists(a.startsWith)),
    scriptedBufferLog := false,
    sbtTestDirectory := sourceDirectory.value / "sbt-test",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % Versions.circe,
      "io.circe" %% "circe-generic" % Versions.circe,
      "io.circe" %% "circe-yaml" % Versions.circeYaml,
      scalaTest.value % Test,
      scalaCheck.value % Test,
      scalaTestPlusScalaCheck.value % Test,
      "com.47deg" %% "scalacheck-toolbox-datetime" % "0.6.0" % Test,
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % Test
    )
  )
  .dependsOn(core % Test, circeJson % Test)

// other

lazy val examples: ProjectMatrix = (projectMatrix in file("examples"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-examples",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-interop-cats" % Versions.zioInteropCats,
      "org.typelevel" %% "cats-effect" % Versions.catsEffect,
      "org.http4s" %% "http4s-dsl" % Versions.http4s,
      "org.http4s" %% "http4s-circe" % Versions.http4s,
      "org.http4s" %% "http4s-blaze-server" % Versions.http4s,
      "com.softwaremill.sttp.client3" %% "akka-http-backend" % Versions.sttp,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2" % Versions.sttp,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % Versions.sttp,
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % Versions.sttp,
      "com.pauldijou" %% "jwt-circe" % Versions.jwtScala
    ),
    libraryDependencies ++= loggerDependencies,
    publishArtifact := false
  )
  .jvmPlatform(scalaVersions = examplesScalaVersions)
  .dependsOn(
    akkaHttpServer,
    http4sServer,
    http4sClient,
    sttpClient,
    openapiCirceYaml,
    openapiDocs,
    asyncapiCirceYaml,
    asyncapiDocs,
    circeJson,
    swaggerUiBundle,
    redocBundle,
    zioHttp4sServer,
    zioHttpServer,
    nettyServer,
    sttpStubServer,
    playJson,
    prometheusMetrics,
    sttpMockServer,
    zioJson
  )

//TODO this should be invoked by compilation process, see #https://github.com/scalameta/mdoc/issues/355
val compileDocumentation: TaskKey[Unit] = taskKey[Unit]("Compiles documentation throwing away its output")
compileDocumentation := {
  (documentation.jvm(documentationScalaVersion) / mdoc).toTask(" --out target/tapir-doc").value
}

lazy val documentation: ProjectMatrix = (projectMatrix in file("generated-doc")) // important: it must not be doc/
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(macros)
  .settings(
    mdocIn := file("doc"),
    moduleName := "tapir-doc",
    mdocVariables := Map(
      "VERSION" -> version.value,
      "PLAY_HTTP_SERVER_VERSION" -> Versions.playServer,
      "JSON4S_VERSION" -> Versions.json4s
    ),
    mdocOut := file("generated-doc/out"),
    mdocExtraArguments := Seq("--clean-target"),
    publishArtifact := false,
    name := "doc",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-netty-server" % Versions.playServer,
      "org.http4s" %% "http4s-blaze-server" % Versions.http4s
    ),
    // needed because of https://github.com/coursier/coursier/issues/2016
    useCoursier := false
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
    asyncapiDocs,
    asyncapiCirceYaml,
    openapiDocs,
    openapiCirceYaml,
    json4s,
    playJson,
    playServer,
    sprayJson,
    http4sClient,
    sttpClient,
    playClient,
    sttpStubServer,
    tethysJson,
    uPickleJson,
    vertxServer,
    zio,
    zioHttp4sServer,
    zioHttpServer,
    derevo,
    zioJson,
    prometheusMetrics,
    opentelemetryMetrics,
    sttpMockServer,
    nettyServer,
    swaggerUiBundle
  )
