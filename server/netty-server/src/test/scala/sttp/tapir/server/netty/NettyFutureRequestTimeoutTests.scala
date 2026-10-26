package sttp.tapir.server.netty

import sttp.tapir._
import sttp.tapir.tests.Test
import scala.concurrent.Future
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.DurationInt
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.Metric
import sttp.tapir.server.metrics.EndpointMetric
import io.netty.channel.EventLoopGroup
import cats.effect.IO
import cats.effect.kernel.Resource
import scala.concurrent.ExecutionContext
import sttp.client4._
import sttp.capabilities.fs2.Fs2Streams
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.matchers.should.Matchers._
import cats.effect.unsafe.implicits.global
import sttp.model.StatusCode
import sttp.tapir.server.tests.{rawSocket, readRawResponse, sendRaw}

import java.nio.charset.StandardCharsets

class NettyFutureRequestTimeoutTests(eventLoopGroup: EventLoopGroup, backend: WebSocketStreamBackend[IO, Fs2Streams[IO]])(implicit
    ec: ExecutionContext
) {
  // increase the patience for `eventually` for slow CI tests
  implicit val patienceConfig: Eventually.PatienceConfig = Eventually.PatienceConfig(
    timeout = org.scalatest.time.Span(15, org.scalatest.time.Seconds),
    interval = org.scalatest.time.Span(150, org.scalatest.time.Millis)
  )

  private val timingOutRequest = new TimingOutRequestSpecData(eventLoopGroup)
  import timingOutRequest._

  def tests(): List[Test] = List(
    Test("properly update metrics when a request times out") {
      // the logic completes only once the timeout response has been received, so a stalled JVM can't let it finish first
      val timeoutResponseReceived = new CountDownLatch(1)
      val e = endpoint.post
        .in(stringBody)
        .out(stringBody)
        .serverLogicSuccess[Future] { body =>
          awaitLatch(timeoutResponseReceived); Future.successful(body)
        }

      val activeRequests = new AtomicInteger()
      val totalRequests = new AtomicInteger()
      val customMetrics: List[Metric[Future, AtomicInteger]] = List(
        Metric(
          metric = activeRequests,
          onRequest = (_, metric, me) =>
            me.eval {
              EndpointMetric()
                .onEndpointRequest { _ => me.eval { val _ = metric.incrementAndGet(); } }
                .onResponseBody { (_, _) => me.eval { val _ = metric.decrementAndGet(); } }
                .onException { (_, _) => me.eval { val _ = metric.decrementAndGet(); } }
            }
        ),
        Metric(
          metric = totalRequests,
          onRequest = (_, metric, me) => me.eval(EndpointMetric().onEndpointRequest { _ => me.eval { val _ = metric.incrementAndGet(); } })
        )
      )

      val config =
        NettyConfig.default
          .eventLoopGroup(eventLoopGroup)
          .randomPort
          .withDontShutdownEventLoopGroupOnClose
          .noGracefulShutdown
          .requestTimeout(1.second)
      val options = NettyFutureServerOptions.customiseInterceptors
        .metricsInterceptor(new MetricsRequestInterceptor[Future](customMetrics, Seq.empty))
        .options
      val bind = IO.fromFuture(IO.delay(NettyFutureServer(options, config).addEndpoints(List(e)).start()))

      Resource
        .make(bind)(server => IO.fromFuture(IO.delay(server.stop())))
        .map(_.port)
        .use { port =>
          basicRequest
            .post(uri"http://localhost:$port")
            .body("test")
            .send(backend)
            .map { response =>
              response.body should matchPattern { case Left(_) => }
              response.code shouldBe StatusCode.ServiceUnavailable
            }
            .guarantee(IO(timeoutResponseReceived.countDown()))
            .map { _ =>
              // the metrics are only updated when the endpoint's logic completes, which happens asynchronously after the latch is released
              eventually {
                activeRequests.get() shouldBe 0
                totalRequests.get() shouldBe 1
              }
            }
        }
        .unsafeToFuture()
    },
    Test("respond with status 408 when not all declared request body bytes are received") {
      statusLinesFromShortTimeoutServer { (socket, port) =>
        for {
          _ <- send(socket, incompleteRequestHead(port))
          status <- readStatusLine(socket)
        } yield List(status)
      }.map { statusLines =>
        statusLines shouldBe List("HTTP/1.1 408 Request Timeout")
      }.unsafeToFuture()
    },
    Test("respond with status 408 for an incomplete request following a complete one on the same connection") {
      statusLinesFromShortTimeoutServer { (socket, port) =>
        for {
          _ <- send(socket, requestHead(port, completeBody.length) ++ completeBody)
          first <- readStatusLine(socket)
          _ <- send(socket, incompleteRequestHead(port))
          second <- readStatusLine(socket)
        } yield List(first, second)
      }.map { statusLines =>
        statusLines shouldBe List("HTTP/1.1 200 OK", "HTTP/1.1 408 Request Timeout")
      }.unsafeToFuture()
    },
    Test("respond with status 503, not 408, for a slow but complete request following a complete fast one on the same connection") {
      statusLinesFromShortTimeoutServer { (socket, port) =>
        for {
          _ <- send(socket, requestHead(port, completeBody.length) ++ completeBody)
          first <- readStatusLine(socket)
          _ <- send(socket, requestHead(port, slowBody.length) ++ slowBody)
          second <- readStatusLine(socket)
        } yield List(first, second)
      }.map { statusLines =>
        statusLines shouldBe List("HTTP/1.1 200 OK", "HTTP/1.1 503 Service Unavailable")
      }.unsafeToFuture()
    },
    // Netty-only, as the request timeout is: a missing body is the client's fault (408) only once it was sent a 100
    Test("respond with status 408 when the body isn't sent after 100 Continue") {
      statusLinesFromShortTimeoutServer { (socket, port) =>
        val head = s"PUT / HTTP/1.1\r\nHost: localhost:$port\r\nContent-Length: 4\r\nExpect: 100-continue\r\n\r\n"
        for {
          _ <- send(socket, head.getBytes(StandardCharsets.UTF_8))
          continue <- readStatusLine(socket)
          status <- readStatusLine(socket)
        } yield List(continue, status)
      }.map { statusLines =>
        statusLines shouldBe List("HTTP/1.1 100 Continue", "HTTP/1.1 408 Request Timeout")
      }.unsafeToFuture()
    },
    Test("respond with status 503, not 408, when the server is slow to read the body of a request awaiting 100 Continue") {
      val timeoutResponseReceived = new CountDownLatch(1)
      // the security logic runs before the body is read, and hence before 100 Continue is sent
      val e = endpoint.put
        .serverSecurityLogicSuccess[Unit, Future](_ => Future(awaitLatch(timeoutResponseReceived)))
        .in(stringBody)
        .out(stringBody)
        .serverLogicSuccess(_ => body => Future.successful(body))

      val config = NettyConfig.default
        .eventLoopGroup(eventLoopGroup)
        .randomPort
        .withDontShutdownEventLoopGroupOnClose
        .noGracefulShutdown
        .requestTimeout(1.second)
      val bind = IO.fromFuture(IO.delay(NettyFutureServer(config).addEndpoints(List(e)).start()))

      Resource
        .make(bind)(server => IO.fromFuture(IO.delay(server.stop())))
        .map(_.port)
        .use { port =>
          val requestHead = s"PUT / HTTP/1.1\r\nHost: localhost:$port\r\nContent-Length: 4\r\nExpect: 100-continue\r\n\r\n"
          rawSocket(port)
            .use(socket => sendRaw(socket, requestHead.getBytes(StandardCharsets.UTF_8)) >> readRawResponse(socket))
            .guarantee(IO(timeoutResponseReceived.countDown()))
        }
        .map { case (status, _) => status shouldBe StatusCode.ServiceUnavailable.code }
        .unsafeToFuture()
    }
  )
}
