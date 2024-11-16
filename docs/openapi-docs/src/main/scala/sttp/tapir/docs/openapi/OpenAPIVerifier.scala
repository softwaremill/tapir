package sttp.tapir.docs.openapi

import sttp.apispec.openapi.OpenAPI
import sttp.apispec.openapi.circe._
import sttp.tapir.AnyEndpoint
import sttp.tapir.docs.openapi.OpenAPIVerifier.Mode
import sttp.tapir.docs.openapi.OpenAPIVerifier.Mode.{AtLeast, AtMost, Exact}
import sttp.tapir.openapi.OpenAPIComparator
import io.circe.parser._

object OpenAPIVerifier {
  sealed trait Mode
  object Mode {
    case object Exact extends Mode
    case object AtLeast extends Mode
    case object AtMost extends Mode
  }

  def verify(endpoints: List[AnyEndpoint], openApiSpec: String, mode: Mode): Either[String, Unit] = {
    parse(openApiSpec) match {
      case Left(error) => Left(s"Failed to parse OpenAPI spec: ${error.getMessage}")
      case Right(json) =>
        json.as[OpenAPI] match {
          case Left(error) => Left(s"Failed to decode OpenAPI spec: ${error.getMessage}")
          case Right(openApi) =>
            val generatedOpenApi = OpenAPIDocsInterpreter().toOpenAPI(endpoints, openApi.info)
            val comparator = new OpenAPIComparator

            mode match {
              case Exact =>
                comparator.compare(generatedOpenApi, openApi) match {
                  case Nil => Right(())
                  case issues => Left(s"Incompatibilities found: ${issues.mkString(", ")}")
                }
              case AtLeast =>
                comparator.compareAtLeast(generatedOpenApi, openApi) match {
                  case Nil => Right(())
                  case issues => Left(s"Incompatibilities found: ${issues.mkString(", ")}")
                }
              case AtMost =>
                comparator.compareAtMost(generatedOpenApi, openApi) match {
                  case Nil => Right(())
                  case issues => Left(s"Incompatibilities found: ${issues.mkString(", ")}")
                }
            }
        }
    }
  }
}
