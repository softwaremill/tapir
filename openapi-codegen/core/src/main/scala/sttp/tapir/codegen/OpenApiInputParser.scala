package sttp.tapir.codegen

import cats.implicits._
import io.circe.Error
import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument

import java.io.File
import java.nio.charset.StandardCharsets

import scala.io.Source

object OpenApiInputParser {

  private val SpecExtensions = Set(".yaml", ".yml", ".json")

  def parse(input: File): Either[Error, OpenapiDocument] =
    if (input.isDirectory) parseDirectory(input)
    else parseFile(input)

  def parseDirectory(dir: File): Either[Error, OpenapiDocument] = {
    val files = listSpecFiles(dir).sortBy(_.getPath)
    if (files.isEmpty)
      Left(io.circe.DecodingFailure(s"No OpenAPI spec files found in directory ${dir.getPath}", Nil))
    else
      files.map(parseFile).toList.sequence.map(OpenApiMerger.merge)
  }

  def parseFile(file: File): Either[Error, OpenapiDocument] =
    YamlParser.parseFile(readFile(file))

  private def readFile(file: File): String = {
    val source = Source.fromFile(file, StandardCharsets.UTF_8.name())
    try source.mkString
    finally source.close()
  }

  private def listSpecFiles(dir: File): Seq[File] = {
    def recurse(current: File): Seq[File] = {
      val children = Option(current.listFiles()).getOrElse(Array.empty[File]).toSeq
      children.flatMap { child =>
        if (child.isDirectory) recurse(child)
        else if (SpecExtensions.exists(ext => child.getName.toLowerCase.endsWith(ext))) Seq(child)
        else Nil
      }
    }
    recurse(dir)
  }
}
