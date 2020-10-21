import sbt._

trait OpenapiCodegenKeys {
  lazy val swaggerFile = settingKey[File]("swagger file with the definitions")

  lazy val generateTapirDefinitions = taskKey[Unit]("Generates tapir definitions based on the input swagger file")
}
