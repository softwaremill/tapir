package sttp.tapir.json.pickler.next

import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.Schema

/** Phase 0 acceptance test.
  *
  * This does not test any derivation *behaviour* — the rules are still stubs. What it proves is that the macro
  * skeleton expands end to end: the bundle is constructed, the cross-quotes plugin is active, the shared
  * `ValDefsCache` produces well-scoped `def`s (a cross-splice staging bug would fail compilation here), and the
  * emitted expression type-checks as a `Pickler[A]`.
  *
  * Once the schema rules land in Phase 1, these assertions get replaced by the real `SchemaDerivationTest`.
  */
class PicklerScaffoldingTest extends AnyFlatSpec with Matchers with OptionValues {

  case class Simple(fieldA: Int, fieldB: String)

  behavior of "the Pickler derivation skeleton"

  it should "expand the macro and produce a Pickler instance" in {
    val pickler = Pickler.derived[Simple]
    pickler should not be null
    pickler.schema should not be null
    pickler.codec should not be null
  }

  it should "expose the derived schema (currently a Phase 0 placeholder)" in {
    val schema: Schema[Simple] = Pickler.derived[Simple].schema
    schema.description.value should include("schema derivation not implemented")
  }

  it should "support the schema-only entry point" in {
    val schema = Pickler.schemaFor[Simple]
    schema.description.value should include("schema derivation not implemented")
  }

  it should "build a tapir codec from the pickler" in {
    val codec = Pickler.derived[Simple].toCodec
    codec should not be null
  }
}
