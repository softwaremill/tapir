package sttp.tapir.serverless.aws.cdk

import cats.effect.IO

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.reflect.io.Directory

class FileMover(sourceDir: String, destDir: String) {

  def clear: IO[Unit] = IO.blocking(new Directory(Paths.get(sourceDir).toFile).deleteRecursively())

  def move(file: String, file2: String): IO[Unit] = IO.blocking {

    val source = getClass.getResource(sourceDir + "/" + file)
    val destination = Paths.get(destDir + "/" + file2)
    val directories = destination.toString.split("/").dropRight(1)

    directories.foldLeft("") { (prefix, path) =>
      val fullPath = if (prefix.isEmpty) path else prefix + "/" + path
      if (!Paths.get(fullPath).toFile.isDirectory) {
        Files.createDirectory(Paths.get(fullPath))
      }

      fullPath
    }

    Files.write(destination, source.toString.getBytes(StandardCharsets.UTF_8))
  }
}
