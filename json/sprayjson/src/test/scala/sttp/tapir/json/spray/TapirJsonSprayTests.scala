package sttp.tapir.json.spray

import org.scalatest.Assertion
import sttp.tapir._
import sttp.tapir.DecodeResult._
import sttp.tapir.generic.auto._
import spray.json._
import sttp.tapir.Codec.JsonCodec
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}

object TapirJsonSprayCodec extends TapirJsonSpray

class TapirJsonSprayTests extends AnyFlatSpec with Matchers with DefaultJsonProtocol {
  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])
  object Customer {
    implicit val rw: JsonFormat[Customer] = jsonFormat3(Customer.apply)
  }

  case class Item(serialNumber: Long, price: Int)
  object Item {
    implicit val itemFmt: JsonFormat[Item] = jsonFormat2(Item.apply)
  }

  case class Order(items: Seq[Item], customer: Customer)
  object Order {
    implicit val orderFmt: JsonFormat[Order] = jsonFormat2(Order.apply)
  }

  val customerCodec: JsonCodec[Customer] = TapirJsonSprayCodec.jsonFormatCodec[Customer]
  val orderCodec: JsonCodec[Order] = TapirJsonSprayCodec.jsonFormatCodec[Order]

  // Helper to test encoding then decoding an object is the same as the original
  def testEncodeDecode[T: JsonFormat: Schema](original: T): Assertion = {
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

  it should "return a JSON specific decode error on failure" in {
    val input = """{"items":[], "customer":{}}"""

    val actual = orderCodec.decode(input)
    actual shouldBe a[DecodeResult.Error]

    val failure = actual.asInstanceOf[Error]
    failure.original shouldEqual input
    failure.error shouldBe a[JsonDecodeException]

    val error = failure.error.asInstanceOf[JsonDecodeException]
    error.errors shouldEqual List(
      JsonError(
        "Object is missing required member 'name'",
        List(FieldName("customer"), FieldName("name"))
      )
    )
    error.underlying shouldBe a[DeserializationException]
  }

  it should "return a JSON specific error on array decode failure" in {
    val itemsCodec = TapirJsonSprayCodec.jsonFormatCodec[Seq[Item]]
    val input = """[{"serialNumber":1}]"""

    val actual = itemsCodec.decode(input)
    actual shouldBe a[DecodeResult.Error]

    val failure = actual.asInstanceOf[DecodeResult.Error]
    failure.original shouldEqual input
    failure.error shouldBe a[JsonDecodeException]

    val error = failure.error.asInstanceOf[JsonDecodeException]
    error.errors shouldEqual List(
      JsonError("Object is missing required member 'price'", List(FieldName("price")))
    )
    error.underlying shouldBe a[DeserializationException]
  }
}
