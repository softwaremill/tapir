package sttp.tapir.codegen

import sttp.tapir.codegen.openapi.models.OpenapiModels.OpenapiDocument
import io.circe.yaml.parser

object YamlParser {
  import cats.implicits._
  import io.circe._

  def parseFile(yamlString: String): Either[Error, OpenapiDocument] = {
    parser
      .parse(yamlString)
      .leftMap(err => err: Error)
      .flatMap(_.as[OpenapiDocument])
  }
}
