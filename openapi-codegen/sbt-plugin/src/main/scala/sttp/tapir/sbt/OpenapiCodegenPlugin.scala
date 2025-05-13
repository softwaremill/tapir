package sttp.tapir.sbt

import sbt._
import Keys._

object OpenapiCodegenPlugin extends AutoPlugin {

  object autoImport extends OpenapiCodegenKeys
  import autoImport._

  override def requires = plugins.JvmPlugin

  override lazy val projectSettings: Seq[Setting[_]] =
    openapiCodegenScopedSettings(Compile) ++ openapiCodegenDefaultSettings

  import TupleSyntax._

  def openapiCodegenScopedSettings(conf: Configuration): Seq[Setting[_]] = inConfig(conf)(
    Seq(
      generateTapirDefinitions := codegen.value,
      sourceGenerators += (codegen.taskValue).map(_.map(_.toPath.toFile))
    )
  )

  def standardParamSetting =
    openapiOpenApiConfiguration := OpenApiConfiguration(
      openapiSwaggerFile.value,
      openapiPackage.value,
      openapiObject.value,
      openapiUseHeadTagForObjectName.value,
      openapiJsonSerdeLib.value,
      openapiXmlSerdeLib.value,
      openapiStreamingImplementation.value,
      openapiValidateNonDiscriminatedOneOfs.value,
      openapiMaxSchemasPerFile.value,
      openapiGenerateEndpointTypes.value,
      openapiDisableValidatorGeneration.value,
      openapiAdditionalPackages.value
    )
  def openapiCodegenDefaultSettings: Seq[Setting[_]] = Seq(
    openapiSwaggerFile := baseDirectory.value / "swagger.yaml",
    openapiPackage := "sttp.tapir.generated",
    openapiObject := "TapirGeneratedEndpoints",
    openapiUseHeadTagForObjectName := false,
    openapiJsonSerdeLib := "circe",
    openapiXmlSerdeLib := "cats-xml",
    openapiValidateNonDiscriminatedOneOfs := true,
    openapiMaxSchemasPerFile := 400,
    openapiAdditionalPackages := Nil,
    openapiStreamingImplementation := "fs2",
    openapiGenerateEndpointTypes := false,
    openapiDisableValidatorGeneration := false,
    standardParamSetting
  )

  private def codegen =
    Def.task {
      val log = sLog.value
      log.info("Zipping file...")
      (
        openapiOpenApiConfiguration,
        sourceManaged,
        streams,
        scalaVersion
      ).flatMap {
        (
            c: OpenApiConfiguration,
            srcDir: File,
            taskStreams: TaskStreams,
            sv: String
        ) =>
          def genTask(swaggerFile: File, packageName: String, directoryName: Option[String] = None) =
            OpenapiCodegenTask(
              swaggerFile,
              packageName,
              c.objectName,
              c.useHeadTagForObjectName,
              c.jsonSerdeLib,
              c.xmlSerdeLib,
              c.streamingImplementation,
              c.validateNonDiscriminatedOneOfs,
              c.maxSchemasPerFile,
              c.generateEndpointTypes,
              c.disableValidatorGeneration,
              srcDir,
              taskStreams.cacheDirectory,
              sv.startsWith("3"),
              directoryName
            )

          val overriddenDefaultLocation = c.additionalPackages.find(_._2 == c.swaggerFile)
          val defaultIsRedeclared = overriddenDefaultLocation.isDefined
          val maybeDefaultFileTask = {
            if (defaultIsRedeclared) {
              System.err.println(s"WARN: Default swagger file is redeclared. Writing to ${overriddenDefaultLocation.get._1}")
              Nil
            } else if (!c.swaggerFile.exists() && c.additionalPackages.nonEmpty) {
              System.err.println(s"WARN: File not found: ${c.swaggerFile.toPath}. Skipping default, only writing additional packages.")
              Nil
            } else Seq(genTask(c.swaggerFile, c.packageName).file)
          }
          (maybeDefaultFileTask ++ c.additionalPackages.map { case (pkg, defns) =>
            genTask(defns, pkg, Some(pkg.replace('.', '/'))).file
          })
            .reduceLeft((l, r) => l.flatMap(_l => r.map(_l ++ _)))
      }.value
    }
}
