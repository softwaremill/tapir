package sttp.tapir.sbt

import sbt._
import sbt.util.FileInfo.hash
import sbt.util.Tracked.inputChanged
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
    dir: File,
    cacheDir: File,
    targetScala3: Boolean,
    overrideDirectoryName: Option[String]
) {

  private val directoryName: String = overrideDirectoryName.getOrElse("sbt-openapi-codegen")
  val tempDirectory = cacheDir / directoryName
  val outDirectory = dir / directoryName

  // 1. make the files under cache/sbt-tapircodegen.
  // 2. compare their SHA1 against cache/sbtbuildinfo-inputs
  def file: Task[Seq[File]] = {
    makeFiles(tempDirectory) map { files =>
      files.map { tempFile =>
        val outFile = outDirectory / tempFile.getName
        cachedCopyFile(tempFile, outFile)(hash(tempFile))
        outFile
      }
    }
  }

  def cachedCopyFile(tempFile: File, outFile: File) =
    inputChanged(cacheDir / s"sbt-openapi-codegen-inputs-${tempFile.getName}") { (inChanged, _: HashFileInfo) =>
      if (inChanged || !outFile.exists) {
        IO.copyFile(tempFile, outFile, preserveLastModified = true)
      }
    }

  def makeFiles(directory: File): Task[Seq[File]] = {
    task {
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
          !disableValidatorGeneration
        )
        .map { case (objectName, fileBody) =>
          val file = directory / s"$objectName.scala"
          val lines = fileBody.linesIterator.toSeq
          IO.writeLines(file, lines, IO.utf8)
          file
        }
        .toSeq
    }
  }
}
