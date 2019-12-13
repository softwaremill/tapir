package sttp.tapir

import org.scalatest.{FlatSpec, Matchers}
import sttp.tapir.SchemaType._

class SchemaTest extends FlatSpec with Matchers {
  it should "modify basic schema" in {
    implicitly[Schema[String]].modifyUnsafe[String]()(_.description("test")) shouldBe implicitly[Schema[String]]
      .copy(description = Some("test"))
  }

  it should "modify product schema" in {
    val info1 = SObjectInfo("X")
    Schema(SProduct(info1, List(("f1", Schema(SString)), ("f2", Schema(SInteger)))))
      .modifyUnsafe[String]("f2")(_.description("test")) shouldBe Schema(
      SProduct(info1, List(("f1", Schema(SString)), ("f2", Schema(SInteger).description("test"))))
    )
  }

  it should "modify nested product schema" in {
    val info1 = SObjectInfo("X")
    val info2 = SObjectInfo("Y")

    val nestedProduct = Schema(SProduct(info2, List(("f1", Schema(SString)), ("f2", Schema(SInteger)))))
    val expectedNestedProduct = Schema(SProduct(info2, List(("f1", Schema(SString)), ("f2", Schema(SInteger).description("test")))))

    Schema(SProduct(info1, List(("f3", Schema(SString)), ("f4", nestedProduct), ("f5", Schema(SBoolean)))))
      .modifyUnsafe[String]("f4", "f2")(_.description("test")) shouldBe
      Schema(SProduct(info1, List(("f3", Schema(SString)), ("f4", expectedNestedProduct), ("f5", Schema(SBoolean)))))
  }

  it should "modify array elements in products" in {
    val info1 = SObjectInfo("X")
    Schema(SProduct(info1, List(("f1", Schema(SArray(Schema(SString)))))))
      .modifyUnsafe[String]("f1", Schema.ModifyCollectionElements)(_.format("xyz")) shouldBe Schema(
      SProduct(info1, List(("f1", Schema(SArray(Schema(SString).format("xyz"))))))
    )
  }

  it should "modify array in products" in {
    val info1 = SObjectInfo("X")
    Schema(SProduct(info1, List(("f1", Schema(SArray(Schema(SString)))))))
      .modifyUnsafe[String]("f1")(_.format("xyz")) shouldBe Schema(
      SProduct(info1, List(("f1", Schema(SArray(Schema(SString))).format("xyz"))))
    )
  }

  it should "modify property of optional parameter" in {
    val info1 = SObjectInfo("X")
    val info2 = SObjectInfo("Y")
    Schema(SProduct(info1, List("f1" -> Schema(SProduct(info2, List("p1" -> Schema(SInteger).asOptional))))))
      .modifyUnsafe[Int]("f1", "p1")(_.format("xyz")) shouldBe Schema(
      SProduct(info1, List("f1" -> Schema(SProduct(info2, List("p1" -> Schema(SInteger).asOptional.format("xyz"))))))
    )
  }

  it should "modify property of map value" in {
    Schema(SOpenProduct(SObjectInfo("Map", List("X")), Schema(SProduct(SObjectInfo("X"), List("f1" -> Schema(SInteger))))))
      .modifyUnsafe[Int](Schema.ModifyCollectionElements)(_.description("test")) shouldBe Schema(
      SOpenProduct(SObjectInfo("Map", List("X")), Schema(SProduct(SObjectInfo("X"), List("f1" -> Schema(SInteger)))).description("test"))
    )
  }

  it should "modify open product schema" in {
    val openProductSchema =
      Schema(SOpenProduct(SObjectInfo("Map", List("X")), Schema(SProduct(SObjectInfo("X"), List("f1" -> Schema(SInteger))))))
    openProductSchema
      .modifyUnsafe[Nothing]()(_.description("test")) shouldBe openProductSchema.description("test")
  }
}
