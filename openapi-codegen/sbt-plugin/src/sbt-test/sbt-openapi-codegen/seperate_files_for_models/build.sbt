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
val tapirVersion = "1.13.27"

libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % tapirVersion,
  "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.11.10",
  "com.beachape" %% "enumeratum" % "1.9.8",
  "com.beachape" %% "enumeratum-circe" % "1.9.8"
)

import scala.io.Source
import scala.util.Using

TaskKey[Unit]("check") := {
  val base = sourceManaged.value / "main/sttp/tapir"
  val main = Using(Source.fromFile(base / "generated/models/package.scala"))(_.mkString).get
  val book = Using(Source.fromFile(base / "generated/models/Book.scala"))(_.mkString).get
  val dup = Using(Source.fromFile(base / "gen_dup/models/package.scala"))(_.mkString).get
  if (main.contains("case class Book")) sys.error("Book model should not be in the main object")
  if (!book.contains("case class Book")) sys.error("Book model should be in models/Book.scala")
  if (!main.contains("type Books =")) sys.error("type alias Books should be defined in package object")
  if (!dup.contains("type Books =")) sys.error("type alias Books should be defined in dependent package object")
  if (!dup.contains("type Animal = sttp.tapir.generated.models.Animal")) sys.error("type alias Animal should be defined in package object")
  if (!dup.contains("type BadgerTypeOfSnuffle =")) sys.error("type alias should be generated for dedupped inline enum defns")
  ()
}
