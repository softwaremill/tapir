package sttp.tapir.openapi.circe

import io.circe.syntax.EncoderOps
import io.circe.yaml.Printer
import sttp.tapir.openapi.OpenAPI

trait TapirOpenAPICirceToYaml {
  implicit class RichOpenAPI(openAPI: OpenAPI) {
    def convertToYaml: String = Printer(dropNullKeys = true, preserveOrder = true).pretty(openAPI.asJson)
  }
}
