lazy val root = (project in file("."))
  .enablePlugins(OpenapiCodegenPlugin)
  .settings(
    scalaVersion := "2.12.4",
    version := "0.1"
  )

libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "0.17.0-M2"
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "0.17.0-M2"
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % "0.17.0-M2"

import scala.io.Source

TaskKey[Unit]("check") := {
  val reference = Source.fromFile("swagger.yaml").getLines.mkString("\n")
  val out = Source.fromFile("target/swagger.yaml").getLines.mkString("\n")
  if (out != reference) {
    sys.error("unexpected output:\n" + out + "\n\n" + (out diff reference) + "\n\n" + (reference diff out))
  }
  ()
}
