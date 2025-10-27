// {cat=Streaming; effects=cats-effect; server=http4s}: Proxy requests, handling bodies as fs2 streams

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.50
//> using dep com.softwaremill.sttp.tapir::tapir-http4s-server:1.11.50
//> using dep com.softwaremill.sttp.client4::fs2:4.0.0-RC3
//> using dep org.http4s::http4s-ember-server:0.23.30

package sttp.tapir.examples.streaming

import cats.effect.{ExitCode, IO, IOApp}
import fs2.Stream
import org.http4s.HttpRoutes
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.*
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import sttp.model.{Header, HeaderNames, Method, QueryParams}
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

/** Proxies requests from /proxy to https://httpbin.org/anything */
object ProxyHttp4sFs2Server extends IOApp:
  import org.slf4j.{Logger, LoggerFactory}
  val logger: Logger = LoggerFactory.getLogger(this.getClass().getName)

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

  def proxyRoutes(backend: StreamBackend[IO, Fs2Streams[IO]]): HttpRoutes[IO] =
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

  override def run(args: List[String]): IO[ExitCode] =
    (for {
      backend <- HttpClientFs2Backend.resource[IO]()
      routes = proxyRoutes(backend)
      _ <- EmberServerBuilder
        .default[IO]
        .withHttpApp(Router("/" -> routes).orNotFound)
        .build
    } yield ()).useForever
