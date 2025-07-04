lazy val root = (project in file("."))
  .enablePlugins(OpenapiCodegenPlugin)
  .settings(
    scalaVersion := "2.13.16",
    version := "0.1",
    openapiGenerateEndpointTypes := true
  )

libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.10.0"
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.10.0"
libraryDependencies += "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.8.0"

import scala.io.Source

TaskKey[Unit]("check") := {
  val reference = Source.fromFile("swagger.yaml").getLines.mkString("\n")
  val out = Source.fromFile("target/swagger.yaml").getLines.mkString("\n")
  if (out != reference) {
    sys.error("unexpected output:\n" + out + "\n\n" + (out diff reference) + "\n\n" + (reference diff out))
  }
  ()
}
