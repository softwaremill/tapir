package sttp.tapir.server.netty

import sttp.tapir.*
import sttp.tapir.tests.Test

import scala.concurrent.Future
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.concurrent.duration.DurationInt
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.Metric
import sttp.tapir.server.metrics.EndpointMetric
import io.netty.channel.EventLoopGroup
import cats.effect.IO
import cats.effect.kernel.Resource

import scala.concurrent.ExecutionContext
import sttp.client4.*
import sttp.capabilities.fs2.Fs2Streams
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.matchers.should.Matchers.*
import cats.effect.unsafe.implicits.global
import sttp.model.StatusCode

import java.net.Socket

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
    Test("respond with status 400 when not all declared bytes are received within time window") {

      val bodiesSeen = new AtomicReference[Vector[String]](Vector.empty[String])

      val e = endpoint.put
        .in(stringBody)
        .out(stringBody)
        .serverLogicSuccess[Future] { body =>
          bodiesSeen.getAndUpdate(_ :+ body)
          Future.successful(body)
        }

      val config: NettyConfig = NettyConfig.default.randomPort.requestTimeout(100.millis)

      val bind = IO.fromFuture(IO.delay(NettyFutureServer(config).addEndpoints(List(e)).start()))

      val createSocket: Int => Socket = port => {
        val s = new Socket("localhost", port)
        s.setSoTimeout(200)
        s
      }

      Resource
        .make(bind)(server => IO.fromFuture(IO.delay(server.stop())))
        .use { server =>
          val bytes =
            s"PUT / HTTP/1.1\r\nHost: localhost:${server.port}\r\nContent-Type: text/plain\r\nContent-Length: 10000\r\n\r\ntest".getBytes

          Resource
            .make(IO(createSocket(server.port)))(socket => IO(socket.close()))
            .use { socket =>
              for {
                _ <- IO(socket.getOutputStream.write(bytes))
                _ <- IO(socket.getOutputStream.flush())
                _ <- IO(socket.getOutputStream.close())
                _ <- IO.sleep(300.millis)
              } yield {
                bodiesSeen.get() should be(Vector.empty[String])
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
    Test("closing the connection mid-body doesn't pass a truncated body to the server logic") {
      val bodiesSeen = new AtomicReference(Vector.empty[String])

      val e = endpoint.put
        .in(stringBody)
        .out(stringBody)
        .serverLogicSuccess[Future] { body =>
          bodiesSeen.getAndUpdate(_ :+ body)
          Future.successful(body)
        }

      val serverConfig = NettyConfig.default
        .eventLoopGroup(eventLoopGroup)
        .randomPort
        .withDontShutdownEventLoopGroupOnClose
        .noGracefulShutdown
        .requestTimeout(1.second)

      val bind = IO.fromFuture(IO.delay(NettyFutureServer(serverConfig).addEndpoints(List(e)).start()))
      Resource
        .make(bind)(server => IO.fromFuture(IO.delay(server.stop())))
        .map(_.port)
        .use { port =>
          Resource
            .fromAutoCloseable(IO(clientSocket(port)))
            .use { socket =>
              for {
                _ <- send(socket, incompleteRequestHead(port) ++ slowBody)
                _ <- IO.blocking(socket.shutdownOutput())
                _ <- IO.sleep(500.millis)
              } yield bodiesSeen.get() shouldBe Vector.empty
            }
        }
        .unsafeToFuture()
    }
  )
}
