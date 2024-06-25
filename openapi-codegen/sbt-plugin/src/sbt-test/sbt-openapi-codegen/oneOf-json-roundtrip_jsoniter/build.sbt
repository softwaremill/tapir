lazy val root = (project in file("."))
  .enablePlugins(OpenapiCodegenPlugin)
  .settings(
    scalaVersion := "2.13.14",
    version := "0.1",
    openapiJsonSerdeLib := "jsoniter"
  )

libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-jsoniter-scala" % "1.10.0",
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.10.0",
  "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.8.0",
  "com.beachape" %% "enumeratum" % "1.7.3",
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "2.30.1",
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.30.1" % "compile-internal",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  "com.softwaremill.sttp.tapir" %% "tapir-sttp-stub-server" % "1.10.0" % Test
)

import sttp.tapir.sbt.OpenapiCodegenPlugin.autoImport.{openapiJsonSerdeLib, openapiUseHeadTagForObjectName}

import scala.io.Source

TaskKey[Unit]("check") := {
  val generatedCode =
    Source.fromFile("target/scala-2.13/src_managed/main/sbt-openapi-codegen/TapirGeneratedEndpoints.scala").getLines.mkString("\n")
  val expected = Source.fromFile("Expected.scala.txt").getLines.mkString("\n")
  val generatedTrimmed =
    generatedCode.linesIterator.zipWithIndex.filterNot(_._1.forall(_.isWhitespace)).map { case (a, i) => a.trim -> i }.toSeq
  val expectedTrimmed = expected.linesIterator.filterNot(_.forall(_.isWhitespace)).map(_.trim).toSeq
  if (generatedTrimmed.size != expectedTrimmed.size)
    sys.error(s"expected ${expectedTrimmed.size} non-empty lines, found ${generatedTrimmed.size}")
  generatedTrimmed.zip(expectedTrimmed).foreach { case ((a, i), b) =>
    if (a != b) sys.error(s"Generated code did not match (expected '$b' on line $i, found '$a')")
  }
  println("Skipping swagger roundtrip for petstore")
  ()
}
