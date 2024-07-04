package sttp.tapir.json.json4s

import org.json4s._
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Error.JsonDecodeException
import sttp.tapir.DecodeResult.Value
import sttp.tapir.SchemaType.SCoproduct
import sttp.tapir.generic.auto._
import sttp.tapir.{DecodeResult, Schema}

case class Customer(name: String, yearOfBirth: Int, lastPurchase: Option[Long])
case class Item(serialNumber: Long, price: Int)
case class Order(items: Seq[Item], customer: Customer)

// the tests are run only for scala 2 because https://github.com/json4s/json4s/issues/1035 is only fixed in
// versions for scala 3.4+ (not LTS)
class TapirJson4sTests extends AnyFlatSpecLike with Matchers {

  implicit val serialization: Serialization = org.json4s.jackson.Serialization
  implicit val formats: Formats = org.json4s.jackson.Serialization.formats(NoTypeHints)

  def testEncodeDecode[T: Manifest: Schema](original: T): Assertion = {
    val codec = json4sCodec[T]
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
    val codec = json4sCodec[Customer]
    val expected = """{"name":"Alita","yearOfBirth":1985}"""
    codec.encode(customer) shouldBe expected
  }

  it should "return a JSON specific error on object decode failure" in {
    val input = """{"items":[]}"""

    val codec = json4sCodec[Customer]
    val actual = codec.decode(input)
    actual shouldBe a[DecodeResult.Error]

    val failure = actual.asInstanceOf[DecodeResult.Error]
    failure.original shouldEqual input
    failure.error shouldBe a[JsonDecodeException]

    val error = failure.error.asInstanceOf[JsonDecodeException]
    error.underlying shouldBe a[MappingException]
  }

  it should "return a coproduct schema for a JValue" in {
    schemaForJson4s.schemaType shouldBe a[SCoproduct[_]]
  }
}
