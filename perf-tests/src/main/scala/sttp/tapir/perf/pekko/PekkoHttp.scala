package sttp.tapir.perf.pekko

import cats.effect.IO
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.scaladsl.FileIO
import sttp.tapir.perf.Common._
import sttp.tapir.perf.apis._
import sttp.tapir.server.pekkohttp.{PekkoHttpServerInterpreter, PekkoHttpServerOptions}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object Vanilla {
  val router: Int => ActorSystem => Route = (nRoutes: Int) =>
    (_: ActorSystem) =>
      concat(
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
        }: _*
      )
}

object Tapir extends Endpoints {
  val serverOptions = PekkoHttpServerOptions
    .customiseInterceptors(ExecutionContext.Implicits.global)
    .serverLog(None)
    .options

  def router: Int => ActorSystem => Route = (nRoutes: Int) =>
    (actorSystem: ActorSystem) =>
      PekkoHttpServerInterpreter(serverOptions)(actorSystem.dispatcher).toRoute(
        genEndpointsFuture(nRoutes)
      )
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
object VanillaServer extends ServerRunner { override def start = PekkoHttp.runServer(Vanilla.router(1)) }
object VanillaMultiServer extends ServerRunner { override def start = PekkoHttp.runServer(Vanilla.router(128)) }
