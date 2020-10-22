package sttp.tapir.json.spray

import org.scalatest.Assertion
import sttp.tapir._
import sttp.tapir.DecodeResult._
import sttp.tapir.generic.auto._
import spray.json._
import sttp.tapir.Codec.JsonCodec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

object TapirJsonSprayCodec extends TapirJsonSpray

class TapirJsonSprayTests extends AnyFlatSpec with Matchers with DefaultJsonProtocol {
  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])

  object Customer {
    implicit val rw: JsonFormat[Customer] = jsonFormat3(Customer.apply)
  }

  val customerDecoder: JsonCodec[Customer] = TapirJsonSprayCodec.jsonFormatCodec[Customer]

  // Helper to test encoding then decoding an object is the same as the original
  def testEncodeDecode[T: JsonFormat: Schema: Validator](original: T): Assertion = {
    val codec = TapirJsonSprayCodec.jsonFormatCodec[T]

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
}
