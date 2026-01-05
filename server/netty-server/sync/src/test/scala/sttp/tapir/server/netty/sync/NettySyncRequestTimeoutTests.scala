package sttp.tapir.server.netty

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.netty.channel.EventLoopGroup
import org.scalatest.matchers.should.Matchers.*
import ox.*
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.*
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.{EndpointMetric, Metric}
import sttp.tapir.server.netty.sync.{NettySyncServer, NettySyncServerOptions}
import sttp.tapir.tests.Test
import sttp.shared.Identity

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import org.slf4j.LoggerFactory

class NettySyncRequestTimeoutTests(eventLoopGroup: EventLoopGroup, backend: WebSocketStreamBackend[IO, Fs2Streams[IO]]):
  val logger = LoggerFactory.getLogger(getClass.getName)

  def tests(): List[Test] = List(
    Test("properly update metrics when a request times out") {
      val e = endpoint.post
        .in(stringBody)
        .out(stringBody)
        .serverLogicSuccess[Identity]: body =>
          Thread.sleep(2000)
          body

      val activeRequests = new AtomicInteger()
      val totalRequests = new AtomicInteger()
      val customMetrics: List[Metric[Identity, AtomicInteger]] = List(
        Metric(
          metric = activeRequests,
          onRequest = (_, metric, me) =>
            me.eval:
              EndpointMetric()
                .onEndpointRequest: _ =>
                  val _ = metric.incrementAndGet();
                  (): Identity[Unit]
                .onResponseBody: (_, _) =>
                  val _ = metric.decrementAndGet();
                .onException: (_, _) =>
                  val _ = metric.decrementAndGet();
        ),
        Metric(
          metric = totalRequests,
          onRequest = (_, metric, me) =>
            me.eval(EndpointMetric().onEndpointRequest: _ =>
              val _ = metric.incrementAndGet();)
        )
      )

      val config =
        NettyConfig.default
          .eventLoopGroup(eventLoopGroup)
          .randomPort
          .withDontShutdownEventLoopGroupOnClose
          .noGracefulShutdown
          .requestTimeout(1.second)
      val options = NettySyncServerOptions.customiseInterceptors
        .metricsInterceptor(new MetricsRequestInterceptor(customMetrics, Seq.empty))
        .options

      Future.successful:
        supervised:
          val port = useInScope(NettySyncServer(options, config).addEndpoint(e).start())(_.stop()).port
          basicRequest
            .post(uri"http://localhost:$port")
            .body("test")
            .send(backend)
            .map: response =>
              response.body should matchPattern { case Left(_) => }
              response.code shouldBe StatusCode.ServiceUnavailable
              // unlike in NettyFutureRequestTimeoutTest, here interruption works properly, and the metrics should be updated quickly
              Thread.sleep(100)
              activeRequests.get() shouldBe 0
              totalRequests.get() shouldBe 1
            .unsafeRunSync()
    }
  )
