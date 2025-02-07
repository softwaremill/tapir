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
import sttp.client3._
import sttp.capabilities.fs2.Fs2Streams
import sttp.capabilities.WebSockets
import org.scalatest.matchers.should.Matchers._
import cats.effect.unsafe.implicits.global
import sttp.model.StatusCode

class NettyFutureRequestTimeoutTests(eventLoopGroup: EventLoopGroup, backend: SttpBackend[IO, Fs2Streams[IO] with WebSockets])(implicit
    ec: ExecutionContext
) {
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
            // the metrics will only be updated when the endpoint's logic completes, which is 1 second after receiving the timeout response
            Thread.sleep(1100)
            activeRequests.get() shouldBe 0
            totalRequests.get() shouldBe 1
          }
        }
        .unsafeToFuture()
    }
  )
}
