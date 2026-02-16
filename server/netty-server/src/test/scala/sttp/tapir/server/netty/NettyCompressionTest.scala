package sttp.tapir.server.netty

import io.netty.channel.nio.NioEventLoopGroup
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.internal.FutureUtil.nettyFutureToScala

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.net.{HttpURLConnection, URL}
import java.util.zip.GZIPInputStream
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class NettyCompressionTest extends AnyFunSuite with Matchers with BeforeAndAfterAll {
  implicit val ec: ExecutionContext = ExecutionContext.global

  private val eventLoopGroup = new NioEventLoopGroup()
  private val largeText = "This is a large text that should benefit from compression. " * 1000

  override def afterAll(): Unit = {
    Await.result(nettyFutureToScala(eventLoopGroup.shutdownGracefully()): Future[_], 10.seconds)
    super.afterAll()
  }

  /** Makes an HTTP request and returns raw response bytes without auto-decompression */
  private def makeRawRequest(
      url: String,
      acceptEncoding: Option[String] = None
  ): (Int, Option[String], Array[Byte]) = {
    val connection = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
    try {
      connection.setRequestMethod("GET")
      acceptEncoding.foreach(enc => connection.setRequestProperty("Accept-Encoding", enc))

      val responseCode = connection.getResponseCode
      val contentEncoding = Option(connection.getHeaderField("Content-Encoding"))

      val inputStream = if (responseCode >= 400) connection.getErrorStream else connection.getInputStream
      val outputStream = new ByteArrayOutputStream()
      val buffer = new Array[Byte](4096)
      var bytesRead = inputStream.read(buffer)
      while (bytesRead != -1) {
        outputStream.write(buffer, 0, bytesRead)
        bytesRead = inputStream.read(buffer)
      }
      inputStream.close()

      (responseCode, contentEncoding, outputStream.toByteArray)
    } finally {
      connection.disconnect()
    }
  }

  private def decompressGzip(compressed: Array[Byte]): String = {
    val gis = new GZIPInputStream(new ByteArrayInputStream(compressed))
    val outputStream = new ByteArrayOutputStream()
    gis.transferTo(outputStream)
    outputStream.toString("UTF-8")
  }

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

  test("server should not compress responses when compression is disabled") {
    val testEndpoint = endpoint.get.in("test").out(stringBody)
    val serverEndpoint = testEndpoint.serverLogicSuccess[Future](_ => Future.successful(largeText))

    withServer(List(serverEndpoint), compressionEnabled = false) { port =>
      val (responseCode, contentEncoding, body) = makeRawRequest(
        s"http://localhost:$port/test",
        acceptEncoding = Some("gzip")
      )

      responseCode shouldBe 200
      contentEncoding shouldBe None
      // Response should NOT be compressed
      new String(body, "UTF-8") shouldBe largeText
    }
  }

  test("server should compress responses when compression is enabled and client accepts gzip") {
    val testEndpoint = endpoint.get.in("test").out(stringBody)
    val serverEndpoint = testEndpoint.serverLogicSuccess[Future](_ => Future.successful(largeText))

    withServer(List(serverEndpoint), compressionEnabled = true) { port =>
      val (responseCode, contentEncoding, compressedBody) = makeRawRequest(
        s"http://localhost:$port/test",
        acceptEncoding = Some("gzip")
      )

      responseCode shouldBe 200
      contentEncoding shouldBe Some("gzip")

      // Verify the response is actually compressed (at least 10 times smaller than original)
      val uncompressedSize = largeText.getBytes("UTF-8").length
      compressedBody.length shouldBe <(uncompressedSize / 10)

      // Verify we can decompress and get the original content
      val decompressed = decompressGzip(compressedBody)
      decompressed shouldBe largeText
    }
  }

  test("server should not compress when compression is enabled but client does not send Accept-Encoding header") {
    val testEndpoint = endpoint.get.in("test").out(stringBody)
    val serverEndpoint = testEndpoint.serverLogicSuccess[Future](_ => Future.successful(largeText))

    withServer(List(serverEndpoint), compressionEnabled = true) { port =>
      val (responseCode, contentEncoding, body) = makeRawRequest(
        s"http://localhost:$port/test",
        acceptEncoding = None
      )

      responseCode shouldBe 200
      contentEncoding shouldBe None
      // Response should NOT be compressed since client didn't request it
      new String(body, "UTF-8") shouldBe largeText
    }
  }
}
