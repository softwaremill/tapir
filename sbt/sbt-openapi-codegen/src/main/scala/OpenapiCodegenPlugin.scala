import sbt._, Keys._
import sbt.io.{IO, Path}

object OpenapiCodegenPlugin extends AutoPlugin {

  object autoImport extends OpenapiCodegenKeys
  import autoImport._

  override def requires = plugins.JvmPlugin

  override lazy val projectSettings: Seq[Setting[_]] =
    openapiCodegenScopedSettings(Compile) ++ openapiCodegenDefaultSettings

  import TupleSyntax._

  def openapiCodegenScopedSettings(conf: Configuration): Seq[Def.Setting[_]] = inConfig(conf)(
    Seq(
      generateTapirDefinitions := codegen.value,
      sourceGenerators += (codegen.taskValue).map(_.map(_.toPath.toFile))
    )
  )

  def openapiCodegenDefaultSettings: Seq[Setting[_]] = Seq(
    swaggerFile := baseDirectory.value / "swagger.yaml"
  )

  private def codegen = Def.task {
    val log = sLog.value
    log.info("Zipping file...")
    (((
      sourceManaged,
      streams
    ) flatMap {
      (
          srcDir: File,
          taskStreams: TaskStreams
      ) =>
        OpenapiCodegenTask(srcDir, taskStreams.cacheDirectory).file
    }) map (Seq(_))).value
  }
}
