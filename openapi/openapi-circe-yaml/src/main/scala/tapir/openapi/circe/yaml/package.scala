package tapir.openapi.circe

import tapir.openapi.OpenAPI
import io.circe.syntax._
import io.circe.yaml.Printer

package object yaml {
  implicit class RichOpenAPI(val openAPI: OpenAPI) extends AnyVal {
    def toYaml: String = Printer(dropNullKeys = true, preserveOrder = true).pretty(openAPI.asJson)
  }
}
