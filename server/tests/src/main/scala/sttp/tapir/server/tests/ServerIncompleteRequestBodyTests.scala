package sttp.tapir.server.tests

import cats.effect.{IO, Resource}
import org.scalatest.matchers.should.Matchers._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.server.interceptor.RequestInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.model.IncompleteRequestBodyException
import sttp.tapir.tests._

import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import scala.concurrent.duration._

/** Not applicable to servers which cancel request processing when the connection closes, as the body is then never decoded. */
class ServerIncompleteRequestBodyTests[F[_], OPTIONS, ROUTE](createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE])(implicit
    m: MonadError[F]
) {
  import createServerTest._

  def tests(): List[Test] = List(
    incompleteBodyTest(endpoint.put.in("incomplete").in(stringBody).out(stringBody), "string")(
      contentType = "text/plain",
      sentBody = "abcd"
    ),
    // a body large enough to arrive in several chunks, the first part of which is complete
    incompleteBodyTest(endpoint.put.in("incomplete").in(multipartBody).out(stringBody), "multipart")(
      contentType = "multipart/form-data; boundary=b",
      sentBody =
        "--b\r\nContent-Disposition: form-data; name=\"p1\"\r\n\r\nv1\r\n--b\r\nContent-Disposition: form-data; name=\"p2\"\r\n\r\n" +
          ("x" * 50000)
    )
  )

  private def incompleteBodyTest[I](e: PublicEndpoint[I, Unit, String, Any], bodyKind: String)(
      contentType: String,
      sentBody: String
  ): Test = {
    val processingStarted = new CompletableFuture[Unit]()
    val outcome = new CompletableFuture[Either[String, DecodeResult.Failure]]()
    val declaredLength = 100000L
    val sentBytes = sentBody.getBytes(StandardCharsets.UTF_8)

    testServerLogic(
      e.serverLogicSuccess[F] { _ =>
        val _ = outcome.complete(Left("server logic called"))
        m.unit("")
      },
      s"connection closed mid-body does not pass the truncated $bodyKind body to the server logic",
      _.prependInterceptor(RequestInterceptor.effect[F](_ => m.eval { val _ = processingStarted.complete(()) }))
        .decodeFailureHandler(DecodeFailureHandler[F] { ctx =>
          val _ = outcome.complete(Right(ctx.failure))
          DefaultDecodeFailureHandler[F](ctx)
        })
    ) { (_, baseUri) =>
      val port = baseUri.port.get
      val requestHead =
        s"PUT /incomplete HTTP/1.1\r\nHost: localhost:$port\r\nContent-Type: $contentType\r\nContent-Length: $declaredLength\r\n\r\n"
          .getBytes(StandardCharsets.UTF_8)

      Resource
        .fromAutoCloseable(IO.blocking(new Socket("localhost", port)))
        .use { socket =>
          // a server might skip requests whose connection is closed before their processing starts
          send(socket, requestHead) >> IO.fromCompletableFuture(IO(processingStarted)).timeout(5.seconds) >>
            send(socket, sentBytes) >> IO.blocking(socket.shutdownOutput())
        } >>
        IO.fromCompletableFuture(IO(outcome)).timeout(5.seconds).map { result =>
          result should matchPattern {
            case Right(DecodeResult.Error(_, IncompleteRequestBodyException(received, `declaredLength`))) if received == sentBytes.length =>
          }
        }
    }
  }

  private def send(socket: Socket, bytes: Array[Byte]): IO[Unit] =
    IO.blocking {
      socket.getOutputStream.write(bytes)
      socket.getOutputStream.flush()
    }
}
