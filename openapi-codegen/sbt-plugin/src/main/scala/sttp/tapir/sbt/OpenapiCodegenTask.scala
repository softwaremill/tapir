package sttp.tapir.sbt

import sbt._
import sbt.util.FileInfo.hash
import sbt.util.Tracked.inputChanged
import sttp.tapir.codegen.{BasicGenerator, YamlParser}

case class OpenapiCodegenTask(
    inputYaml: File,
    packageName: String,
    objectName: String,
    dir: File,
    cacheDir: File,
    targetScala3: Boolean
) {

  val tempFile = cacheDir / "sbt-openapi-codegen" / s"$objectName.scala"
  val outFile = dir / "sbt-openapi-codegen" / s"$objectName.scala"

  // 1. make the file under cache/sbt-tapircodegen.
  // 2. compare its SHA1 against cache/sbtbuildinfo-inputs
  def file: Task[File] = {
    makeFile(tempFile) map { _ =>
      cachedCopyFile(hash(tempFile))
      outFile
    }
  }

  val cachedCopyFile =
    inputChanged(cacheDir / "sbt-openapi-codegen-inputs") { (inChanged, _: HashFileInfo) =>
      if (inChanged || !outFile.exists) {
        IO.copyFile(tempFile, outFile, preserveLastModified = true)
      } // if
    }

  def makeFile(file: File): Task[File] = {
    task {
      val parsed = YamlParser.parseFile(IO.readLines(inputYaml).mkString("\n"))
        .left.map(d => new RuntimeException(_root_.io.circe.Error.showError.show(d)))
      val lines = BasicGenerator.generateObjects(parsed.toTry.get, packageName, objectName, targetScala3).linesIterator.toSeq
      IO.writeLines(file, lines, IO.utf8)
      file
    }
  }
}
