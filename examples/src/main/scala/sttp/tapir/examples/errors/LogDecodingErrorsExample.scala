package sttp.tapir.examples.errors

import cats.Applicative
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Sync
import cats.syntax.all._
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import io.circe.Codec
import io.circe.generic.semiauto._
import org.http4s.ember.server.EmberServerBuilder
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interceptor.DecodeSuccessContext
import sttp.tapir.server.interceptor.SecurityFailureContext
import sttp.tapir.server.interceptor.log.ExceptionContext
import sttp.tapir.server.interceptor.log.ServerLog
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.stringBody

final case class Person(
    name: String
)
object Person {
  implicit val personC: Codec[Person] = deriveCodec
  implicit val personS: Schema[Person] = Schema.derived[Person]
}

object LogDecodingErrorsExample extends IOApp.Simple {
  val greetingEndpoint = endpoint.post
    .in("greeting")
    .in(jsonBody[Person])
    .out(htmlBodyUtf8)
    .errorOut(stringBody)
    .description("greeting endpoint")

  val allEndpoints: List[ServerEndpoint[Any, IO]] = List(
    greetingEndpoint.serverLogic { case Person(name) =>
      Right(s"Hello $name").pure[IO]
    }
  )

  val options = Http4sServerOptions
    .customiseInterceptors[IO]
    .serverLog(new DecodingFailureServerLog[IO](new Log[IO]))
    .options
  val routes = Http4sServerInterpreter[IO](options).toRoutes(allEndpoints)

  val server = EmberServerBuilder
    .default[IO]
    .withHostOption(Host.fromString("0.0.0.0"))
    .withPort(Port.fromInt(8080).get)
    .withHttpApp(routes.orNotFound)
    .build

  override def run: IO[Unit] =
    server.use(_ => IO.never)
}

class Log[F[_]: Sync] {
  def error(msg: String) = Sync[F].delay(println(msg))
}

/** Tapir's ServerLog implementation which logs decoding failures and does nothing else. */
class DecodingFailureServerLog[F[_]: Applicative](log: Log[F]) extends ServerLog[F] {

  override type TOKEN = Long

  override def requestToken: TOKEN = 0

  override def decodeFailureNotHandled(
      ctx: DecodeFailureContext,
      token: TOKEN
  ): F[Unit] = logDecodeFailure(ctx)

  override def decodeFailureHandled(
      ctx: DecodeFailureContext,
      response: ServerResponse[_],
      token: TOKEN
  ): F[Unit] = logDecodeFailure(ctx)

  private def logDecodeFailure(
      ctx: DecodeFailureContext
  ): F[Unit] =
    ctx.failure match {
      // Lets do a custom message for DecodeResult.Error.JsonDecodeException
      case DecodeResult.Error(
            original,
            DecodeResult.Error.JsonDecodeException(errors, _)
          ) =>
        val input = original.filterNot(char => char.isWhitespace)
        val msg =
          s"Request: ${ctx.request.showShort}, not handled by: ${ctx.endpoint.showShort}; decode failure for input: $input. Error: $errors"
        log.error(msg)

      // otherwise just log whatever we have
      case error =>
        val msg =
          s"Request: ${ctx.request.showShort}, not handled by: ${ctx.endpoint.showShort}; decode failure: ${ctx.failure}. Error: $error"
        log.error(msg)
    }

  /*
    ###############
      no-ops, we're not interested in logging different things.
    ###############
   */

  override def requestReceived(request: ServerRequest, token: Long): F[Unit] =
    Applicative[F].unit

  override def securityFailureHandled(
      ctx: SecurityFailureContext[F, _],
      response: ServerResponse[_],
      token: TOKEN
  ): F[Unit] = Applicative[F].unit

  override def requestHandled(
      ctx: DecodeSuccessContext[F, _, _, _],
      response: ServerResponse[_],
      token: TOKEN
  ): F[Unit] = Applicative[F].unit

  override def exception(
      ctx: ExceptionContext[_, _],
      ex: Throwable,
      token: TOKEN
  ): F[Unit] = Applicative[F].unit
}
