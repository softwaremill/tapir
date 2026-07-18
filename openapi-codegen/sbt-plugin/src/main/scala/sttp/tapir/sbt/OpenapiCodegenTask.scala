package sttp.tapir.sbt

import sbt._
import sttp.tapir.codegen.{OpenApiInputParser, RootGenerator}
import sttp.tapir.codegen.dedup.{GenerationMeta, PackageReuseContext}
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

case class OpenapiCodegenTask(
    inputYaml: File,
    packageName: String,
    objectName: String,
    useHeadTagForObjectName: Boolean,
    jsonSerdeLib: String,
    xmlSerdeLib: String,
    streamingImplementation: String,
    validateNonDiscriminatedOneOfs: Boolean,
    maxSchemasPerFile: Int,
    generateEndpointTypes: Boolean,
    disableValidatorGeneration: Boolean,
    useCustomJsoniterSerdes: Boolean,
    dir: File,
    targetScala3: Boolean,
    overrideDirectoryName: Option[String],
    preParsedDoc: Option[OpenapiDocument] = None,
    packageReuse: PackageReuseContext = PackageReuseContext.none,
    seperateFilesForModels: Boolean = false,
    alwaysGenerateParamSupport: Boolean,
    addDisambiguationCodes: Boolean = true
) {

  private val directoryName: String = overrideDirectoryName.getOrElse("sbt-openapi-codegen")
  val outDirectory = dir / directoryName

  def filesAndMeta: (Seq[File], GenerationMeta) = {
    val doc = preParsedDoc.getOrElse {
      OpenApiInputParser
        .parse(inputYaml)
        .fold(err => throw new RuntimeException(_root_.io.circe.Error.showError.show(err)), identity)
    }
    val generationInfo = RootGenerator
      .generateObjects(
        doc.resolveAllOfSchemas,
        packageName,
        objectName,
        targetScala3,
        useHeadTagForObjectName,
        jsonSerdeLib,
        xmlSerdeLib,
        streamingImplementation,
        validateNonDiscriminatedOneOfs,
        maxSchemasPerFile,
        generateEndpointTypes,
        !disableValidatorGeneration,
        useCustomJsoniterSerdes,
        packageReuse,
        seperateFilesForModels,
        alwaysGenerateParamSupport,
        addDisambiguationCodes
      )
    Await.result(
      Future.traverse(generationInfo.allFiles.toSeq) { case (objectName, fileBody) =>
        Future[File] {
          val segments = objectName.split('.')
          val file = segments.toList match {
            case name :: Nil  => outDirectory / s"$name.scala"
            case init :+ last => init.foldLeft(outDirectory)(_ / _) / s"$last.scala"
          }
          val lines = fileBody.linesIterator.toSeq
          IO.writeLines(file, lines, IO.utf8)
          file
        }
      },
      Duration.Inf
    ) -> generationInfo.meta
  }
}
