lazy val root = (project in file("."))
  .enablePlugins(OpenapiCodegenPlugin)
  .settings(
    scalaVersion := "2.13.16",
    version := "0.1",
    openapiJsonSerdeLib := "jsoniter",
    openapiStreamingImplementation := "pekko",
    openapiGenerateEndpointTypes := true
  )

val catsXmlVersion = "0.0.20"
val jsoniterScalaVersion = "2.34.1"
val tapirVersion = "1.11.18"
libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-jsoniter-scala" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-pekko-http-server" % tapirVersion,
  "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.11.9",
  "com.beachape" %% "enumeratum" % "1.7.6",
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % jsoniterScalaVersion,
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % jsoniterScalaVersion % "compile-internal",
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-circe" % jsoniterScalaVersion,
  "com.github.geirolz" %% "cats-xml" % catsXmlVersion,
  "com.github.geirolz" %% "cats-xml-generic" % catsXmlVersion,
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % tapirVersion % Test
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
  compare("endpoints", "target/scala-2.13/src_managed/main/sbt-openapi-codegen/TapirGeneratedEndpoints.scala", "Expected.scala.txt")
  compare(
    "xml",
    "target/scala-2.13/src_managed/main/sbt-openapi-codegen/TapirGeneratedEndpointsXmlSerdes.scala",
    "ExpectedXmlSerdes.scala.txt"
  )
  ()
}
