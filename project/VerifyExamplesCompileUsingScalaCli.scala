import java.io.File
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.*
import sbt.Logger
import scala.sys.process.{Process, ProcessLogger}

object VerifyExamplesCompileUsingScalaCli {
  private def listScalaFiles(basePath: File): Seq[Path] = {
    val dirPath = basePath.toPath
    var result = Vector.empty[Path]

    val fileVisitor = new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        if (file.toString.endsWith(".scala")) {
          result = result :+ file
        }
        FileVisitResult.CONTINUE
      }
    }

    Files.walkFileTree(dirPath, fileVisitor)
    result
  }

  def apply(log: Logger, basePath: File): Unit = {
    val examples = listScalaFiles(basePath)
    log.info(s"Found ${examples.size} examples")

    for (example <- examples) {
      log.info(s"Compiling: $example")
      val errorOutput = new StringBuilder
      val logger = ProcessLogger((o: String) => (), (e: String) => errorOutput.append(e + "\n"))
      try {
        val result = Process(List("scala-cli", "compile", example.toFile.getAbsolutePath), basePath).!(logger)
        if (result != 0) {
          throw new Exception(s"""Compiling $example failed.\n$errorOutput""".stripMargin)
        }
      } finally {
        Process(List("scala-cli", "clean", basePath.getAbsolutePath), basePath).!
      }
    }
  }
}
