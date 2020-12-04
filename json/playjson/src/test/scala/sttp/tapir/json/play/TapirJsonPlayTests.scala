package sttp.tapir.json.play

import org.scalatest.Assertion
import play.api.libs.json._
import sttp.tapir._
import sttp.tapir.DecodeResult._
import sttp.tapir.generic.auto._

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

object TapirJsonPlayCodec extends TapirJsonPlay

class TapirJsonPlayTests extends AnyFlatSpec with TapirJsonPlayTestExtensions with Matchers {
  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])

  object Customer {
    implicit val rw: Format[Customer] = Json.format
  }

  val customerDecoder = TapirJsonPlayCodec.readsWritesCodec[Customer]

  // Helper to test encoding then decoding an object is the same as the original
  def testEncodeDecode[T: Format: Schema](original: T): Assertion = {
    val codec = TapirJsonPlayCodec.readsWritesCodec[T]

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

  it should "encode to non-prettified Json" in {
    val customer = Customer("Alita", 1985, None)
    val codec = TapirJsonPlayCodec.readsWritesCodec[Customer]
    val expected = """{"name":"Alita","yearOfBirth":1985}"""
    codec.encode(customer) shouldBe expected
  }

  it should "return a JSON specific decode error on failure" in {
    val codec = TapirJsonPlayCodec.readsWritesCodec[String]
    val actual = codec.decode("[]")
    actual shouldBe a[DecodeResult.InvalidJson]
    actual.asInstanceOf[DecodeResult.InvalidJson].json shouldEqual "[]"
  }
}
