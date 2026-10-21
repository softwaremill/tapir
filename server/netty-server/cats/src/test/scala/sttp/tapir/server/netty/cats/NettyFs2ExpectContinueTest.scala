package sttp.tapir.server.netty.cats

import cats.effect.IO
import org.scalatest.matchers.should.Matchers._
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.server.tests.CreateServerTest
import sttp.tapir.server.tests.RawHttpClient._
import sttp.tapir.tests.Test

import java.nio.charset.StandardCharsets

class NettyFs2ExpectContinueTest[OPTIONS, ROUTE](createServerTest: CreateServerTest[IO, Fs2Streams[IO], OPTIONS, ROUTE]) {
  import createServerTest._

  implicit val m: MonadError[IO] = new CatsMonadError[IO]()

  // #5503: the body is only requested (triggering 100 Continue) when the stream is pulled in the server logic, not during decoding
  def tests(): List[Test] = List(
    testServerLogic(
      endpoint.put
        .in("expect-continue-stream")
        .in(streamTextBody(Fs2Streams[IO])(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))
        .out(stringBody)
        .serverLogicSuccess[IO](_.through(fs2.text.utf8.decode).compile.string),
      "body streamed in the server logic is sent after 100 Continue"
    ) { (_, baseUri) =>
      val port = baseUri.port.get
      val body = "abcd"

      clientSocket(port)
        .use { socket =>
          val in = socket.getInputStream
          for {
            _ <- send(socket, requestHead(port, "/expect-continue-stream", body, List("Expect: 100-continue")))
            continue <- readResponseHead(in)
            _ <- send(socket, body.getBytes(StandardCharsets.UTF_8))
            response <- readResponse(in)
          } yield {
            continue.statusLine shouldBe "HTTP/1.1 100 Continue"
            response.head.statusLine shouldBe "HTTP/1.1 200 OK"
            response.body shouldBe body
          }
        }
    }
  )
}
