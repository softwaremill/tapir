package sttp.tapir.json.play

import org.scalatest.Assertion
import play.api.libs.json._
import sttp.tapir._
import sttp.tapir.DecodeResult._
import sttp.tapir.generic.auto._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.SchemaType.{SCoproduct, SProduct}

object TapirJsonPlayCodec extends TapirJsonPlay

class TapirJsonPlayTests extends AnyFlatSpec with TapirJsonPlayTestExtensions with Matchers {
  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])
  object Customer {
    implicit val rw: Format[Customer] = Json.format
  }

  case class Item(serialNumber: Long, price: Int)
  object Item {
    implicit val itemFmt: Format[Item] = Json.format
  }

  case class Order(items: Seq[Item], customer: Customer)
  object Order {
    implicit val orderFmt: Format[Order] = Json.format
  }

  val customerCodec: JsonCodec[Customer] = TapirJsonPlayCodec.readsWritesCodec[Customer]
  val orderCodec: JsonCodec[Order] = TapirJsonPlayCodec.readsWritesCodec[Order]

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

  it should "return a JSON specific error on object decode failure" in {
    val input = """{"items":[{"serialNumber":1}], "customer":{"name":"Alita"}}"""

    val actual = orderCodec.decode(input)
    actual shouldBe a[DecodeResult.Error]

    val failure = actual.asInstanceOf[DecodeResult.Error]
    failure.original shouldEqual input
    failure.error shouldBe a[JsonDecodeException]

    val error = failure.error.asInstanceOf[JsonDecodeException]
    error.errors should contain theSameElementsAs List(
      JsonError("error.path.missing", List(FieldName("obj"), FieldName("customer"), FieldName("yearOfBirth"))),
      JsonError("error.path.missing", List(FieldName("obj"), FieldName("items[0]"), FieldName("price")))
    )
    error.underlying shouldBe a[JsResultException]
  }

  it should "return a JSON specific error on array decode failure" in {
    val itemsCodec = TapirJsonPlayCodec.readsWritesCodec[Seq[Item]]
    val input = """[{"serialNumber":1}]"""

    val actual = itemsCodec.decode(input)
    actual shouldBe a[DecodeResult.Error]

    val failure = actual.asInstanceOf[DecodeResult.Error]
    failure.original shouldEqual input
    failure.error shouldBe a[JsonDecodeException]

    val error = failure.error.asInstanceOf[JsonDecodeException]
    error.errors shouldEqual List(
      JsonError("error.path.missing", List(FieldName("obj[0]"), FieldName("price")))
    )
    error.underlying shouldBe a[JsResultException]
  }

  it should "return a coproduct schema for a JsValue" in {
    schemaForPlayJsValue.schemaType shouldBe a[SCoproduct[_]]
  }

  it should "return a product schema for a JsObject" in {
    schemaForPlayJsObject.schemaType shouldBe a[SProduct[_]]
  }

}
