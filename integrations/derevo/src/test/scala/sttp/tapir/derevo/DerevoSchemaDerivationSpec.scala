package sttp.tapir.derevo

import derevo.derive
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema

class DerevoSchemaDerivationSpec extends AnyFlatSpec with Matchers {

  "Generated schema by derevo" should "be the same as Schema.derived" in {

    @derive(schema)
    sealed trait Adt
    object Adt {
      final case class Foo(bar: String, baz: Int) extends Adt
      final case object Bar extends Adt
    }

    val expectedSchema: Schema[Adt] = Schema.derived[Adt]
    val generatedSchema: Schema[Adt] = implicitly[Schema[Adt]]

    generatedSchema.show shouldBe expectedSchema.show
    }

  "Generated schema by derevo with custom description" should "be the same as Schema.derived with altered description" in {

    val testDescription = "test description"

    @derive(schema(testDescription))
    sealed trait Adt
    object Adt {
      final case class Foo(bar: String, baz: Int) extends Adt
      final case object Bar extends Adt
    }
    val expectedSchema: Schema[Adt] = Schema.derived[Adt].description(testDescription)
    val generatedSchema: Schema[Adt] = implicitly[Schema[Adt]]
    generatedSchema.description shouldBe Some(testDescription)
    generatedSchema.show shouldBe expectedSchema.show
  }



}
