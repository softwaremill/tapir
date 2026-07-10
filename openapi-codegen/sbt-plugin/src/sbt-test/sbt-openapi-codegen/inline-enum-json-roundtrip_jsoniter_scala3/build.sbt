lazy val root = (project in file("."))
  .enablePlugins(OpenapiCodegenPlugin)
  .settings(
    scalaVersion := "3.3.3",
    version := "0.1",
    openapiJsonSerdeLib := "jsoniter",
    openapiXmlSerdeLib := "none",
    openapiUseCustomJsoniterSerdes := false,
    openapiGenerateEndpointTypes := true
  )

val tapirVersion = "1.11.16"
libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-jsoniter-scala" % tapirVersion,
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core" % "2.38.16",
  "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.39.0" % "compile-internal",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)
