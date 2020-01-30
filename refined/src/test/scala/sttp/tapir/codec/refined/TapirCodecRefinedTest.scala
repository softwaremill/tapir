package sttp.tapir.codec.refined

import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineMV
import eu.timepit.refined.types.string.NonEmptyString
import org.scalatest.{FlatSpec, Matchers}
import sttp.tapir.Codec.PlainCodec
import sttp.tapir.DecodeResult

class TapirCodecRefinedTest extends FlatSpec with Matchers with TapirCodecRefined {

  val nonEmptyStringCodec = implicitly[PlainCodec[NonEmptyString]]

  it should "return DecodResult.Invalid if subtype can't be refined" in {
    nonEmptyStringCodec.decode("") should matchPattern{case DecodeResult.InvalidValue(_) =>}
  }

  it should "correctly delegate to raw parser and refine it" in {
    nonEmptyStringCodec.decode("vive le fromage") shouldBe DecodeResult.Value(refineMV[NonEmpty]("vive le fromage"))
  }
}
