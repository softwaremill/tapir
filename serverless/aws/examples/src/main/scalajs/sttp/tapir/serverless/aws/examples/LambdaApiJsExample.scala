package sttp.tapir.serverless.aws.examples

import cats.syntax.all._
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda._
import sttp.tapir.serverless.aws.lambda.js._

import scala.scalajs.js.annotation._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

object LambdaApiJsExample {

  implicit val ec: ExecutionContextExecutor = ExecutionContext.global

  val helloEndpoint: ServerEndpoint[Any, Future] = endpoint.get
    .in("api" / "hello")
    .out(stringBody)
    .serverLogic { _ => Future(s"Hello!".asRight[Unit]) }

  val options: AwsServerOptions[Future] = AwsFutureServerOptions.default.copy(encodeResponseBody = false)

  val route: Route[Future] = AwsFutureServerInterpreter(options).toRoute(helloEndpoint)

  @JSExportTopLevel(name = "LambdaApiJsExampleHandler")
  def handler(event: AwsJsRequest, context: Any): scala.scalajs.js.Promise[AwsJsResponse] = AwsJsRouteHandler.futureHandler(event, route)
}
