lazy val root = (project in file("."))
  .enablePlugins(OpenapiCodegenPlugin)
  .settings(
    scalaVersion := "2.13.13",
    version := "0.1",
    openapiJsonSerdeLib := "zio"
  )

libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-json-zio" % "1.10.0",
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.10.0",
  "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.8.0",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % "1.10.0" % Test
)

import scala.io.Source

TaskKey[Unit]("check") := {
  def check(generatedFileName: String, expectedFileName: String) = {
    val generatedCode =
      Source.fromFile(s"target/scala-2.13/src_managed/main/sbt-openapi-codegen/$generatedFileName").getLines.mkString("\n")
    val expectedCode = Source.fromFile(expectedFileName).getLines.mkString("\n")
    val generatedTrimmed =
      generatedCode.linesIterator.zipWithIndex.filterNot(_._1.forall(_.isWhitespace)).map { case (a, i) => a.trim -> i }.toSeq
    val expectedTrimmed = expectedCode.linesIterator.filterNot(_.forall(_.isWhitespace)).map(_.trim).toSeq
    if (generatedTrimmed.size != expectedTrimmed.size)
      sys.error(s"expected ${expectedTrimmed.size} non-empty lines, found ${generatedTrimmed.size}")
    generatedTrimmed.zip(expectedTrimmed).foreach { case ((a, i), b) =>
      if (a != b) sys.error(s"Generated code in file $generatedCode did not match (expected '$b' on line $i, found '$a')")
    }
  }
  Seq(
    "TapirGeneratedEndpoints.scala" -> "Expected.scala.txt",
    "TapirGeneratedEndpointsJsonSerdes.scala" -> "ExpectedJsonSerdes.scala.txt",
    "TapirGeneratedEndpointsSchemas.scala" -> "ExpectedSchemas.scala.txt"
  ).foreach { case (generated, expected) => check(generated, expected) }
  ()
}
