package sttp.tapir.perf.play

import cats.effect.IO
import org.apache.pekko.actor.ActorSystem
import play.api.Mode
import play.api.mvc.{Handler, PlayBodyParsers, RequestHeader, _}
import play.api.routing.Router
import play.api.routing.Router.Routes
import play.api.routing.sird._
import play.core.server.{DefaultPekkoHttpServerComponents, ServerConfig}
import play.mvc.Http.HttpVerbs
import sttp.tapir.perf.apis._
import sttp.tapir.server.play.PlayServerInterpreter

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object Vanilla extends Results {
  implicit lazy val perfActorSystem: ActorSystem = ActorSystem("vanilla-play")
  implicit lazy val perfExecutionContext: ExecutionContextExecutor = perfActorSystem.dispatcher
  val verbs = new HttpVerbs {}
  val pcc = new ActionBuilder[Request, AnyContent] {

    override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] =
      block(request)

    override protected def executionContext: ExecutionContext = perfExecutionContext

    override val parser: BodyParser[AnyContent] = PlayBodyParsers.apply().anyContent
  }
  def simpleGet: Action[AnyContent] = pcc.async { implicit request =>
    val param = request.path.split("/").last
    Future.successful(
      Ok(param)
    )
  }

  def genRoutesSingle(number: Int): Routes = { case GET(p"/path$number/$param") =>
    simpleGet
  }
  def router: Int => Routes = (nRoutes: Int) => (0 until nRoutes).map(genRoutesSingle).reduceLeft(_ orElse _)
}

object Tapir extends Endpoints {
  val serverEndpointGens = replyingWithDummyStr(allEndpoints, Future.successful)

  def genEndpoints(i: Int) = genServerEndpoints(serverEndpointGens)(i).toList
  import Play._

  val router: Int => Routes = (nRoutes: Int) =>
    PlayServerInterpreter().toRoutes(
      genEndpoints(nRoutes)
    )
}

object Play {
  implicit lazy val perfActorSystem: ActorSystem = ActorSystem("tapir-play")
  implicit lazy val executionContext: ExecutionContextExecutor = perfActorSystem.dispatcher

  def runServer(routes: Routes): IO[ServerRunner.KillSwitch] = {
    val components = new DefaultPekkoHttpServerComponents {
      override lazy val serverConfig: ServerConfig = ServerConfig(port = Some(8080), address = "127.0.0.1", mode = Mode.Test)
      override lazy val actorSystem: ActorSystem = perfActorSystem
      override def router: Router =
        Router.from(
          List(routes).reduce((a: Routes, b: Routes) => {
            val handler: PartialFunction[RequestHeader, Handler] = { case request =>
              a.applyOrElse(request, b)
            }

            handler
          })
        )
    }
    IO(components.server).map(server => IO(server.stop()))
  }
}

object TapirServer extends ServerRunner { override def start = Play.runServer(Tapir.router(1)) }
object TapirMultiServer extends ServerRunner { override def start = Play.runServer(Tapir.router(128)) }
object VanillaServer extends ServerRunner { override def start = Play.runServer(Vanilla.router(1)) }
object VanillaMultiServer extends ServerRunner { override def start = Play.runServer(Vanilla.router(128)) }
