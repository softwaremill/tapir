package sttp.tapir.serverless.aws.examples

import cats.syntax.all._
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda._
import sttp.tapir.serverless.aws.lambda.js._

import scala.scalajs.js.annotation._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object LambdaApiJsExample {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  implicit val monad: MonadError[Future] = new FutureMonad()

  val helloEndpoint: ServerEndpoint[Unit, Unit, String, Any, Future] = endpoint.get
    .in("api" / "hello")
    .out(stringBody)
    .serverLogic { _ => Future(s"Hello!".asRight[Unit]) }

  val options: AwsServerOptions[Future] = AwsServerOptions.default[Future].copy(encodeResponseBody = false)

  val route: JsRoute[Future] = AwsServerInterpreter(options).toRoute(helloEndpoint).toJsRoute

  def main(event: AwsJsRequest): Future[AwsJsResponse] = route(event)

  @JSExportTopLevel(name="handler")
  val handler: scala.scalajs.js.Function2[AwsJsRequest, Any, scala.scalajs.js.Promise[AwsJsResponse]] = {
    (event: AwsJsRequest, _) =>
      import scala.scalajs.js.JSConverters._
      main(event).toJSPromise
  }
}
