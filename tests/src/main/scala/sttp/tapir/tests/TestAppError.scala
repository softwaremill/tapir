package sttp.tapir.tests

import io.circe.Codec
import io.circe.{Decoder, HCursor}
import io.circe.Json

sealed trait TestAppError extends Product with Serializable
object TestAppError {
    case object ErrorA extends TestAppError
    case object ErrorB extends TestAppError
    implicit val codec: Codec[TestAppError] = new Codec[TestAppError] {

      override def apply(c: HCursor): Decoder.Result[TestAppError] = ???

      override def apply(a: TestAppError): Json = ???
 

    }
}
