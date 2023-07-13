package sttp.tapir.ztapir

import sttp.tapir.{Codec, DecodeResult}
import sttp.tapir.CodecFormat.TextPlain

import scala.util.control.NoStackTrace
import scala.util.matching.Regex

case class TestErrorThrowable(message: String) extends Throwable(message) with NoStackTrace

object TestErrorThrowable {
  val errorPattern: Regex = """TestErrorThrowable\((.*)\)""".r

  implicit val codec: Codec[String, TestErrorThrowable, TextPlain] = Codec.string.mapDecode {
    case errorPattern(message) => DecodeResult.Value(TestErrorThrowable(message))
    case value => DecodeResult.Error(value, new RuntimeException(s"Unable to decode value $value"))
  }(_.toString)

}
