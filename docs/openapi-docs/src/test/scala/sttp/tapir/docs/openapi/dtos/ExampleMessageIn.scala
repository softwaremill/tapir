package sttp.tapir.docs.openapi.dtos

import io.circe.Json
import sttp.tapir.Schema.annotations.description

case class ExampleMessageIn(
    @description("Circe Json Option description")
    maybeJson: Option[Json] = Some(Json.fromString("test"))
)
