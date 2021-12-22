package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AttributeTest extends AnyFlatSpec with Matchers {
  it should "create an attribute key for a case class" in {
    AttributeKey[AttributeTestData].typeName shouldBe "sttp.tapir.AttributeTestData"
  }

  it should "create an attribute key for a list of case classes" in {
    // in scala3, List is printed with the package (scala.collection.immutable)
    AttributeKey[List[AttributeTestData]].typeName should endWith("List[sttp.tapir.AttributeTestData]")
  }

  it should "create an attribute key for a list of generic case classes" in {
    val result = AttributeKey[List[AttributeTestDataGeneric[Int]]].typeName
    result should include("List[sttp.tapir.AttributeTestDataGeneric[")
    result should include("Int]]")
  }
}

case class AttributeTestData(value: String)
case class AttributeTestDataGeneric[T](value: T)
