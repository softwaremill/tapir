lazy val root = (project in file("."))
  .enablePlugins(OpenapiCodegenPlugin)
  .settings(
    scalaVersion := "2.13.16",
    version := "0.1"
  )

libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.10.0"
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.10.0"
libraryDependencies += "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.8.0"

import scala.io.Source
import scala.util.Using

def check(generatedFileName: String, expectedFileName: String) = {
  val generatedCode =
    Using(Source.fromFile(s"target/scala-2.13/src_managed/main/sttp/tapir/generated/$generatedFileName"))(_.getLines.mkString("\n")).get
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

openapiAdditionalPackages := List(
  "sttp.tapir.generated.swagger" -> baseDirectory.value / "swagger.yaml",
  "sttp.tapir.generated.swagger2" -> baseDirectory.value / "swagger2.yaml"
)

TaskKey[Unit]("check") := {
  Seq(
    "swagger/TapirGeneratedEndpoints.scala" -> "Expected.scala.txt",
    "swagger2/TapirGeneratedEndpoints.scala" -> "Expected2.scala.txt"
  ).foreach { case (generated, expected) => check(generated, expected) }
  ()
}
