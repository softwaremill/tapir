
import sttp.tapir._
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.zio.ZioMetrics
import sttp.tapir.server.ziohttp.{ZioHttpInterpreter, ZioHttpServerOptions}
import sttp.tapir.ztapir.ZServerEndpoint
import zhttp.http.HttpApp
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{EventLoopGroup, Server}
import zio.{Task, ZIO, _}

/** Based on https://adopt-tapir.softwaremill.com zio version. */
object ZioMetricsExample extends ZIOAppDefault {

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

  //noinspection DuplicatedCode
  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = {
    val serverOptions: ZioHttpServerOptions[Any] =
      ZioHttpServerOptions.customiseInterceptors.metricsInterceptor(metricsInterceptor).options
    val app: HttpApp[Any, Throwable] = ZioHttpInterpreter(serverOptions).toHttp(all)

    val port = sys.env.get("http.port").map(_.toInt).getOrElse(8080)

    (for {
      serverStart <- Server(app).withPort(port).make
      _ <- Console.printLine(s"Server started at http://localhost:${serverStart.port}. Press ENTER key to exit.")
      _ <- Console.readLine
    } yield serverStart)
      .provideSomeLayer(EventLoopGroup.auto(0) ++ ServerChannelFactory.auto ++ Scope.default)
      .exitCode
  }


}
