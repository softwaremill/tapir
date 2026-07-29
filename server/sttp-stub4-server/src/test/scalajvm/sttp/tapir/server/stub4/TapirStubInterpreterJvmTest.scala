package sttp.tapir.server.stub4

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.shared.Identity
import sttp.tapir._
import sttp.tapir.client.sttp4.SttpClientInterpreter
import sttp.tapir.tests.TestUtil.{readFromFile, writeToFile}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/** Ranged file responses are materialized into a temporary file, which requires file-system access - on Scala.js, the stub throws instead.
  */
class TapirStubInterpreterJvmTest extends AnyFlatSpec with Matchers {

  behavior of "TapirStubInterpreter"

  it should "return a file range body, applying the range" in {
    // given
    val content = "hello"
    val file = writeToFile(content)
    val e = endpoint.get.in("file-range").out(fileRangeBody)
    // bytes 0-2, inclusive
    val range = RangeValue(Some(0), Some(2), content.length.toLong)

    val server = TapirSyncStubInterpreter()
      .whenServerEndpointRunLogic(e.serverLogicSuccess[Identity](_ => FileRange(file, Some(range))))
      .backend()

    // when
    val response = SttpClientInterpreter().toClientThrowDecodeFailures(e, None, server)(())

    // then
    Await.result(readFromFile(response.toOption.get.file), 3.seconds) shouldBe "hel"
  }

  it should "return a file range body, reading from the tail when the range has no start" in {
    // given
    val content = "hello"
    val file = writeToFile(content)
    val e = endpoint.get.in("file-range").out(fileRangeBody)
    // the last 2 bytes
    val range = RangeValue(None, Some(2), content.length.toLong)

    val server = TapirSyncStubInterpreter()
      .whenServerEndpointRunLogic(e.serverLogicSuccess[Identity](_ => FileRange(file, Some(range))))
      .backend()

    // when
    val response = SttpClientInterpreter().toClientThrowDecodeFailures(e, None, server)(())

    // then
    Await.result(readFromFile(response.toOption.get.file), 3.seconds) shouldBe "lo"
  }

  it should "return a file range body, applying a range without an end" in {
    // given
    val content = "hello"
    val file = writeToFile(content)
    val e = endpoint.get.in("file-range").out(fileRangeBody)
    val range = RangeValue(Some(1), None, content.length.toLong)

    val server = TapirSyncStubInterpreter()
      .whenServerEndpointRunLogic(e.serverLogicSuccess[Identity](_ => FileRange(file, Some(range))))
      .backend()

    // when
    val response = SttpClientInterpreter().toClientThrowDecodeFailures(e, None, server)(())

    // then
    Await.result(readFromFile(response.toOption.get.file), 3.seconds) shouldBe "ello"
  }
}
