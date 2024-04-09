package sttp.tapir.sbt

import sbt._
import sbt.util.FileInfo.hash
import sbt.util.Tracked.inputChanged
import sttp.tapir.codegen.{BasicGenerator, YamlParser}

case class OpenapiCodegenTask(
    inputYaml: File,
    packageName: String,
    objectName: String,
    useHeadTagForObjectName: Boolean,
    jsonSerdeLib: String,
    validateNonDiscriminatedOneOfs: Boolean,
    dir: File,
    cacheDir: File,
    targetScala3: Boolean
) {

  val tempDirectory = cacheDir / "sbt-openapi-codegen"
  val outDirectory = dir / "sbt-openapi-codegen"

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
      BasicGenerator
        .generateObjects(
          parsed.toTry.get,
          packageName,
          objectName,
          targetScala3,
          useHeadTagForObjectName,
          jsonSerdeLib,
          validateNonDiscriminatedOneOfs
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
