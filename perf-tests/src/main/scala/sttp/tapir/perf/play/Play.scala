package sttp.tapir.perf.play

import cats.effect.IO
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.util.ByteString
import play.api.Mode
import play.api.libs.Files
import play.api.mvc._
import play.api.routing.Router
import play.api.routing.Router.Routes
import play.api.routing.sird._
import play.core.server.{DefaultPekkoHttpServerComponents, ServerConfig}
import sttp.tapir.perf.Common._
import sttp.tapir.perf.apis._
import sttp.tapir.server.play.PlayServerInterpreter
import sttp.tapir.server.play.PlayServerOptions

import scala.concurrent.{ExecutionContext, Future}

object Vanilla extends ControllerHelpers {

  def genRoutesSingle(actorSystem: ActorSystem)(number: Int): Routes = {

    def actionBuilder[T](parserParam: BodyParser[T]): ActionBuilder[Request, T] =
      new ActionBuilder[Request, T] {

        override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] =
          block(request)

        override protected def executionContext: ExecutionContext = actorSystem.dispatcher

        override val parser: BodyParser[T] = parserParam
      }

    implicit val actorSystemForMaterializer: ActorSystem = actorSystem

    def simpleGet(n: Int): Action[AnyContent] = actionBuilder(PlayBodyParsers().anyContent).async { implicit request =>
      val param = request.path.split("/").last
      Future.successful(
        Ok((n + param.toInt).toString)
      )
    }

    def postString(n: Int): Action[String] = actionBuilder(PlayBodyParsers().text(maxLength = LargeInputSize + 1024L)).async {
      implicit request =>
        val body: String = request.body
        Future.successful(Ok(s"Ok [$n], string length = ${body.length}"))
    }

    def postBytes(n: Int): Action[ByteString] =
      actionBuilder(PlayBodyParsers().byteString(maxLength = LargeInputSize + 1024L)).async { implicit request =>
        val body: ByteString = request.body
        Future.successful(Ok(s"Ok [$n], bytes length = ${body.length}"))
      }

    def postFile(n: Int): Action[Files.TemporaryFile] = actionBuilder(PlayBodyParsers().temporaryFile).async { implicit request =>
      val body: Files.TemporaryFile = request.body
      Future.successful(Ok(s"Ok [$n], file saved to ${body.toPath}"))
    }

    {
      case GET(p"/path$number/$_") =>
        simpleGet(number.toInt)
      case POST(p"/pathBytes$number") =>
        postBytes(number.toInt)
      case POST(p"/pathFile$number") =>
        postFile(number.toInt)
      case POST(p"/path$number") =>
        postString(number.toInt)
    }
  }
  def router: Int => ActorSystem => Routes = (nRoutes: Int) =>
    (actorSystem: ActorSystem) => (0 until nRoutes).map(genRoutesSingle(actorSystem)).reduceLeft(_ orElse _)
}

object Tapir extends Endpoints {
  def router(nRoutes: Int, withServerLog: Boolean = false): ActorSystem => Routes =
    (actorSystem: ActorSystem) => {
      implicit val actorSystemForMaterializer: ActorSystem = actorSystem
      implicit val ec: ExecutionContext = actorSystem.dispatcher
      val serverOptions = buildOptions(PlayServerOptions.customiseInterceptors(), withServerLog)
      PlayServerInterpreter(serverOptions).toRoutes(
        genEndpointsFuture(nRoutes)
      )
    }
}

object Play {

  def runServer(routes: ActorSystem => Routes): IO[ServerRunner.KillSwitch] = {
    implicit lazy val perfActorSystem: ActorSystem = ActorSystem(s"tapir-play")
    val playRouter =
      Router.from(
        List(routes(perfActorSystem)).reduce((a: Routes, b: Routes) => {
          val handler: PartialFunction[RequestHeader, Handler] = { case request =>
            a.applyOrElse(request, b)
          }
          handler
        })
      )
    val components = new DefaultPekkoHttpServerComponents {
      override lazy val serverConfig: ServerConfig = ServerConfig(port = Some(Port), address = "127.0.0.1", mode = Mode.Test)
      override lazy val actorSystem: ActorSystem = perfActorSystem
      override def router: Router = playRouter
    }
    IO(components.server).map(server => IO(server.stop()))
  }
}

object TapirServer extends ServerRunner { override def start = Play.runServer(Tapir.router(1)) }
object TapirMultiServer extends ServerRunner { override def start = Play.runServer(Tapir.router(128)) }
object TapirInterceptorMultiServer extends ServerRunner { override def start = Play.runServer(Tapir.router(128, withServerLog = true)) }
object VanillaServer extends ServerRunner { override def start = Play.runServer(Vanilla.router(1)) }
object VanillaMultiServer extends ServerRunner { override def start = Play.runServer(Vanilla.router(128)) }
