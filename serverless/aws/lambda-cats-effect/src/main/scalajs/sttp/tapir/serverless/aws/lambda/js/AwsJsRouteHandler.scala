package sttp.tapir.serverless.aws.lambda.js

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import sttp.client4.impl.cats.CatsMonadAsyncError
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.serverless.aws.lambda.Route

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.JSConverters._

object AwsJsRouteHandler {

  private def toJsRoute[F[_]](route: Route[F])(implicit monadError: MonadError[F]): JsRoute[F] = { awsJsRequest: AwsJsRequest =>
    monadError.map(route.apply(AwsJsRequest.toAwsRequest(awsJsRequest)))(AwsJsResponse.fromAwsResponse)
  }

  def futureHandler(event: AwsJsRequest, route: Route[Future])(implicit ec: ExecutionContext): scala.scalajs.js.Promise[AwsJsResponse] = {
    implicit val monadError: MonadError[Future] = new FutureMonad()
    val jsRoute = toJsRoute(route)
    jsRoute(event).toJSPromise
  }

  def catsIOHandler(event: AwsJsRequest, route: Route[IO]): scala.scalajs.js.Promise[AwsJsResponse] = {
    implicit val monadError: MonadError[IO] = new CatsMonadAsyncError[IO]()
    val jsRoute = toJsRoute(route)
    jsRoute(event).unsafeToPromise()
  }

  def catsResourceHandler(event: AwsJsRequest, routeR: Resource[IO, Route[IO]]): scala.scalajs.js.Promise[AwsJsResponse] = {
    implicit val monadError: MonadError[IO] = new CatsMonadAsyncError[IO]()
    routeR.use(route => toJsRoute(route).apply(event)).unsafeToPromise()
  }
}
