package sttp.tapir.codegen

import cats.data.Validated
import cats.effect.{IO, ExitCode}
import cats.implicits._

import com.monovore.decline._

import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import sttp.tapir.codegen.{RootGenerator, YamlParser}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, StandardOpenOption}

import scala.jdk.CollectionConverters._

object GenScala {
  final val DefaultObjectName = "TapirGeneratedEndpoints"

  private val fileOpt: Opts[File] =
    Opts
      .option[String]("file", "OpenAPI file specification", "f")
      .mapValidated { path =>
        val file = new File(path)
        if (!file.exists()) Validated.invalidNel(s"File not found: $path")
        else if (file.isDirectory()) Validated.invalidNel(s"Given file is a directory: $path")
        else Validated.valid(file)
      }

  private val packageNameOpt: Opts[String] =
    Opts.option[String]("package", "Generated package name", "p")

  private val objectNameOpt: Opts[Option[String]] =
    Opts
      .option[String](
        "objectName",
        "Name of object/namespace where the endpoints are going to be generated.",
        "o"
      )
      .orNone

  private val targetScala3Opt: Opts[Boolean] =
    Opts.flag("scala3", "Whether to generate Scala 3 code", "3").orFalse

  private val headTagForNamesOpt: Opts[Boolean] =
    Opts.flag("headTagForNames", "Whether to group generated endpoints by first declared tag", "t").orFalse

  private val validateNonDiscriminatedOneOfsOpt: Opts[Boolean] =
    Opts
      .flag(
        "validateNonDiscriminatedOneOfs",
        "Whether to validate that all variants of oneOfs without discriminators can be disambiguated",
        "v"
      )
      .orFalse
  private val maxSchemasPerFileOpt: Opts[Option[Int]] =
    Opts
      .option[Int]("maxSchemasPerFile", "Maximum number of schemas to generate in a single file.", "m")
      .orNone

  private val jsonLibOpt: Opts[Option[String]] =
    Opts.option[String]("jsonLib", "Json library to use for serdes", "j").orNone

  private val xmlLibOpt: Opts[Option[String]] =
    Opts.option[String]("xmlLib", "XML library to use for serdes", "x").orNone

  private val streamingImplementationOpt: Opts[Option[String]] =
    Opts.option[String]("streamingImplementation", "Capability to use for binary streams", "s").orNone

  private val generateEndpointTypesOpt: Opts[Boolean] =
    Opts.flag("generateEndpointTypes", "Whether to emit explicit type aliases for endpoint declarations", "e").orFalse

  private val destDirOpt: Opts[File] =
    Opts
      .option[String]("destdir", "Destination directory", "d")
      .mapValidated { path =>
        val dir = new File(path)
        if (dir.exists() && !dir.isDirectory)
          Validated.invalidNel(
            s"Destination given is a file when it should be a directory: $path"
          )
        else Validated.valid(dir)
      }

  val cmd: Command[IO[ExitCode]] = Command("genscala", "Generate Scala classes", helpFlag = true) {
    (
      fileOpt,
      packageNameOpt,
      destDirOpt,
      objectNameOpt,
      targetScala3Opt,
      headTagForNamesOpt,
      jsonLibOpt,
      xmlLibOpt,
      validateNonDiscriminatedOneOfsOpt,
      maxSchemasPerFileOpt,
      streamingImplementationOpt,
      generateEndpointTypesOpt
    )
      .mapN {
        case (
              file,
              packageName,
              destDir,
              maybeObjectName,
              targetScala3,
              headTagForNames,
              jsonLib,
              xmlLib,
              validateNonDiscriminatedOneOfs,
              maxSchemasPerFile,
              streamingImplementation,
              generateEndpointTypes
            ) =>
          val objectName = maybeObjectName.getOrElse(DefaultObjectName)

          def generateCode(doc: OpenapiDocument): IO[Unit] = for {
            contents <- IO.pure(
              RootGenerator.generateObjects(
                doc,
                packageName,
                objectName,
                targetScala3,
                headTagForNames,
                jsonLib.getOrElse("circe"),
                xmlLib.getOrElse("cats-xml"),
                streamingImplementation.getOrElse("fs2"),
                validateNonDiscriminatedOneOfs,
                maxSchemasPerFile.getOrElse(400),
                generateEndpointTypes
              )
            )
            destFiles <- contents.toVector.traverse { case (fileName, content) => writeGeneratedFile(destDir, fileName, content) }
            _ <- IO.println(s"Generated endpoints written to: ${destFiles.mkString(", ")}")
          } yield ()

          for {
            parsed <- readFile(file).map(YamlParser.parseFile)
            exitCode <- parsed match {
              case Left(err)  => IO.println(s"Invalid YAML file: ${err.getMessage}").as(ExitCode.Error)
              case Right(doc) => generateCode(doc).as(ExitCode.Success)
            }
          } yield exitCode
      }
  }

  private def readFile(file: File): IO[String] = {
    IO(Files.readAllLines(file.toPath, StandardCharsets.UTF_8).asScala.mkString("\n"))
  }

  private def writeGeneratedFile(destDir: File, objectName: String, content: String): IO[File] = {
    val destPath = new File(destDir, s"$objectName.scala").toPath
    for {
      _ <- IO(destDir.mkdirs())
      writenFile <- IO(Files.writeString(destPath, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE))
    } yield writenFile.toFile
  }

}
