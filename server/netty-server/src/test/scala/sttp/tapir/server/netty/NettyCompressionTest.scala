package sttp.tapir.server.netty

import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir._
import sttp.tapir.server.netty.internal.FutureUtil.nettyFutureToScala

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class NettyCompressionTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {
  implicit val ec: ExecutionContext = ExecutionContext.global

  private val eventLoopGroup = new NioEventLoopGroup()
  private val backend = HttpURLConnectionBackend()

  override def afterAll(): Unit = {
    backend.close()
    Await.result(nettyFutureToScala(eventLoopGroup.shutdownGracefully()): Future[_], 5.seconds)
    super.afterAll()
  }

  // Helper to decompress gzip data
  private def decompressGzip(compressed: Array[Byte]): Array[Byte] = {
    val inputStream = new GZIPInputStream(new ByteArrayInputStream(compressed))
    try {
      inputStream.readAllBytes()
    } finally {
      inputStream.close()
    }
  }

  test("compression should be disabled by default") {
    val testEndpoint = endpoint.get.in("test").out(stringBody)
    val serverEndpoint = testEndpoint.serverLogicSuccess[Future](_ => Future.successful("Hello, World!"))

    val config = NettyConfig.default.randomPort
    val serverBinding = Await.result(
      NettyFutureServer(config)
        .addEndpoint(serverEndpoint)
        .start(),
      5.seconds
    )

    try {
      val response = basicRequest
        .header(HeaderNames.AcceptEncoding, "gzip")
        .get(uri"http://localhost:${serverBinding.port}/test")
        .send(backend)

      response.code shouldBe StatusCode.Ok
      response.header(HeaderNames.ContentEncoding) shouldBe None
      response.body.getOrElse("") shouldBe "Hello, World!"
    } finally {
      Await.result(serverBinding.stop(), 5.seconds)
    }
  }

  test("compression should work when enabled") {
    val largeText = "This is a large text that should be compressed. " * 100
    val testEndpoint = endpoint.get.in("large").out(stringBody)
    val serverEndpoint = testEndpoint.serverLogicSuccess[Future](_ => Future.successful(largeText))

    val compressionConfig = NettyCompressionConfig.default.withEnabled(true)
    val config = NettyConfig.default.randomPort.compressionConfig(compressionConfig)

    val serverBinding = Await.result(
      NettyFutureServer(config)
        .addEndpoint(serverEndpoint)
        .start(),
      5.seconds
    )

    try {
      val response = basicRequest
        .header(HeaderNames.AcceptEncoding, "gzip")
        .response(asByteArray)
        .get(uri"http://localhost:${serverBinding.port}/large")
        .send(backend)

      response.code shouldBe StatusCode.Ok
      response.header(HeaderNames.ContentEncoding) shouldBe Some("gzip")

      val compressedBody = response.body.getOrElse(Array.empty)
      val decompressedBody = new String(decompressGzip(compressedBody), "UTF-8")
      decompressedBody shouldBe largeText

      // Verify compression actually reduced size
      compressedBody.length should be < largeText.getBytes("UTF-8").length
    } finally {
      Await.result(serverBinding.stop(), 5.seconds)
    }
  }


  test("compression should not be applied when client doesn't support it") {
    val text = "Hello, World!" * 100
    val testEndpoint = endpoint.get.in("test").out(stringBody)
    val serverEndpoint = testEndpoint.serverLogicSuccess[Future](_ => Future.successful(text))

    val compressionConfig = NettyCompressionConfig.default.withEnabled(true)
    val config = NettyConfig.default.randomPort.compressionConfig(compressionConfig)

    val serverBinding = Await.result(
      NettyFutureServer(config)
        .addEndpoint(serverEndpoint)
        .start(),
      5.seconds
    )

    try {
      // Request without Accept-Encoding header
      val response = basicRequest
        .get(uri"http://localhost:${serverBinding.port}/test")
        .send(backend)

      response.code shouldBe StatusCode.Ok
      response.header(HeaderNames.ContentEncoding) shouldBe None
      response.body.getOrElse("") shouldBe text
    } finally {
      Await.result(serverBinding.stop(), 5.seconds)
    }
  }


  test("convenience methods should work correctly") {
    // Test withCompressionEnabled
    val config1 = NettyConfig.default.randomPort.withCompressionEnabled
    config1.compressionConfig.enabled shouldBe true
  }

  test("predefined compression configs should work") {
    NettyCompressionConfig.enabled.enabled shouldBe true
    NettyCompressionConfig.default.enabled shouldBe false
  }
}

