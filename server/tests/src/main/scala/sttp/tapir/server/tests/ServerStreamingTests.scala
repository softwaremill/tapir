package sttp.tapir.server.tests

import cats.syntax.all._
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.Streams
import sttp.client4._
import sttp.model.{Header, MediaType, StatusCode}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.tests.Test
import sttp.tapir.tests.Streaming._
import sttp.tapir.server.model.MaxContentLength
import sttp.tapir.AttributeKey
import cats.effect.IO
import sttp.capabilities.fs2.Fs2Streams

class ServerStreamingTests[F[_], S, OPTIONS, ROUTE](
    createServerTest: CreateServerTest[F, S, OPTIONS, ROUTE],
    maxLengthSupported: Boolean = true
)(implicit
    m: MonadError[F]
) {

  def tests(streams: Streams[_ >: S])(drain: streams.BinaryStream => F[Unit]): List[Test] = {
    import createServerTest._

    val penPineapple = "pen pineapple apple pen"

    val baseTests = List(
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

    val maxContentLengthTests = List(
      {
        val inputByteCount = 1024
        val maxBytes = 1024L
        val inputStream = fs2.Stream.fromIterator[IO](Iterator.fill[Byte](inputByteCount)('5'.toByte), chunkSize = 256)
        testServer(
          in_stream_out_stream(streams).attribute(AttributeKey[MaxContentLength], MaxContentLength(maxBytes)),
          "with request content length == max"
        )((s: streams.BinaryStream) => pureResult(s.asRight[Unit])) { (backend, baseUri) =>
          basicRequest
            .post(uri"$baseUri/api/echo")
            .streamBody(Fs2Streams[IO])(inputStream)
            .send(backend)
            .map(resp => assert(resp.isSuccess, "Response 200 OK"))
        }
      }, {
        val inputByteCount = 1024
        val maxBytes = 1023L
        val inputStream = fs2.Stream.fromIterator[IO](Iterator.fill[Byte](inputByteCount)('5'.toByte), chunkSize = 256)
        testServer(
          in_stream_out_string(streams).attribute(AttributeKey[MaxContentLength], MaxContentLength(maxBytes)),
          "with request content length > max"
        )((s: streams.BinaryStream) => drain(s).flatMap(_ => pureResult("ok".asRight[Unit]))) { (backend, baseUri) =>
          basicRequest
            .post(uri"$baseUri/api/echo")
            .streamBody(Fs2Streams[IO])(inputStream)
            .send(backend)
            .map(_.code shouldBe (StatusCode.PayloadTooLarge))
        }
      }
    )

    if (maxLengthSupported)
      baseTests ++ maxContentLengthTests
    else baseTests
  }
}
