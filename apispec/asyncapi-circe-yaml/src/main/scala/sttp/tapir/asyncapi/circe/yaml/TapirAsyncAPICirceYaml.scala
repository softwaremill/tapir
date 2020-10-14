package sttp.tapir.asyncapi.circe.yaml

import sttp.tapir.asyncapi.circe._
import io.circe.syntax._
import io.circe.yaml.Printer
import sttp.tapir.asyncapi.AsyncAPI

trait TapirAsyncAPICirceYaml {
  implicit class RichAsyncAPI(asyncAPI: AsyncAPI) {
    def toYaml: String = Printer(dropNullKeys = true, preserveOrder = true).pretty(asyncAPI.asJson)
  }
}
