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

  it should "correctly derived schema for list" in {
    case class Foo(x: Int)
    val fooSchema = implicitly[Schema[Foo]]
    val schema = implicitly[Schema[List[Foo]]]
    schema shouldBe fooSchema.asArrayElement
  }

  it should "correctly derived schema for list when case class of element contain seq" in {
    case class Foo(x: Seq[Int])
    val fooSchema = implicitly[Schema[Foo]]
    val schema = implicitly[Schema[List[Foo]]]
    schema shouldBe fooSchema.asArrayElement
  }

  it should "correctly derived schema for list when case class of element contain nested seq" in {
    case class Foo(x: Seq[Seq[Int]])
    val fooSchema = implicitly[Schema[Foo]]
    val schema = implicitly[Schema[List[Foo]]]

    //this test break here list is interpreted as an recursive ADT (head, tail, etc.., etc..)
    schema shouldBe fooSchema.asArrayElement
  }


  it should "correctly derived schema for seq" in {
    case class Foo(x: Int)
    val fooSchema = implicitly[Schema[Foo]]
    val schema = implicitly[Schema[Seq[Foo]]]
    schema shouldBe fooSchema.asArrayElement
  }

  it should "correctly derived schema for seq when case class of element contain seq" in {
    case class Foo(x: Seq[Int])
    val fooSchema = implicitly[Schema[Foo]]
    val schema = implicitly[Schema[Seq[Foo]]]
    schema shouldBe fooSchema.asArrayElement
  }

  it should "correctly derived schema for seq when case class of element contain nested seq" in {
    case class Foo(x: Seq[Seq[Int]])
    val fooSchema = implicitly[Schema[Foo]]
    // val schema = implicitly[Schema[Seq[Foo]]] this don't compile: could not find implicit value
    // but the next line work fine
    val schema = Schema.schemaForIterable[Foo, Seq](fooSchema)
    schema shouldBe fooSchema.asArrayElement
  }
}
