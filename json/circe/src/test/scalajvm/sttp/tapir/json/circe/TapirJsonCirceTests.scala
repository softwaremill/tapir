package sttp.tapir.json.circe

import io.circe.DecodingFailure
import io.circe.generic.auto._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.SchemaType.SProduct
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
      List(JsonError("Attempt to decode value on failed cursor", List(FieldName("name"))))
    error.underlying shouldBe a[DecodingFailure]
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
      List(JsonError("Attempt to decode value on failed cursor", List(FieldName("[0]"), FieldName("serialNumber"))))
    error.underlying shouldBe a[DecodingFailure]
  }

  it should "return a product schema for a Json" in {
    schemaForCirceJson.schemaType shouldBe a[SProduct[_]]
  }

  it should "return a product schema for a JsonObject" in {
    schemaForCirceJsonObject.schemaType shouldBe a[SProduct[_]]
  }

}
