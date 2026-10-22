lazy val root = (project in file("."))
  .enablePlugins(OpenapiCodegenPlugin)
  .settings(
    scalaVersion := "2.13.18",
    version := "0.1",
    openapiGenerateEndpointTypes := true
  )

val tapirVersion = "1.13.31"

libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % tapirVersion,
  "com.beachape" %% "enumeratum-circe" % "1.9.8",
  "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.11.0"
)

import scala.io.Source
import scala.util.Using

TaskKey[Unit]("check") := {
  def check(generatedFileName: String, expectedFileName: String) = {
    val generatedCode =
      Using(Source.fromFile(s"${sourceManaged.value}/main/sbt-openapi-codegen/$generatedFileName"))(_.getLines.mkString("\n")).get
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
  ).foreach { case (generated, expected) => check(generated, expected) }
  ()
}