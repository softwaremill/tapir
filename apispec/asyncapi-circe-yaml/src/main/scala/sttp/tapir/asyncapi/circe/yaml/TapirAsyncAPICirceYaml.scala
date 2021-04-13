package sttp.tapir.asyncapi.circe.yaml

import io.circe.syntax._
import io.circe.yaml.Printer
import io.circe.yaml.Printer.StringStyle
import sttp.tapir.asyncapi.AsyncAPI
import sttp.tapir.asyncapi.circe._

trait TapirAsyncAPICirceYaml {
  implicit class RichAsyncAPI(asyncAPI: AsyncAPI) {
    def toYaml: String = Printer(dropNullKeys = true, preserveOrder = true).pretty(asyncAPI.asJson)
    def toYaml(stringStyle: StringStyle): String =
      Printer(dropNullKeys = true, preserveOrder = true, stringStyle = stringStyle).pretty(asyncAPI.asJson)
  }
}
