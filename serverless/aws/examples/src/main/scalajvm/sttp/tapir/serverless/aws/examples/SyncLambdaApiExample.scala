package sttp.tapir.serverless.aws.examples

import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda._

object SyncLambdaApiExample extends SyncLambdaHandler[AwsRequest](AwsSyncServerOptions.default) {

  val helloEndpoint: ServerEndpoint[Any, sttp.shared.Identity] = endpoint.get
    .in("api" / "hello")
    .out(stringBody)
    .serverLogic[sttp.shared.Identity] { _ => Right(s"Hello!") }

  override protected def getAllEndpoints: List[ServerEndpoint[Any, sttp.shared.Identity]] = List(helloEndpoint)
}
