package sttp.tapir.serverless.aws.cdk.internal

import cats.effect._
import cats.syntax.all._

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.io.Source

class AppTemplateFiles[F[_]: Sync](sourceDir: String, outputDir: String) {
  private val outputStackFile = s"$outputDir/lib/tapir-cdk-stack.ts"
  private val files = Map(
    "bin/tapir-cdk-stack.ts" -> "bin/tapir-cdk-stack.ts",
    "gitignore" -> ".gitignore",
    "cdk.json" -> "cdk.json",
    "jest.config.js" -> "jest.config.js",
    "package.json" -> "package.json",
    "readme.md" -> "readme.md",
    "tsconfig.json" -> "tsconfig.json"
  )

  def copyStaticFiles(): F[Unit] =
    files.toList.map { case (source, destination) => copy(source, destination) }.sequence.void

  def clearOutputDir(): F[Unit] = deleteRecursively(Paths.get(outputDir).toFile)

  def renderStackTemplate(stackTemplatePath: String, render: String => F[String]): F[Unit] =
    for {
      _ <- createDirectories(outputStackFile)
      c <- getContent(stackTemplatePath)
      r <- render(c)
      _ <- save(r, outputStackFile)
    } yield ()

  private def copy(from: String, to: String): F[Unit] =
    for {
      content <- getContent(sourceDir + "/" + from)
      destination = outputDir + "/" + to
      _ <- createDirectories(destination)
      _ <- save(content, destination)
    } yield ()

  private def getContent(path: String): F[String] =
    Resource
      .fromAutoCloseable[F, Source](Sync[F].blocking(Source.fromInputStream(getClass.getResourceAsStream(path))))
      .use(content => Sync[F].delay(content.getLines().mkString(System.lineSeparator())))

  private def save(content: String, destination: String): F[Unit] = Sync[F].blocking {
    Files.write(Paths.get(destination), content.getBytes(StandardCharsets.UTF_8))
    ()
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
    ()
  }
}
