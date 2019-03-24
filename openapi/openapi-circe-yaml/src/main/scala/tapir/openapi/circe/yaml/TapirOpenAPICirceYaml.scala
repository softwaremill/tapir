package tapir.openapi.circe.yaml

import tapir.openapi.OpenAPI
import tapir.openapi.circe._
import io.circe.syntax._
import io.circe.yaml.Printer

trait TapirOpenAPICirceYaml {
  implicit class RichOpenAPI(openAPI: OpenAPI) {
    def toYaml: String = Printer(dropNullKeys = true, preserveOrder = true).pretty(openAPI.asJson)
  }
}
