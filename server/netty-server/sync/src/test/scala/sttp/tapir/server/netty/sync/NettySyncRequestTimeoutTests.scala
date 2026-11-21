package sttp.tapir.server.netty

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.netty.channel.EventLoopGroup
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.matchers.should.Matchers.*
import ox.*
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.*
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.{EndpointMetric, Metric}
import sttp.tapir.server.netty.sync.{NettySyncServer, NettySyncServerOptions, OxStreams}
import sttp.tapir.server.tests.{rawSocket, sendRaw}
import sttp.tapir.tests.Test
import sttp.shared.Identity

import ox.flow.Flow

import java.nio.charset.StandardCharsets
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import org.slf4j.LoggerFactory

class NettySyncRequestTimeoutTests(eventLoopGroup: EventLoopGroup, backend: WebSocketStreamBackend[IO, Fs2Streams[IO]]):
  val logger = LoggerFactory.getLogger(getClass.getName)

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
              // unlike in NettyFutureRequestTimeoutTest, here interruption works properly, and the metrics should be updated
              // quickly; however, on a loaded CI machine this might still take some time
              eventually {
                activeRequests.get() shouldBe 0
                totalRequests.get() shouldBe 1
              }
            .unsafeRunSync()
    }, {
      val releaseStream = new CountDownLatch(1)
      val stream = endpoint.get
        .in("stream")
        .out(streamBinaryBody(OxStreams)(CodecFormat.OctetStream()))
        .handleSuccess: _ =>
          Flow.usingEmit: emit =>
            emit(Chunk.fromArray("a".getBytes(StandardCharsets.US_ASCII)))
            releaseStream.await(10, TimeUnit.SECONDS).discard
      val slow = endpoint.get.in("slow").out(stringBody).handleSuccess: _ =>
        Thread.sleep(5000)
        "slow"
      val config =
        NettyConfig.default.eventLoopGroup(eventLoopGroup).randomPort.withDontShutdownEventLoopGroupOnClose.noGracefulShutdown
          .requestTimeout(1.second)
      def request(path: String) = s"GET /$path HTTP/1.1\r\nHost: localhost\r\n\r\n"

      Test("close the connection when a request times out while the response to an earlier one is being written") {
        Future.successful:
          supervised:
            val port = useInScope(NettySyncServer(config).addEndpoints(List(stream, slow)).start())(_.stop()).port
            try
              rawSocket(port)
                .use: socket =>
                  sendRaw(socket, (request("stream") + request("slow")).getBytes(StandardCharsets.US_ASCII)) >>
                    IO.blocking(socket.getInputStream.readAllBytes()) // until the connection is closed
                .map: received =>
                  val response = new String(received, StandardCharsets.US_ASCII)
                  (response should startWith("HTTP/1.1 200")): Unit
                  (response should include("\r\n\r\n1\r\na\r\n")): Unit // the headers, then the first chunk
                  // a 503 would be written only after the streamed response
                  response should not include "503"
                .unsafeRunSync()
            finally releaseStream.countDown()
      }
    }
  )
