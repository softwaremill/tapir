package sttp.tapir.grpc.protobuf

import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers

trait ProtobufMatchers extends Matchers {
  def matchProtos(result: String, expected: String): Assertion =
    result.filterNot(_.isWhitespace) shouldBe expected.filterNot(_.isWhitespace)
}
