package sttp.tapir.server.tests

import cats.syntax.all._
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.Streams
import sttp.client3._
import sttp.model.{Header, HeaderNames, MediaType}
import sttp.monad.MonadError
import sttp.tapir.tests.Test
import sttp.tapir.tests.Streaming.{in_stream_out_stream, in_stream_out_stream_with_content_length, out_custom_content_type_stream_body}

class ServerStreamingTests[F[_], S, OPTIONS, ROUTE](createServerTest: CreateServerTest[F, S, OPTIONS, ROUTE], streams: Streams[S])(implicit
    m: MonadError[F]
) {

  def tests(): List[Test] = {
    import createServerTest._

    val penPineapple = "pen pineapple apple pen"

    List(
      // TODO: remove explicit type parameters when https://github.com/lampepfl/dotty/issues/12803 fixed
      testServer[streams.BinaryStream, Unit, streams.BinaryStream](in_stream_out_stream(streams))((s: streams.BinaryStream) =>
        pureResult(s.asRight[Unit])
      ) { (backend, baseUri) =>
        basicRequest.post(uri"$baseUri/api/echo").body(penPineapple).send(backend).map(_.body shouldBe Right(penPineapple))
      },
      testServer[(Long, streams.BinaryStream), Unit, (Long, streams.BinaryStream)](
        in_stream_out_stream_with_content_length(streams)
      )((in: (Long, streams.BinaryStream)) => pureResult(in.asRight[Unit])) { (backend, baseUri) =>
        {
          basicRequest
            .post(uri"$baseUri/api/echo")
            .contentLength(penPineapple.length.toLong)
            .body(penPineapple)
            .send(backend)
            .map { response =>
              response.body shouldBe Right(penPineapple)
              if (response.headers.contains(Header(HeaderNames.TransferEncoding, "chunked"))) {
                response.contentLength shouldBe None
              } else {
                response.contentLength shouldBe Some(penPineapple.length)
              }
            }
        }
      },
      testServer(out_custom_content_type_stream_body(streams)) { case (k, s) =>
        pureResult((if (k < 0) (MediaType.ApplicationJson.toString(), s) else (MediaType.ApplicationXml.toString(), s)).asRight[Unit])
      } { (backend, baseUri) =>
        basicRequest
          .post(uri"$baseUri?kind=-1")
          .body(penPineapple)
          .send(backend)
          .map { r =>
            r.body shouldBe Right(penPineapple)
            r.contentType shouldBe Some(MediaType.ApplicationJson.toString())
          } >>
          basicRequest
            .post(uri"$baseUri?kind=1")
            .body(penPineapple)
            .send(backend)
            .map { r =>
              r.body shouldBe Right(penPineapple)
              r.contentType shouldBe Some(MediaType.ApplicationXml.toString())
            }
      }
    )
  }
}
