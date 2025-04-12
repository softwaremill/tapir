package to.inject

object MyModels {
  case class StringWrapper(s: String) extends AnyVal
  case class IntWrapper(s: Int) extends AnyVal

  implicit val stringWrapperJsonDecoder: io.circe.Decoder[StringWrapper] = io.circe.generic.semiauto.deriveDecoder[StringWrapper]
  implicit val stringWrapperJsonEncoder: io.circe.Encoder[StringWrapper] = io.circe.generic.semiauto.deriveEncoder[StringWrapper]
  implicit val intWrapperJsonDecoder: io.circe.Decoder[IntWrapper] = io.circe.generic.semiauto.deriveDecoder[IntWrapper]
  implicit val intWrapperJsonEncoder: io.circe.Encoder[IntWrapper] = io.circe.generic.semiauto.deriveEncoder[IntWrapper]

  implicit val intWrapperTapirSchema: sttp.tapir.Schema[IntWrapper] = sttp.tapir.Schema.derived
  implicit val stringWrapperTapirSchema: sttp.tapir.Schema[StringWrapper] = sttp.tapir.Schema.derived
}
