package sttp.tapir.perf.http4s

import cats.effect._
import fs2._
import fs2.io.file.{Files, Path => Fs2Path}
import org.http4s._
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.perf.Common._
import sttp.tapir.perf.apis._
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.{CodecFormat, webSocketBody}

object Http4sCommon {
  // Websocket response is returned with a lag, so that we can have more concurrent users talking to the server.
  // This lag is not relevant for measurements, because the server returns a timestamp after having a response ready to send back,
  // so the client can measure only the latency of the server stack handling the response.
  val wsResponseStream = Stream.fixedRate[IO](WebSocketSingleResponseLag, dampen = false)
}

object Vanilla {
  val router: Int => HttpRoutes[IO] = (nRoutes: Int) =>
    Router(
      (0 to nRoutes).map((n: Int) =>
        ("/") -> {
          val dsl = Http4sDsl[IO]
          import dsl._
          HttpRoutes.of[IO] {
            case GET -> Root / s"path$n" / IntVar(id) =>
              Ok((id + n.toInt).toString)
            case req @ POST -> Root / s"path$n" =>
              req.as[String].flatMap { str =>
                Ok(s"Ok [$n], string length = ${str.length}")
              }
            case req @ POST -> Root / s"pathBytes$n" =>
              req.as[Array[Byte]].flatMap { bytes =>
                Ok(s"Ok [$n], bytes length = ${bytes.length}")
              }
            case req @ POST -> Root / s"pathFile$n" =>
              val filePath = newTempFilePath()
              val sink = Files[IO].writeAll(Fs2Path.fromNioPath(filePath))
              req.body
                .through(sink)
                .compile
                .drain
                .flatMap(_ => Ok(s"Ok [$n], file saved to ${filePath.toAbsolutePath.toString}"))
          }
        }
      ): _*
    )

  def webSocketApp(wsb: WebSocketBuilder2[IO]): HttpApp[IO] = {
    val dsl = new Http4sDsl[IO] {}
    import dsl._
    val receive: Pipe[IO, WebSocketFrame, Unit] = _.as(())
    val responseStream = Http4sCommon.wsResponseStream.evalMap(_ => IO.realTime.map(ts => WebSocketFrame.Text(s"${ts.toMillis}")))

    HttpRoutes
      .of[IO] { case GET -> Root / "ws" / "ts" =>
        wsb.withFilterPingPongs(true).build(responseStream, receive)
      }
      .orNotFound
  }
}

object Tapir extends Endpoints {

  implicit val mErr: MonadError[IO] = new CatsMonadError[IO]

  private val wsEndpoint = wsBaseEndpoint
    .out(
      webSocketBody[Long, CodecFormat.TextPlain, Long, CodecFormat.TextPlain](Fs2Streams[IO])
        .concatenateFragmentedFrames(false)
        .autoPongOnPing(false)
        .ignorePong(false)
        .autoPing(None)
    )

  def router(nRoutes: Int, withServerLog: Boolean = false): HttpRoutes[IO] = {
    val serverOptions = buildOptions(Http4sServerOptions.customiseInterceptors[IO], withServerLog)
    Router("/" -> {
      Http4sServerInterpreter[IO](serverOptions).toRoutes(
        genEndpointsIO(nRoutes)
      )
    })
  }

  def wsApp(withServerLog: Boolean = false): WebSocketBuilder2[IO] => HttpApp[IO] = { wsb =>
    val serverOptions = buildOptions(Http4sServerOptions.customiseInterceptors[IO], withServerLog)
    Router("/" -> {
      Http4sServerInterpreter[IO](serverOptions)
        .toWebSocketRoutes(
          wsEndpoint.serverLogicSuccess(_ =>
            IO.pure { (in: Stream[IO, Long]) =>
              Http4sCommon.wsResponseStream.evalMap(_ => IO.realTime.map(_.toMillis)).concurrently(in.as(()))
            }
          )
        )(wsb)
    }).orNotFound
  }
}

object server {
  val maxConnections = 65536
  val connectorPoolSize: Int = Math.max(2, Runtime.getRuntime.availableProcessors() / 4)
  def runServer(router: HttpRoutes[IO], webSocketApp: WebSocketBuilder2[IO] => HttpApp[IO]): IO[ServerRunner.KillSwitch] =
    BlazeServerBuilder[IO]
      .bindHttp(Port, "localhost")
      .withHttpApp(router.orNotFound)
      .withHttpWebSocketApp(webSocketApp)
      .withMaxConnections(maxConnections)
      .withConnectorPoolSize(connectorPoolSize)
      .resource
      .allocated
      .map(_._2)
      .map(_.flatTap { _ =>
        IO.println("Http4s server closed.")
      })
}

object TapirServer extends ServerRunner { override def start = server.runServer(Tapir.router(1), Tapir.wsApp()) }
object TapirMultiServer extends ServerRunner { override def start = server.runServer(Tapir.router(128), Tapir.wsApp()) }
object TapirInterceptorMultiServer extends ServerRunner {
  override def start = server.runServer(Tapir.router(128, withServerLog = true), Tapir.wsApp(withServerLog = true))
}
object VanillaServer extends ServerRunner { override def start = server.runServer(Vanilla.router(1), Vanilla.webSocketApp) }
object VanillaMultiServer extends ServerRunner { override def start = server.runServer(Vanilla.router(128), Vanilla.webSocketApp) }
