// {cat=Observability; effects=ZIO; server=ZIO HTTP}: Reporting Prometheus metrics

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-zio-http-server:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-zio-metrics:1.11.8

package sttp.tapir.examples.observability

import sttp.tapir.*
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.zio.ZioMetrics
import sttp.tapir.server.ziohttp.{ZioHttpInterpreter, ZioHttpServerOptions}
import sttp.tapir.ztapir.ZServerEndpoint
import zio.http.{Response => ZioHttpResponse, Routes, Server}
import zio.{Task, ZIO, _}

/** Based on https://adopt-tapir.softwaremill.com zio version. */
object ZioMetricsExample extends ZIOAppDefault:

  case class User(name: String) extends AnyVal

  val helloEndpoint: PublicEndpoint[User, Unit, String, Any] = endpoint.get
    .in("hello")
    .in(query[User]("name"))
    .out(stringBody)
  val helloServerEndpoint: ZServerEndpoint[Any, Any] = helloEndpoint.serverLogicSuccess(user => ZIO.succeed(s"Hello ${user.name}"))

  val apiEndpoints: List[ZServerEndpoint[Any, Any]] = List(helloServerEndpoint)

  val all: List[ZServerEndpoint[Any, Any]] = apiEndpoints

  val metrics: ZioMetrics[Task] = ZioMetrics.default[Task]()
  val metricsInterceptor: MetricsRequestInterceptor[Task] = metrics.metricsInterceptor()

  // noinspection DuplicatedCode
  override def run: ZIO[Any & ZIOAppArgs & Scope, Any, Any] =
    val serverOptions: ZioHttpServerOptions[Any] =
      ZioHttpServerOptions.customiseInterceptors.metricsInterceptor(metricsInterceptor).options
    val app: Routes[Any, ZioHttpResponse] = ZioHttpInterpreter(serverOptions).toHttp(all)

    (for {
      serverPort <- Server.install(app)
      _ <- Console.printLine(s"Server started at http://localhost:$serverPort. Press ENTER key to exit.")
      _ <- Console.readLine
    } yield serverPort)
      .provide(
        ZLayer.succeed(Server.Config.default.port(8080)),
        Server.live
      )
      .exitCode
