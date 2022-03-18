package sttp.tapir.perf

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import sttp.tapir.perf
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

import scala.concurrent.{ExecutionContextExecutor, Future}

object Vanilla {
  val router: Int => Route = (nRoutes: Int) =>
    concat(
      (0 to nRoutes).map((n: Int) =>
        get {
          path(("path" + n.toString) / IntNumber) { id =>
            complete((n + id).toString)
          }
        }
      ): _*
    )
}

object Tapir {
  val router: Int => Route = (nRoutes: Int) =>
    AkkaHttpServerInterpreter().toRoute(
      (0 to nRoutes)
        .map((n: Int) =>
          perf.Common
            .genTapirEndpoint(n)
            .serverLogic((id: Int) => Future.successful(Right((id + n).toString)): Future[Either[String, String]])
        )
        .toList
    )
}

object AkkaHttp {
  implicit val actorSystem: ActorSystem = ActorSystem("akka-http")
  implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher

  def runServer(router: Route): Unit = {
    Http()
      .newServerAt("127.0.0.1", 8080)
      .bind(router)
      .flatMap((x) => { Common.blockServer(); x.unbind() })
      .onComplete(_ => actorSystem.terminate())
  }
}

object TapirServer extends App { AkkaHttp.runServer(Tapir.router(1)) }
object TapirMultiServer extends App { AkkaHttp.runServer(Tapir.router(128)) }
object VanillaServer extends App { AkkaHttp.runServer(Vanilla.router(1)) }
object VanillaMultiServer extends App { AkkaHttp.runServer(Vanilla.router(128)) }
