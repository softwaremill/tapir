package sttp.tapir.serverless.aws.cdk

import cats.effect.IO
import cats.implicits.toTraverseOps

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.io.Source

class FileMover(sourceDir: String, destDir: String) { // fixme use F[_]

  def move(files: Map[String, String]): IO[Unit] =
    files.toList.map { case (source, destination) => move(source, destination) }.sequence.void

  def move(from: String, to: String): IO[Unit] = {
    val bytes = Source.fromInputStream(getClass.getResourceAsStream(sourceDir + "/" + from)).getLines().mkString("\n")
    val destination = destDir + "/" + to
    createDirectories(destination) >> save(bytes, destination)
  }

  def clear: IO[Unit] =
    deleteRecursively(Paths.get(destDir).toFile)

  def put(content: IO[String], destination: String): IO[Unit] = {
    createDirectories(destination) >> content.flatMap(c => save(c, destination))
  }

  private def save(content: String, destination: String): IO[Unit] = IO.blocking {
    Files.write(Paths.get(destination), content.getBytes(StandardCharsets.UTF_8))
  }

  private def deleteRecursively(file: File): IO[Unit] = {
    val deleteFiles = Option(file.listFiles)
      .map(_.toList)
      .getOrElse(List.empty)
      .map(deleteRecursively)
      .sequence
      .void

    deleteFiles >> IO.blocking(file.delete()).void
  }

  private def createDirectories(destination: String): IO[Unit] = IO.blocking {
    val directories = destination.split("/").dropRight(1)
    directories.foldLeft("") { (prefix, path) =>
      val fullPath = if (prefix.isEmpty) path else prefix + "/" + path
      if (!Paths.get(fullPath).toFile.isDirectory) {
        Files.createDirectory(Paths.get(fullPath))
      }

      fullPath
    }
  }
}
