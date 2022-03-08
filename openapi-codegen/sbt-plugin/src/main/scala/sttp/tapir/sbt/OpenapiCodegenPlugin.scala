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

  def openapiCodegenDefaultSettings: Seq[Setting[_]] = Seq(
    openapiSwaggerFile := baseDirectory.value / "swagger.yaml",
    openapiPackage := "sttp.tapir.generated",
    openapiObject := "TapirGeneratedEndpoints"
  )

  private def codegen = Def.task {
    val log = sLog.value
    log.info("Zipping file...")
    (((
      openapiSwaggerFile,
      openapiPackage,
      openapiObject,
      sourceManaged,
      streams
    ) flatMap {
      (
          swaggerFile: File,
          packageName: String,
          objectName: String,
          srcDir: File,
          taskStreams: TaskStreams
      ) =>
        OpenapiCodegenTask(swaggerFile, packageName, objectName, srcDir, taskStreams.cacheDirectory).file
    }) map (Seq(_))).value
  }
}
