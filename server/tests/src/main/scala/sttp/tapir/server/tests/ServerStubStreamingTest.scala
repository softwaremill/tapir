package sttp.tapir.server.tests

import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.Streams
import sttp.client3._
import sttp.tapir.client.sttp.SttpClientInterpreter
import sttp.tapir.server.stub.TapirStubInterpreter
import sttp.tapir.tests.Streaming.in_stream_out_stream

abstract class ServerStubStreamingTest[F[_], S, OPTIONS](
    createStubServerTest: CreateServerStubTest[F, OPTIONS],
    streams: Streams[S]
) extends AsyncFlatSpec
    with Matchers
    with BeforeAndAfterAll {

  /** Must be an instance of streams.BinaryStream */
  def sampleStream: Any

  override protected def afterAll(): Unit = createStubServerTest.cleanUp()

  it should "accept stream input and stub stream output" in {
    val server: SttpBackend[F, S] = TapirStubInterpreter(createStubServerTest.customInterceptors, createStubServerTest.stub)
      .whenEndpoint(in_stream_out_stream(streams))
      .respond(sampleStream.asInstanceOf[streams.BinaryStream])
      .backend()

    val response = SttpClientInterpreter()
      .toRequestThrowDecodeFailures(in_stream_out_stream(streams), Some(uri"http://test.com"))
      .apply(sampleStream.asInstanceOf[streams.BinaryStream])
      .send(server)

    createStubServerTest.asFuture(response).map(_.body.isInstanceOf[streams.BinaryStream] shouldBe true)
  }
}
