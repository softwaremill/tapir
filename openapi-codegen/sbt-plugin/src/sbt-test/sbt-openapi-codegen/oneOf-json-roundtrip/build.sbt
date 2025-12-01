lazy val root = (project in file("."))
  .enablePlugins(OpenapiCodegenPlugin)
  .settings(
    scalaVersion := "2.13.16",
    version := "0.1",
    openapiStreamingImplementation := "pekko",
    openapiGenerateEndpointTypes := true
  )

val tapirVersion = "1.11.20"
libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-pekko-http-server" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-sttp-client" % tapirVersion,
  "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.11.10",
  "com.softwaremill.sttp.client3" %% "http4s-backend" % "3.11.0",
  "io.circe" %% "circe-generic" % "0.14.15",
  "com.beachape" %% "enumeratum" % "1.9.1",
  "com.beachape" %% "enumeratum-circe" % "1.9.1",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % "1.11.16" % Test
)

import scala.io.Source
import scala.util.Using

TaskKey[Unit]("check") := {
  def check(generatedFileName: String, expectedFileName: String) = {
    val generatedCode =
      Using(Source.fromFile(s"target/scala-2.13/src_managed/main/sbt-openapi-codegen/$generatedFileName"))(_.getLines.mkString("\n")).get
    val expectedCode = Using(Source.fromFile(expectedFileName))(_.getLines.mkString("\n")).get
    val generatedTrimmed =
      generatedCode.linesIterator.zipWithIndex.filterNot(_._1.isBlank).map { case (a, i) => a.trim -> i }.toSeq
    val expectedTrimmed = expectedCode.linesIterator.filterNot(_.isBlank).map(_.trim).toSeq
    generatedTrimmed.zip(expectedTrimmed).foreach { case ((a, i), b) =>
      if (a != b) sys.error(s"Generated code in file $generatedCode did not match (expected '$b' on line $i, found '$a')")
    }
    if (generatedTrimmed.size != expectedTrimmed.size)
      sys.error(s"expected ${expectedTrimmed.size} non-empty lines in ${generatedFileName}, found ${generatedTrimmed.size}")
  }
  Seq(
    "TapirGeneratedEndpoints.scala" -> "Expected.scala.txt",
    "TapirGeneratedEndpointsJsonSerdes.scala" -> "ExpectedJsonSerdes.scala.txt",
    "TapirGeneratedEndpointsSchemas.scala" -> "ExpectedSchemas.scala.txt",
    "TapirGeneratedEndpointsValidators.scala" -> "ExpectedValidators.scala.txt"
  ).foreach { case (generated, expected) => check(generated, expected) }
  ()
}
