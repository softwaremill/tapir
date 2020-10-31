package sttp.tapir

import java.time._
import java.util.Date

import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult.Value

trait CodecTestExtensions { self: CodecTest =>
  // while Date works on Scala.js, ScalaCheck tests involving Date use java.util.Calendar which doesn't
  it should "correctly encode and decode Date" in {
    val codec = implicitly[Codec[String, Date, TextPlain]]
    check { d: Date =>
      val encoded = codec.encode(d)
      codec.decode(encoded) == Value(d) && Date.from(Instant.parse(encoded)) == d
    }
  }
}
