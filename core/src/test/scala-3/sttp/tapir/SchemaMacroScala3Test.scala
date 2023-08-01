package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.Duration

class SchemaMacroScala3Test extends AnyFlatSpec with Matchers:
  import SchemaMacroScala3Test._

  it should "validate enum" in {
    given s: Schema[Fruit] = Schema.oneOfWrapped

    s.schemaType should matchPattern { case SchemaType.SCoproduct(_, _) => }

    val coproduct = s.schemaType.asInstanceOf[SchemaType.SCoproduct[Fruit]]
    coproduct.subtypeSchema(Fruit.Apple).map(_.schema) shouldBe Some(Schema(SchemaType.SProduct(Nil), Some(Schema.SName("Apple"))))
    coproduct.subtypeSchema(Fruit.Apple).map(_.value) shouldBe Some(Fruit.Apple)
  }

object SchemaMacroScala3Test:
  enum Fruit:
    case Apple, Banana
