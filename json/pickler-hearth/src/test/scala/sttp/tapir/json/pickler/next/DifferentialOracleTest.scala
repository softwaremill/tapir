package sttp.tapir.json.pickler.next

import org.scalacheck.Arbitrary
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import sttp.tapir.{Schema, Validator}
import sttp.tapir.SchemaType.*
import sttp.tapir.json.pickler.{Pickler as OldPickler, PicklerConfiguration as OldConfiguration}

import scala.reflect.ClassTag

/** The uPickle-based module and this one, side by side on generated values (decision D2): the JSON both write for the same value must be
  * the same JSON, and each must read what the other wrote.
  *
  * JSON is compared as parsed `ujson` trees, not as strings: the two writers escape control characters differently (`\u0001` vs `\u0001` is
  * the same JSON either way) and that is not a wire-format difference worth pinning.
  *
  * Exclusions, each an intentional divergence recorded in the plan: `Either` (§5.3, untagged here vs `[0, x]`), and decoding of a missing
  * field with a tapir `@default` (§5.1). `Map` iteration order is the same on both sides since both iterate the same `Map` instance. This
  * test is deleted at cutover, together with the incumbent.
  */
class DifferentialOracleTest extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {
  import Fixtures.*
  import PropertyFixtures.*
  import Generators.given

  private def sameJson[T: Arbitrary](next: Pickler[T], old: OldPickler[T]): Unit = {
    val nextCodec = next.toCodec
    val oldCodec = old.toCodec
    forAll { (value: T) =>
      val nextJson = nextCodec.encode(value)
      val oldJson = oldCodec.encode(value)
      withClue(s"value $value:\n  next: $nextJson\n  old:  $oldJson\n") {
        ujson.read(nextJson) shouldBe ujson.read(oldJson)
      }
      nextCodec.decode(oldJson) shouldBe oldCodec.decode(oldJson)
      oldCodec.decode(nextJson) shouldBe nextCodec.decode(nextJson)
    }
  }

  /** Two normalisations before comparing: coproduct subtypes as sets (magnolia, the incumbent's schema derivation, sorts them
    * alphabetically; this module keeps declaration order; neither is part of what a schema means), and enumeration validators by their
    * *encoded values* (each side's validator holds its own lambda, and the incumbent's carries a `Fixtures$.SealedVariant`-style name from
    * core's `ValidatorMacros`). The incumbent also *names* an enumeration schema that way (`Fixtures$.SealedVariant`, the module `$`
    * leaking through), where every other name on both sides is `Fixtures.X`; the `$.` is dropped before comparing.
    */
  private def sameSchema[T](next: Pickler[T], old: OldPickler[T]): Unit =
    normalise(next.schema) shouldBe normalise(old.schema)

  private def normalise(s: Schema[?]): Schema[?] = {
    def validator[X](v: Validator[X]): Validator[X] = v match {
      case e: Validator.Enumeration[X @unchecked] =>
        val encoded = e.possibleValues.map(pv => e.encode.flatMap(_.apply(pv)).map(_.toString).getOrElse(pv.toString))
        Validator.Enumeration[X](encoded.asInstanceOf[List[X]], None, None)
      case Validator.All(vs) => Validator.All(vs.map(validator))
      case other             => other
    }
    def go[X](s: Schema[X]): Schema[X] = s.copy(
      name = s.name.map(n => n.copy(fullName = n.fullName.replace("$.", "."))),
      validator = validator(s.validator),
      schemaType = s.schemaType match {
        case p: SProduct[X @unchecked] =>
          p.copy(fields =
            p.fields.map(f => SProductField[X, Any](f.name, go(f.schema.asInstanceOf[Schema[Any]]), f.get.asInstanceOf[X => Option[Any]]))
          )
        case c: SCoproduct[X @unchecked] =>
          SCoproduct[X](c.subtypes.map(go(_)).sortBy(_.name.map(_.fullName).getOrElse("")), c.discriminator)(c.subtypeSchema)
        case o: SOption[X @unchecked, e]      => SOption[X, e](go(o.element))(o.toOption)
        case a: SArray[X @unchecked, e]       => SArray[X, e](go(a.element))(a.toIterable)
        case o: SOpenProduct[X @unchecked, v] => SOpenProduct[X, v](o.fields, go(o.valueSchema))(o.mapFieldValues)
        case other                            => other
      }
    )
    go(s)
  }

  // Both modules are asked for a pickler for every type explicitly; automatic derivation is imported for the nested
  // types each needs. The two `auto` imports do not compete: they produce different types.
  import sttp.tapir.json.pickler.generic.auto.*
  import sttp.tapir.json.pickler.next.generic.auto.*

  /** The same configuration expressed for both modules. */
  private inline def compareUnder[T: Arbitrary: ClassTag](
      inline next: PicklerConfiguration,
      old: OldConfiguration
  ): Unit = {
    given OldConfiguration = old
    sameJson(Pickler.derived[T](using next), OldPickler.derived[T])
  }

  private inline def compare[T: Arbitrary: ClassTag]: Unit = {
    compareUnder[T](PicklerConfiguration.default, OldConfiguration.default)
    compareUnder[T](PicklerConfiguration.default.withSnakeCaseMemberNames, OldConfiguration.default.withSnakeCaseMemberNames)
    compareUnder[T](PicklerConfiguration.default.withKebabCaseMemberNames, OldConfiguration.default.withKebabCaseMemberNames)
    compareUnder[T](PicklerConfiguration.default.withDiscriminator("kind"), OldConfiguration.default.withDiscriminator("kind"))
    compareUnder[T](
      PicklerConfiguration.default.withFullKebabCaseDiscriminatorValues,
      OldConfiguration.default.withFullKebabCaseDiscriminatorValues
    )
    compareUnder[T](
      PicklerConfiguration.default.withSnakeCaseDiscriminatorValues,
      OldConfiguration.default.withSnakeCaseDiscriminatorValues
    )
    compareUnder[T](PicklerConfiguration.default.withTransientNone(false), OldConfiguration.default.withTransientNone(false))
    sameSchema(Pickler.derived[T], OldPickler.derived[T])
  }

  behavior of "the uPickle-based and the Hearth-based pickler"

  it should "agree on products" in {
    compare[FlatClass]
    compare[TopClass]
    compare[TopClass2]
    compare[ClassWithValues]
    compare[ClassWithScalaDefault]
  }

  it should "agree on optional fields" in {
    compare[FlatClassWithOption]
    compare[NestedClassWithOption]
  }

  it should "agree on collections and maps" in {
    compare[FlatClassWithList]
    compare[NestedClassWithList]
    compare[ClassWithMap]
  }

  it should "agree on sealed hierarchies" in {
    compare[ErrorCodeHolder]
    compare[StatusResponse]
    compare[Status]
    compare[NotAllSealedVariant]
    compare[Entity]
  }

  it should "agree on enumerations" in {
    compare[SealedVariantContainer]
    compare[Response]
    compare[RichColorResponse]
    sameJson(
      Pickler.derivedEnumeration[ColorEnum].customStringBased(_.ordinal.toString),
      OldPickler.derivedEnumeration[ColorEnum].customStringBased(_.ordinal.toString)
    )
  }

  // No "agree on recursive types": the incumbent cannot derive `Tree`, `Node`/`Edge` or `MutualA`/`MutualB` at all
  // ("Failed to summon Pickler[List[Tree]]" -- its inline derivation has no recursion guard). Covered by
  // `SchemaCodecAgreementTest` and `CodecDerivationTest` on this side only.

  it should "agree on oneOfUsingField" in {
    sameJson(
      Pickler.oneOfUsingField[Status, Int](_.code, code => s"code-$code")(
        200 -> Pickler.derived[StatusOk],
        400 -> Pickler.derived[StatusBadRequest],
        500 -> Pickler.derived[StatusInternalError.type]
      ),
      OldPickler.oneOfUsingField[Status, Int](_.code, code => s"code-$code")(
        200 -> OldPickler.derived[StatusOk],
        400 -> OldPickler.derived[StatusBadRequest],
        500 -> OldPickler.derived[StatusInternalError.type]
      )
    )
  }

  it should "differ on Either only in the documented way" in {
    // §5.3: [0, x] / [1, x] became the bare value. Everything around the Either is the same.
    val next = Pickler.derived[ClassWithEither].toCodec
    val old = OldPickler.derived[ClassWithEither].toCodec
    forAll { (value: ClassWithEither) =>
      val nextJson = ujson.read(next.encode(value)).obj
      val oldJson = ujson.read(old.encode(value)).obj
      nextJson("fieldA") shouldBe oldJson("fieldA")
      val oldEither = oldJson("fieldB").arr
      oldEither(0) shouldBe ujson.Num(if (value.fieldB.isLeft) 0 else 1)
      nextJson("fieldB") shouldBe oldEither(1)
    }
  }
}
