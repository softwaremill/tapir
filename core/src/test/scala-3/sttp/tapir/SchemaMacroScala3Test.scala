package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.internal.SNameMacros

class SchemaMacroScala3Test extends AnyFlatSpec with Matchers:
  import SchemaMacroScala3Test._

  it should "derive a one-of-wrapped schema for enums" in {
    given s: Schema[Fruit] = Schema.oneOfWrapped

    s.schemaType should matchPattern { case SchemaType.SCoproduct(_, _) => }

    val coproduct = s.schemaType.asInstanceOf[SchemaType.SCoproduct[Fruit]]
    coproduct.subtypeSchema(Fruit.Apple).map(_.schema) shouldBe Some(Schema(SchemaType.SProduct(Nil), Some(Schema.SName("Apple"))))
    coproduct.subtypeSchema(Fruit.Apple).map(_.value) shouldBe Some(Fruit.Apple)
  }

  it should "derive schema for union types" in {
    // when
    val s: Schema[String | Int] = Schema.derivedUnion

    // then
    s.name.map(_.show) shouldBe Some("java.lang.String_or_scala.Int")

    s.schemaType should matchPattern { case SchemaType.SCoproduct(_, _) => }
    val coproduct = s.schemaType.asInstanceOf[SchemaType.SCoproduct[String | Int]]
    coproduct.subtypes should have size 2
    coproduct.subtypeSchema("a").map(_.schema.schemaType) shouldBe Some(SchemaType.SString())
    coproduct.subtypeSchema(10).map(_.schema.schemaType) shouldBe Some(SchemaType.SInteger())
  }

  it should "derive schema for a named union type" in {
    // when
    val s: Schema[StringOrInt] = Schema.derivedUnion[StringOrInt]

    // then
    s.name.map(_.show) shouldBe Some("sttp.tapir.SchemaMacroScala3Test.StringOrInt")

    s.schemaType should matchPattern { case SchemaType.SCoproduct(_, _) => }
    val coproduct = s.schemaType.asInstanceOf[SchemaType.SCoproduct[StringOrInt]]
    coproduct.subtypes should have size 2
    coproduct.subtypeSchema("a").map(_.schema.schemaType) shouldBe Some(SchemaType.SString())
    coproduct.subtypeSchema(10).map(_.schema.schemaType) shouldBe Some(SchemaType.SInteger())
  }

  it should "derive schema for a union type with generics (same type constructor, different arguments)" in {
    // when
    val s: Schema[List[String] | List[Int]] = Schema.derivedUnion[List[String] | List[Int]]

    // then
    s.name.map(_.show) shouldBe Some("scala.collection.immutable.List[String]_or_scala.collection.immutable.List[Int]")

    s.schemaType should matchPattern { case SchemaType.SCoproduct(_, _) => }
    val coproduct = s.schemaType.asInstanceOf[SchemaType.SCoproduct[List[String] | List[Int]]]
    coproduct.subtypes should have size 2
    // no subtype schemas for generic types, as there's no runtime tag
    coproduct.subtypeSchema(List("")).map(_.schema.schemaType) shouldBe None
  }

  it should "derive schema for a union type with generics (different type constructors)" in {
    // when
    val s: Schema[List[String] | Vector[Int]] = Schema.derivedUnion[List[String] | Vector[Int]]

    // then
    s.name.map(_.show) shouldBe Some("scala.collection.immutable.List[String]_or_scala.collection.immutable.Vector[Int]")

    s.schemaType should matchPattern { case SchemaType.SCoproduct(_, _) => }
    val coproduct = s.schemaType.asInstanceOf[SchemaType.SCoproduct[List[String] | Vector[Int]]]
    coproduct.subtypes should have size 2
    coproduct.subtypeSchema(List("")).map(_.schema.schemaType) should matchPattern { case Some(_) => }
    coproduct.subtypeSchema(Vector(10)).map(_.schema.schemaType) should matchPattern { case Some(_) => }
  }

  it should "derive schema for union types with 3 components" in {
    // when
    val s: Schema[String | Int | Boolean] = Schema.derivedUnion

    // then
    s.name.map(_.show) shouldBe Some("java.lang.String_or_scala.Int_or_scala.Boolean")

    s.schemaType should matchPattern { case SchemaType.SCoproduct(_, _) => }
    val coproduct = s.schemaType.asInstanceOf[SchemaType.SCoproduct[String | Int | Boolean]]
    coproduct.subtypes should have size 3
    coproduct.subtypeSchema("a").map(_.schema.schemaType) shouldBe Some(SchemaType.SString())
    coproduct.subtypeSchema(10).map(_.schema.schemaType) shouldBe Some(SchemaType.SInteger())
    coproduct.subtypeSchema(true).map(_.schema.schemaType) shouldBe Some(SchemaType.SBoolean())
  }

object SchemaMacroScala3Test:
  enum Fruit:
    case Apple, Banana

  type StringOrInt = String | Int
