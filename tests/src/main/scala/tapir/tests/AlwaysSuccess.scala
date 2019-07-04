package tapir.tests

import tapir.Codec
import tapir.Codec.PlainCodec

final case class AlwaysSuccess()

object AlwaysSuccess {
  implicit val codec: PlainCodec[AlwaysSuccess] = Codec.stringPlainCodecUtf8.map(_ => AlwaysSuccess())(_ => "")
}
