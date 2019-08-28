package tapir.json.play

import play.api.libs.json._
import tapir._
import tapir.Schema._
import tapir.Codec.JsonCodec
import tapir.DecodeResult.{Error, Value}

import java.nio.charset.StandardCharsets

trait TapirJsonPlay {
  implicit def readsWritesCodec[T: Reads: Writes: SchemaFor]: JsonCodec[T] = new JsonCodec[T] {
    override def decode(s: String): DecodeResult[T] = implicitly[Reads[T]].reads(Json.parse(s)) match {
      case JsError(errors)     => Error(s, JsResultException(errors))
      case JsSuccess(value, _) => Value(value)
    }
    override def encode(t: T): String = Json.prettyPrint(Json.toJson(t))
    override def meta: CodecMeta[MediaType.Json, String] =
      CodecMeta(implicitly[SchemaFor[T]].schema, MediaType.Json(), StringValueType(StandardCharsets.UTF_8))
  }

  implicit val schemaForPlayJsValue: SchemaFor[JsValue] = SchemaFor(
    SProduct(
      SObjectInfo("play.api.libs.json.JsValue"),
      List.empty,
      List.empty
    )
  )
}
