package sttp.tapir.server.tests

import cats.effect.{IO, Resource}
import org.scalatest.matchers.should.Matchers._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.model.IncompleteRequestBodyException
import sttp.tapir.tests._

import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.duration._

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
    val logicCalled = new AtomicBoolean(false)
    val decodeFailure = new AtomicReference[Option[DecodeResult.Failure]](None)
    val declaredLength = 100000L
    val sentBytes = sentBody.getBytes(StandardCharsets.UTF_8)

    testServerLogic(
      e.serverLogicSuccess[F] { _ =>
        logicCalled.set(true)
        m.unit("")
      },
      s"connection closed mid-body does not pass the truncated $bodyKind body to the server logic",
      _.decodeFailureHandler(DecodeFailureHandler[F] { ctx =>
        decodeFailure.set(Some(ctx.failure))
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
          send(socket, requestHead) >> send(socket, sentBytes) >> IO.blocking(socket.shutdownOutput())
        } >>
        // servers which cancel request processing when the connection closes might never reach decoding or the logic
        (IO.sleep(10.millis) >> IO(decodeFailure.get().isDefined || logicCalled.get()))
          .iterateUntil(identity)
          .timeoutTo(2.seconds, IO.unit) >>
        IO {
          logicCalled.get() shouldBe false
          decodeFailure.get() should matchPattern {
            case None                                                                                                                    =>
            case Some(DecodeResult.Error(_, IncompleteRequestBodyException(received, `declaredLength`))) if received == sentBytes.length =>
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
