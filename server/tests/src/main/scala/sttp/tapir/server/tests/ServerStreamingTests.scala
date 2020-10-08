package sttp.tapir.server.tests

import sttp.capabilities.Streams
import sttp.client3._
import sttp.tapir.tests.{in_stream_out_stream, in_stream_out_stream_with_content_length}
import cats.syntax.all._

trait ServerStreamingTests[F[_], S, ROUTE] { this: ServerTests[F, S, ROUTE] =>
  val streams: Streams[S]

  def streamingTests(): Unit = {
    val penPineapple = "pen pineapple apple pen"

    testServer(in_stream_out_stream(streams))((s: streams.BinaryStream) => pureResult(s.asRight[Unit])) { baseUri =>
      basicRequest.post(uri"$baseUri/api/echo").body(penPineapple).send(backend).map(_.body shouldBe Right(penPineapple))
    }

    testServer(
      in_stream_out_stream_with_content_length(streams)
    )((in: (Long, streams.BinaryStream)) => pureResult(in.asRight[Unit])) { baseUri =>
      {
        basicRequest.post(uri"$baseUri/api/echo").contentLength(penPineapple.length).body(penPineapple).send(backend).map { response =>
          response.body shouldBe Right(penPineapple)
          response.contentLength shouldBe Some(penPineapple.length)
        }
      }
    }
  }
}
