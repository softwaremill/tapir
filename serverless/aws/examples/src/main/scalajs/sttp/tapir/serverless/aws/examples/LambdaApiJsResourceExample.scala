package sttp.tapir.serverless.aws.examples

import cats.effect.{IO, Resource}
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda._
import sttp.tapir.serverless.aws.lambda.js._

import scala.scalajs.js.annotation._

object LambdaApiJsResourceExample {

  val helloEndpoint: ServerEndpoint[Any, IO] = endpoint.get
    .in("api" / "hello")
    .out(stringBody)
    .serverLogicSuccess { _ => IO("Hello!") }

  val options: AwsServerOptions[IO] = AwsCatsEffectServerOptions.default[IO].copy(encodeResponseBody = false)

  val route: Resource[IO, Route[IO]] =
    Resource.pure(AwsCatsEffectServerInterpreter(options).toRoute(helloEndpoint)) // for demonstration purposes only

  @JSExportTopLevel(name = "LambdaApiJsResourceExampleHandler")
  def handler(event: AwsJsRequest, context: Any): scala.scalajs.js.Promise[AwsJsResponse] =
    AwsJsRouteHandler.catsResourceHandler(event, route)
}
