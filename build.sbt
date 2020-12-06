import java.io.File
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

import com.softwaremill.SbtSoftwareMillBrowserTestJS._
import sbt.Reference.display
import sbtrelease.ReleaseStateTransformations.{checkSnapshotDependencies, commitReleaseVersion, inquireVersions, publishArtifacts, pushChanges, runClean, runTest, setReleaseVersion, tagRelease}
import sbt.internal.ProjectMatrix

val scala2_12 = "2.12.12"
val scala2_13 = "2.13.4"

val allScalaVersions = List(scala2_12, scala2_13)
val scala2_12Versions = List(scala2_12)
val documentationScalaVersion = scala2_12 // Documentation depends on finatraServer, which is 2.12 only

scalaVersion := scala2_12

lazy val testServerPort = settingKey[Int]("Port to run the http test server on")
lazy val startTestServer = taskKey[Unit]("Start a http server used by tests")

concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

excludeLintKeys in Global ++= Set(ideSkipProject)

val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.sttp.tapir",
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
  ideSkipProject := (scalaVersion.value == scala2_13) || thisProjectRef.value.project.contains("JS"),
  // slow down for CI
  Test / parallelExecution := false
)

val commonJvmSettings: Seq[Def.Setting[_]] = commonSettings

lazy val downloadGeckoDriver: TaskKey[Unit] = taskKey[Unit](
  "Download gecko driver"
)

val downloadGeckoDriverSettings: Seq[Def.Setting[Task[Unit]]] = Seq(
  Global / downloadGeckoDriver := {
    if (
      java.nio.file.Files.notExists(new File("target", "geckodriver").toPath)
    ) {
      val version = "v0.28.0"
      println(s"geckodriver binary file not found")
      import sys.process._
      val osName = sys.props("os.name")
      val isMac = osName.toLowerCase.contains("mac")
      val isWin = osName.toLowerCase.contains("win")
      val platformDependentName = if (isMac) {
        "macos.tar.gz"
      } else if (isWin) {
        "win64.zip"
      } else {
        "linux64.tar.gz"
      }
      println(s"Downloading gecko driver version $version for $osName")
      val geckoDriverUrl = s"https://github.com/mozilla/geckodriver/releases/download/$version/geckodriver-$version-$platformDependentName"
      if (!isWin) {
        url(geckoDriverUrl) #> file("target/geckodriver.tar.gz") #&&
          "tar -xz -C target -f target/geckodriver.tar.gz" #&&
          "rm target/geckodriver.tar.gz" !
      } else {
        IO.unzipURL(new URL(geckoDriverUrl), new File("target"))
      }
      IO.chmod("rwxrwxr-x", new File("target", "geckodriver"))
    } else {
      println("Detected geckodriver binary file, skipping downloading.")
    }
  }
)

// run JS tests inside Chrome, due to jsdom not supporting fetch and to avoid having to install node
val commonJsSettings = commonSettings ++ downloadGeckoDriverSettings ++ Seq(
  // https://github.com/scalaz/scalaz/pull/1734#issuecomment-385627061
  scalaJSLinkerConfig ~= {
    _.withBatchMode(System.getenv("GITHUB_ACTIONS") == "true")
  },
  jsEnv in Test := {
    val debugging = false // set to true to help debugging
    System.setProperty("webdriver.gecko.driver", "target/geckodriver")
    new org.scalajs.jsenv.selenium.SeleniumJSEnv(
      {
        val options = new org.openqa.selenium.firefox.FirefoxOptions()
        val args = (if (debugging) Seq("--devtools") else Seq("-headless"))
        options.addArguments(args: _*)
        options
      },
      org.scalajs.jsenv.selenium.SeleniumJSEnv
        .Config()
        .withKeepAlive(debugging)
    )
  },
  test in Test := (test in Test)
    .dependsOn(downloadGeckoDriver)
    .value
)

def dependenciesFor(version: String)(deps: (Option[(Long, Long)] => ModuleID)*): Seq[ModuleID] =
  deps.map(_.apply(CrossVersion.partialVersion(version)))

val scalaTest = Def.setting("org.scalatest" %%% "scalatest" % Versions.scalaTest)
val scalaCheck = Def.setting("org.scalacheck" %%% "scalacheck" % Versions.scalaCheck)
val scalaTestPlusScalaCheck = Def.setting("org.scalatestplus" %%% "scalacheck-1-14" % Versions.scalaTestPlusScalaCheck)

lazy val loggerDependencies = Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "ch.qos.logback" % "logback-core" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
)

lazy val allAggregates = core.projectRefs ++
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
  playClient.projectRefs ++
  tests.projectRefs ++
  examples.projectRefs ++
  playground.projectRefs ++
  documentation.projectRefs ++
  openapiCodegen.projectRefs

val testJVM = taskKey[Unit]("Test JVM projects")
val testJS = taskKey[Unit]("Test JS projects")

def filterProject(p: String => Boolean) =
  ScopeFilter(inProjects(allAggregates.filter(pr => p(display(pr.project))): _*))

lazy val rootProject = (project in file("."))
  .settings(commonSettings)
  .settings(mimaPreviousArtifacts := Set.empty)
  .settings(
    publishArtifact := false,
    name := "tapir",
    testJVM := (test in Test).all(filterProject(p => !p.contains("JS") && !p.contains("Native"))).value,
    testJS := (test in Test).all(filterProject(_.contains("JS"))).value
  )
  .aggregate(allAggregates: _*)

// start a test server before running tests of a backend; this is required both for JS tests run inside a
// nodejs/browser environment, as well as for JVM tests where akka-http isn't available (e.g. dotty). To simplify
// things, we always start the test server.
val testServerSettings = Seq(
  test in Test := (test in Test)
    .dependsOn(startTestServer in testServer2_13)
    .value,
  testOnly in Test := (testOnly in Test)
    .dependsOn(startTestServer in testServer2_13)
    .evaluated,
  testOptions in Test += Tests.Setup(() => {
    val port = (testServerPort in testServer2_13).value
    PollingUtils.waitUntilServerAvailable(new URL(s"http://localhost:$port"))
  })
)

lazy val testServer = (projectMatrix in file("client/testserver"))
  .settings(commonJvmSettings)
  .settings(
    name := "testing-server",
    skip in publish := true,
    libraryDependencies ++= loggerDependencies ++ Seq(
      "org.http4s" %% "http4s-dsl" % Versions.http4s,
      "org.http4s" %% "http4s-blaze-server" % Versions.http4s,
      "org.http4s" %% "http4s-circe" % Versions.http4s
    ),
    // the test server needs to be started before running any client tests
    mainClass in reStart := Some("sttp.tapir.client.tests.HttpServer"),
    reStartArgs in reStart := Seq(s"${(testServerPort in Test).value}"),
    fullClasspath in reStart := (fullClasspath in Test).value,
    testServerPort := 51823,
    startTestServer := reStart.toTask("").value
  )
  .jvmPlatform(scalaVersions = List(scala2_13))

lazy val testServer2_13 = testServer.jvm(scala2_13)

// core

lazy val core: ProjectMatrix = (projectMatrix in file("core"))
  .settings(commonSettings)
  .settings(
    name := "tapir-core",
    libraryDependencies ++= Seq(
      "com.propensive" %%% "magnolia" % "0.17.0",
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "com.softwaremill.sttp.model" %%% "core" % Versions.sttpModel,
      "com.softwaremill.sttp.shared" %%% "core" % Versions.sttpShared,
      "com.softwaremill.sttp.shared" %%% "ws" % Versions.sttpShared,
      scalaTest.value % Test,
      scalaCheck.value % Test,
      scalaTestPlusScalaCheck.value % Test,
      "com.47deg" %%% "scalacheck-toolbox-datetime" % "0.4.0" % Test
    ),
    unmanagedSourceDirectories in Compile += {
      val sourceDir = (sourceDirectory in Compile).value
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n >= 13 => sourceDir / "scala-2.13+"
        case _                       => sourceDir / "scala-2.13-"
      }
    }
  )
  .jvmPlatform(
    scalaVersions = allScalaVersions,
    libraryDependencies ++= Seq("org.scala-lang" % "scala-compiler" % scalaVersion.value % Test)
  )
  .jsPlatform(
    scalaVersions = allScalaVersions,
    settings = commonJsSettings ++ Seq(
      libraryDependencies ++= Seq(
        "org.scala-js" %%% "scalajs-dom" % "1.1.0",
        "io.github.cquiroz" %%% "scala-java-time" % "2.0.0" % Test,
        "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.0.0" % Test
      )
    )
  )
  .enablePlugins(spray.boilerplate.BoilerplatePlugin)

lazy val tests: ProjectMatrix = (projectMatrix in file("tests"))
  .settings(commonSettings)
  .settings(
    name := "tapir-tests",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-generic" % Versions.circe,
      "com.beachape" %%% "enumeratum-circe" % Versions.enumeratum,
      "com.softwaremill.common" %%% "tagging" % "2.2.1",
      scalaTest.value,
      "com.softwaremill.macwire" %% "macros" % "2.3.7" % "provided",
      "org.typelevel" %%% "cats-effect" % Versions.catsEffect
    ),
    libraryDependencies ++= loggerDependencies
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .jsPlatform(
    scalaVersions = allScalaVersions,
    settings = commonJsSettings
  )
  .dependsOn(core, circeJson, enumeratum, cats)

// integrations

lazy val cats: ProjectMatrix = (projectMatrix in file("integrations/cats"))
  .settings(commonSettings)
  .settings(
    name := "tapir-cats",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % "2.3.0",
      scalaTest.value % Test,
      scalaCheck.value % Test,
      scalaTestPlusScalaCheck.value % Test,
      "org.typelevel" %%% "discipline-scalatest" % "2.1.0" % Test,
      "org.typelevel" %%% "cats-laws" % "2.3.0" % Test
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .jsPlatform(
    scalaVersions = allScalaVersions,
    settings = commonJsSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % "2.0.0" % Test
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
  .jvmPlatform(scalaVersions = allScalaVersions)
  .jsPlatform(
    scalaVersions = allScalaVersions,
    settings = commonJsSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % "2.0.0" % Test
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
  .jvmPlatform(scalaVersions = allScalaVersions)
  .jsPlatform(
    scalaVersions = allScalaVersions,
    settings = commonJsSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % "2.0.0" % Test
      )
    )
  )
  .dependsOn(core, circeJson % Test)

lazy val zio: ProjectMatrix = (projectMatrix in file("integrations/zio"))
  .settings(commonSettings)
  .settings(
    name := "tapir-zio",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % Versions.zio,
      "dev.zio" %% "zio-streams" % Versions.zio,
      scalaTest.value % Test,
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
      "io.circe" %%% "circe-core" % Versions.circe,
      "io.circe" %%% "circe-parser" % Versions.circe
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .jsPlatform(
    scalaVersions = allScalaVersions,
    settings = commonJsSettings
  )
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
  .jvmPlatform(scalaVersions = allScalaVersions)
  .jsPlatform(
    scalaVersions = allScalaVersions,
    settings = commonJsSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % "2.0.0" % Test
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
  .jvmPlatform(scalaVersions = allScalaVersions)
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
  .jvmPlatform(scalaVersions = allScalaVersions)
  .jsPlatform(
    scalaVersions = allScalaVersions,
    settings = commonJsSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % "2.0.0" % Test
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
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-core" % "2.6.2",
      scalaTest.value % Test
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .jsPlatform(
    scalaVersions = allScalaVersions,
    settings = commonJsSettings
  )
  .dependsOn(core)

// apispec

lazy val apispecModel: ProjectMatrix = (projectMatrix in file("apispec/apispec-model"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-apispec-model"
  )
  .settings(libraryDependencies += scalaTest.value % Test)
  .jvmPlatform(scalaVersions = allScalaVersions)

// openapi

lazy val openapiModel: ProjectMatrix = (projectMatrix in file("apispec/openapi-model"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-openapi-model"
  )
  .settings(libraryDependencies += scalaTest.value % Test)
  .jvmPlatform(scalaVersions = allScalaVersions)
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
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(openapiModel)

lazy val openapiCirceYaml: ProjectMatrix = (projectMatrix in file("apispec/openapi-circe-yaml"))
  .settings(commonJvmSettings)
  .settings(
    libraryDependencies += "io.circe" %% "circe-yaml" % Versions.circeYaml,
    name := "tapir-openapi-circe-yaml"
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(openapiCirce)

// asyncapi

lazy val asyncapiModel: ProjectMatrix = (projectMatrix in file("apispec/asyncapi-model"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-asyncapi-model"
  )
  .settings(libraryDependencies += scalaTest.value % Test)
  .jvmPlatform(scalaVersions = allScalaVersions)
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
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(asyncapiModel)

lazy val asyncapiCirceYaml: ProjectMatrix = (projectMatrix in file("apispec/asyncapi-circe-yaml"))
  .settings(commonJvmSettings)
  .settings(
    libraryDependencies += "io.circe" %% "circe-yaml" % Versions.circeYaml,
    name := "tapir-asyncapi-circe-yaml"
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(asyncapiCirce)

// docs

lazy val apispecDocs: ProjectMatrix = (projectMatrix in file("docs/apispec-docs"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-apispec-docs"
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core, tests % Test, apispecModel)

lazy val openapiDocs: ProjectMatrix = (projectMatrix in file("docs/openapi-docs"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-openapi-docs"
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(openapiModel, core, apispecDocs, tests % Test, openapiCirceYaml % Test)

lazy val asyncapiDocs: ProjectMatrix = (projectMatrix in file("docs/asyncapi-docs"))
  .settings(commonJvmSettings)
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
  .settings(commonJvmSettings)
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
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-redoc-akka-http",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp,
      "com.typesafe.akka" %% "akka-stream" % Versions.akkaStreams
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)

lazy val swaggerUiHttp4s: ProjectMatrix = (projectMatrix in file("docs/swagger-ui-http4s"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-swagger-ui-http4s",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl" % Versions.http4s,
      "org.webjars" % "swagger-ui" % Versions.swaggerUi
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)

lazy val redocHttp4s: ProjectMatrix = (projectMatrix in file("docs/redoc-http4s"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-redoc-http4s",
    libraryDependencies += "org.http4s" %% "http4s-dsl" % Versions.http4s
  )
  .jvmPlatform(scalaVersions = allScalaVersions)

lazy val swaggerUiFinatra: ProjectMatrix = (projectMatrix in file("docs/swagger-ui-finatra"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-swagger-ui-finatra",
    libraryDependencies ++= Seq(
      "com.twitter" %% "finatra-http" % Versions.finatra,
      "org.webjars" % "swagger-ui" % Versions.swaggerUi
    )
  )
  .jvmPlatform(scalaVersions = scala2_12Versions)

lazy val swaggerUiPlay: ProjectMatrix = (projectMatrix in file("docs/swagger-ui-play"))
  .settings(commonJvmSettings)
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
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-redoc-play",
    libraryDependencies += "com.typesafe.play" %% "play" % Versions.playServer
  )
  .jvmPlatform(scalaVersions = allScalaVersions)

// server

lazy val serverTests: ProjectMatrix = (projectMatrix in file("server/tests"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-server-tests",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2" % Versions.sttp
    )
  )
  .dependsOn(tests)
  .jvmPlatform(scalaVersions = allScalaVersions)

lazy val akkaHttpServer: ProjectMatrix = (projectMatrix in file("server/akka-http-server"))
  .settings(commonJvmSettings)
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
  .settings(commonJvmSettings)
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
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-sttp-stub-server"
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core, serverTests % "test", sttpClient)

lazy val finatraServer: ProjectMatrix = (projectMatrix in file("server/finatra-server"))
  .settings(commonJvmSettings)
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
    .settings(commonJvmSettings)
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
  .settings(commonJvmSettings)
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
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-vertx-server",
    libraryDependencies ++= Seq(
      "io.vertx" %% "vertx-web-scala" % Versions.vertx
    )
  )
  .jvmPlatform(scalaVersions = scala2_12Versions)
  .dependsOn(core, serverTests % Test)

lazy val zioServer: ProjectMatrix = (projectMatrix in file("server/zio-http4-server"))
  .settings(commonJvmSettings)
  .settings(
    name := "tapir-zio-http4s-server",
    libraryDependencies += "dev.zio" %% "zio-interop-cats" % Versions.zioInteropCats
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(zio, http4sServer, serverTests % Test)

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
  .jvmPlatform(scalaVersions = allScalaVersions)
  .jsPlatform(
    scalaVersions = allScalaVersions,
    settings = commonJsSettings
  )
  .dependsOn(tests)

lazy val sttpClient: ProjectMatrix = (projectMatrix in file("client/sttp-client"))
  .settings(testServerSettings)
  .settings(
    name := "tapir-sttp-client",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client3" %%% "core" % Versions.sttp,
    )
  )
  .jvmPlatform(
    scalaVersions = allScalaVersions,
    settings = commonJvmSettings ++ Seq(
      libraryDependencies ++=  loggerDependencies ++ Seq(
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2" % Versions.sttp % Test,
      "com.softwaremill.sttp.shared" %% "fs2" % Versions.sttpShared % Optional,
      "com.softwaremill.sttp.shared" %% "akka" % Versions.sttpShared % Optional,
      "com.typesafe.akka" %% "akka-stream" % Versions.akkaStreams % Optional
    )
    )
  )
  .jsPlatform(
    scalaVersions = allScalaVersions,
    settings = commonJsSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.github.cquiroz" %%% "scala-java-time" % "2.0.0" % Test
      )
    )
  )
  .dependsOn(core, clientTests % Test)

lazy val playClient: ProjectMatrix = (projectMatrix in file("client/play-client"))
  .settings(commonSettings)
  .settings(
    name := "tapir-play-client",
    libraryDependencies ++= Seq(
      "com.typesafe.play" %% "play-ahc-ws-standalone" % Versions.playClient,
      "com.softwaremill.sttp.shared" %% "akka" % Versions.sttpShared % Optional,
      "com.typesafe.akka" %% "akka-stream" % Versions.akkaStreams % Optional
    )
  )
  .jvmPlatform(scalaVersions = allScalaVersions)
  .dependsOn(core, clientTests % Test)

import scala.collection.JavaConverters._
lazy val openapiCodegen = (projectMatrix in file("sbt/sbt-openapi-codegen"))
  .enablePlugins(SbtPlugin)
  .settings(commonSettings)
  .jvmPlatform(scalaVersions = scala2_12Versions)
  .settings(
    name := "sbt-openapi-codegen",
    organization := "com.softwaremill.sttp.tapir",
    version := "0.1-SNAPSHOT",
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
      "com.47deg" %% "scalacheck-toolbox-datetime" % "0.4.0" % Test,
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
  .settings(commonJvmSettings)
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
    asyncapiDocs,
    asyncapiCirceYaml,
    openapiDocs,
    openapiCirceYaml,
    playJson,
    playServer,
    sprayJson,
    sttpClient,
    playClient,
    sttpStubServer,
    swaggerUiAkka,
    tethysJson,
    uPickleJson,
    vertxServer,
    zio,
    zioServer
  )
