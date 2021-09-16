import sbt._
import codegen._

case class OpenapiCodegenTask(
    inputYaml: File,
    packageName: String,
    objectName: String,
    dir: File,
    cacheDir: File
) {

  import FileInfo.hash
  import Tracked.inputChanged

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
      val lines = BasicGenerator.generateObjects(parsed.toTry.get, packageName, objectName).linesIterator.toSeq
      IO.writeLines(file, lines, IO.utf8)
      file
    }
  }
}
