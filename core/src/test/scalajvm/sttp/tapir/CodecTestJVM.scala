package sttp.tapir

import java.time._
import java.util.Date

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir.DecodeResult.Value

class CodecTestJVM extends AnyFlatSpec with Matchers with Checkers {

  // while Date works on Scala.js, ScalaCheck tests involving Date use java.util.Calendar which doesn't
  it should "correctly encode and decode Date" in {
    val codec = implicitly[Codec[String, Date, TextPlain]]
    check { d: Date =>
      val encoded = codec.encode(d)
      codec.decode(encoded) == Value(d) && Date.from(Instant.parse(encoded)) == d
    }
  }

}
