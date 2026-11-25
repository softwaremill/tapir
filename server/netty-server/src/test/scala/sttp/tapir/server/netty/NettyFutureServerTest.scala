package sttp.tapir.server.netty

import cats.effect.{IO, Resource}
import cats.syntax.all._
import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers._
import sttp.monad.FutureMonad
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.netty.internal.FutureUtil.nettyFutureToScala
import sttp.tapir.server.tests._
import sttp.tapir.tests.{Test, TestSuite}
import sttp.client4._

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class NettyFutureServerTest extends TestSuite with EitherValues {
  override def tests: Resource[IO, List[Test]] =
    backendResource.flatMap { backend =>
      Resource
        .make(IO.delay {
          // not the suite's serial execution context, which runs one task at a time: then, tests in which requests block concurrently are flaky
          val serverEc = ExecutionContext.global
          implicit val m: FutureMonad = new FutureMonad()(serverEc)
          val eventLoopGroup = new NioEventLoopGroup()

          val interpreter = new NettyFutureTestServerInterpreter(eventLoopGroup)(serverEc)
          val createServerTest = new DefaultCreateServerTest(backend, interpreter)

          val tests =
            new AllServerTests(createServerTest, interpreter, backend, multipart = true, partOtherHeaderSupport = false).tests() ++
              new ServerGracefulShutdownTests(createServerTest).tests() ++
              new NettyServerStopTests(eventLoopGroup) {
                // not IO.fromFuture, which can't be timed out
                override def withServer[A](config: NettyConfig)(use: IO[Unit] => IO[A]): IO[A] =
                  IO.blocking(Await.result(NettyFutureServer(config)(using serverEc).start(), 10.seconds))
                    .flatMap(server => use(IO.blocking(Await.result(server.stop(), 10.seconds))))
              }.tests() ++
              new ServerIncompleteRequestBodyTests(createServerTest).tests() ++
              new NettyFutureRequestTimeoutTests(eventLoopGroup, backend).tests() ++
              additionalTests(backend, eventLoopGroup) ++
              expectContinueTests(createServerTest)

          (tests, eventLoopGroup)
        }) { case (_, eventLoopGroup) =>
          IO.fromFuture(IO.delay(nettyFutureToScala(eventLoopGroup.shutdownGracefully()): Future[?])).void
        }
        .map { case (tests, _) => tests }
    }

  def additionalTests(backend: Backend[IO], eventLoopGroup: NioEventLoopGroup)(implicit ec: ExecutionContext): List[Test] = List(
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
    },
    Test("a route throwing an exception, instead of returning a failed Future, should result in a 500, sent after the earlier responses") {
      import cats.effect.unsafe.implicits.global

      val releaseFirst = Promise[Unit]()
      val secondReceived = new CountDownLatch(1)
      val route: FutureRoute = request =>
        if (request.pathSegments == List("first"))
          releaseFirst.future.map(_ => Some(ServerResponse[NettyResponse](StatusCode.Ok, Nil, None, None)))(ec)
        else {
          secondReceived.countDown()
          throw new RuntimeException("boom")
        }

      Resource
        .make(IO.fromFuture(IO.delay(NettyFutureServer()(using ec).port(0).addRoute(route).start())))(server =>
          IO.fromFuture(IO.delay(server.stop()))
        )
        .flatMap(server => rawSocket(server.port))
        .use { socket =>
          for {
            _ <- sendRaw(socket, rawGet("/first") ++ rawGet("/second"))
            // the second request is handled while the first one is in flight
            secondArrived <- IO.blocking(secondReceived.await(10, TimeUnit.SECONDS))
            _ <- IO(releaseFirst.success(()))
            response1 <- readRawResponse(socket)
            response2 <- readRawResponse(socket)
          } yield {
            (secondArrived shouldBe true): Unit
            List(response1, response2) shouldBe List((200, ""), (500, ""))
          }
        }
        .unsafeToFuture()
    }
  )

  // Netty-only: if the body isn't read, RFC 9110 lets a server either skip the 100 (as Netty does), or send it and discard the body. The
  // code is the same for all Netty interpreters, so it's tested once.
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
