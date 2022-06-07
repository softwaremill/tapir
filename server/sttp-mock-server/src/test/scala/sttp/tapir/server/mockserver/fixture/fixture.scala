package sttp.tapir.server.mockserver.fixture

import io.circe.{Codec, Printer}
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.json.circe.TapirJsonCirce

import java.util.UUID

case class CreatePersonCommand(name: String, age: Int)

object CreatePersonCommand {
  implicit val codec: Codec.AsObject[CreatePersonCommand] = deriveCodec[CreatePersonCommand]
}

case class PersonView(id: String, name: String, age: Int)

object PersonView {
  implicit val codec: Codec.AsObject[PersonView] = deriveCodec[PersonView]
}

case class ApiError(code: Int, message: String)

object ApiError {
  implicit val codec: Codec.AsObject[ApiError] = deriveCodec[ApiError]
}

case class CreateOrderCommand(name: String, total: Option[Int])

object CreateOrderCommand {
  implicit val codec: Codec.AsObject[CreateOrderCommand] = deriveCodec[CreateOrderCommand]
}

case class OrderCreatedEvent(id: String, name: String, total: Option[Int])

object OrderCreatedEvent {
  implicit val codec: Codec.AsObject[OrderCreatedEvent] = deriveCodec[OrderCreatedEvent]
}

object TapirJsonCirceWithDropNullDisabled extends TapirJsonCirce {
  override def jsonPrinter: Printer = Printer.noSpaces.copy(dropNullValues = false)
}

object TapirJsonCirceWithDropNullEnabled extends TapirJsonCirce {
  override def jsonPrinter: Printer = Printer.noSpaces.copy(dropNullValues = true)
}
