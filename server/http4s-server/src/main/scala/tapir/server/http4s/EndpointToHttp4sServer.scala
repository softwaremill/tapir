package tapir.server.http4s

import cats.data._
import cats.effect.{ContextShift, Sync}
import cats.implicits._
import org.http4s.{EntityBody, HttpRoutes, Request, Response, Status}
import tapir.internal.SeqToParams
import tapir.internal.server.{DecodeInputs, DecodeInputsResult, InputValues}
import tapir.server.{DecodeFailureHandling, ServerDefaults, ServerEndpoint}
import tapir.{DecodeFailure, DecodeResult, Endpoint, EndpointIO, EndpointInput, EndpointOutput}
import org.log4s._

import scala.reflect.ClassTag

class EndpointToHttp4sServer[F[_]: Sync: ContextShift](serverOptions: Http4sServerOptions[F]) {
  private val log = getLogger

  def toRoutes[I, E, O](se: ServerEndpoint[I, E, O, EntityBody[F], F]): HttpRoutes[F] = {

    val service: HttpRoutes[F] = HttpRoutes[F] { req: Request[F] =>
      def decodeBody(result: DecodeInputsResult): F[DecodeInputsResult] = {
        result match {
          case values: DecodeInputsResult.Values =>
            values.bodyInput match {
              case Some(bodyInput @ EndpointIO.Body(codec, _)) =>
                new Http4sRequestToRawBody(serverOptions).apply(req.body, codec.meta.rawValueType, req.charset, req).map { v =>
                  codec.safeDecode(Some(v)) match {
                    case DecodeResult.Value(bodyV) => values.value(bodyInput, bodyV)
                    case failure: DecodeFailure    => DecodeInputsResult.Failure(bodyInput, failure): DecodeInputsResult
                  }
                }

              case None => (values: DecodeInputsResult).pure[F]
            }
          case failure: DecodeInputsResult.Failure => (failure: DecodeInputsResult).pure[F]
        }
      }

      def valuesToResponse(values: DecodeInputsResult.Values): F[Response[F]] = {
        val i = SeqToParams(InputValues(se.endpoint.input, values.values)).asInstanceOf[I]
        se.logic(i)
          .map {
            case Right(result) =>
              makeResponse(Status.Ok, se.endpoint.output, result)
            case Left(err) =>
              makeResponse(
                statusCodeToHttp4sStatus(ServerDefaults.errorStatusCode),
                se.endpoint.errorOutput,
                err
              )
          }
          .map { response =>
            serverOptions.loggingOptions.requestHandledMsg(se.endpoint, response.status.code).foreach(log.debug(_))
            response
          }
          .onError {
            case e: Exception =>
              implicitly[Sync[F]].delay(serverOptions.loggingOptions.logicExceptionMsg(se.endpoint).foreach(log.error(e)(_)))
          }
      }

      OptionT(decodeBody(DecodeInputs(se.endpoint.input, new Http4sDecodeInputsContext[F](req))).flatMap {
        case values: DecodeInputsResult.Values          => valuesToResponse(values).map(_.some)
        case DecodeInputsResult.Failure(input, failure) => handleDecodeFailure(se.endpoint, req, input, failure).pure[F]
      })
    }

    service
  }

  def toRoutesRecoverErrors[I, E, O](e: Endpoint[I, E, O, EntityBody[F]])(logic: I => F[O])(
      implicit serverOptions: Http4sServerOptions[F],
      eIsThrowable: E <:< Throwable,
      eClassTag: ClassTag[E]
  ): HttpRoutes[F] = {
    def reifyFailedF(f: F[O]): F[Either[E, O]] = {
      f.map(Right(_): Either[E, O]).recover {
        case e: Throwable if eClassTag.runtimeClass.isInstance(e) => Left(e.asInstanceOf[E]): Either[E, O]
      }
    }

    toRoutes(e.serverLogic(logic.andThen(reifyFailedF)))
  }

  def toRoutes[I, E, O](serverEndpoints: List[ServerEndpoint[_, _, _, EntityBody[F], F]]): HttpRoutes[F] = {
    NonEmptyList.fromList(serverEndpoints.map(se => toRoutes(se))) match {
      case Some(routes) => routes.reduceK
      case None         => HttpRoutes.empty
    }
  }

  private def statusCodeToHttp4sStatus(code: tapir.model.StatusCode): Status =
    Status.fromInt(code).right.getOrElse(throw new IllegalArgumentException(s"Invalid status code: $code"))

  private def makeResponse[O](defaultStatusCode: org.http4s.Status, output: EndpointOutput[O], v: O): Response[F] = {
    val responseValues = new OutputToHttp4sResponse[F](serverOptions).apply(output, v)
    val statusCode = responseValues.statusCode.map(statusCodeToHttp4sStatus).getOrElse(defaultStatusCode)

    val headers = responseValues.allHeaders
    responseValues.body match {
      case Some(entity) => Response(status = statusCode, headers = headers, body = entity)
      case None         => Response(status = statusCode, headers = headers)
    }
  }

  private def handleDecodeFailure[I](
      e: Endpoint[_, _, _, _],
      req: Request[F],
      input: EndpointInput.Single[_],
      failure: DecodeFailure
  ): Option[Response[F]] = {
    val handling = serverOptions.decodeFailureHandler(req, input, failure)
    handling match {
      case DecodeFailureHandling.NoMatch =>
        serverOptions.loggingOptions.decodeFailureNotHandledMsg(e, failure, input).foreach(log.debug(_))
        None
      case DecodeFailureHandling.RespondWithResponse(output, value) =>
        serverOptions.loggingOptions.decodeFailureHandledMsg(e, failure, input, value).foreach {
          case (msg, Some(t)) => log.debug(t)(msg)
          case (msg, None)    => log.debug(msg)
        }

        Some(
          makeResponse(
            statusCodeToHttp4sStatus(ServerDefaults.errorStatusCode),
            output,
            value
          )
        )
    }
  }
}
