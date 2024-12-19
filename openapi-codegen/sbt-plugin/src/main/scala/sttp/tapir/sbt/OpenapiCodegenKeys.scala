package sttp.tapir.sbt

import sbt._

case class OpenApiConfiguration(
    swaggerFile: File,
    packageName: String,
    objectName: String,
    useHeadTagForObjectName: Boolean,
    jsonSerdeLib: String,
    streamingImplementation: String,
    endpointCapabilites: String,
    validateNonDiscriminatedOneOfs: Boolean,
    maxSchemasPerFile: Int,
    generateEndpointTypes: Boolean,
    additionalPackages: List[(String, File)]
)

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
  lazy val openapiMaxSchemasPerFile = settingKey[Int]("Maximum number of schemas to generate for a single file")
  lazy val openapiAdditionalPackages = settingKey[List[(String, File)]]("Addition package -> spec mappings to generate.")
  lazy val openapiStreamingImplementation = settingKey[String]("Implementation for streamTextBody. Supports: akka, fs2, pekko, zio.")
  lazy val openapiEndpointCapabilites = settingKey[String]("Implementation for streamTextBody. Supports: akka, fs2, nothing, pekko, zio.")
  lazy val openapiGenerateEndpointTypes = settingKey[Boolean]("Whether to emit explicit types for endpoint denfs")
  lazy val openapiOpenApiConfiguration =
    settingKey[OpenApiConfiguration]("Aggregation of other settings. Manually set value will be disregarded.")

  lazy val generateTapirDefinitions = taskKey[Unit]("The task that generates tapir definitions based on the input swagger file.")
}

object OpenapiCodegenKeys extends OpenapiCodegenKeys
