package sttp.tapir.perf.play

import cats.effect.IO
import org.apache.pekko.actor.ActorSystem
import play.api.Mode
import play.api.mvc._
import play.api.routing.Router
import play.api.routing.Router.Routes
import play.api.routing.sird._
import play.core.server.{DefaultPekkoHttpServerComponents, ServerConfig}
import sttp.tapir.perf.Common._
import sttp.tapir.perf.apis._

import sttp.tapir.server.play.PlayServerInterpreter

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import org.apache.pekko.util.ByteString
import play.api.libs.Files

object Vanilla extends ControllerHelpers { 
  implicit lazy val perfActorSystem: ActorSystem = ActorSystem("vanilla-play")
  implicit lazy val perfExecutionContext: ExecutionContextExecutor = perfActorSystem.dispatcher
  val anyContentActionBuilder = new ActionBuilder[Request, AnyContent] {

    override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] =
      block(request)

    override protected def executionContext: ExecutionContext = perfExecutionContext

    override val parser: BodyParser[AnyContent] = PlayBodyParsers.apply().anyContent
  }

  val byteStringActionBuilder = new ActionBuilder[Request, ByteString] {
    override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] =
      block(request)

    override protected def executionContext: ExecutionContext = perfExecutionContext

    override val parser: BodyParser[ByteString] = PlayBodyParsers.apply().byteString(maxLength = LargeInputSize + 1024L)
  }

  val stringActionBuilder = new ActionBuilder[Request, String] {
    override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] =
      block(request)

    override protected def executionContext: ExecutionContext = perfExecutionContext

    override val parser: BodyParser[String] = PlayBodyParsers.apply().text(maxLength = LargeInputSize + 1024L)
  }

  val fileReqActionBuilder = new ActionBuilder[Request, Files.TemporaryFile] {
    override def invokeBlock[A](request: Request[A], block: Request[A] => Future[Result]): Future[Result] =
      block(request)

    override protected def executionContext: ExecutionContext = perfExecutionContext

    override val parser: BodyParser[Files.TemporaryFile] = PlayBodyParsers.apply().temporaryFile
  }

  def simpleGet: Action[AnyContent] = anyContentActionBuilder.async { implicit request =>
    val param = request.path.split("/").last
    Future.successful(
      Ok(param)
    )
  }

  def postBytes: Action[ByteString] = byteStringActionBuilder.async { implicit request =>
    val param = request.path.split("/").last
    val byteArray: ByteString = request.body
    Future.successful(Ok(s"$param-${byteArray.length}"))
  }

  def postString: Action[String] = stringActionBuilder.async { implicit request =>
    val param = request.path.split("/").last
    val str: String = request.body
    Future.successful(Ok(s"$param-${str.length}"))
  }

  def postFile: Action[Files.TemporaryFile] = fileReqActionBuilder.async { implicit request =>
    val param = request.path.split("/").last
    val file: Files.TemporaryFile = request.body
    Future.successful(Ok(s"$param-${file.path.toString}"))
  }


  def genRoutesSingle(number: Int): Routes = { 
    case GET(p"/path$number/$param") =>
      simpleGet
    case POST(p"/path$number/$param") =>
      postString
    case POST(p"/pathBytes$number/$param") =>
      postBytes
    case POST(p"/pathFile$number/$param") =>
      postFile
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
