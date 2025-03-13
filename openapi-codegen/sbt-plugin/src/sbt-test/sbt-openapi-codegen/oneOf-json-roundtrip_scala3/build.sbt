lazy val root = (project in file("."))
  .enablePlugins(OpenapiCodegenPlugin)
  .settings(
    scalaVersion := "3.3.1",
    version := "0.1"
  )

val catsXmlVersion = "0.0.20+8-506b0e7d+20250313-1350-SNAPSHOT"
libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.11.16",
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.11.16",
  "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.11.7",
  "io.circe" %% "circe-generic" % "0.14.12",
  "org.latestbit" %% "circe-tagged-adt-codec" % "0.11.0",
  "io.github.bishabosha" %% "enum-extensions" % "0.1.1",
  "com.github.geirolz" %% "cats-xml" % catsXmlVersion,
  "com.github.geirolz" %% "cats-xml-generic" % catsXmlVersion,
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % "1.11.16" % Test
)
openapiGenerateEndpointTypes := true

import scala.io.Source

def compare(name: String, genFn: String, expFn: String) = {
  val generatedCode =
    Source.fromFile(genFn).getLines.mkString("\n")
  val expected = Source.fromFile(expFn).getLines.mkString("\n")
  val generatedTrimmed =
    generatedCode.linesIterator.zipWithIndex.filterNot(_._1.forall(_.isWhitespace)).map { case (a, i) => a.trim -> i }.toSeq
  val expectedTrimmed = expected.linesIterator.filterNot(_.forall(_.isWhitespace)).map(_.trim).toSeq
  if (generatedTrimmed.size != expectedTrimmed.size)
    sys.error(s"For $name expected ${expectedTrimmed.size} non-empty lines, found ${generatedTrimmed.size}")
  generatedTrimmed.zip(expectedTrimmed).foreach { case ((a, i), b) =>
    if (a != b) sys.error(s"Generated code did not match for $name (expected '$b' on line $i, found '$a')")
  }
}

TaskKey[Unit]("check") := {
  compare("endpoints", "target/scala-3.3.1/src_managed/main/sbt-openapi-codegen/TapirGeneratedEndpoints.scala", "Expected.scala.txt")
  compare(
    "xml",
    "target/scala-3.3.1/src_managed/main/sbt-openapi-codegen/TapirGeneratedEndpointsXmlSerdes.scala",
    "ExpectedXmlSerdes.scala.txt"
  )
  ()
}
