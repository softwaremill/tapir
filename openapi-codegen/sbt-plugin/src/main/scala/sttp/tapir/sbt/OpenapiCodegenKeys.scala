package sttp.tapir.sbt

import sbt._

trait OpenapiCodegenKeys {
  lazy val openapiSwaggerFile = settingKey[File]("The swagger file with the api definitions.")
  lazy val openapiPackage = settingKey[String]("The name for the generated package.")
  lazy val openapiObject = settingKey[String]("The name for the generated object.")
  lazy val openapiUseHeadTagForObjectName = settingKey[Boolean](
    "If true, any tagged endpoints will be defined in an object with a name based on the first tag, instead of on the default generated object."
  )
  lazy val openapiJsonSerdeLib = settingKey[String]("The lib to use for json serdes. Supports 'circe' and 'jsoniter'.")
  lazy val openapiValidateNonDiscriminatedOneOfs =
    settingKey[Boolean]("Whether to fail if variants of a oneOf without a discriminator cannot be disambiguated..")

  lazy val generateTapirDefinitions = taskKey[Unit]("The task that generates tapir definitions based on the input swagger file.")
}

object OpenapiCodegenKeys extends OpenapiCodegenKeys
