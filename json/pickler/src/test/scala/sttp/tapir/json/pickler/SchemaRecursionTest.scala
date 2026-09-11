package sttp.tapir.json.pickler

import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType.{SArray, SCoproduct, SOption, SProduct, SRef}
import sttp.tapir.{FieldName, Schema, ValidationError, Validator}

/** Recursive-type support for the schema half of the derivation.
  *
  * ==Why this file exists separately from `SchemaDerivationTest`==
  * The ported suite declares recursive fixtures (`F`, `Node`/`Edge`, `IOpt`, `IList`) but never references them, so it imposes no
  * constraint at all on recursion. It could not have: the uPickle-based pickler **cannot derive a self-recursive type**, failing at compile
  * time with `Failed to summon Pickler[List[RecF]]`. (Its `SchemaDerivation.withCache`, which would have emitted an `SRef`, has zero call
  * sites — dead code.) Recursion is therefore new capability rather than behaviour to match, and these expectations are modelled on tapir
  * core's own `Schema.derived`, which does support it.
  *
  * ==The invariant under test==
  * A recursive occurrence becomes `Schema(SRef(name))`. That is only useful if the `name` is *exactly* the `SName` carried by an enclosing
  * schema, because `Schema.applyValidation` resolves a reference by looking it up in a `Map[SName, Schema]` accumulated during traversal
  * (`core/src/main/scala/sttp/tapir/Schema.scala:272-292`). A merely *stable* name is not enough — a mismatch degrades silently to "no
  * validation" rather than raising an error, which is why `should "resolve ..."` below is the load-bearing test in this file.
  */
class SchemaRecursionTest extends AnyFlatSpec with Matchers with OptionValues {

  // -- Fixtures ---------------------------------------------------------------------------------------------------

  case class RecList(children: List[RecList], value: Int)
  case class RecOpt(child: Option[RecOpt], value: Int)
  case class RecWrapper(data: Option[RecOpt])

  case class MutualA(b: Option[MutualB], id: Int)
  case class MutualB(a: Option[MutualA], id: Int)

  case class RecName(name: String, subNames: List[RecName])

  // -- Helpers ----------------------------------------------------------------------------------------------------

  private def productOf[T](schema: Schema[T]): SProduct[T] =
    schema.schemaType.asInstanceOf[SProduct[T]]

  private def fieldSchema[T](schema: Schema[T], name: String): Schema[?] =
    productOf(schema).fields.find(_.name.name == name).value.schema

  /** The element schema of a `List`/`Option` field. */
  private def elementOf(schema: Schema[?]): Schema[?] = schema.schemaType match {
    case SArray(element)  => element
    case SOption(element) => element
    case other            => fail(s"expected a collection or option schema, got $other")
  }

  private def refNameOf(schema: Schema[?]): SName = schema.schemaType match {
    case SRef(name) => name
    case other      => fail(s"expected an SRef, got $other")
  }

  // -- Structure --------------------------------------------------------------------------------------------------

  behavior of "schema derivation for recursive types"

  it should "terminate and emit an SRef for a type that recurses through a collection" in {
    val schema = Pickler.schemaFor[RecList]

    schema.name.value.fullName should endWith("RecList")
    // The recursive occurrence must be a reference, not an inlined copy -- inlining cannot terminate.
    refNameOf(elementOf(fieldSchema(schema, "children"))) shouldBe schema.name.value
  }

  it should "terminate and emit an SRef for a type that recurses through an Option" in {
    val schema = Pickler.schemaFor[RecOpt]

    refNameOf(elementOf(fieldSchema(schema, "child"))) shouldBe schema.name.value
  }

  it should "emit an SRef whose SName is exactly the enclosing schema's name" in {
    // Stated separately because this is the property `applyValidation` depends on, and a near-miss (a stable but
    // different name) would pass every other assertion in this file while silently disabling validation.
    val schema = Pickler.schemaFor[RecList]
    val ref = refNameOf(elementOf(fieldSchema(schema, "children")))

    ref shouldBe SName("sttp.tapir.json.pickler.SchemaRecursionTest.RecList")
    ref shouldBe schema.name.value
  }

  it should "handle mutual recursion between two case classes" in {
    val schema = Pickler.schemaFor[MutualA]

    // B is expanded in full inside A ...
    val b = elementOf(fieldSchema(schema, "b"))
    b.name.value.fullName should endWith("MutualB")
    // ... and only the second occurrence of A collapses to a reference.
    refNameOf(elementOf(fieldSchema(b, "a"))) shouldBe schema.name.value
  }

  it should "handle recursion through a sealed hierarchy" in {
    val schema = Pickler.schemaFor[RecNode]
    val subtypes = schema.schemaType.asInstanceOf[SCoproduct[RecNode]].subtypes

    subtypes.flatMap(_.name.map(_.fullName.split('.').last)) should contain theSameElementsAs List("RecEdge", "RecSimpleNode")

    val edge = subtypes.find(_.name.exists(_.fullName.endsWith("RecEdge"))).value
    // `RecEdge.source: RecNode` points back at the coproduct itself.
    refNameOf(fieldSchema(edge, "source")) shouldBe schema.name.value
  }

  it should "derive a non-recursive type that merely contains a recursive one" in {
    val schema = Pickler.schemaFor[RecWrapper]
    val inner = elementOf(fieldSchema(schema, "data"))

    // Entering from outside the cycle, `RecOpt` is still expanded in full; only its own back-edge is a reference.
    inner.name.value.fullName should endWith("RecOpt")
    refNameOf(elementOf(fieldSchema(inner, "child"))) shouldBe inner.name.value
  }

  it should "agree between the schema-only and full-pickler entry points" in {
    Pickler.schemaFor[RecList] shouldBe Pickler.derived[RecList].schema
  }

  // -- Validation -------------------------------------------------------------------------------------------------

  it should "resolve the SRef when applying validation at depth" in {
    // Mirrors core's own recursion test (core/src/test/scala/sttp/tapir/SchemaApplyValidationTest.scala:113-129):
    // a validator on `String` must fire however deeply the recursive structure is nested. This only works if the
    // SRef's SName matches an ancestor's -- otherwise `objects.get(name)` misses and validation quietly returns Nil.
    implicit val stringSchema: Schema[String] = Schema.schemaForString.validate(Validator.minLength(1))
    val schema = Pickler.schemaFor[RecName]

    schema.applyValidation(RecName("x", Nil)) shouldBe Nil

    schema.applyValidation(RecName("", Nil)) shouldBe List(
      ValidationError(Validator.minLength(1), "", List(FieldName("name")))
    )

    schema.applyValidation(RecName("x", List(RecName("x", Nil)))) shouldBe Nil

    schema.applyValidation(RecName("x", List(RecName("", Nil)))) shouldBe List(
      ValidationError(Validator.minLength(1), "", List(FieldName("subNames"), FieldName("name")))
    )

    schema.applyValidation(RecName("x", List(RecName("x", List(RecName("", Nil)))))) shouldBe List(
      ValidationError(Validator.minLength(1), "", List(FieldName("subNames"), FieldName("subNames"), FieldName("name")))
    )
  }
}

// Declared at the top level: a sealed hierarchy nested inside the test class would make the fixture's own SName
// depend on the enclosing-class naming rules, which is `SchemaDerivationTest`'s business, not this file's. Prefixed
// `Rec` because that file declares a `Node`/`Edge` pair of its own in the same package.
sealed trait RecNode
case class RecEdge(id: Long, source: RecNode) extends RecNode
case class RecSimpleNode(id: Long) extends RecNode
