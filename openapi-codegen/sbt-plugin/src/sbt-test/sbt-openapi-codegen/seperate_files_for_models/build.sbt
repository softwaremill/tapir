lazy val root = (project in file("."))
  .enablePlugins(OpenapiCodegenPlugin)
  .settings(
    scalaVersion := "2.13.18",
    version := "0.1",
    openapiSeperateFilesForModels := true,
    openapiAdditionalPackages := List(
      "sttp.tapir.generated" -> baseDirectory.value / "swagger.yaml",
      "sttp.tapir.gen_dup" -> baseDirectory.value / "swagger.yaml"
    ),
    openapiPackageDependencies := Map("sttp.tapir.gen_dup" -> "sttp.tapir.generated")
  )

libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.10.0"
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.10.0"
libraryDependencies += "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.8.0"

import scala.io.Source
import scala.util.Using

TaskKey[Unit]("check") := {
  val base = sourceManaged.value / "main/sttp/tapir/generated"
  val main = Using(Source.fromFile(base / "models/package.scala"))(_.mkString).get
  val book = Using(Source.fromFile(base / "models/Book.scala"))(_.mkString).get
  if (main.contains("case class Book")) sys.error("Book model should not be in the main object")
  if (!book.contains("case class Book")) sys.error("Book model should be in models/Book.scala")
  if (!main.contains("type Books =")) sys.error("type alias Books should be defined in package object")
  ()
}
