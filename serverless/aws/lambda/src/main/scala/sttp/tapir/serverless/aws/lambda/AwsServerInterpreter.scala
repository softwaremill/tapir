package sttp.tapir.serverless.aws.lambda

import cats.data.Kleisli
import cats.effect.Sync
import io.circe.generic.auto._
import io.circe.parser.decode
import sttp.model.StatusCode
import sttp.monad.syntax._
import sttp.tapir.Endpoint
import sttp.tapir.integ.cats.CatsMonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interpreter.{BodyListener, ServerInterpreter}

import java.nio.charset.StandardCharsets

trait AwsServerInterpreter {
  def toRoute[I, E, O, F[_]](e: Endpoint[I, E, O, Any])(
      logic: I => F[Either[E, O]]
  )(implicit serverOptions: AwsServerOptions[F], sync: Sync[F]): Route[F] = toRoute(e.serverLogic(logic))

  def toRoute[I, E, O, F[_]](se: ServerEndpoint[I, E, O, Any, F])(implicit serverOptions: AwsServerOptions[F], sync: Sync[F]): Route[F] =
    toRoute(List(se))

  def toRoute[F[_]](ses: List[ServerEndpoint[_, _, _, Any, F]])(implicit serverOptions: AwsServerOptions[F], sync: Sync[F]): Route[F] = {
    implicit val monad: CatsMonadError[F] = new CatsMonadError[F]
    implicit val bodyListener: BodyListener[F, String] = new AwsBodyListener[F]

    Kleisli { context: LambdaRuntimeContext =>
      val json = new String(context.input.readAllBytes(), StandardCharsets.UTF_8)

      decode[AwsRequest](json) match {
        case Left(error) => AwsResponse(Nil, isBase64Encoded = false, StatusCode.BadRequest.code, Map.empty, error.getMessage).unit
        case Right(awsReq) =>
          implicit val monad: CatsMonadError[F] = new CatsMonadError[F]
          implicit val bodyListener: BodyListener[F, String] = new AwsBodyListener[F]
          val serverRequest = new AwsServerRequest(awsReq)
          val interpreter = new ServerInterpreter[Any, F, String, Nothing](
            new AwsRequestBody[F](awsReq),
            AwsToResponseBody,
            serverOptions.interceptors,
            deleteFile = _ => ().unit // no file support
          )

          interpreter.apply(serverRequest, ses).map {
            case None => AwsResponse(Nil, isBase64Encoded = false, StatusCode.NotFound.code, Map.empty, "")
            case Some(res) =>
              val cookies = res.cookies.collect { case Right(cookie) => cookie.value }.toList
              val headers = res.headers.map(h => h.name -> h.value).toMap
              AwsResponse(cookies, isBase64Encoded = true, res.code.code, headers, res.body.getOrElse(""))
          }
//            .map { awsRes =>
//              val writer = new BufferedWriter(new OutputStreamWriter(context.output, StandardCharsets.UTF_8))
//              writer.write(Printer.noSpaces.print(awsRes.asJson))
//              writer.flush()
//            }
      }
    }
  }
}

object AwsServerInterpreter extends AwsServerInterpreter
