package sttp.tapir.server.tests

import cats.effect.IO
import cats.syntax.all._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers._
import sttp.client4._
import sttp.model.HeaderNames
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir._
import sttp.tapir.tests._

import java.io.ByteArrayInputStream
import java.net.{ConnectException, Socket}
import java.nio.charset.StandardCharsets
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.duration._

class ServerGracefulShutdownTests[F[_], OPTIONS, ROUTE](createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE], sleeper: Sleeper[F])(
    implicit m: MonadError[F]
) extends EitherValues {
  import createServerTest._
  import ServerGracefulShutdownTests._

  def tests(): List[Test] = List(
    {
      // Released by the server logic once it starts handling the request, so the test triggers shutdown only after
      // the request is genuinely in-flight. Relying on a fixed sleep instead races under load: the stop could begin
      // before the request reaches the server, which then aborts it and fails the test.
      // The graceful-shutdown timeout is kept well above the request duration: the server force-closes in-flight
      // requests once the timeout elapses, and a tight margin (e.g. 3s of work vs a 4s timeout) flakes on a loaded
      // CI runner where the sleep and response write drift past it. A larger timeout doesn't slow the happy path -
      // the stop returns as soon as the request's connection closes, not when the timeout elapses.
      val requestReceived = new CountDownLatch(1)
      testServerLogicWithStop(
        endpoint
          .out(plainBody[String])
          .serverLogic { _ =>
            m.eval(requestReceived.countDown())
              .flatMap(_ => sleeper.sleep(2.seconds))
              .flatMap(_ => pureResult("processing finished".asRight[Unit]))
          },
        "Server waits for long-running request to complete within timeout",
        gracefulShutdownTimeout = Some(10.seconds)
      ) { (stopServer) => (backend, baseUri) =>
        (for {
          runningRequest <- basicRequest.get(uri"$baseUri").send(backend).start
          requestArrived <- IO.blocking(requestReceived.await(10, TimeUnit.SECONDS))
          runningStop <- stopServer.start
          result <- runningRequest.join.attempt
          _ <- runningStop.joinWithNever
        } yield {
          (requestArrived shouldBe true): Unit
          result.value.isSuccess shouldBe true
        })
      }
    }, {
      val requestReceived = new CountDownLatch(1)
      val releaseRequest = new CountDownLatch(1)
      testServerLogicWithStop(
        endpoint
          .out(plainBody[String])
          .serverLogic { _ =>
            m.eval(requestReceived.countDown())
              .flatMap(_ => m.blocking(releaseRequest.await(10, TimeUnit.SECONDS)))
              .flatMap(_ => pureResult("processing finished".asRight[Unit]))
          },
        "Server refuses new connections during shutdown",
        gracefulShutdownTimeout = Some(10.seconds)
      ) { (stopServer) => (backend, baseUri) =>
        val port = baseUri.port.get
        (for {
          refusedBeforeStop <- connectionRefused(port)
          runningRequest <- basicRequest.get(uri"$baseUri").send(backend).start
          requestArrived <- IO.blocking(requestReceived.await(10, TimeUnit.SECONDS))
          runningStop <- stopServer.start
          refusedDuringStop <- awaitConnectionsRefused(port).timeout(5.seconds).attempt
          _ <- IO(releaseRequest.countDown())
          result <- runningRequest.joinWithNever
          _ <- runningStop.joinWithNever
        } yield {
          (refusedBeforeStop shouldBe false): Unit
          (requestArrived shouldBe true): Unit
          (refusedDuringStop shouldBe Right(())): Unit
          result.body shouldBe Right("processing finished")
        })
      }
    },
    testServerLogicWithStop(
      endpoint.out(plainBody[String]).serverLogic(_ => pureResult("ok".asRight[Unit])),
      "Server closes idle keep-alive connections at once during shutdown",
      // longer than the socket's read timeout, so that the test fails if the connection is closed only when this passes
      gracefulShutdownTimeout = Some(30.seconds)
    ) { (stopServer) => (_, baseUri) =>
      // HTTP clients don't expose when the server closes a pooled connection
      rawSocket(baseUri.port.get).use { socket =>
        for {
          _ <- sendRaw(socket, "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
          response <- readRawResponse(socket)
          // in the background, as stopping the netty-sync server waits until the connection is closed
          runningStop <- stopServer.start
          afterStop <- IO.blocking(socket.getInputStream.read())
          _ <- runningStop.joinWithNever
        } yield {
          (response shouldBe ((200, "ok"))): Unit
          afterStop shouldBe -1
        }
      }
    }, {
      val requestReceived = new CountDownLatch(2) // both pipelined requests are handled before the shutdown starts
      val releaseRequests = new CountDownLatch(1)
      testServerLogicWithStop(
        endpoint
          .out(plainBody[String])
          .serverLogic { _ =>
            m.eval(requestReceived.countDown())
              .flatMap(_ => m.blocking(releaseRequests.await(10, TimeUnit.SECONDS)))
              .flatMap(_ => pureResult("processing finished".asRight[Unit]))
          },
        "Server completes in-flight pipelined requests, but doesn't handle new ones, during shutdown",
        // longer than the socket's read timeout, so that the test fails if the connection is closed only when this passes
        gracefulShutdownTimeout = Some(30.seconds)
      ) { (stopServer) => (_, baseUri) =>
        val port = baseUri.port.get
        def request = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
        // with a body, which isn't read
        def requestDuringShutdown =
          "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nhello".getBytes(StandardCharsets.US_ASCII)
        rawSocket(port).use { socket =>
          for {
            _ <- sendRaw(socket, request ++ request)
            requestArrived <- IO.blocking(requestReceived.await(10, TimeUnit.SECONDS))
            runningStop <- stopServer.start
            _ <- awaitConnectionsRefused(port).timeout(5.seconds)
            _ <- sendRaw(socket, requestDuringShutdown)
            _ <- IO(releaseRequests.countDown())
            response1 <- readRawResponseWithHeaders(socket)
            response2 <- readRawResponseWithHeaders(socket)
            afterResponses <- IO.blocking(socket.getInputStream.read())
            _ <- runningStop.joinWithNever
          } yield {
            (requestArrived shouldBe true): Unit
            (List(response1, response2).map { case (code, _, body) => (code, body) } shouldBe List.fill(2)(
              (200, "processing finished")
            )): Unit
            // only the last response closes the connection
            (List(response1, response2).map(_._2.contains("connection: close")) shouldBe List(false, true)): Unit
            afterResponses shouldBe -1 // the request sent during shutdown isn't answered, and the connection is closed
          }
        }
      }
    }, {
      // larger than the socket buffers, so that the response is still being written, as the client doesn't read it
      val bodySize = 32 * 1024 * 1024
      testServerLogicWithStop(
        endpoint
          .out(header(HeaderNames.ContentLength, bodySize.toString))
          .out(inputStreamBody)
          .serverLogic(_ => pureResult(new ByteArrayInputStream(new Array[Byte](bodySize)).asRight[Unit])),
        "Server completes a response which is being streamed during shutdown, before closing the connection",
        gracefulShutdownTimeout = Some(30.seconds)
      ) { (stopServer) => (_, baseUri) =>
        val port = baseUri.port.get
        rawSocket(port).use { socket =>
          for {
            _ <- sendRaw(socket, "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
            firstByte <- IO.blocking(socket.getInputStream.read()) // the response is being written
            runningStop <- stopServer.start
            _ <- awaitConnectionsRefused(port).timeout(5.seconds)
            rest <- IO.blocking(socket.getInputStream.readAllBytes()) // until the connection is closed
            _ <- runningStop.joinWithNever
          } yield {
            val bodyStart = new String(rest.take(1000), StandardCharsets.US_ASCII).indexOf("\r\n\r\n") + 4
            (firstByte shouldBe 'H'.toInt): Unit
            rest.length - bodyStart shouldBe bodySize
          }
        }
      }
    }, {
      val requestReceived = new CountDownLatch(1)
      val releaseRequest = new CountDownLatch(1)
      testServerLogicWithStop(
        endpoint
          .out(plainBody[String])
          .serverLogic { _ =>
            m.eval(requestReceived.countDown())
              .flatMap(_ => m.blocking(releaseRequest.await(10, TimeUnit.SECONDS)))
              .flatMap(_ => pureResult("processing finished".asRight[Unit]))
          },
        "Server stops once the client of an in-flight request disconnects during shutdown",
        gracefulShutdownTimeout = Some(30.seconds)
      ) { (stopServer) => (_, baseUri) =>
        val port = baseUri.port.get
        (for {
          runningStop <- rawSocket(port).use { socket =>
            for {
              _ <- sendRaw(socket, "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
              _ <- IO.blocking(requestReceived.await(10, TimeUnit.SECONDS))
              runningStop <- stopServer.start
              _ <- awaitConnectionsRefused(port).timeout(5.seconds)
            } yield runningStop
          }
          stopped <- runningStop.joinWithNever.timeout(10.seconds).attempt
        } yield stopped shouldBe Right(())).guarantee(IO(releaseRequest.countDown()))
      }
    }, {
      val requestsReceived = new CountDownLatch(2) // both pipelined requests are handled before the shutdown starts
      val releaseRequests = new CountDownLatch(1)
      testServerLogicWithStop(
        endpoint
          .in(query[Boolean]("fail"))
          .out(plainBody[String])
          .serverLogic { fail =>
            m.eval(requestsReceived.countDown())
              .flatMap(_ => m.blocking(releaseRequests.await(10, TimeUnit.SECONDS)))
              .flatMap(_ => if (fail) m.error[Either[Unit, String]](new RuntimeException("boom")) else pureResult("ok".asRight[Unit]))
          },
        "Server completes a failed pipelined request, and the next one, during shutdown",
        interceptors = _.exceptionHandler(None), // so that the error is handled by the server, instead of an interceptor
        gracefulShutdownTimeout = Some(30.seconds)
      ) { (stopServer) => (_, baseUri) =>
        val port = baseUri.port.get
        def request(fail: Boolean) = s"GET /?fail=$fail HTTP/1.1\r\nHost: localhost\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
        rawSocket(port).use { socket =>
          for {
            _ <- sendRaw(socket, request(fail = true) ++ request(fail = false))
            requestsArrived <- IO.blocking(requestsReceived.await(10, TimeUnit.SECONDS))
            runningStop <- stopServer.start
            _ <- awaitConnectionsRefused(port).timeout(5.seconds)
            _ <- IO(releaseRequests.countDown())
            response1 <- readRawResponseWithHeaders(socket)
            response2 <- readRawResponseWithHeaders(socket)
            afterResponses <- IO.blocking(socket.getInputStream.read())
            _ <- runningStop.joinWithNever
          } yield {
            (requestsArrived shouldBe true): Unit
            (List(response1, response2).map { case (code, _, body) => (code, body) } shouldBe List((500, ""), (200, "ok"))): Unit
            (List(response1, response2).map(_._2.contains("connection: close")) shouldBe List(false, true)): Unit
            afterResponses shouldBe -1
          }
        }
      }
    }
  )
}

object ServerGracefulShutdownTests {

  /** Netty servers start draining before they stop listening, so once connections are refused, the server is draining. */
  def awaitConnectionsRefused(port: Int): IO[Unit] =
    (IO.sleep(100.millis) >> connectionRefused(port)).iterateUntil(identity).void

  def connectionRefused(port: Int): IO[Boolean] =
    IO.blocking(new Socket("localhost", port).close()).attempt.map(_.left.exists(_.isInstanceOf[ConnectException]))
}
