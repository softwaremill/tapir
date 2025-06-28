lazy val root = (project in file("."))
  .enablePlugins(OpenapiCodegenPlugin)
  .settings(
    scalaVersion := "3.3.3",
    version := "0.1",
    openapiJsonSerdeLib := "jsoniter",
    openapiXmlSerdeLib := "none",
    openapiStreamingImplementation := "pekko",
    openapiUseCustomJsoniterSerdes := false,
    openapiGenerateEndpointTypes := true
  )

val tapirVersion = "1.11.16"
libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-jsoniter-scala" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-pekko-http-server" % tapirVersion,
  "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.11.9",
  "com.beachape" %% "enumeratum" % "1.9.0",
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "2.36.6",
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.36.6" % "compile-internal",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % "1.10.0" % Test,
  "com.softwaremill.sttp.client3" %% "http4s-backend" % "3.11.0" % Test
)

import sttp.tapir.sbt.OpenapiCodegenPlugin.autoImport.{openapiJsonSerdeLib, openapiUseHeadTagForObjectName}

import scala.io.Source
import scala.util.Using

def compare(name: String, genFn: String, expFn: String) = {
  val generatedCode =
    Using(Source.fromFile(genFn))(_.getLines.mkString("\n")).get
  val expected = Using(Source.fromFile(expFn))(_.getLines.mkString("\n")).get
  val generatedTrimmed =
    generatedCode.linesIterator.zipWithIndex.filterNot(_._1.isBlank).map { case (a, i) => a.trim -> i }.toSeq
  val expectedTrimmed = expected.linesIterator.filterNot(_.isBlank).map(_.trim).toSeq
  generatedTrimmed.zip(expectedTrimmed).foreach { case ((a, i), b) =>
    if (a != b) sys.error(s"Generated code did not match for $name (expected '$b' on line $i, found '$a')")
  }
  if (generatedTrimmed.size != expectedTrimmed.size)
    sys.error(s"For $name expected ${expectedTrimmed.size} non-empty lines, found ${generatedTrimmed.size}")

}

TaskKey[Unit]("check") := {
  compare("endpoints", "target/scala-3.3.3/src_managed/main/sbt-openapi-codegen/TapirGeneratedEndpoints.scala", "Expected.scala.txt")
  compare(
    "json",
    "target/scala-3.3.3/src_managed/main/sbt-openapi-codegen/TapirGeneratedEndpointsJsonSerdes.scala",
    "ExpectedJsonSerdes.scala.txt"
  )
  compare(
    "schemas",
    "target/scala-3.3.3/src_managed/main/sbt-openapi-codegen/TapirGeneratedEndpointsSchemas.scala",
    "ExpectedSchemas.scala.txt"
  )
  ()
}
