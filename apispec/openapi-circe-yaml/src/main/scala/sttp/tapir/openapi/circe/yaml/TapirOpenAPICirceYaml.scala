package sttp.tapir.openapi.circe.yaml

import io.circe.syntax._
import io.circe.yaml.Printer
import io.circe.yaml.Printer.StringStyle
import sttp.apispec.openapi.OpenAPI
import sttp.apispec.openapi.circe._

trait TapirOpenAPICirceYaml {
  implicit class RichOpenAPI(openAPI: OpenAPI) {
    def toYaml: String = Printer(dropNullKeys = true, preserveOrder = true).pretty(openAPI.asJson)
    def toYaml(stringStyle: StringStyle): String =
      Printer(dropNullKeys = true, preserveOrder = true, stringStyle = stringStyle).pretty(openAPI.asJson)
  }
}
