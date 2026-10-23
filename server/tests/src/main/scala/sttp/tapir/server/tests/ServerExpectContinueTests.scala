package sttp.tapir.server.tests

import cats.effect.IO
import org.scalatest.matchers.should.Matchers._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.server.tests.RawHttpClient._
import sttp.tapir.tests._

import java.nio.charset.StandardCharsets

/** Tests requests with `Expect: 100-continue`, using a raw socket, as HTTP clients handle the `100 Continue` response transparently. */
class ServerExpectContinueTests[F[_], OPTIONS, ROUTE](createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE])(implicit
    m: MonadError[F]
) {
  import createServerTest._

  private val echoEndpoint = endpoint.put.in("expect-continue").in(stringBody).out(stringBody).serverLogicSuccess[F](body => m.unit(body))

  def tests(): List[Test] = List(
    testServerLogic(echoEndpoint, "body sent after 100 Continue is echoed, and the connection stays open") { (_, baseUri) =>
      val port = baseUri.port.get
      val body = "abcd"

      clientSocket(port)
        .use { socket =>
          val in = socket.getInputStream
          for {
            _ <- send(socket, requestHead(port, "/expect-continue", body, List("Expect: 100-continue")))
            continue <- readResponseHead(in)
            _ <- send(socket, body.getBytes(StandardCharsets.UTF_8))
            response <- readResponse(in)
            // a plain request on the same connection
            _ <- send(socket, requestHead(port, "/expect-continue", body, Nil) ++ body.getBytes(StandardCharsets.UTF_8))
            secondResponse <- readResponse(in)
          } yield {
            continue.statusLine shouldBe "HTTP/1.1 100 Continue"
            response.head.statusLine shouldBe "HTTP/1.1 200 OK"
            response.body shouldBe body
            secondResponse.head.statusLine shouldBe "HTTP/1.1 200 OK"
            secondResponse.body shouldBe body
          }
        }
    },
    testServerLogic(echoEndpoint, "request answered without reading the body gets no 100 Continue, and the connection is closed") {
      (_, baseUri) =>
        val port = baseUri.port.get

        clientSocket(port)
          .use { socket =>
            val in = socket.getInputStream
            for {
              // the body is never sent; the server must respond without waiting for it
              _ <- send(socket, requestHead(port, "/not-expect-continue", "abcd", List("Expect: 100-continue")))
              response <- readResponse(in)
              afterResponse <- IO.blocking(in.read())
            } yield {
              response.head.statusLine shouldBe "HTTP/1.1 404 Not Found"
              afterResponse shouldBe -1
            }
          }
    }
  )
}
