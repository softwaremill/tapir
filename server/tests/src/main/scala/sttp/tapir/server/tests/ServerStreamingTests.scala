package sttp.tapir.server.tests

import cats.syntax.all._
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.Streams
import sttp.client3._
import sttp.model.{Header, HeaderNames, MediaType}
import sttp.monad.MonadError
import sttp.tapir.tests.Test
import sttp.tapir.tests.Streaming.{
  in_stream_out_either_json_xml_stream,
  in_stream_out_stream,
  in_stream_out_stream_with_content_length,
  in_string_stream_out_either_stream_string,
  out_custom_content_type_stream_body
}

class ServerStreamingTests[F[_], S, OPTIONS, ROUTE](createServerTest: CreateServerTest[F, S, OPTIONS, ROUTE], streams: Streams[S])(implicit
    m: MonadError[F]
) {

  def tests(): List[Test] = {
    import createServerTest._

    val penPineapple = "pen pineapple apple pen"

    List(
      testServer(in_stream_out_stream(streams))((s: streams.BinaryStream) => pureResult(s.asRight[Unit])) { (backend, baseUri) =>
        basicRequest.post(uri"$baseUri/api/echo").body(penPineapple).send(backend).map(_.body shouldBe Right(penPineapple))
      },
      testServer(
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
              response.contentLength shouldBe Some(penPineapple.length)
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
      },
      testServer(in_string_stream_out_either_stream_string(streams)) {
        case ("left", s) => pureResult((Left(s): Either[streams.BinaryStream, String]).asRight[Unit])
        case _           => pureResult((Right("was not left"): Either[streams.BinaryStream, String]).asRight[Unit])
      } { (backend, baseUri) =>
        basicRequest
          .post(uri"$baseUri?which=left")
          .body(penPineapple)
          .send(backend)
          .map(_.body shouldBe Right(penPineapple)) >>
          basicRequest
            .post(uri"$baseUri?which=right")
            .body(penPineapple)
            .send(backend)
            .map(_.body shouldBe Right("was not left"))
      },
      testServer(in_stream_out_either_json_xml_stream(streams)) { s => pureResult(s.asRight[Unit]) } { (backend, baseUri) =>
        basicRequest
          .post(uri"$baseUri")
          .body(penPineapple)
          .header(Header.accept(MediaType.ApplicationXml, MediaType.ApplicationJson))
          .send(backend)
          .map { r =>
            r.contentType shouldBe Some(MediaType.ApplicationXml.toString())
            r.body shouldBe Right(penPineapple)
          } >>
          basicRequest
            .post(uri"$baseUri")
            .body(penPineapple)
            .header(Header.accept(MediaType.ApplicationJson, MediaType.ApplicationXml))
            .send(backend)
            .map { r =>
              r.contentType shouldBe Some(MediaType.ApplicationJson.toString())
              r.body shouldBe Right(penPineapple)
            }
      }
    )
  }
}
