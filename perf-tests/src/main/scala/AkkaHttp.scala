package perfTests.AkkaHttp

import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.Http
import scala.concurrent.Future

object Vanilla {
  val router = (nRoutes: Int) => concat(
    (0 to nRoutes).map((n: Int) =>
      get { path(("path" + n.toString) / IntNumber) {
             id => complete((n + id).toString)}
      }
    ):_*
  )
}

object Tapir {
  val router = (nRoutes: Int) => AkkaHttpServerInterpreter().toRoute(
    (0 to nRoutes).map((n: Int) =>
      perfTests.Common.genTapirEndpoint(n).serverLogic(
        (id: Int) => Future.successful(Right((id + n).toString)): Future[Either[String, String]]
      )
    ).toList
  )
}

object AkkaHttp {
  implicit val actorSystem = ActorSystem(Behaviors.empty, "akka-http")
  implicit val executionContext = actorSystem.executionContext

  def runServer(router: Route) = {
    Http()
      .newServerAt("127.0.0.1", 8080)
      .bind(router)
      .flatMap((x) => {perfTests.Common.blockServer(); x.unbind()})
      .onComplete(_ => actorSystem.terminate())
  }
}

object TapirServer        extends App {AkkaHttp.runServer(Tapir.router(1))}
object TapirMultiServer   extends App {AkkaHttp.runServer(Tapir.router(128))}
object VanillaServer      extends App {AkkaHttp.runServer(Vanilla.router(1))}
object VanillaMultiServer extends App {AkkaHttp.runServer(Vanilla.router(128))}
