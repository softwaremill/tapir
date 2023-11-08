package sttp.tapir.server.jdkhttp.internal

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.server.jdkhttp.internal.KMPMatcher.{Match, NotMatched}

class KMPMatcherTest extends AnyFlatSpec with Matchers {

  it should "match over a set of bytes and not allow writing of any bytes if only matching" in {
    val matchBytes = "--abc\r\n".getBytes
    val matcher = new KMPMatcher(matchBytes)
    matcher.matchByte('-'.toByte) shouldBe NotMatched(0)
    matcher.matchByte('-'.toByte) shouldBe NotMatched(0)
    matcher.matchByte('a'.toByte) shouldBe NotMatched(0)
    matcher.matchByte('b'.toByte) shouldBe NotMatched(0)
    matcher.matchByte('c'.toByte) shouldBe NotMatched(0)
    matcher.matchByte('\r'.toByte) shouldBe NotMatched(0)
    matcher.matchByte('\n'.toByte) shouldBe Match
  }

  it should "match over a set of bytes and allow writing of any non-matched bytes" in {
    val matchBytes = "--abc\r\n".getBytes
    val matcher = new KMPMatcher(matchBytes)
    matcher.matchByte('-'.toByte) shouldBe NotMatched(0)
    matcher.matchByte('-'.toByte) shouldBe NotMatched(0)
    matcher.matchByte('-'.toByte) shouldBe NotMatched(1)
    matcher.matchByte('a'.toByte) shouldBe NotMatched(0)
    matcher.matchByte('a'.toByte) shouldBe NotMatched(3)
    matcher.matchByte('-'.toByte) shouldBe NotMatched(0)
    matcher.matchByte('-'.toByte) shouldBe NotMatched(0)
    matcher.matchByte('a'.toByte) shouldBe NotMatched(0)
    matcher.matchByte('b'.toByte) shouldBe NotMatched(0)
    matcher.matchByte('c'.toByte) shouldBe NotMatched(0)
    matcher.matchByte('\r'.toByte) shouldBe NotMatched(0)
    matcher.matchByte('\n'.toByte) shouldBe Match
  }
}
