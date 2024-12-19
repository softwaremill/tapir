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
      openapiStreamingImplementation.value,
      openapiValidateNonDiscriminatedOneOfs.value,
      openapiMaxSchemasPerFile.value,
      openapiGenerateEndpointTypes.value,
      openapiAdditionalPackages.value
    )
  def openapiCodegenDefaultSettings: Seq[Setting[_]] = Seq(
    openapiSwaggerFile := baseDirectory.value / "swagger.yaml",
    openapiPackage := "sttp.tapir.generated",
    openapiObject := "TapirGeneratedEndpoints",
    openapiUseHeadTagForObjectName := false,
    openapiJsonSerdeLib := "circe",
    openapiValidateNonDiscriminatedOneOfs := true,
    openapiMaxSchemasPerFile := 400,
    openapiAdditionalPackages := Nil,
    openapiStreamingImplementation := "fs2",
    openapiGenerateEndpointTypes := false,
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
              c.streamingImplementation,
              c.validateNonDiscriminatedOneOfs,
              c.maxSchemasPerFile,
              c.generateEndpointTypes,
              srcDir,
              taskStreams.cacheDirectory,
              sv.startsWith("3"),
              directoryName
            )
          (genTask(c.swaggerFile, c.packageName).file +: c.additionalPackages.map { case (pkg, defns) =>
            genTask(defns, pkg, Some(pkg.replace('.', '/'))).file
          })
            .reduceLeft((l, r) => l.flatMap(_l => r.map(_l ++ _)))
      }.value
    }
}
