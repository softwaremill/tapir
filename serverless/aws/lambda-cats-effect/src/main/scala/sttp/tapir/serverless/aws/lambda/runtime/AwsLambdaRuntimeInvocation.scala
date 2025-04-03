package sttp.tapir.serverless.aws.lambda.runtime

import cats.effect.{Resource, Sync}
import cats.syntax.either._
import org.slf4j.LoggerFactory
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import sttp.client4._
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.serverless.aws.lambda.{AwsRequest, AwsResponse, Route}

import scala.concurrent.duration.DurationInt

// loosely based on https://github.com/carpe/scalambda/blob/master/native/src/main/scala/io/carpe/scalambda/native/ScalambdaIO.scala
object AwsLambdaRuntimeInvocation {

  private val logger = LoggerFactory.getLogger(getClass.getName)

  /** Handles the next, single lambda invocation, read from api at `awsRuntimeApiHost` using `backend`, with the given `route`. */
  def handleNext[F[_]: Sync](
      route: Route[F],
      awsRuntimeApiHost: String,
      backend: Resource[F, Backend[F]]
  ): F[Either[Throwable, Unit]] = {
    implicit val monad: MonadError[F] = new CatsMonadError[F]

    val runtimeApiInvocationUri = uri"http://$awsRuntimeApiHost/2018-06-01/runtime/invocation"

    /** Make request (without a timeout as prescribed by the AWS Custom Lambda Runtime documentation). This is due to the possibility of the
      * runtime being frozen between lambda function invocations.
      */
    val nextEventRequest = basicRequest.get(uri"$runtimeApiInvocationUri/next").response(asStringAlways).readTimeout(0.seconds)

    val pollEvent: F[RequestEvent] = {
      logger.info("Fetching request event")
      backend
        .use(nextEventRequest.send(_))
        .flatMap { response =>
          response.header("lambda-runtime-aws-request-id") match {
            case Some(id) => RequestEvent(id, response.body).unit
            case _ =>
              monad.error[RequestEvent](new RuntimeException(s"Missing lambda-runtime-aws-request-id header in request event $response"))
          }
        }
        .handleError { case e => monad.error(new RuntimeException(s"Failed to fetch request event, ${e.getMessage}")) }
    }

    val decodeEvent: RequestEvent => F[AwsRequest] = event => {
      decode[AwsRequest](event.body) match {
        case Right(awsRequest) => awsRequest.unit
        case Left(e)           => monad.error(new RuntimeException(s"Failed to decode request event ${event.requestId}, ${e.getMessage}"))
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
        .handleError { case e => monad.error(new RuntimeException(s"Failed to send response for event ${event.requestId}")) }

    val sendError: (RequestEvent, Throwable) => F[Unit] = (event, e) =>
      backend
        .use { b =>
          basicRequest.post(uri"$runtimeApiInvocationUri/${event.requestId}/error").body(e.getMessage).send(b)
        }
        .map(_ => ())
        .handleError { case e => monad.error(new RuntimeException(s"Failed to send error for event ${event.requestId}")) }

    val sendResult: (RequestEvent, Either[Throwable, AwsResponse]) => F[Unit] = (event, result) =>
      result match {
        case Right(response) =>
          logger.info(s"Request event ${event.requestId} completed successfully")
          sendResponse(event, response)
        case Left(e) =>
          logger.error(s"Request event ${event.requestId} failed", e)
          sendError(event, e)
      }

    (for {
      event <- pollEvent
      request <- decodeEvent(event)
      result <- routeRequest(event, request)
      _ <- sendResult(event, result)
    } yield ().asRight[Throwable]).handleError { case e =>
      logger.error(e.getMessage)
      e.asLeft[Unit].unit
    }
  }
}

case class RequestEvent(requestId: String, body: String)
