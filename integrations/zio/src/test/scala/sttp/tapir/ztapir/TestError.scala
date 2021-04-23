package sttp.tapir.ztapir

import sttp.tapir.{Codec, CodecFormat, DecodeResult, Schema}
import sttp.tapir.CodecFormat.TextPlain

sealed trait TestError

object TestError {
  case object SomeError extends TestError

  implicit val schema: Schema[TestError] =
    Schema.schemaForString.map(value => if (value == "SomeError") Some(SomeError: TestError) else None)(_.toString)

  implicit val codec: Codec[String, TestError, TextPlain] = Codec.anyStringCodec(CodecFormat.TextPlain()) {
    case "SomeError" => DecodeResult.Value(SomeError: TestError)
    case value       => DecodeResult.Error(value, new RuntimeException(s"Unable to decode value $value"))
  }(_.toString)
}
