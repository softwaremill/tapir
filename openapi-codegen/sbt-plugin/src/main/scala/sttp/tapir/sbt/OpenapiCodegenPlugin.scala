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
    openapiUseHeadTagForObjectName := false
  )

  private def codegen = Def.task {
    val log = sLog.value
    log.info("Zipping file...")
    (((
      openapiSwaggerFile,
      openapiPackage,
      openapiObject,
      openapiUseHeadTagForObjectName,
      sourceManaged,
      streams,
      scalaVersion
    ) flatMap {
      (
          swaggerFile: File,
          packageName: String,
          objectName: String,
          useHeadTagForObjectName: Boolean,
          srcDir: File,
          taskStreams: TaskStreams,
          sv: String
      ) =>
        OpenapiCodegenTask(swaggerFile, packageName, objectName, useHeadTagForObjectName, srcDir, taskStreams.cacheDirectory, sv.startsWith("3")).file
    }) map (Seq(_))).value
  }
}
