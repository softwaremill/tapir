package sttp.tapir.json.json4s.native

import org.json4s.native.Serialization
import org.json4s.{Formats, NoTypeHints}
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.Schema
import sttp.tapir.json.json4s.TapirJson4sTests

class TapirJson4sNativeTests extends TapirJson4sTests {

  implicit val formats: Formats = Serialization.formats(NoTypeHints)

  override def json4sCodec[T: Manifest: Schema]: JsonCodec[T] = json4sNativeCodec[T]
}
