lazy val root = (project in file("."))
  .enablePlugins(OpenapiCodegenPlugin)
  .settings(
    scalaVersion := "2.13.13",
    version := "0.1",
    openapiPackage := "com.example.generated.apis",
    openapiObject := "MyExampleEndpoints",
    openapiSwaggerFile := baseDirectory.value / "example_swagger.yaml"
  )

libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.10.0"
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "1.10.0"
libraryDependencies += "com.softwaremill.sttp.apispec" %% "openapi-circe-yaml" % "0.8.0"

import scala.io.Source

TaskKey[Unit]("check") := {
  val reference = Source.fromFile("example_swagger.yaml").getLines.mkString("\n")
  val out = Source.fromFile("target/swagger.yaml").getLines.mkString("\n")
  if (out != reference) {
    sys.error("unexpected output:\n" + out + "\n== Out diff ref ==\n" + (out diff reference) + "\n== Ref diff out ==\n" + (reference diff out))
  }
  ()
}
