package sttp.tapir.json.json4s.jackson

import org.json4s.jackson.Serialization
import org.json4s.{Formats, NoTypeHints}
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.Schema
import sttp.tapir.json.json4s.TapirJson4sTests

class TapirJson4sJacksonTests extends TapirJson4sTests {

  implicit val formats: Formats = Serialization.formats(NoTypeHints)

  override def json4sCodec[T: Manifest: Schema]: JsonCodec[T] = json4sJacksonCodec[T]
}
