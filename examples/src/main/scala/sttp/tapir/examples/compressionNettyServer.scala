// {cat=Server features; effects=Future; server=Netty}: HTTP response compression with Netty server

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.4
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server:1.11.4
//> using dep com.softwaremill.sttp.client3::core:3.9.7

package sttp.tapir.examples

import sttp.client3.{HttpURLConnectionBackend, SttpBackend, UriContext, asByteArray, basicRequest}
import sttp.model.{Header, HeaderNames}
import sttp.shared.Identity
import sttp.tapir.server.netty.{NettyCompressionConfig, NettyConfig, NettyFutureServer, NettyFutureServerBinding}
import sttp.tapir.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

@main def compressionNettyServer(): Unit =
  // Endpoint that returns a large text response that will benefit from compression
  val largeTextEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.get.in("large-text").out(stringBody)

  val largeText = "This is a large text response that will be compressed. " * 100

  val largeTextServerEndpoint = largeTextEndpoint
    .serverLogic(_ => Future.successful[Either[Unit, String]](Right(largeText)))

  // Small endpoint that might not benefit from compression
  val smallTextEndpoint: PublicEndpoint[Unit, Unit, String, Any] =
    endpoint.get.in("small-text").out(stringBody)

  val smallTextServerEndpoint = smallTextEndpoint
    .serverLogic(_ => Future.successful[Either[Unit, String]](Right("Small text")))

  val declaredPort = 9091
  val declaredHost = "localhost"

  // Configure compression - simply enable it to use Netty's default settings
  val compressionConfig = NettyCompressionConfig.enabled

  val nettyConfig = NettyConfig.default
    .port(declaredPort)
    .host(declaredHost)
    .compressionConfig(compressionConfig)

  // Starting netty server with compression enabled
  val serverBinding: NettyFutureServerBinding =
    Await.result(
      NettyFutureServer(nettyConfig)
        .addEndpoint(largeTextServerEndpoint)
        .addEndpoint(smallTextServerEndpoint)
        .start(),
      Duration.Inf
    )

  val port = serverBinding.port
  val host = serverBinding.hostName
  println(s"Server started at port = ${serverBinding.port}")

  val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

  // Test 1: Request with gzip encoding - large response should be compressed
  println("\n=== Test 1: Large text with gzip encoding ===")
  val largeTextRequest = basicRequest
    .header(HeaderNames.AcceptEncoding, "gzip")
    .response(asByteArray)
    .get(uri"http://$host:$port/large-text")

  val largeTextResponse = largeTextRequest.send(backend)
  val contentEncoding = largeTextResponse.header(HeaderNames.ContentEncoding)
  val uncompressedSize = largeText.getBytes("UTF-8").length
  val compressedSize = largeTextResponse.body.map(_.length).getOrElse(0)

  println(s"Uncompressed size: $uncompressedSize bytes")
  println(s"Compressed size: $compressedSize bytes")
  println(s"Content-Encoding header: ${contentEncoding.getOrElse("none")}")
  println(s"Compression ratio: ${(1.0 - compressedSize.toDouble / uncompressedSize) * 100}%")

  assert(contentEncoding.contains("gzip"), "Large response should be compressed with gzip")
  assert(compressedSize < uncompressedSize, "Compressed size should be smaller than uncompressed")

  // Test 2: Request without Accept-Encoding - response should not be compressed
  println("\n=== Test 2: Large text without Accept-Encoding ===")
  val noEncodingRequest = basicRequest
    .response(asByteArray)
    .get(uri"http://$host:$port/large-text")

  val noEncodingResponse = noEncodingRequest.send(backend)
  val noEncodingContentEncoding = noEncodingResponse.header(HeaderNames.ContentEncoding)
  val noEncodingSize = noEncodingResponse.body.map(_.length).getOrElse(0)

  println(s"Response size: $noEncodingSize bytes")
  println(s"Content-Encoding header: ${noEncodingContentEncoding.getOrElse("none")}")

  assert(noEncodingContentEncoding.isEmpty, "Response without Accept-Encoding should not be compressed")
  assert(noEncodingSize == uncompressedSize, "Uncompressed response should have original size")

  println("\n=== All tests passed! ===")
  println("Compression is working correctly:")
  println("- Large responses are compressed when client supports it")
  println("- Responses are not compressed when client doesn't support it")

  Await.result(serverBinding.stop(), Duration.Inf)

