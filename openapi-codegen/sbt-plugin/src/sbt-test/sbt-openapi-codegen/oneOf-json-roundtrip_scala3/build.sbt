lazy val root = (project in file("."))
  .enablePlugins(OpenapiCodegenPlugin)
  .settings(
    scalaVersion := "3.3.1",
    version := "0.1"
  )

libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.11.16",
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.11.16",
  "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.11.10",
  "io.circe" %% "circe-generic" % "0.14.14",
  "org.latestbit" %% "circe-tagged-adt-codec" % "0.11.0",
  "io.github.bishabosha" %% "enum-extensions" % "0.1.1",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % "1.11.16" % Test
)
openapiGenerateEndpointTypes := true

import scala.io.Source
import scala.util.Using

TaskKey[Unit]("check") := {
  val generatedCode =
    Using(Source.fromFile("target/scala-3.3.1/src_managed/main/sbt-openapi-codegen/TapirGeneratedEndpoints.scala"))(
      _.getLines.mkString("\n")
    ).get
  val expected = Using(Source.fromFile("Expected.scala.txt"))(_.getLines.mkString("\n")).get
  val generatedTrimmed =
    generatedCode.linesIterator.zipWithIndex.filterNot(_._1.isBlank).map { case (a, i) => a.trim -> i }.toSeq
  val expectedTrimmed = expected.linesIterator.filterNot(_.isBlank).map(_.trim).toSeq
  generatedTrimmed.zip(expectedTrimmed).foreach { case ((a, i), b) =>
    if (a != b) sys.error(s"Generated code did not match (expected '$b' on line $i, found '$a')")
  }
  if (generatedTrimmed.size != expectedTrimmed.size)
    sys.error(s"expected ${expectedTrimmed.size} non-empty lines, found ${generatedTrimmed.size}")
  ()
}
