package sttp.tapir.examples.streaming

import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.StrictLogging
import fs2.Stream
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.*
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import sttp.model.{Header, HeaderNames, Method, QueryParams}
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

/** Proxies requests from /proxy to https://httpbin.org/anything */
object ProxyHttp4sFs2Server extends IOApp with StrictLogging {
  val proxyEndpoint: PublicEndpoint[
    (Method, List[String], QueryParams, List[Header], Stream[IO, Byte]),
    Unit,
    (List[Header], Stream[IO, Byte]),
    Fs2Streams[IO]
  ] =
    endpoint
      .in(extractFromRequest(_.method))
      .in("proxy")
      .in(paths)
      .in(queryParams)
      .in(headers)
      .in(streamBinaryBody(Fs2Streams[IO])(CodecFormat.OctetStream()))
      .out(headers)
      .out(streamBinaryBody(Fs2Streams[IO])(CodecFormat.OctetStream()))

  def proxyRoutes(backend: SttpBackend[IO, Fs2Streams[IO]]): HttpRoutes[IO] =
    Http4sServerInterpreter[IO]().toRoutes(proxyEndpoint.serverLogicSuccess { case (method, paths, queryParams, headers, body) =>
      val proxyUri = uri"https://httpbin.org/anything/$paths?$queryParams"
      val filteredHeaders = headers.filterNot(h => h.is(HeaderNames.Host))
      logger.info(s"Proxying: $method $paths $queryParams ($filteredHeaders) -> $proxyUri")
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
