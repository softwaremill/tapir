package sttp.tapir.serverless.aws.cdk

import cats.effect.IO
import cats.implicits.toTraverseOps

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.io.Source
import scala.reflect.io.Directory

class FileMover(sourceDir: String, destDir: String) {
  def move(files: Map[String, String]): IO[Unit] =
    files.toList.map { case (source, destination) => move(source, destination) }.sequence.void

  def move(from: String, to: String): IO[Unit] = IO.blocking {
    val destination = Paths.get(destDir + "/" + to)
    val directories = destination.toString.split("/").dropRight(1)

    directories.foldLeft("") { (prefix, path) =>
      val fullPath = if (prefix.isEmpty) path else prefix + "/" + path
      if (!Paths.get(fullPath).toFile.isDirectory) {
        Files.createDirectory(Paths.get(fullPath))
      }

      fullPath
    }

    val bytes = Source.fromInputStream(getClass.getResourceAsStream(sourceDir + "/" + from)).getLines().mkString("\n")
    Files.write(destination, bytes.getBytes(StandardCharsets.UTF_8))
  }

  def clear: IO[Unit] = IO.blocking(new Directory(Paths.get(sourceDir).toFile).deleteRecursively())

  def put(content: IO[String], destination: String): IO[Unit] = content.map { c =>

    val directories = destination.split("/").dropRight(1) // fixme extract
    directories.foldLeft("") { (prefix, path) =>
      val fullPath = if (prefix.isEmpty) path else prefix + "/" + path
      if (!Paths.get(fullPath).toFile.isDirectory) {
        Files.createDirectory(Paths.get(fullPath))
      }

      fullPath
    }

    Files.write(Paths.get(destination), c.getBytes(StandardCharsets.UTF_8)) //extract to const?
  }
}
