package sttp.tapir.sbt

import sbt._
import Keys._
import sbt.nio.Keys.fileInputs

object OpenapiCodegenPlugin extends AutoPlugin {

  object autoImport extends OpenapiCodegenKeys
  import autoImport._

  override def requires = plugins.JvmPlugin

  override lazy val projectSettings: Seq[Setting[_]] =
    openapiCodegenScopedSettings(Compile) ++ openapiCodegenDefaultSettings

  def openapiCodegenScopedSettings(conf: Configuration): Seq[Setting[_]] = inConfig(conf)(
    Seq(
      generateTapirDefinitions := Def.task {
        val c = openapiOpenApiConfiguration.value
        val srcDir = sourceManaged.value
        val sv = scalaVersion.value
        val log = sLog.value
        val cacheDir = streams.value.cacheDirectory
        val swaggerFiles = (c.swaggerFile +: c.additionalPackages.map(_._2)).filter(_.exists()).toSet
        FileFunction.cached(cacheDir / s"scala-$sv" / "openapi-inputs", FileInfo.hash) { _ =>
          log.info("Generating OpenAPI sources...")
          codegen(c, srcDir, sv).toSet
        }(swaggerFiles).toSeq
      }.value,
      generateTapirDefinitions / fileInputs ++= {
        (openapiSwaggerFile.value +: openapiAdditionalPackages.value.map(_._2))
          .map(f => Glob(f.toPath))
      },
      sourceGenerators += generateTapirDefinitions.taskValue
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
      openapiUseCustomJsoniterSerdes.value,
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
    openapiUseCustomJsoniterSerdes := false,
    standardParamSetting
  )

  private def codegen(c: OpenApiConfiguration, srcDir: File, sv: String): Seq[File] = {
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
        c.useCustomJsoniterSerdes,
        srcDir,
        sv.startsWith("3"),
        directoryName
      )

    val overriddenDefaultLocation = c.additionalPackages.find(_._2 == c.swaggerFile)
    val defaultIsRedeclared = overriddenDefaultLocation.isDefined
    val maybeDefaultFiles: Seq[File] = {
      if (defaultIsRedeclared) {
        System.err.println(s"WARN: Default swagger file is redeclared. Writing to ${overriddenDefaultLocation.get._1}")
        Nil
      } else if (!c.swaggerFile.exists() && c.additionalPackages.nonEmpty) {
        System.err.println(s"WARN: File not found: ${c.swaggerFile.toPath}. Skipping default, only writing additional packages.")
        Nil
      } else genTask(c.swaggerFile, c.packageName).file
    }
    maybeDefaultFiles ++ c.additionalPackages.flatMap { case (pkg, defns) =>
      genTask(defns, pkg, Some(pkg.replace('.', '/'))).file
    }
  }
}
