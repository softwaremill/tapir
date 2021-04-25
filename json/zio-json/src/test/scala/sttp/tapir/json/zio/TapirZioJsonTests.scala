package sttp.tapir.json.zio

import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.DecodeResult.Value
import sttp.tapir.SchemaType.{SCoproduct, SProduct}
import sttp.tapir.generic.auto._
import sttp.tapir.{DecodeResult, FieldName, Schema}
import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}

object TapirZioJsonCodec extends TapirZioJson

class TapirZioJsonTests extends AnyFlatSpecLike with Matchers {

  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])

  object Customer {
    implicit val customerDecoder: JsonDecoder[Customer] = DeriveJsonDecoder.gen
    implicit val customerEncoder: JsonEncoder[Customer] = DeriveJsonEncoder.gen
  }

  case class Item(serialNumber: Long, price: Int)

  object Item {
    implicit val itemDecoder: JsonDecoder[Item] = DeriveJsonDecoder.gen
    implicit val itemEncoder: JsonEncoder[Item] = DeriveJsonEncoder.gen
  }

  case class Order(items: Seq[Item], customer: Customer)

  object Order {
    implicit val orderDecoder: JsonDecoder[Order] = DeriveJsonDecoder.gen
    implicit val orderEncoder: JsonEncoder[Order] = DeriveJsonEncoder.gen
  }

  val customerCodec: JsonCodec[Customer] = zioJsonCodec[Customer]
  val orderCodec: JsonCodec[Order] = zioJsonCodec[Order]

  // Helper to test encoding then decoding an object is the same as the original
  def testEncodeDecode[T: JsonEncoder: JsonDecoder: Schema](original: T): Assertion = {
    val codec = TapirZioJsonCodec.zioJsonCodec[T]

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
    val codec = TapirZioJsonCodec.zioJsonCodec[Customer]
    val expected = """{"name":"Alita","yearOfBirth":1985}"""
    codec.encode(customer) shouldBe expected
  }

  it should "return a JSON specific error on object decode failure" in {
    val input = """{"items":[]}"""

    val actual = customerCodec.decode(input)
    actual shouldBe a[DecodeResult.Error]

    val failure = actual.asInstanceOf[DecodeResult.Error]
    failure.original shouldEqual input
    failure.error shouldBe a[JsonDecodeException]

    val error = failure.error.asInstanceOf[JsonDecodeException]
    error.errors shouldEqual
      List(JsonError("missing", List(FieldName("name"))))
    error.underlying shouldBe a[Exception]
  }

  it should "return a JSON specific error on array decode failure" in {
    val input = """[{}]"""

    val actual = zioJsonCodec[Seq[Item]].decode(input)
    actual shouldBe a[DecodeResult.Error]

    val failure = actual.asInstanceOf[DecodeResult.Error]
    failure.original shouldEqual input
    failure.error shouldBe a[JsonDecodeException]

    val error = failure.error.asInstanceOf[JsonDecodeException]
    error.errors shouldEqual
      List(JsonError("missing", List(FieldName("[0]"), FieldName("serialNumber"))))
    error.underlying shouldBe a[Exception]
  }

  it should "return a coproduct schema for a Json" in {
    schemaForZioJson.schemaType shouldBe a[SCoproduct[_]]
  }

  it should "return a product schema for a JsonObject" in {
    schemaForZioJsonObject.schemaType shouldBe a[SProduct[_]]
  }

}
