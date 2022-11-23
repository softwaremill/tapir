package sttp.tapir.serverless.aws.cdk

import cats.effect._
import cats.syntax.all._

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.io.Source

class FileMover[F[_]: Sync](sourceDir: String, destDir: String) {

  def move(files: Map[String, String]): F[Unit] =
    files.toList.map { case (source, destination) => move(source, destination) }.sequence.void

  def move(from: String, to: String): F[Unit] = {
    val bytes = Source.fromInputStream(getClass.getResourceAsStream(sourceDir + "/" + from)).getLines().mkString("\n")
    val destination = destDir + "/" + to
    createDirectories(destination) >> save(bytes, destination)
  }

  def clear: F[Unit] =
    deleteRecursively(Paths.get(destDir).toFile)

  def put(content: F[String], destination: String): F[Unit] = {
    createDirectories(destination) >> content.flatMap(c => save(c, destination))
  }

  private def save(content: String, destination: String): F[Unit] = Sync[F].blocking {
    Files.write(Paths.get(destination), content.getBytes(StandardCharsets.UTF_8))
  }

  private def deleteRecursively(file: File): F[Unit] = {
    val deleteFiles = Option(file.listFiles)
      .map(_.toList)
      .getOrElse(List.empty)
      .map(deleteRecursively)
      .sequence
      .void

    deleteFiles >> Sync[F].blocking(file.delete()).void
  }

  private def createDirectories(destination: String): F[Unit] = Sync[F].blocking {
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
