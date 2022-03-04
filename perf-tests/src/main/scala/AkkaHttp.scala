package perfTests.AkkaHttp

import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.Http
import scala.concurrent.Future

object Vanilla {
  val route = (n: Int) => get {
    path(("path" + n.toString) / IntNumber) {
      id => complete((n + id).toString)
    }
  }
}

object Tapir {
  val route = (n: Int) => AkkaHttpServerInterpreter().toRoute(
    perfTests.Common.genTapirEndpoint(n).serverLogic(
      (id: Int) => Future.successful(Right((id + n).toString)): Future[Either[String, String]]
    )
  )
}

object AkkaHttp {
  implicit val actorSystem = ActorSystem(Behaviors.empty, "akka-http")
  implicit val executionContext = actorSystem.executionContext

  def runServer(nRoutes: Int, route: Int => Route) = {
    Http()
      .newServerAt("127.0.0.1", 8080)
      .bind(concat((0 to nRoutes).map(route):_*))
      .flatMap((x) => {
                 perfTests.Common.blockServer()
                 x.unbind()
               })
      .onComplete(_ => actorSystem.terminate())
  }
}

object VanillaMultiServer extends App {AkkaHttp.runServer(128, Vanilla.route)}
object VanillaServer      extends App {AkkaHttp.runServer(1,   Vanilla.route)}
object TapirMultiServer   extends App {AkkaHttp.runServer(128, Tapir.route  )}
object TapirServer        extends App {AkkaHttp.runServer(1,   Tapir.route  )}
