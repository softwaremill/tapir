package sttp.tapir.json.spray

import java.nio.charset.StandardCharsets

import spray.json._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.SchemaType._
import sttp.tapir._

import scala.util.{Failure, Success, Try}

trait TapirJsonSpray {
  implicit def jsonFormatCodec[T: JsonFormat: Schema]: JsonCodec[T] = new JsonCodec[T] {
    override def encode(t: T): String = t.toJson.toString()

    override def rawDecode(s: String): DecodeResult[T] = Try(s.parseJson.convertTo[T]) match {
      case Success(v) => Value(v)
      case Failure(e) => Error("spray json decoder failed", e)
    }

    override def meta: CodecMeta[T, CodecFormat.Json, String] =
      CodecMeta(implicitly[Schema[T]], CodecFormat.Json(), StringValueType(StandardCharsets.UTF_8))
  }

  implicit val schemaForSprayJsValue: Schema[JsValue] = Schema(
    SProduct(
      SObjectInfo("spray.json.JsValue"),
      List.empty
    )
  )
}
