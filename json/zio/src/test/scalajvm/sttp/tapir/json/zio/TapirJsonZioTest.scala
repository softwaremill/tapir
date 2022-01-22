package sttp.tapir.json.zio

import sttp.tapir.generic.auto._
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.{JsonCodec => TapirJsonCodec}
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.{DecodeResult, FieldName, Schema, SchemaType}
import sttp.tapir.DecodeResult.Value
import sttp.tapir.SchemaType.{SCoproduct, SProduct}
import zio.json._
import zio.json.ast.Json

class TapirJsonZioTest extends AnyFlatSpecLike with Matchers {

  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])
  case class Item(serialNumber: Long, price: Int)
  case class Order(items: Seq[Item], customer: Customer)

  implicit val customerZioCodec: JsonCodec[Customer] = DeriveJsonCodec.gen[Customer]
  implicit val itemZioCodec: JsonCodec[Item] = DeriveJsonCodec.gen[Item]
  implicit val orderZioCodec: JsonCodec[Order] = DeriveJsonCodec.gen[Order]

  val customerCodec: TapirJsonCodec[Customer] = zioCodec[Customer]

  def testEncodeDecode[T: Schema: zio.json.JsonEncoder: zio.json.JsonDecoder](original: T): Assertion = {
    val codec = zioCodec[T]
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

  it should "encode and decode Scala case class with list" in {
    val order = Order(Seq(Item(100, 200), Item(101, 300)), Customer("Alita", 1985, None))
    testEncodeDecode(order)
  }

  it should "encode to non-prettified Json" in {
    val customer = Customer("Alita", 1985, None)
    val codec = zioCodec[Customer]
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
    val actual = zioCodec[Seq[Item]].decode(input)
    actual shouldBe a[DecodeResult.Error]

    val failure = actual.asInstanceOf[DecodeResult.Error]
    failure.original shouldEqual input
    failure.error shouldBe a[JsonDecodeException]

    val error = failure.error.asInstanceOf[JsonDecodeException]
    error.errors shouldEqual
      List(JsonError("missing", List(FieldName("[0]"), FieldName("serialNumber"))))
    error.underlying shouldBe a[Exception]
  }

  it should "return a coproduct schema for a JsonValue" in {
    schemaForZioJsonValue.schemaType shouldBe a[SCoproduct[_]]
  }

  it should "return a coproduct schema for a JsonObject" in {
    schemaForZioJsonObject.schemaType shouldBe a[SProduct[_]]
  }

  it should "represent big decimals as numbers" in {
    val n = BigDecimal(10)
    implicitly[JsonEncoder[BigDecimal]].toJsonAST(n) shouldBe Right(Json.Num(10))
    implicitly[Schema[BigDecimal]] shouldBe Schema(SchemaType.SNumber())
  }
}
