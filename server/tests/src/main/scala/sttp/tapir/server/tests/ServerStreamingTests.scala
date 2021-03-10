package sttp.tapir.server.tests

import cats.effect.IO
import sttp.capabilities.Streams
import sttp.client3._
import sttp.tapir.tests.{Test, in_stream_out_stream, in_stream_out_stream_with_content_length}
import cats.syntax.all._
import sttp.monad.MonadError
import org.scalatest.matchers.should.Matchers._
import sttp.model.{Header, HeaderNames}

class ServerStreamingTests[F[_], S, ROUTE](backend: SttpBackend[IO, Any], serverTests: CreateServerTest[F, S, ROUTE], streams: Streams[S])(
    implicit m: MonadError[F]
) {

  private def pureResult[T](t: T): F[T] = m.unit(t)

  def tests(): List[Test] = {
    import serverTests._

    val penPineapple = "pen pineapple apple pen"

    List(
      testServer(in_stream_out_stream(streams))((s: streams.BinaryStream) => pureResult(s.asRight[Unit])) { baseUri =>
        basicRequest.post(uri"$baseUri/api/echo").body(penPineapple).send(backend).map(_.body shouldBe Right(penPineapple))
      },
      testServer(
        in_stream_out_stream_with_content_length(streams)
      )((in: (Long, streams.BinaryStream)) => pureResult(in.asRight[Unit])) { baseUri =>
        {
          basicRequest.post(uri"$baseUri/api/echo").contentLength(penPineapple.length.toLong).body(penPineapple).send(backend).map {
            response =>
              response.body shouldBe Right(penPineapple)
              if (response.headers.contains(Header(HeaderNames.TransferEncoding, "chunked"))) {
                response.contentLength shouldBe None
              } else {
                response.contentLength shouldBe Some(penPineapple.length)
              }
          }
        }
      }
    )
  }
}
