package sttp.tapir.sbt

import sbt._
import Keys._
import sbt.nio.Keys.fileInputs
import sttp.tapir.codegen.OpenApiInputParser
import sttp.tapir.codegen.dedup.{GenerationMeta, PackageReuseContext}
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument

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
        val configHash = java.security.MessageDigest.getInstance("sha256").digest((version.value + c.toString).getBytes("utf-8"))
        val hashFile = cacheDir / "tapir-config-hash"
        hashFile.delete()
        java.nio.file.Files.write(hashFile.toPath, configHash)
        val swaggerFiles = (c.swaggerFile +: c.additionalPackages.map(_._2)).filter(_.exists()).toSet + hashFile
        FileFunction
          .cached(cacheDir / s"scala-$sv" / "openapi-inputs", FileInfo.hash) { _ =>
            log.info("Generating OpenAPI sources...")
            codegen(c, srcDir, sv).toSet
          }(swaggerFiles)
          .toSeq
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
      openapiAdditionalPackages.value,
      openapiPackageDependencies.value,
      openapiSeperateFilesForModels.value
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
    openapiPackageDependencies := Map.empty,
    openapiStreamingImplementation := "fs2",
    openapiGenerateEndpointTypes := false,
    openapiDisableValidatorGeneration := false,
    openapiUseCustomJsoniterSerdes := false,
    openapiSeperateFilesForModels := false,
    standardParamSetting
  )

  private def codegen(c: OpenApiConfiguration, srcDir: File, sv: String): Seq[File] = {
    val packageToFile = packageFileMapping(c)
    val parsedDocs = packageToFile.map { case (pkg, file) =>
      pkg -> OpenApiInputParser.parse(file).fold(err => throw new RuntimeException(err.getMessage), identity).resolveAllOfSchemas
    }
    val generationOrder = topologicalSort(packageToFile.keys.toSeq, c.packageDependencies)

    generationOrder
      .foldLeft((Seq.empty[File], Map.empty[String, GenerationMeta])) { case ((accFiles, accSchemaMeta), pkg) =>
        val file = packageToFile(pkg)
        val doc = parsedDocs(pkg)
        val reuseContext = c.packageDependencies
          .get(pkg)
          .flatMap { depPkg =>
            parsedDocs.get(depPkg).map { depDoc =>
              val parentMeta = accSchemaMeta.getOrElse(depPkg, GenerationMeta.default)
              PackageReuseContext.fromDocuments(doc, depDoc, depPkg, c.objectName, parentMeta)
            }
          }
          .getOrElse(PackageReuseContext.none)

        val directoryName =
          if (pkg == c.packageName && !c.additionalPackages.exists(_._1 == c.packageName)) None
          else Some(pkg.replace('.', '/'))

        val (files, schemaMeta) = genTask(c, srcDir, sv, file, pkg, doc, reuseContext, directoryName).filesAndMeta
        (accFiles ++ files, accSchemaMeta + (pkg -> schemaMeta))
      }
      ._1
  }

  private def packageFileMapping(c: OpenApiConfiguration): Map[String, File] = {
    val overriddenDefaultLocation = c.additionalPackages.find(_._2 == c.swaggerFile)
    val defaultIsRedeclared = overriddenDefaultLocation.isDefined
    val maybeDefault: Map[String, File] =
      if (defaultIsRedeclared) Map.empty
      else if (!c.swaggerFile.exists() && c.additionalPackages.nonEmpty) Map.empty
      else Map(c.packageName -> c.swaggerFile)
    maybeDefault ++ c.additionalPackages.toMap
  }

  /** Packages that depend on others are generated after their dependencies. */
  private def topologicalSort(packages: Seq[String], dependencies: Map[String, String]): Seq[String] = {
    @scala.annotation.tailrec
    def loop(remaining: List[String], done: List[String]): List[String] = remaining match {
      case Nil => done
      case _   =>
        remaining.find { pkg =>
          dependencies.get(pkg).forall(dep => !remaining.contains(dep) || !packages.contains(dep))
        } match {
          case Some(next) => loop(remaining.filterNot(_ == next), done :+ next)
          case None       =>
            throw new IllegalArgumentException(s"Cyclic or missing package dependencies involving: ${remaining.mkString(", ")}")
        }
    }
    loop(packages.toList, Nil)
  }

  private def genTask(
      c: OpenApiConfiguration,
      srcDir: File,
      sv: String,
      swaggerFile: File,
      packageName: String,
      doc: OpenapiDocument,
      packageReuse: PackageReuseContext,
      directoryName: Option[String]
  ): OpenapiCodegenTask =
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
      directoryName,
      Some(doc),
      packageReuse,
      c.seperateFilesForModels
    )
}
