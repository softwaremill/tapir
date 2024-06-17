package sttp.tapir

import sttp.tapir.SchemaType._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema.SName
import sttp.tapir.TestUtil.field
import javax.swing.plaf.ListUI

class SchemaTest extends AnyFlatSpec with Matchers {
  it should "modify basic schema" in {
    implicitly[Schema[String]].modifyUnsafe[String]()(_.description("test")) shouldBe implicitly[Schema[String]]
      .copy(description = Some("test"))
  }

  it should "modify product schema" in {
    val name1 = SName("X")
    Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SInteger())), field(FieldName("f2"), Schema(SString())))), Some(name1))
      .modifyUnsafe[String]("f2")(_.description("test").default("f2").encodedExample("f2_example")) shouldBe Schema(
      SProduct[Unit](
        List(
          field(FieldName("f1"), Schema(SInteger())),
          field(FieldName("f2"), Schema(SString()).description("test").default("f2").encodedExample("f2_example"))
        )
      ),
      Some(name1)
    )
  }

  it should "modify nested product schema" in {
    val name1 = SName("X")
    val name2 = SName("Y")

    val nestedProduct =
      Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SInteger())), field(FieldName("f2"), Schema(SString())))), Some(name2))
    val expectedNestedProduct =
      Schema(
        SProduct[Unit](
          List(
            field(FieldName("f1"), Schema(SInteger())),
            field(FieldName("f2"), Schema(SString()).description("test").default("f2").encodedExample("f2_example"))
          )
        ),
        Some(name2)
      )

    Schema(
      SProduct[Unit](
        List(field(FieldName("f3"), Schema(SString())), field(FieldName("f4"), nestedProduct), field(FieldName("f5"), Schema(SBoolean())))
      ),
      Some(name1)
    )
      .modifyUnsafe[String]("f4", "f2")(_.description("test").default("f2").encodedExample("f2_example")) shouldBe
      Schema(
        SProduct[Unit](
          List(
            field(FieldName("f3"), Schema(SString())),
            field(FieldName("f4"), expectedNestedProduct),
            field(FieldName("f5"), Schema(SBoolean()))
          )
        ),
        Some(name1)
      )
  }

  it should "modify array elements in products" in {
    val name1 = SName("X")
    Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SArray[List[String], String](Schema(SString()))(_.toIterable))))), Some(name1))
      .modifyUnsafe[String]("f1", Schema.ModifyCollectionElements)(_.format("xyz")) shouldBe Schema(
      SProduct[Unit](
        List(field(FieldName("f1"), Schema(SArray[List[String], String](Schema[String](SString()).format("xyz"))(_.toIterable))))
      ),
      Some(name1)
    )
  }

  it should "modify array in products" in {
    val name1 = SName("X")
    Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SArray[List[String], String](Schema(SString()))(_.toIterable))))), Some(name1))
      .modifyUnsafe[String]("f1")(_.format("xyz")) shouldBe Schema(
      SProduct[Unit](
        List(field(FieldName("f1"), Schema(SArray[List[String], String](Schema(SString()))(_.toIterable)).format("xyz")))
      ),
      Some(name1)
    )
  }

  it should "modify property of optional parameter" in {
    val name1 = SName("X")
    val name2 = SName("Y")
    Schema(
      SProduct[Unit](
        List(field(FieldName("f1"), Schema(SProduct[Unit](List(field(FieldName("p1"), Schema(SInteger()).asOption))), Some(name2))))
      ),
      Some(name1)
    )
      .modifyUnsafe[Int]("f1", "p1")(_.format("xyz")) shouldBe Schema(
      SProduct[Unit](
        List(
          field(
            FieldName("f1"),
            Schema(SProduct[Unit](List(field(FieldName("p1"), Schema(SInteger()).asOption.format("xyz")))), Some(name2))
          )
        )
      ),
      Some(name1)
    )
  }

  it should "modify property of map value" in {
    Schema(
      SOpenProduct[Map[String, Unit], Unit](
        Nil,
        Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SInteger())))), Some(SName("X")))
      )(identity),
      Some(SName("Map", List("X")))
    )
      .modifyUnsafe[Int](Schema.ModifyCollectionElements)(_.description("test")) shouldBe Schema(
      SOpenProduct[Map[String, Unit], Unit](
        Nil,
        Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SInteger())))), Some(SName("X"))).description("test")
      )(identity),
      Some(SName("Map", List("X")))
    )
  }

  it should "modify open product schema" in {
    val openProductSchema =
      Schema(
        SOpenProduct[Map[String, Unit], Unit](
          Nil,
          Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SInteger())))), Some(SName("X")))
        )(_ => Map.empty),
        Some(SName("Map", List("X")))
      )
    openProductSchema
      .modifyUnsafe[Nothing]()(_.description("test")) shouldBe openProductSchema.description("test")
  }

  it should "generate one-of schema using the given discriminator" in {
    val coproduct = SCoproduct[Unit](
      List(
        Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SInteger())))), Some(SName("H"))),
        Schema(
          SProduct[Unit](List(field(FieldName("f1"), Schema(SString())), field(FieldName("f2"), Schema(SString())))),
          Some(SName("G"))
        ),
        Schema(SString[Unit]())
      ),
      None
    )(_ => None)

    val coproduct2 = coproduct.addDiscriminatorField(FieldName("who_am_i"))

    coproduct2.subtypes shouldBe List(
      Schema(
        SProduct[Unit](List(field(FieldName("f1"), Schema(SInteger())), field(FieldName("who_am_i"), Schema(SString())))),
        Some(SName("H"))
      ),
      Schema(
        SProduct[Unit](
          List(
            field(FieldName("f1"), Schema(SString())),
            field(FieldName("f2"), Schema(SString())),
            field(FieldName("who_am_i"), Schema(SString()))
          )
        ),
        Some(SName("G"))
      ),
      Schema(SString[Unit]())
    )

    coproduct2.discriminator shouldBe Some(SDiscriminator(FieldName("who_am_i"), Map.empty))
  }

  it should "addDiscriminatorField should only add discriminator field to child schemas if not yet present" in {
    val coproduct = SCoproduct[Unit](
      List(
        Schema(SProduct[Unit](List(field(FieldName("f0"), Schema(SString())))), Some(SName("H"))),
        Schema(SProduct[Unit](List(field(FieldName("f1"), Schema(SString())))), Some(SName("G")))
      ),
      None
    )(_ => None)

    val coproduct2 = coproduct.addDiscriminatorField(FieldName("f0"))

    coproduct2.subtypes shouldBe List(
      Schema(SProduct[Unit](List(field(FieldName("f0"), Schema(SString())))), Some(SName("H"))),
      Schema(
        SProduct[Unit](
          List(
            field(FieldName("f1"), Schema(SString())),
            field(FieldName("f0"), Schema(SString()))
          )
        ),
        Some(SName("G"))
      )
    )

    coproduct2.discriminator shouldBe Some(SDiscriminator(FieldName("f0"), Map.empty))
  }

  it should "propagate format for optional schemas" in {
    implicitly[Schema[Option[Double]]].format shouldBe Some("double")
  }

  case class SomeValueString[A](value: String, v2: A)
  final case class SomeValueInt(value: Int)
  final case class Node[A](values: List[A])
  it should "generate correct names for Eithers with parameterized types" in {

    import sttp.tapir.generic.auto._

    implicitly[Schema[Either[Int, Int]]].name shouldBe None    
    implicitly[Schema[Either[SomeValueInt, Int]]].name shouldBe None
    implicitly[Schema[Either[SomeValueInt, SomeValueInt]]].name shouldBe Some(
      SName("Either", List("sttp.tapir.SchemaTest.SomeValueInt", "sttp.tapir.SchemaTest.SomeValueInt"))
    )
    implicitly[Schema[Either[SomeValueInt, Node[SomeValueString[Boolean]]]]].name shouldBe Some(
      SName("Either", List("sttp.tapir.SchemaTest.SomeValueInt", "sttp.tapir.SchemaTest.Node", "sttp.tapir.SchemaTest.SomeValueString", "scala.Boolean"))
    )
    implicitly[Schema[Either[SomeValueInt, Node[String]]]].name shouldBe Some(
      SName("Either", List("sttp.tapir.SchemaTest.SomeValueInt", "sttp.tapir.SchemaTest.Node", "java.lang.String"))
    )
    implicitly[Schema[Either[Node[Boolean], SomeValueInt]]].name shouldBe Some(
      SName("Either", List("sttp.tapir.SchemaTest.Node", "scala.Boolean", "sttp.tapir.SchemaTest.SomeValueInt"))
    )

  }

}
