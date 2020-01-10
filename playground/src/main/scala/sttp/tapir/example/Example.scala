package sttp.tapir.example

import io.circe.generic.JsonCodec
import sttp.tapir._
import sttp.model.StatusCode
import sttp.tapir.docs.openapi._
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.json.circe._

object Example extends App {
  @JsonCodec
  case class Book(author: String, title: String, year: Int)

}
