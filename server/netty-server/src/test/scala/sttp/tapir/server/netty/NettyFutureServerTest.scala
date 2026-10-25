package sttp.tapir.server.netty

import cats.effect.{IO, Resource}
import cats.syntax.all._
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers._
import sttp.monad.FutureMonad
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.server.netty.internal.FutureUtil.nettyFutureToScala
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import sttp.client4._

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

class NettyFutureServerTest extends TestSuite with EitherValues {
  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource
        .make(IO.delay {
          implicit val m: FutureMonad = new FutureMonad()
          val eventLoopGroup = new NioEventLoopGroup()

          val interpreter = new NettyFutureTestServerInterpreter(eventLoopGroup)
          val createServerTest = new DefaultCreateServerTest(backend, interpreter)

          val tests =
            new AllServerTests(createServerTest, interpreter, backend, multipart = true, partOtherHeaderSupport = false).tests() ++
              new ServerGracefulShutdownTests(createServerTest, Sleeper.futureSleeper).tests() ++
              new ServerIncompleteRequestBodyTests(createServerTest).tests() ++
              new NettyFutureRequestTimeoutTests(eventLoopGroup, backend).tests() ++
              additionalTests(backend) ++
              expectContinueTests(createServerTest)

          (tests, eventLoopGroup)
        }) { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(nettyFutureToScala(eventLoopGroup.shutdownGracefully()): Future[?])).void
        }
        .map { case (tests, _) => tests }
    }

  def additionalTests(backend: Backend[IO])(implicit ec: ExecutionContext): List[Test] = List(
    // Netty samples the requests by default, hence leaks are only detected if the number of requests is large enough.
    // There are no assertions in this test, as if there are leaks, they are reported in the test output;
    // the verification is done as part of CI.
    Test("a large number of requests ignoring the request body shouldn't cause leaks") {
      import cats.effect.unsafe.implicits.global

      val ep = endpoint.post.in(query[Int]("x")).in(stringBody).out(stringBody)
      val sep = ep.serverLogic[Future] { case (a, b) => Future.successful(Right(s"$a $b")) }

      val bind = IO.fromFuture(IO.delay(NettyFutureServer()(using ec).addEndpoints(List(sep)).start()))
      Resource
        .make(bind)(server => IO.fromFuture(IO.delay(server.stop())))
        .map(_.port)
        .use { port =>
          (0 until 10000).toList
            .traverse_ { i =>
              // the query is causing a decode failure, which in turn returns a 400 bad request
              // the request body is never read and should be ignored
              basicRequest.response(asStringAlways).body("abcde").post(uri"http://localhost:$port?x=abc").send(backend).map { response =>
                response.code shouldBe StatusCode.BadRequest
              }
            }
            .map(_ => succeed)
        }
        .unsafeToFuture()
    }
  )

  // Netty-specific, as other servers might instead read and discard the body; the same for all Netty interpreters, so tested once
  private def expectContinueTests(createServerTest: CreateServerTest[Future, Any, NettyFutureServerOptions, FutureRoute]): List[Test] =
    List(
      createServerTest.testServerLogic(
        // a streamed response, as that's when a 100 might be sent for a body that is read later
        endpoint.post
          .in("expect")
          .out(inputStreamBody)
          .serverLogicSuccess[Future](_ => Future.successful(new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)))),
        "respond with a streamed body, without 100 Continue, and close the connection, if the request body is not read"
      ) { (_, baseUri) =>
        val port = baseUri.port.get
        val requestHead = s"POST /expect HTTP/1.1\r\nHost: localhost:$port\r\nContent-Length: 4\r\nExpect: 100-continue\r\n\r\n"
        rawSocket(port).use { socket =>
          for {
            _ <- sendRaw(socket, requestHead.getBytes(StandardCharsets.UTF_8))
            response <- readRawResponse(socket)
            connectionClosed <- IO.blocking(socket.getInputStream.read() == -1)
          } yield (response, connectionClosed) shouldBe (((200, "ok"), true))
        }
      }
    )
}
