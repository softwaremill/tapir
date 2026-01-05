// {cat=Server features; effects=Future; server=Netty}: HTTP response compression with Netty server

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.4
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server:1.11.4
//> using dep com.softwaremill.sttp.client4::core:4.0.0-M22

package sttp.tapir.examples

import sttp.client4.{SyncBackend, UriContext, basicRequest}
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.tapir.server.netty.{NettyCompressionConfig, NettyConfig, NettyFutureServer, NettyFutureServerBinding}
import sttp.tapir.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

@main def compressionNettyServer(): Unit =
  // Endpoint that returns a large text response that will benefit from compression
  val largeTextEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.get.in("large-text").out(stringBody)

  val largeText = "This is a large text response that will be compressed by the server. " * 100

  val largeTextServerEndpoint = largeTextEndpoint
    .serverLogic(_ => Future.successful[Either[Unit, String]](Right(largeText)))

  val declaredPort = 9091
  val declaredHost = "localhost"

  // Configure compression - simply enable it to use Netty's default settings
  // When enabled, the server will automatically compress responses based on
  // the client's Accept-Encoding header (gzip/deflate)
  val compressionConfig = NettyCompressionConfig.enabled

  val nettyConfig = NettyConfig.default
    .port(declaredPort)
    .host(declaredHost)
    .compressionConfig(compressionConfig)

  // Alternative: use convenience method
  // val nettyConfig = NettyConfig.default
  //   .port(declaredPort)
  //   .host(declaredHost)
  //   .withCompressionEnabled

  // Starting netty server with compression enabled
  val serverBinding: NettyFutureServerBinding =
    Await.result(
      NettyFutureServer(nettyConfig)
        .addEndpoint(largeTextServerEndpoint)
        .start(),
      Duration.Inf
    )

  val port = serverBinding.port
  val host = serverBinding.hostName
  println(s"Server started at http://$host:$port with compression enabled")
  println(s"Compression config: enabled=${nettyConfig.compressionConfig.enabled}")

  val backend: SyncBackend = HttpClientSyncBackend()

  // Test: Request the large text endpoint
  // Note: HttpURLConnectionBackend automatically handles gzip decompression,
  // so the response body will be the original uncompressed text
  println("\n=== Testing compression ===")
  val response = basicRequest
    .get(uri"http://$host:$port/large-text")
    .send(backend)

  println(s"Response status: ${response.code}")
  println(s"Response body length: ${response.body.map(_.length).getOrElse(0)} characters")
  println(s"Expected body length: ${largeText.length} characters")

  response.body match {
    case Right(body) =>
      assert(body == largeText, "Response body should match the expected text")
      println("✓ Response body matches expected text")
    case Left(error) =>
      println(s"✗ Error: $error")
  }

  println("\n=== Compression example completed successfully! ===")
  println("""
    |When compression is enabled:
    |- Server adds HttpContentCompressor to the Netty pipeline
    |- Responses are compressed based on client's Accept-Encoding header
    |- Supported encodings: gzip, deflate
    |- Clients that accept gzip/deflate will receive compressed responses
    |""".stripMargin)

  Await.result(serverBinding.stop(), Duration.Inf)
