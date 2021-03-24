package sttp.tapir

import sttp.tapir.SchemaType._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.TestUtil.field

class SchemaTest extends AnyFlatSpec with Matchers {
  it should "modify basic schema" in {
    implicitly[Schema[String]].modifyUnsafe[String]()(_.description("test")) shouldBe implicitly[Schema[String]]
      .copy(description = Some("test"))
  }

  it should "modify product schema" in {
    val info1 = SObjectInfo("X")
    Schema(SProduct[Unit](info1, List(field(FieldName("f1"), Schema(SInteger())), field(FieldName("f2"), Schema(SString())))))
      .modifyUnsafe[String]("f2")(_.description("test").default("f2").encodedExample("f2_example")) shouldBe Schema(
      SProduct[Unit](
        info1,
        List(
          field(FieldName("f1"), Schema(SInteger())),
          field(FieldName("f2"), Schema(SString()).description("test").default("f2").encodedExample("f2_example"))
        )
      )
    )
  }

  it should "modify nested product schema" in {
    val info1 = SObjectInfo("X")
    val info2 = SObjectInfo("Y")

    val nestedProduct =
      Schema(SProduct[Unit](info2, List(field(FieldName("f1"), Schema(SInteger())), field(FieldName("f2"), Schema(SString())))))
    val expectedNestedProduct =
      Schema(
        SProduct[Unit](
          info2,
          List(
            field(FieldName("f1"), Schema(SInteger())),
            field(FieldName("f2"), Schema(SString()).description("test").default("f2").encodedExample("f2_example"))
          )
        )
      )

    Schema(
      SProduct[Unit](
        info1,
        List(field(FieldName("f3"), Schema(SString())), field(FieldName("f4"), nestedProduct), field(FieldName("f5"), Schema(SBoolean())))
      )
    )
      .modifyUnsafe[String]("f4", "f2")(_.description("test").default("f2").encodedExample("f2_example")) shouldBe
      Schema(
        SProduct[Unit](
          info1,
          List(
            field(FieldName("f3"), Schema(SString())),
            field(FieldName("f4"), expectedNestedProduct),
            field(FieldName("f5"), Schema(SBoolean()))
          )
        )
      )
  }

  it should "modify array elements in products" in {
    val info1 = SObjectInfo("X")
    Schema(SProduct[Unit](info1, List(field(FieldName("f1"), Schema(SArray[List[String], String](Schema(SString()))(_.toIterable))))))
      .modifyUnsafe[String]("f1", Schema.ModifyCollectionElements)(_.format("xyz")) shouldBe Schema(
      SProduct[Unit](
        info1,
        List(field(FieldName("f1"), Schema(SArray[List[String], String](Schema[String](SString()).format("xyz"))(_.toIterable))))
      )
    )
  }

  it should "modify array in products" in {
    val info1 = SObjectInfo("X")
    Schema(SProduct[Unit](info1, List(field(FieldName("f1"), Schema(SArray[List[String], String](Schema(SString()))(_.toIterable))))))
      .modifyUnsafe[String]("f1")(_.format("xyz")) shouldBe Schema(
      SProduct[Unit](
        info1,
        List(field(FieldName("f1"), Schema(SArray[List[String], String](Schema(SString()))(_.toIterable)).format("xyz")))
      )
    )
  }

  it should "modify property of optional parameter" in {
    val info1 = SObjectInfo("X")
    val info2 = SObjectInfo("Y")
    Schema(
      SProduct[Unit](
        info1,
        List(field(FieldName("f1"), Schema(SProduct[Unit](info2, List(field(FieldName("p1"), Schema(SInteger()).asOption))))))
      )
    )
      .modifyUnsafe[Int]("f1", "p1")(_.format("xyz")) shouldBe Schema(
      SProduct[Unit](
        info1,
        List(field(FieldName("f1"), Schema(SProduct[Unit](info2, List(field(FieldName("p1"), Schema(SInteger()).asOption.format("xyz")))))))
      )
    )
  }

  it should "modify property of map value" in {
    Schema(
      SOpenProduct[Map[String, Unit], Unit](
        SObjectInfo("Map", List("X")),
        Schema(SProduct[Unit](SObjectInfo("X"), List(field(FieldName("f1"), Schema(SInteger())))))
      )(identity)
    )
      .modifyUnsafe[Int](Schema.ModifyCollectionElements)(_.description("test")) shouldBe Schema(
      SOpenProduct[Map[String, Unit], Unit](
        SObjectInfo("Map", List("X")),
        Schema(SProduct[Unit](SObjectInfo("X"), List(field(FieldName("f1"), Schema(SInteger()))))).description("test")
      )(identity)
    )
  }

  it should "modify open product schema" in {
    val openProductSchema =
      Schema(
        SOpenProduct[Map[String, Unit], Unit](
          SObjectInfo("Map", List("X")),
          Schema(SProduct[Unit](SObjectInfo("X"), List(field(FieldName("f1"), Schema(SInteger())))))
        )(_ => Map.empty)
      )
    openProductSchema
      .modifyUnsafe[Nothing]()(_.description("test")) shouldBe openProductSchema.description("test")
  }

  it should "generate one-of schema using the given discriminator" in {
    val coproduct = SCoproduct[Unit](
      SObjectInfo("A"),
      Map(
        SObjectInfo("H") -> Schema(SProduct[Unit](SObjectInfo("H"), List(field(FieldName("f1"), Schema(SInteger()))))),
        SObjectInfo("G") -> Schema(
          SProduct[Unit](SObjectInfo("G"), List(field(FieldName("f1"), Schema(SString())), field(FieldName("f2"), Schema(SString()))))
        ),
        SObjectInfo("U") -> Schema(SString[Unit]())
      ),
      None
    )(_ => SObjectInfo(""))

    val coproduct2 = coproduct.addDiscriminatorField(FieldName("who_am_i"))

    coproduct2.subtypes shouldBe Map(
      SObjectInfo("H") -> Schema(
        SProduct[Unit](SObjectInfo("H"), List(field(FieldName("f1"), Schema(SInteger())), field(FieldName("who_am_i"), Schema(SString()))))
      ),
      SObjectInfo("G") -> Schema(
        SProduct[Unit](
          SObjectInfo("G"),
          List(
            field(FieldName("f1"), Schema(SString())),
            field(FieldName("f2"), Schema(SString())),
            field(FieldName("who_am_i"), Schema(SString()))
          )
        )
      ),
      SObjectInfo("U") -> Schema(SString[Unit]())
    )

    coproduct2.discriminator shouldBe Some(Discriminator("who_am_i", Map.empty))
  }

}
