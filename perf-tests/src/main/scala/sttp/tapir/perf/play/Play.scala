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

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.ActorMaterializer

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

    val simpleGet: Action[AnyContent] = actionBuilder(PlayBodyParsers().anyContent).async {
      implicit request =>
        val param = request.path.split("/").last
        Future.successful(
          Ok(param)
        )
    }

    val postBytes: Action[ByteString] =
      actionBuilder(PlayBodyParsers().byteString(maxLength = LargeInputSize + 1024L)).async { implicit request =>
        val param = request.path.split("/").last
        val byteArray: ByteString = request.body
        Future.successful(Ok(s"$param-${byteArray.length}"))
      }

    val postString: Action[String] = actionBuilder(PlayBodyParsers().text(maxLength = LargeInputSize + 1024L)).async { implicit request =>
      val param = request.path.split("/").last
      val str: String = request.body
      Future.successful(Ok(s"$param-${str.length}"))
    }

    val postFile: Action[Files.TemporaryFile] = actionBuilder(PlayBodyParsers.apply().temporaryFile).async { implicit request =>
      val param = request.path.split("/").last
      val file: Files.TemporaryFile = request.body
      Future.successful(Ok(s"$param-${file.path.toString}"))
    }

    {
      case GET(p"/path$number/$param") =>
        simpleGet
      case POST(p"/path$number/$param") =>
        postString
      case POST(p"/pathBytes$number/$param") =>
        postBytes
      case POST(p"/pathFile$number/$param") =>
        postFile
    }
  }
  def router: Int => ActorSystem => Routes = (nRoutes: Int) => (actorSystem: ActorSystem) => (0 until nRoutes).map(genRoutesSingle(actorSystem)).reduceLeft(_ orElse _)
}

object Tapir extends Endpoints {
  val serverEndpointGens = replyingWithDummyStr(allEndpoints, Future.successful)

  def genEndpoints(i: Int) = genServerEndpoints(serverEndpointGens)(i).toList

  val router: Int => ActorSystem => Routes = (nRoutes: Int) =>
    (actorSystem: ActorSystem) => {
      implicit val actorSystemForMaterializer: ActorSystem = actorSystem
      PlayServerInterpreter().toRoutes(
        genEndpoints(nRoutes)
      )
    }
}

object Play {

  def runServer(routes: ActorSystem => Routes): IO[ServerRunner.KillSwitch] = {
    implicit lazy val perfActorSystem: ActorSystem = ActorSystem(s"tapir-play")
    val components = new DefaultPekkoHttpServerComponents {
      override lazy val serverConfig: ServerConfig = ServerConfig(port = Some(8080), address = "127.0.0.1", mode = Mode.Test)
      override lazy val actorSystem: ActorSystem = perfActorSystem
      override def router: Router =
        Router.from(
          List(routes(actorSystem)).reduce((a: Routes, b: Routes) => {
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
