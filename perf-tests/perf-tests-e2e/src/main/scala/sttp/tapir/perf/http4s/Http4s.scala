package sttp.tapir.perf.http4s

import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s
import fs2._
import fs2.io.file.{Files, Path => Fs2Path}
import org.http4s._
import org.http4s.ember.server.EmberServerBuilder
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
  val dsl = Http4sDsl[IO]
  import dsl._
  val receive: Pipe[IO, WebSocketFrame, Unit] = _.as(())
  val wsResponseStream = Http4sCommon.wsResponseStream.evalMap(_ => IO.realTime.map(ts => WebSocketFrame.Text(s"${ts.toMillis}")))

  val router: Int => WebSocketBuilder2[IO] => HttpRoutes[IO] = (nRoutes: Int) =>
    wsb =>
      Router(
        (("/ws") -> {
          HttpRoutes
            .of[IO] { case GET -> Root / "ts" =>
              wsb.withFilterPingPongs(true).build(wsResponseStream, receive)
            }
        }) ::
          (0 to nRoutes)
            .map((n: Int) =>
              ("/") -> {
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
            )
            .toList: _*
      )
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

  def router(nRoutes: Int, withServerLog: Boolean = false)(wsb: WebSocketBuilder2[IO]): HttpRoutes[IO] = {
    val serverOptions = buildOptions(Http4sServerOptions.customiseInterceptors[IO], withServerLog)
    val interpreter = Http4sServerInterpreter[IO](serverOptions)
    Router(
      (
        (("/") -> {
          interpreter
            .toWebSocketRoutes(
              wsEndpoint.serverLogicSuccess(_ =>
                IO.pure { (in: Stream[IO, Long]) =>
                  Http4sCommon.wsResponseStream.evalMap(_ => IO.realTime.map(_.toMillis)).concurrently(in.as(()))
                }
              )
            )(wsb) <+> interpreter.toRoutes(genEndpointsIO(nRoutes))
        })
      )
    )
  }
}

object server {
  val maxConnections = 65536
  def runServer(router: WebSocketBuilder2[IO] => HttpRoutes[IO]): Resource[IO, Unit] =
    EmberServerBuilder
      .default[IO]
      .withPort(ip4s.Port.fromInt(Port).get)
      .withHttpWebSocketApp(wsb => router(wsb).orNotFound)
      .withMaxConnections(maxConnections)
      .build
      .map(_ => ())
      .onFinalize(IO.println("Http4s server closed."))
}

object TapirServer extends ServerRunner { override def runServer = server.runServer(Tapir.router(1)) }
object TapirMultiServer extends ServerRunner { override def runServer = server.runServer(Tapir.router(128)) }
object TapirInterceptorMultiServer extends ServerRunner {
  override def runServer = server.runServer(Tapir.router(128, withServerLog = true))
}
object VanillaServer extends ServerRunner { override def runServer = server.runServer(Vanilla.router(1)) }
object VanillaMultiServer extends ServerRunner { override def runServer = server.runServer(Vanilla.router(128)) }
