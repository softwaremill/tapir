package sttp.tapir.json.play

import org.scalatest.{FlatSpec, Matchers, Assertion}
import play.api.libs.json._
import sttp.tapir._
import sttp.tapir.DecodeResult._

import java.util.Date

object TapirJsonPlayCodec extends TapirJsonPlay

class TapirJsonPlayTests extends FlatSpec with Matchers {
  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])

  object Customer {
    implicit val rw: Format[Customer] = Json.format
  }

  val customerDecoder = TapirJsonPlayCodec.readsWritesCodec[Customer]

  // Helper to test encoding then decoding an object is the same as the original
  def testEncodeDecode[T: Format: Schema: Validator](original: T): Assertion = {
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

  it should "encode and decode using custom Date serializer" in {
    val d = new Date
    testEncodeDecode(d)
  }

  it should "Fail to encode a badly formatted date" in {
    val codec = TapirJsonPlayCodec.readsWritesCodec[Date]
    val encoded = "\"OOPS-10-10 11:20:49.029\""

    codec.decode(encoded) match {
      case _: DecodeResult.Failure =>
        succeed
      case Value(d) =>
        fail(s"Should not have been able to decode this date: $d")
    }
  }

  it should "encode to non-prettified Json" in {
    val customer = Customer("Alita", 1985, None)
    val codec = TapirJsonPlayCodec.readsWritesCodec[Customer]
    val expected = """{"name":"Alita","yearOfBirth":1985}"""
    codec.encode(customer) shouldBe expected
  }
}
