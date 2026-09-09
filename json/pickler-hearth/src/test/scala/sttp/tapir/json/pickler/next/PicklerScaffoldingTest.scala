package sttp.tapir.json.pickler.next

import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.SchemaType.SProduct
import sttp.tapir.{FieldName, Schema}

/** Structural acceptance test for the macro skeleton.
  *
  * Behavioural coverage of the schema half lives in `SchemaDerivationTest`, ported verbatim from the incumbent module. What this file pins
  * down is the *plumbing*, which that suite exercises only incidentally: that the bundle is constructed, the cross-quotes plugin is active,
  * the shared `ValDefsCache` produces well-scoped `def`s (a cross-splice staging bug would fail compilation here rather than at runtime),
  * and that both entry points — `Pickler.derived` and `Pickler.schemaFor` — agree.
  */
class PicklerScaffoldingTest extends AnyFlatSpec with Matchers with OptionValues {

  case class Simple(fieldA: Int, fieldB: String)
  case class Nested(first: Simple, second: Simple)

  behavior of "the Pickler derivation skeleton"

  it should "expand the macro and produce a Pickler instance" in {
    val pickler = Pickler.derived[Simple]
    pickler should not be null
    pickler.schema should not be null
    pickler.codec should not be null
  }

  it should "expose a fully derived schema" in {
    val schema: Schema[Simple] = Pickler.derived[Simple].schema
    schema.name.value.fullName should endWith("Simple")
    schema.schemaType shouldBe a[SProduct[?]]
    schema.schemaType.asInstanceOf[SProduct[Simple]].fields.map(_.name) shouldBe List(
      FieldName("fieldA"),
      FieldName("fieldB")
    )
  }

  it should "derive the same schema through the schema-only entry point" in {
    // The two entry points share `derivePicklerCore`, so a divergence here means the schema branch is sensitive to
    // whether the codec halves are also being derived -- exactly the kind of coupling the single-expansion design
    // exists to prevent.
    Pickler.schemaFor[Simple] shouldBe Pickler.derived[Simple].schema
  }

  it should "hoist derived schemas rather than inlining them at every occurrence" in {
    // `Nested` mentions `Simple` twice; both must resolve to the same cached `lazy val`.
    val schema = Pickler.schemaFor[Nested].schemaType.asInstanceOf[SProduct[Nested]]
    val fieldSchemas = schema.fields.map(_.schema)
    fieldSchemas.head should be theSameInstanceAs fieldSchemas(1)
  }

  it should "build a tapir codec from the pickler" in {
    val codec = Pickler.derived[Simple].toCodec
    codec should not be null
  }
}
