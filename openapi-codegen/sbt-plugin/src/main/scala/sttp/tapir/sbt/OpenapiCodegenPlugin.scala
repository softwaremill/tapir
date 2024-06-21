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
      sourceGenerators += (codegen.taskValue).map(_.flatMap(_.map(_.toPath.toFile)))
    )
  )

  def openapiCodegenDefaultSettings: Seq[Setting[_]] = Seq(
    openapiSwaggerFile := baseDirectory.value / "swagger.yaml",
    openapiPackage := "sttp.tapir.generated",
    openapiObject := "TapirGeneratedEndpoints",
    openapiUseHeadTagForObjectName := false,
    openapiJsonSerdeLib := "circe",
    openapiValidateNonDiscriminatedOneOfs := true,
    openapiMaxSchemasPerFile := 400,
    openapiAdditionalPackages := Nil
  )

  private def codegen = Def.task {
    val log = sLog.value
    log.info("Zipping file...")
    (((
      openapiSwaggerFile,
      openapiPackage,
      openapiObject,
      openapiUseHeadTagForObjectName,
      openapiJsonSerdeLib,
      openapiValidateNonDiscriminatedOneOfs,
      openapiMaxSchemasPerFile,
      openapiAdditionalPackages,
      sourceManaged,
      streams,
      scalaVersion
    ) flatMap {
      (
          swaggerFile: File,
          packageName: String,
          objectName: String,
          useHeadTagForObjectName: Boolean,
          jsonSerdeLib: String,
          validateNonDiscriminatedOneOfs: Boolean,
          maxSchemasPerFile: Int,
          additionalPackages: List[(String, File)],
          srcDir: File,
          taskStreams: TaskStreams,
          sv: String
      ) =>
        def genTask(swaggerFile: File, packageName: String, directoryName: Option[String] = None) =
          OpenapiCodegenTask(
            swaggerFile,
            packageName,
            objectName,
            useHeadTagForObjectName,
            jsonSerdeLib,
            validateNonDiscriminatedOneOfs,
            maxSchemasPerFile,
            srcDir,
            taskStreams.cacheDirectory,
            sv.startsWith("3"),
            directoryName
          )
        (genTask(swaggerFile, packageName).file +: additionalPackages.map { case (pkg, defns) =>
          genTask(defns, pkg, Some(pkg.replace('.', '/'))).file
        })
          .reduceLeft((l, r) => l.flatMap(_l => r.map(_l ++ _)))
    }) map (Seq(_))).value
  }
}
