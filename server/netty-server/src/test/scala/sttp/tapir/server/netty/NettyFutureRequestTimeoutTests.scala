package sttp.tapir.server.netty

import sttp.tapir._
import sttp.tapir.tests.Test
import scala.concurrent.Future
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
import java.net.Socket
import scala.concurrent.duration.FiniteDuration

class NettyFutureRequestTimeoutTests(eventLoopGroup: EventLoopGroup, backend: WebSocketStreamBackend[IO, Fs2Streams[IO]])(implicit
    ec: ExecutionContext
) {
  // increase the patience for `eventually` for slow CI tests
  implicit val patienceConfig: Eventually.PatienceConfig = Eventually.PatienceConfig(
    timeout = org.scalatest.time.Span(15, org.scalatest.time.Seconds),
    interval = org.scalatest.time.Span(150, org.scalatest.time.Millis)
  )

  def tests(): List[Test] = List(
    Test("properly update metrics when a request times out") {
      val e = endpoint.post
        .in(stringBody)
        .out(stringBody)
        .serverLogicSuccess[Future] { body =>
          Thread.sleep(2000); Future.successful(body)
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
          basicRequest.post(uri"http://localhost:$port").body("test").send(backend).map { response =>
            response.body should matchPattern { case Left(_) => }
            response.code shouldBe StatusCode.ServiceUnavailable
            // the metrics will only be updated when the endpoint's logic completes, which is ~1 second
            // after receiving the timeout response (and possibly later on a loaded CI machine)
            eventually {
              activeRequests.get() shouldBe 0
              totalRequests.get() shouldBe 1
            }
          }
        }
        .unsafeToFuture()
    },
    Test("respond with status 408 when not all declared body bytes are received, in a single write") {
      val requestTimeout = 500.millis

      def requestHead(port: Int): Array[Byte] =
        s"PUT / HTTP/1.1\r\nHost: localhost:$port\r\nContent-Type: text/plain\r\nContent-Length: 10000\r\n\r\n".getBytes

      val bodyFragment: Array[Byte] = "test".getBytes

      val e = endpoint.put
        .in(stringBody)
        .out(stringBody)
        .serverLogicSuccess[Future](body => Future.successful(body))

      val serverConfig = NettyConfig.default
        .eventLoopGroup(eventLoopGroup)
        .randomPort
        .withDontShutdownEventLoopGroupOnClose
        .noGracefulShutdown
        .requestTimeout(requestTimeout)

      val bind = IO.fromFuture(IO.delay(NettyFutureServer(serverConfig).addEndpoints(List(e)).start()))

      Resource
        .make(bind)(server => IO.fromFuture(IO.delay(server.stop())))
        .map(_.port)
        .use { port =>
          Resource.fromAutoCloseable(IO(clientSocket(port, requestTimeout))).use { socket =>
            (for {
              _ <- IO.blocking(socket.getOutputStream.write(requestHead(port) ++ bodyFragment))
              _ <- IO.blocking(socket.getOutputStream.flush())
              resp <- IO.blocking(new String(socket.getInputStream.readAllBytes()))
            } yield resp)
              .map { response =>
                // asserting on the status line, rather than just containment, also covers a second response written behind the first
                response should startWith("HTTP/1.1 408 Request Timeout")
                response should not include ("503")
              }
          }
        }
        .unsafeToFuture()
    },
    Test(
      "respond with status 408 when not all declared body bytes are received, with the body fragment in a separate write, then a stall"
    ) {
      val requestTimeout = 500.millis
      val dribbleInterval = requestTimeout / 3

      def requestHead(port: Int): Array[Byte] =
        s"PUT / HTTP/1.1\r\nHost: localhost:$port\r\nContent-Type: text/plain\r\nContent-Length: 10000\r\n\r\n".getBytes

      val bodyFragment: Array[Byte] = "test".getBytes

      val e = endpoint.put
        .in(stringBody)
        .out(stringBody)
        .serverLogicSuccess[Future](body => Future.successful(body))

      val serverConfig = NettyConfig.default
        .eventLoopGroup(eventLoopGroup)
        .randomPort
        .withDontShutdownEventLoopGroupOnClose
        .noGracefulShutdown
        .requestTimeout(requestTimeout)

      val bind = IO.fromFuture(IO.delay(NettyFutureServer(serverConfig).addEndpoints(List(e)).start()))

      Resource
        .make(bind)(server => IO.fromFuture(IO.delay(server.stop())))
        .map(_.port)
        .use { port =>
          Resource.fromAutoCloseable(IO(clientSocket(port, requestTimeout))).use { socket =>
            (for {
              _ <- IO.blocking(socket.getOutputStream.write(requestHead(port)))
              _ <- IO.blocking(socket.getOutputStream.flush())
              _ <- IO.blocking(socket.getOutputStream.write(bodyFragment))
              _ <- IO.blocking(socket.getOutputStream.flush())
              _ <- IO.sleep(dribbleInterval)
              response <- IO.blocking(new String(socket.getInputStream.readAllBytes()))
            } yield response).map { response =>
              response should startWith("HTTP/1.1 408 Request Timeout")
              response should not include ("503")
            }
          }
        }
        .unsafeToFuture()
    }
  )

  private def clientSocket(port: Int, requestTimeout: FiniteDuration): Socket = {
    val socket = new Socket("localhost", port)
    socket.setSoTimeout((requestTimeout * 20).toMillis.toInt)
    socket
  }
}
