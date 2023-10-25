package sttp.tapir.json.circe

import io.circe.{Errors, JsonObject}
import io.circe.generic.auto._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.SchemaType.{SCoproduct, SProduct}
import sttp.tapir.generic.auto._
import sttp.tapir.{DecodeResult, FieldName}

class TapirJsonCirceTests extends AnyFlatSpecLike with Matchers {

  case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])
  case class Item(serialNumber: Long, price: Int)
  case class Order(items: Seq[Item], customer: Customer)

  val customerCodec: JsonCodec[Customer] = circeCodec[Customer]

  it should "return a JSON specific error on object decode failure" in {
    val input = """{"items":[]}"""

    val actual = customerCodec.decode(input)
    actual shouldBe a[DecodeResult.Error]

    val failure = actual.asInstanceOf[DecodeResult.Error]
    failure.original shouldEqual input
    failure.error shouldBe a[JsonDecodeException]

    val error = failure.error.asInstanceOf[JsonDecodeException]
    error.errors shouldEqual
      List(
        JsonError("Missing required field", List(FieldName("name"))),
        JsonError("Missing required field", List(FieldName("yearOfBirth")))
      )
  }

  it should "return a JSON specific error on array decode failure" in {
    val input = """[{}]"""

    val actual = circeCodec[Seq[Item]].decode(input)
    actual shouldBe a[DecodeResult.Error]

    val failure = actual.asInstanceOf[DecodeResult.Error]
    failure.original shouldEqual input
    failure.error shouldBe a[JsonDecodeException]

    val error = failure.error.asInstanceOf[JsonDecodeException]
    error.errors shouldEqual
      List(
        JsonError("Missing required field", List(FieldName("[0]"), FieldName("serialNumber"))),
        JsonError("Missing required field", List(FieldName("[0]"), FieldName("price")))
      )
    error.underlying shouldBe a[Errors]
  }

  it should "include JSON specific errors when converting a decode failure to a string" in {
    val input = """{"items":[]}"""

    val asString = customerCodec.decode(input).toString

    asString should startWith("Error")
    asString should include("Missing required field")
  }

  it should "return a coproduct schema for a Json" in {
    schemaForCirceJson.schemaType shouldBe a[SCoproduct[_]]
  }

  it should "return a product schema for a JsonObject" in {
    schemaForCirceJsonObject.schemaType shouldBe a[SProduct[_]]
  }

  it should "properly define a json query input" in {
    jsonQuery[Option[JsonObject]]("content").codec.decode(Nil) shouldBe DecodeResult.Value(None)
    jsonQuery[Option[JsonObject]]("content").codec.decode(List("")) shouldBe DecodeResult.Value(None)
    jsonQuery[Option[JsonObject]]("content").codec.decode(List("{}")) shouldBe DecodeResult.Value(Some(JsonObject.empty))

    jsonQuery[JsonObject]("content").codec.decode(Nil) shouldBe DecodeResult.Missing
    jsonQuery[JsonObject]("content").codec.decode(List("")) shouldBe a[DecodeResult.Error]
    jsonQuery[JsonObject]("content").codec.decode(List("{}")) shouldBe DecodeResult.Value(JsonObject.empty)
  }
}
