package tapir.json.play
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import tapir._
import tapir.Schema._
import tapir.Codec.JsonCodec
import play.api.libs.json.Json
import play.api.libs.json.JsError
import play.api.libs.json.JsSuccess
import tapir.DecodeResult.{Error, Value}
import play.api.libs.json.JsResultException
import play.api.libs.json.JsValue

trait TapirJsonPlay {
  implicit def readsWritesCodec[T: Reads: Writes: SchemaFor]: JsonCodec[T] = new JsonCodec[T] {
    override def decode(s: String): DecodeResult[T] = implicitly[Reads[T]].reads(Json.parse(s)) match {
      case JsError(errors)     => Error(s, JsResultException(errors))
      case JsSuccess(value, _) => Value(value)
    }
    override def encode(t: T): String = Json.prettyPrint(Json.toJson(t))
    override def meta: CodecMeta[MediaType.Json, String] = ???
  }

  implicit val schemaForPlayJsValue: SchemaFor[JsValue] = SchemaFor(
    SProduct(
      SObjectInfo("play.api.libs.json.JsValue"),
      List.empty,
      List.empty
    )
  )
}
