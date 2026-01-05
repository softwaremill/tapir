package sttp.tapir.server.netty

import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.client4._
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.internal.FutureUtil.nettyFutureToScala

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class NettyCompressionTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {
  implicit val ec: ExecutionContext = ExecutionContext.global

  private val eventLoopGroup = new NioEventLoopGroup()
  private val backend: SyncBackend = HttpClientSyncBackend()

  override def afterAll(): Unit = {
    backend.close()
    Await.result(nettyFutureToScala(eventLoopGroup.shutdownGracefully()): Future[_], 10.seconds)
    super.afterAll()
  }

  /** Creates a test server with optional compression configuration and runs a test */
  private def withServer[A](
      endpoints: List[ServerEndpoint[Any, Future]],
      compressionEnabled: Boolean
  )(test: Int => A): A = {
    val compressionConfig =
      if (compressionEnabled) NettyCompressionConfig.enabled
      else NettyCompressionConfig.default

    val config = NettyConfig.default
      .eventLoopGroup(eventLoopGroup)
      .randomPort
      .withDontShutdownEventLoopGroupOnClose
      .noGracefulShutdown
      .compressionConfig(compressionConfig)

    val serverOptions = NettyFutureServerOptions.default
    val routes = NettyFutureServerInterpreter(serverOptions).toRoute(endpoints)

    val binding = Await.result(
      NettyFutureServer(serverOptions, config).addRoutes(List(routes)).start(),
      10.seconds
    )

    try {
      test(binding.port)
    } finally {
      Await.result(binding.stop(), 10.seconds)
    }
  }

  test("compression should be disabled by default in config") {
    NettyConfig.default.compressionConfig.enabled shouldBe false
  }

  test("withCompressionEnabled should enable compression") {
    val config = NettyConfig.default.withCompressionEnabled
    config.compressionConfig.enabled shouldBe true
  }

  test("compressionConfig method should set compression config") {
    val config = NettyConfig.default.compressionConfig(NettyCompressionConfig.enabled)
    config.compressionConfig.enabled shouldBe true
  }

  test("predefined compression configs should have correct values") {
    NettyCompressionConfig.default.enabled shouldBe false
    NettyCompressionConfig.enabled.enabled shouldBe true
  }

  test("server should respond normally when compression is disabled") {
    val testEndpoint = endpoint.get.in("test").out(stringBody)
    val serverEndpoint = testEndpoint.serverLogicSuccess[Future](_ => Future.successful("Hello, World!"))

    withServer(List(serverEndpoint), compressionEnabled = false) { port =>
      val response = basicRequest
        .get(uri"http://localhost:$port/test")
        .send(backend)

      response.code shouldBe StatusCode.Ok
      response.body.toOption.get shouldBe "Hello, World!"
    }
  }

  test("server should respond normally when compression is enabled") {
    val testEndpoint = endpoint.get.in("test").out(stringBody)
    val serverEndpoint = testEndpoint.serverLogicSuccess[Future](_ => Future.successful("Hello, World!"))

    withServer(List(serverEndpoint), compressionEnabled = true) { port =>
      val response = basicRequest
        .get(uri"http://localhost:$port/test")
        .send(backend)

      response.code shouldBe StatusCode.Ok
      response.body.toOption.get shouldBe "Hello, World!"
    }
  }

  test("server should handle large responses when compression is enabled") {
    val largeText = "This is a large text that should benefit from compression. " * 1000
    val testEndpoint = endpoint.get.in("large").out(stringBody)
    val serverEndpoint = testEndpoint.serverLogicSuccess[Future](_ => Future.successful(largeText))

    withServer(List(serverEndpoint), compressionEnabled = true) { port =>
      val response = basicRequest
        .get(uri"http://localhost:$port/large")
        .send(backend)

      response.code shouldBe StatusCode.Ok
      response.body.toOption.get shouldBe largeText
    }
  }

  test("compression config should be properly passed through NettyConfig") {
    val config1 = NettyConfig.default
    config1.compressionConfig.enabled shouldBe false

    val config2 = config1.withCompressionEnabled
    config2.compressionConfig.enabled shouldBe true
    // Original should be unchanged (immutability)
    config1.compressionConfig.enabled shouldBe false

    val config3 = NettyConfig.default.compressionConfig(NettyCompressionConfig.enabled)
    config3.compressionConfig.enabled shouldBe true
  }
}
