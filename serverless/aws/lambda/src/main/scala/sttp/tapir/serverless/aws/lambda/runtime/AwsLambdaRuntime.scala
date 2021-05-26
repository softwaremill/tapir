package sttp.tapir.serverless.aws.lambda.runtime

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Resource}
import cats.syntax.either._
import com.typesafe.scalalogging.StrictLogging
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import org.http4s.client.blaze.BlazeClientBuilder
import sttp.client3._
import sttp.client3.http4s.Http4sBackend
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

// loosely based on https://github.com/carpe/scalambda/blob/master/native/src/main/scala/io/carpe/scalambda/native/ScalambdaIO.scala
abstract class AwsLambdaRuntime[F[_]] extends StrictLogging {
  implicit def executionContext: ExecutionContext
  implicit def contextShift: ContextShift[F]
  implicit def concurrentEffect: ConcurrentEffect[F]

  implicit def serverOptions: AwsServerOptions[F] = AwsServerOptions.customInterceptors()
  def endpoints: Iterable[ServerEndpoint[_, _, _, Any, F]]

  protected val backend: Resource[F, SttpBackend[F, Any]] = Http4sBackend.usingBlazeClientBuilder(
    BlazeClientBuilder[F](executionContext).withConnectTimeout(0.seconds),
    Blocker.liftExecutionContext(implicitly)
  )

  def main(args: Array[String]): Unit = {
    val route: Route[F] = AwsServerInterpreter.toRoute(endpoints.toList)
    implicit val monad: MonadError[F] = new CatsMonadError[F]

    val runtimeApiInvocationUri = uri"http://${sys.env("AWS_LAMBDA_RUNTIME_API")}/2018-06-01/runtime/invocation"
    val nextEventRequest = basicRequest.get(uri"$runtimeApiInvocationUri/next").response(asStringAlways).readTimeout(0.seconds)

    val pollEvent: F[RequestEvent] = {
      logger.info(s"Fetching request event")
      backend
        .use(nextEventRequest.send(_))
        .flatMap { response =>
          response.header("lambda-runtime-aws-request-id") match {
            case Some(id) => RequestEvent(id, response.body).unit
            case _ =>
              monad.error[RequestEvent](new RuntimeException(s"Missing lambda-runtime-aws-request-id header in request event $response"))
          }
        }
        .handleError { case e =>
          logger.error("Failed to fetch request event", e)
          monad.error(e)
        }
    }

    val decodeEvent: RequestEvent => F[AwsRequest] = event => {
      decode[AwsRequest](event.body) match {
        case Right(awsRequest) => awsRequest.unit
        case Left(e) =>
          logger.error(s"Failed to decode request event ${event.requestId}", e.getCause)
          monad.error(e.getCause)
      }
    }

    val routeRequest: (RequestEvent, AwsRequest) => F[Either[Throwable, AwsResponse]] = (event, request) =>
      route(request).map(_.asRight[Throwable]).handleError { case e =>
        logger.error(s"Failed to process request event ${event.requestId}", e)
        e.asLeft[AwsResponse].unit
      }

    val sendResponse: (RequestEvent, AwsResponse) => F[Unit] = (event, response) =>
      backend
        .use { b =>
          basicRequest
            .post(uri"$runtimeApiInvocationUri/${event.requestId}/response")
            .body(Printer.noSpaces.print(response.asJson))
            .send(b)
        }
        .map(_ => ())

    val sendError: (RequestEvent, Throwable) => F[Unit] = (event, e) =>
      backend
        .use { b =>
          basicRequest.post(uri"$runtimeApiInvocationUri/${event.requestId}/error").body(e.getMessage).send(b)
        }
        .map(_ => ())

    val sendResult: (RequestEvent, Either[Throwable, AwsResponse]) => F[Unit] = (event, result) =>
      result match {
        case Right(response) =>
          logger.info(s"Request event ${event.requestId} completed successfully")
          sendResponse(event, response)
        case Left(e) =>
          logger.error(s"Request event ${event.requestId} failed", e)
          sendError(event, e)
      }

    val loop = for {
      event <- pollEvent
      request <- decodeEvent(event)
      result <- routeRequest(event, request)
      _ <- sendResult(event, result)
    } yield ()

    while (true) ConcurrentEffect[F].toIO(loop).unsafeRunSync()
  }
}

case class RequestEvent(requestId: String, body: String)
