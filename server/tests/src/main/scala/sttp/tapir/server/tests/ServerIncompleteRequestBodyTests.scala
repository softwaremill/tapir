package sttp.tapir.server.tests

import cats.effect.{IO, Resource}
import org.scalatest.matchers.should.Matchers._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.tests._

import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._

class ServerIncompleteRequestBodyTests[F[_], OPTIONS, ROUTE](createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE])(implicit
    m: MonadError[F]
) {
  import createServerTest._

  def tests(): List[Test] = List({
    val logicCalled = new AtomicBoolean(false)

    testServerLogic(
      endpoint.put
        .in("incomplete")
        .in(stringBody)
        .out(stringBody)
        .serverLogicSuccess[F] { body =>
          logicCalled.set(true)
          m.unit(body)
        },
      "connection closed mid-body does not pass the truncated body to the server logic"
    ) { (_, baseUri) =>
      val port = baseUri.port.get
      val requestHead =
        s"PUT /incomplete HTTP/1.1\r\nHost: localhost:$port\r\nContent-Type: text/plain\r\nContent-Length: 10000\r\n\r\n"
          .getBytes(StandardCharsets.UTF_8)

      Resource
        .fromAutoCloseable(IO.blocking(new Socket("localhost", port)))
        .use { socket =>
          send(socket, requestHead) >> send(socket, "abcd".getBytes(StandardCharsets.UTF_8)) >> IO.blocking(socket.shutdownOutput())
        } >>
        IO.sleep(1.second) >>
        IO {
          logicCalled.get() shouldBe false
        }
    }
  })

  private def send(socket: Socket, bytes: Array[Byte]): IO[Unit] =
    IO.blocking {
      socket.getOutputStream.write(bytes)
      socket.getOutputStream.flush()
    }
}
