package sttp.tapir.sbt

import sbt._
import sttp.tapir.codegen.{RootGenerator, YamlParser}

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
    overrideDirectoryName: Option[String]
) {

  private val directoryName: String = overrideDirectoryName.getOrElse("sbt-openapi-codegen")
  val outDirectory = dir / directoryName

  def file: Seq[File] = {
    val parsed = YamlParser
      .parseFile(IO.readLines(inputYaml).mkString("\n"))
      .left
      .map(d => new RuntimeException(_root_.io.circe.Error.showError.show(d)))
    RootGenerator
      .generateObjects(
        parsed.toTry.get,
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
        useCustomJsoniterSerdes
      )
      .map { case (objectName, fileBody) =>
        val file = outDirectory / s"$objectName.scala"
        val lines = fileBody.linesIterator.toSeq
        IO.writeLines(file, lines, IO.utf8)
        file
      }
      .toSeq
  }
}
