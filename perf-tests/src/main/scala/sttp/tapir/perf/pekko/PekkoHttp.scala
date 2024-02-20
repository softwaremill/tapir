package sttp.tapir.perf.pekko

import cats.effect.IO
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.ws.{Message, TextMessage}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.scaladsl.{FileIO, Flow, Sink, Source}
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.perf.Common._
import sttp.tapir.perf.apis._
import sttp.tapir.server.pekkohttp.{PekkoHttpServerInterpreter, PekkoHttpServerOptions}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object PekkoCommon {
  // Define a source that emits the current timestamp every 100 milliseconds
  // We have to .take exact number of items, which will guarantee returning a proper final frame. Otherwise the stream would never end,
  // causing Gatling to hang for long and then fail.
  val timestampSource = Source.tick(0.seconds, 100.milliseconds, ()).take(WebSocketRequestsPerUser.toLong).map { _ =>
    System.currentTimeMillis()
  }
}

object Vanilla {
  val wsRoute: Route = path("ws" / "ts") {
    handleWebSocketMessages(timeStampWebSocketFlow.map(ts => TextMessage(ts.toString)))
  }

  def timeStampWebSocketFlow: Flow[Message, Long, Any] = {
    // Incoming messages are ignored, but we need to define a sink for them
    val sink = Flow[Message].to(Sink.ignore)
    Flow.fromSinkAndSource(sink, PekkoCommon.timestampSource)
  }

  val router: Int => ActorSystem => Route = (nRoutes: Int) =>
    (_: ActorSystem) =>
      concat(
        wsRoute ::
          (0 to nRoutes).flatMap { (n: Int) =>
            List(
              get {
                path(("path" + n.toString) / IntNumber) { id =>
                  complete((n + id).toString)
                }
              },
              post {
                path(("path" + n.toString)) {
                  entity(as[String]) { _ =>
                    complete((n).toString)
                  }
                }
              },
              post {
                path(("pathBytes" + n.toString)) {
                  entity(as[Array[Byte]]) { bytes =>
                    complete(s"Received ${bytes.length} bytes")
                  }
                }
              },
              post {
                path(("pathFile" + n.toString)) {
                  extractRequestContext { ctx =>
                    entity(as[HttpEntity]) { httpEntity =>
                      val path = newTempFilePath()
                      val sink = FileIO.toPath(path)
                      val finishedWriting = httpEntity.dataBytes.runWith(sink)(ctx.materializer)
                      onSuccess(finishedWriting) { _ =>
                        complete(s"File saved to $path")
                      }
                    }
                  }
                }
              }
            )
          }.toList: _*
      )
}

object Tapir extends Endpoints {
  import sttp.tapir._
  val wsSink = Flow[Long].to(Sink.ignore)
  val wsEndpoint = wsBaseEndpoint
    .out(
      webSocketBody[Long, CodecFormat.TextPlain, Long, CodecFormat.TextPlain](PekkoStreams)
        .concatenateFragmentedFrames(false)
    )
  val wsServerEndpoint = wsEndpoint.serverLogicSuccess[Future] { _ =>
    Future.successful {
      Flow.fromSinkAndSource(wsSink, PekkoCommon.timestampSource)
    }
  }

  def router(nRoutes: Int, withServerLog: Boolean = false): ActorSystem => Route = { (actorSystem: ActorSystem) =>
    val serverOptions = buildOptions(PekkoHttpServerOptions.customiseInterceptors(ExecutionContext.Implicits.global), withServerLog)
    PekkoHttpServerInterpreter(serverOptions)(actorSystem.dispatcher).toRoute(
      wsServerEndpoint :: genEndpointsFuture(nRoutes)
    )
  }
}

object PekkoHttp {
  def runServer(router: ActorSystem => Route): IO[ServerRunner.KillSwitch] = {
    // We need to create a new actor system each time server is run
    implicit val actorSystem: ActorSystem = ActorSystem("tapir-pekko-http")
    implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
    IO.fromFuture(
      IO(
        Http()
          .newServerAt("127.0.0.1", Port)
          .bind(router(actorSystem))
          .map { binding =>
            IO.fromFuture(IO(binding.unbind().flatMap(_ => actorSystem.terminate()))).void
          }
      )
    )
  }
}

object TapirServer extends ServerRunner { override def start = PekkoHttp.runServer(Tapir.router(1)) }
object TapirMultiServer extends ServerRunner { override def start = PekkoHttp.runServer(Tapir.router(128)) }
object TapirInterceptorMultiServer extends ServerRunner {
  override def start = PekkoHttp.runServer(Tapir.router(128, withServerLog = true))
}
object VanillaServer extends ServerRunner { override def start = PekkoHttp.runServer(Vanilla.router(1)) }
object VanillaMultiServer extends ServerRunner { override def start = PekkoHttp.runServer(Vanilla.router(128)) }
