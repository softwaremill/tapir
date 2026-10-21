package sttp.tapir.json.pickler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.json.pickler.internal.compiletime.DerivationTimeout

import scala.concurrent.duration.*

class DerivationTimeoutTest extends AnyFlatSpec with Matchers {
  it should "parse the timeout setting" in {
    DerivationTimeout.parse("120") shouldBe Some(120.seconds)
    DerivationTimeout.parse("30s") shouldBe Some(30.seconds)
    DerivationTimeout.parse("2 minutes") shouldBe Some(2.minutes)
    DerivationTimeout.parse("0") shouldBe None
    DerivationTimeout.parse("Inf") shouldBe None
    DerivationTimeout.parse("soon") shouldBe None
  }
}
