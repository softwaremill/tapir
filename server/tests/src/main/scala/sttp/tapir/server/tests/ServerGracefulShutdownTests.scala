package sttp.tapir.server.tests

import cats.effect.{IO, Resource}
import cats.syntax.all._
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers._
import sttp.client4._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir._
import sttp.tapir.tests._

import java.io.InputStream
import java.net.{ConnectException, Socket}
import java.nio.charset.StandardCharsets
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.concurrent.duration._

class ServerGracefulShutdownTests[F[_], OPTIONS, ROUTE](createServerTest: CreateServerTest[F, Any, OPTIONS, ROUTE], sleeper: Sleeper[F])(
    implicit m: MonadError[F]
) extends EitherValues {
  import createServerTest._

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
          _ <- runningStop.join
        } yield {
          (requestArrived shouldBe true): Unit
          result.value.isSuccess shouldBe true
        })
      }
    }, {
      val requestReceived = new CountDownLatch(1)
      testServerLogicWithStop(
        endpoint
          .out(plainBody[String])
          .serverLogic { _ =>
            m.eval(requestReceived.countDown())
              .flatMap(_ => sleeper.sleep(4.seconds))
              .flatMap(_ => pureResult("processing finished".asRight[Unit]))
          },
        "Server refuses new connections during shutdown",
        gracefulShutdownTimeout = Some(10.seconds)
      ) { (stopServer) => (backend, baseUri) =>
        val connectionRefused = IO
          .blocking(new Socket(baseUri.host.get, baseUri.port.get).close())
          .attempt
          .map(_.left.exists(_.isInstanceOf[ConnectException]))
        (for {
          runningRequest <- basicRequest.get(uri"$baseUri").send(backend).start
          requestArrived <- IO.blocking(requestReceived.await(10, TimeUnit.SECONDS))
          runningStop <- stopServer.start
          // while the request is still in flight
          refused <- (IO.sleep(100.millis) >> connectionRefused).iterateUntil(identity).timeout(2.seconds).attempt
          firstResult <- runningRequest.join.attempt
          _ <- runningStop.join
        } yield {
          (requestArrived shouldBe true): Unit
          (refused shouldBe Right(true)): Unit
          firstResult.value.isSuccess shouldBe true
        })
      }
    },
    testServerLogicWithStop(
      endpoint.out(plainBody[String]).serverLogic(_ => pureResult("ok".asRight[Unit])),
      "Server closes idle keep-alive connections at once during shutdown",
      gracefulShutdownTimeout = Some(10.seconds)
    ) { (stopServer) => (_, baseUri) =>
      Resource.fromAutoCloseable(IO.blocking(new Socket(baseUri.host.get, baseUri.port.get))).use { socket =>
        for {
          response <- IO.blocking {
            socket.setSoTimeout(5000)
            socket.getOutputStream.write(s"GET / HTTP/1.1\r\nHost: ${baseUri.host.get}\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
            readResponse(socket.getInputStream)
          }
          stopStart <- IO.monotonic
          _ <- stopServer
          stopEnd <- IO.monotonic
          afterStop <- IO.blocking(socket.getInputStream.read())
        } yield {
          (response should startWith("HTTP/1.1 200")): Unit
          ((stopEnd - stopStart) should be < 5.seconds): Unit
          afterStop shouldBe -1 // the connection is closed by the server
        }
      }
    }
  )

  /** Reads a single response with a `Content-Length`, leaving the connection open. */
  private def readResponse(in: InputStream): String = {
    val headers = new StringBuilder
    while (!headers.endsWith("\r\n\r\n")) {
      val b = in.read()
      if (b == -1) throw new IllegalStateException(s"Connection closed before the response headers were read: $headers")
      headers.append(b.toChar)
    }
    val contentLength = headers.toString
      .split("\r\n")
      .collectFirst { case h if h.toLowerCase.startsWith("content-length:") => h.substring("content-length:".length).trim.toInt }
      .getOrElse(0)
    val body = new Array[Byte](contentLength)
    var read = 0
    while (read < contentLength) {
      val n = in.read(body, read, contentLength - read)
      if (n == -1) throw new IllegalStateException("Connection closed before the response body was read")
      read += n
    }
    headers.toString + new String(body, StandardCharsets.UTF_8)
  }
}
