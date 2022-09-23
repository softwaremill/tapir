package sttp.tapir.examples.streaming

import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.StrictLogging
import fs2.Stream
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import sttp.model.{Header, HeaderNames, Method, Uri}
import sttp.tapir._
import sttp.tapir.server.http4s.Http4sServerInterpreter

/** Proxies requests to https://httpbin.org/anything */
object ProxyHttp4sFs2Server extends IOApp with StrictLogging {
  val proxyEndpoint: PublicEndpoint[(Method, Uri, List[Header], Stream[IO, Byte]), Unit, (List[Header], Stream[IO, Byte]), Fs2Streams[IO]] =
    endpoint
      .in(extractFromRequest(_.method))
      .in(extractFromRequest(_.uri))
      .in(headers)
      .in(streamBinaryBody(Fs2Streams[IO])(CodecFormat.OctetStream()))
      .out(headers)
      .out(streamBinaryBody(Fs2Streams[IO])(CodecFormat.OctetStream()))

  def proxyRoutes(backend: SttpBackend[IO, Fs2Streams[IO]]): HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(proxyEndpoint.serverLogicSuccess { case (method, uri, headers, body) =>
      val proxyUri = uri.scheme("https").host("httpbin.org").port(None).withPath("anything" +: uri.path)
      val filteredHeaders = headers.filterNot(h => h.is(HeaderNames.Host))
      logger.info(s"Proxying: $method $uri ($filteredHeaders)  -> $proxyUri")
      basicRequest
        .method(method, proxyUri)
        .headers(filteredHeaders: _*)
        .streamBody(Fs2Streams[IO])(body)
        .response(asStreamAlwaysUnsafe(Fs2Streams[IO]))
        .send(backend)
        .map { response => (response.headers.toList, response.body) }
    })

  override def run(args: List[String]): IO[ExitCode] = {
    (for {
      backend <- HttpClientFs2Backend.resource[IO]()
      routes = proxyRoutes(backend)
      _ <- BlazeServerBuilder[IO]
        .bindHttp(8080, "localhost")
        .withHttpApp(Router("/" -> routes).orNotFound)
        .resource
    } yield ())
      .use { _ => IO.never }
      .as(ExitCode.Success)
  }
}
