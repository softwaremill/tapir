package sttp.tapir.json.weepickle

import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import sttp.tapir.DecodeResult._
import sttp.tapir._
import sttp.tapir.generic.auto._
import com.rallyhealth.weepickle.v1.WeePickle._
import scala.annotation.nowarn
import com.rallyhealth.weepickle.v1.core.TransformException
// import com.rallyhealth.weejson.v1.jackson._

object TapirJsonWeePickleCodec extends TapirJsonWeePickle

class TapirJsonWeePickleTests extends AnyFlatSpec with Matchers {
  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])

  object Customer {
    @nowarn
    implicit val rw: FromTo[Customer] = macroFromTo[Customer]
  }

  val customerDecoder: JsonCodec[Customer] = TapirJsonWeePickleCodec.fromToCodec

  // Helper to test encoding then decoding an object is the same as the original
  def testEncodeDecode[T: FromTo: Schema](original: T): Assertion = {
    val codec = TapirJsonWeePickleCodec.fromToCodec[T]

    val encoded = codec.encode(original)
    codec.decode(encoded) match {
      case Value(d) =>
        d shouldBe original
      case f: DecodeResult.Failure =>
        fail(f.toString)
    }
  }

  it should "encode and decode Scala case class with non-empty Option elements" in {
    val customer = Customer("Alita", 1985, Some(1566150331L))
    testEncodeDecode(customer)
  }

  it should "encode and decode Scala case class with empty Option elements" in {
    val customer = Customer("Alita", 1985, None)
    testEncodeDecode(customer)
  }

  it should "encode and decode String type" in {
    testEncodeDecode("Hello, World!")
  }

  it should "encode and decode Long type" in {
    testEncodeDecode(1566150331L)
  }

  it should "return a JSON specific decode error on failure" in {
    val codec = TapirJsonWeePickleCodec.fromToCodec[Customer]

    val actual = codec.decode("{}")
    actual shouldBe a[DecodeResult.Error]

    val failure = actual.asInstanceOf[DecodeResult.Error]
    failure.original shouldEqual "{}"
    failure.error shouldBe a[JsonDecodeException]

    val error = failure.error.asInstanceOf[JsonDecodeException]
    error.errors shouldEqual List.empty
    error.underlying shouldBe an[TransformException]
  }
}
