package tapir

import org.scalatest.{FlatSpec, Matchers}
import tapir.SchemaType._

class SchemaTest extends FlatSpec with Matchers {
  it should "modify basic schema" in {
    implicitly[Schema[String]].modifyUnsafe()(_.description("test")) shouldBe implicitly[Schema[String]].copy(description = Some("test"))
  }

  it should "modify product schema" in {
    val info1 = SObjectInfo("X")
    Schema(SProduct(info1, List(("f1", Schema(SString)), ("f2", Schema(SInteger)))))
      .modifyUnsafe("f2")(_.description("test")) shouldBe Schema(
      SProduct(info1, List(("f1", Schema(SString)), ("f2", Schema(SInteger).description("test"))))
    )
  }

  it should "modify nested product schema" in {
    val info1 = SObjectInfo("X")
    val info2 = SObjectInfo("Y")

    val nestedProduct = Schema(SProduct(info2, List(("f1", Schema(SString)), ("f2", Schema(SInteger)))))
    val expectedNestedProduct = Schema(SProduct(info2, List(("f1", Schema(SString)), ("f2", Schema(SInteger).description("test")))))

    Schema(SProduct(info1, List(("f3", Schema(SString)), ("f4", nestedProduct), ("f5", Schema(SBoolean)))))
      .modifyUnsafe("f4", "f2")(_.description("test")) shouldBe
      Schema(SProduct(info1, List(("f3", Schema(SString)), ("f4", expectedNestedProduct), ("f5", Schema(SBoolean)))))
  }

  it should "modify array elements in products" in {
    val info1 = SObjectInfo("X")
    Schema(SProduct(info1, List(("f1", Schema(SArray(Schema(SString)))))))
      .modifyUnsafe("f1", Schema.ModifyCollectionElements)(_.format("xyz")) shouldBe Schema(
      SProduct(info1, List(("f1", Schema(SArray(Schema(SString).format("xyz"))))))
    )
  }

  it should "modify array in products" in {
    val info1 = SObjectInfo("X")
    Schema(SProduct(info1, List(("f1", Schema(SArray(Schema(SString)))))))
      .modifyUnsafe("f1")(_.format("xyz")) shouldBe Schema(
      SProduct(info1, List(("f1", Schema(SArray(Schema(SString))).format("xyz"))))
    )
  }
}
