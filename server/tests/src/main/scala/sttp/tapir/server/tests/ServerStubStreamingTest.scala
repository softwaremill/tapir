package sttp.tapir.server.tests

import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.Streams
import sttp.client4._
import sttp.client4.testing.StreamBackendStub
import sttp.tapir.client.sttp4.stream.StreamSttpClientInterpreter
import sttp.tapir.server.stub4.TapirStreamStubInterpreter
import sttp.tapir.tests.Streaming.in_stream_out_stream

abstract class ServerStubStreamingTest[F[_], S <: Streams[S], OPTIONS](
    createStubServerTest: CreateServerStubTest[F, OPTIONS],
    streams: Streams[S]
) extends AsyncFlatSpec
    with Matchers
    with BeforeAndAfterAll {

  /** Must be an instance of streams.BinaryStream */
  def sampleStream: Any

  override protected def afterAll(): Unit = createStubServerTest.cleanUp()

  it should "accept stream input and stub stream output" in {
    val server: StreamBackend[F, S] =
      TapirStreamStubInterpreter(createStubServerTest.customiseInterceptors, StreamBackendStub[F, S](createStubServerTest.stub.monad))
        .whenEndpoint(in_stream_out_stream(streams))
        .thenRespond(sampleStream.asInstanceOf[streams.BinaryStream])
        .backend()

    val response = StreamSttpClientInterpreter()
      // for an unknown reason, explicit type parameters are required in Scala3, otherwise there's a compiler error
      .toRequestThrowDecodeFailures[streams.BinaryStream, Unit, streams.BinaryStream, S](
        in_stream_out_stream(streams),
        Some(uri"http://test.com")
      )
      .apply(sampleStream.asInstanceOf[streams.BinaryStream])
      .send(server)

    createStubServerTest.asFuture(response).map(_.body.isInstanceOf[streams.BinaryStream] shouldBe true)
  }
}
