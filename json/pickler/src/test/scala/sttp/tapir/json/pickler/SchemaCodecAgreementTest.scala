package sttp.tapir.json.pickler

import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, writeToString}
import org.scalacheck.Arbitrary
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

/** For generated values of every fixture, under every configuration: the JSON the codec writes has the shape the schema documents
  * (`SchemaJsonAgreement`), and decodes back to the value. Plan §7.2 called this "the entire reason for deriving them together" -- it is
  * the property the string assertions in the other suites only sample.
  */
class SchemaCodecAgreementTest extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {
  import CodecFixtures.{MutualA, Node, Tree}
  import Fixtures.*
  import PropertyFixtures.*
  import Generators.given

  /** The jsoniter codec is used directly: the tapir `Codec` on top of it maps `None` of an optional root to an empty body
    * (`Codec.anyString`), which is not JSON and not this module's doing.
    */
  private def agree[T: Arbitrary](pickler: Pickler[T]): Unit = {
    given com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec[T] = pickler.codec
    forAll { (value: T) =>
      val json = writeToString(value)
      val mismatches = SchemaJsonAgreement.mismatches(pickler.schema, ujson.read(json))
      withClue(s"value $value encoded as $json under schema ${pickler.schema}:\n") {
        mismatches shouldBe Nil
      }
      readFromString[T](json) shouldBe value
    }
  }

  /** Every configuration knob that changes the wire format; the schema has to follow each one. */
  private inline def agreeUnderEveryConfiguration[T: Arbitrary]: Unit = {
    agree(Pickler.derived[T](using PicklerConfiguration.default))
    agree(Pickler.derived[T](using PicklerConfiguration.default.withSnakeCaseMemberNames))
    agree(Pickler.derived[T](using PicklerConfiguration.default.withScreamingSnakeCaseMemberNames))
    agree(Pickler.derived[T](using PicklerConfiguration.default.withKebabCaseMemberNames))
    agree(Pickler.derived[T](using PicklerConfiguration.default.withToEncodedName(_.toUpperCase)))
    agree(Pickler.derived[T](using PicklerConfiguration.default.withDiscriminator("kind")))
    agree(Pickler.derived[T](using PicklerConfiguration.default.withSnakeCaseDiscriminatorValues))
    agree(Pickler.derived[T](using PicklerConfiguration.default.withKebabCaseDiscriminatorValues))
    agree(Pickler.derived[T](using PicklerConfiguration.default.withScreamingSnakeCaseDiscriminatorValues))
    agree(Pickler.derived[T](using PicklerConfiguration.default.withFullDiscriminatorValues))
    agree(Pickler.derived[T](using PicklerConfiguration.default.withFullSnakeCaseDiscriminatorValues))
    agree(Pickler.derived[T](using PicklerConfiguration.default.withFullKebabCaseDiscriminatorValues))
    agree(Pickler.derived[T](using PicklerConfiguration.default.withTransientNone(false)))
    agree(
      Pickler.derived[T](using
        PicklerConfiguration.default.withSnakeCaseMemberNames.withDiscriminator("t").withFullKebabCaseDiscriminatorValues
      )
    )
  }

  behavior of "schema/codec agreement"

  it should "hold for products" in {
    agreeUnderEveryConfiguration[FlatClass]
    agreeUnderEveryConfiguration[TopClass]
    agreeUnderEveryConfiguration[TopClass2]
    agreeUnderEveryConfiguration[ClassWithValues]
    agreeUnderEveryConfiguration[ClassWithScalaDefault]
  }

  it should "hold for optional fields" in {
    agreeUnderEveryConfiguration[FlatClassWithOption]
    agreeUnderEveryConfiguration[NestedClassWithOption]
  }

  it should "hold for collections and maps" in {
    agreeUnderEveryConfiguration[FlatClassWithList]
    agreeUnderEveryConfiguration[NestedClassWithList]
    agreeUnderEveryConfiguration[ClassWithMap]
  }

  it should "hold for Either" in {
    agreeUnderEveryConfiguration[ClassWithEither]
  }

  it should "hold for sealed hierarchies" in {
    agreeUnderEveryConfiguration[ErrorCodeHolder]
    agreeUnderEveryConfiguration[StatusResponse]
    agreeUnderEveryConfiguration[Status]
    agreeUnderEveryConfiguration[NotAllSealedVariant]
    agreeUnderEveryConfiguration[Entity]
  }

  it should "hold for enumerations" in {
    agreeUnderEveryConfiguration[SealedVariantContainer]
    agreeUnderEveryConfiguration[Response]
    agreeUnderEveryConfiguration[RichColorResponse]
    agree(Pickler.derivedEnumeration[ColorEnum].customStringBased(_.ordinal.toString))
    agree(Pickler.derivedEnumeration[RichColorEnum].customStringBased(c => s"color-${c.code}"))
  }

  it should "hold for recursive types" in {
    agreeUnderEveryConfiguration[Tree]
    agreeUnderEveryConfiguration[Node]
    agreeUnderEveryConfiguration[MutualA]
  }

  it should "hold for non-structural roots" in {
    agree(Pickler.derived[List[FlatClass]])
    agree(Pickler.derived[Option[Status]])
    agree(Pickler.derived[Map[String, Tree]])
    agree(Pickler.derived[Either[List[Int], Status]])
  }

  it should "hold for a user pickler for a nested type" in {
    given Pickler[SimpleTestResult] =
      Pickler.derived[SimpleTestResult](using PicklerConfiguration.default.withScreamingSnakeCaseMemberNames)
    agree(Pickler.derived[ClassWithMap])
    agree(Pickler.derived[ClassWithEither])
  }

  it should "hold for oneOfUsingField" in {
    given Pickler[Status] = Pickler.oneOfUsingField[Status, Int](_.code, code => s"code-$code")(
      200 -> Pickler.derived[StatusOk],
      400 -> Pickler.derived[StatusBadRequest],
      500 -> Pickler.derived[StatusInternalError.type]
    )
    agree(summon[Pickler[Status]])
    agree(Pickler.derived[StatusResponse])
  }
}
