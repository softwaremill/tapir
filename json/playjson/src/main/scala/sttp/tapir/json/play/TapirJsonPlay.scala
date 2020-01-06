package sttp.tapir.json.play

import play.api.libs.json._
import sttp.tapir._
import sttp.tapir.SchemaType._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.{Error, Value}

import java.nio.charset.StandardCharsets

trait TapirJsonPlay {
  implicit def readsWritesCodec[T: Reads: Writes: Schema]: JsonCodec[T] = new JsonCodec[T] {
    override def rawDecode(s: String): DecodeResult[T] = implicitly[Reads[T]].reads(Json.parse(s)) match {
      case JsError(errors)     => Error(s, JsResultException(errors))
      case JsSuccess(value, _) => Value(value)
    }
    override def encode(t: T): String = Json.prettyPrint(Json.toJson(t))
    override def meta: CodecMeta[T, CodecFormat.Json, String] =
      CodecMeta(implicitly[Schema[T]], CodecFormat.Json(), StringValueType(StandardCharsets.UTF_8))
  }

  implicit val schemaForPlayJsValue: Schema[JsValue] = Schema(
    SProduct(
      SObjectInfo("play.api.libs.json.JsValue"),
      List.empty
    )
  )
}
