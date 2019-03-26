package tapir.server.http4s

import cats.data._
import cats.effect.{ContextShift, Sync}
import cats.implicits._
import org.http4s.{EntityBody, Headers, HttpRoutes, Request, Response, Status}
import tapir.internal.SeqToParams
import tapir.internal.server.{DecodeInputs, DecodeInputsResult, InputValues}
import tapir.server.{DecodeFailureHandling, ServerEndpoint}
import tapir.{DecodeFailure, DecodeResult, Endpoint, EndpointIO, EndpointInput, EndpointOutput}

class EndpointToHttp4sServer[F[_]: Sync: ContextShift](serverOptions: Http4sServerOptions[F]) {

  def toRoutes[I, E, O](se: ServerEndpoint[I, E, O, EntityBody[F], F]): HttpRoutes[F] = toRoutes(se.endpoint)(se.logic)

  def toRoutes[I, E, O](e: Endpoint[I, E, O, EntityBody[F]])(logic: I => F[Either[E, O]]): HttpRoutes[F] = {

    val service: HttpRoutes[F] = HttpRoutes[F] { req: Request[F] =>
      def decodeBody(result: DecodeInputsResult): F[DecodeInputsResult] = {
        result match {
          case values: DecodeInputsResult.Values =>
            values.bodyInput match {
              case Some(bodyInput @ EndpointIO.Body(codec, _)) =>
                new Http4sRequestToRawBody(serverOptions).apply(req.body, codec.meta.rawValueType, req.charset, req).map { v =>
                  codec.decode(Some(v)) match {
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
        val i = SeqToParams(InputValues(e.input, values.values)).asInstanceOf[I]
        logic(i).map {
          case Right(result) =>
            makeResponse(Status.Ok, e.output, result)
          case Left(err) =>
            makeResponse(Status.BadRequest, e.errorOutput, err)
        }
      }

      OptionT(decodeBody(DecodeInputs(e.input, new Http4sDecodeInputsContext[F](req))).flatMap {
        case values: DecodeInputsResult.Values          => valuesToResponse(values).map(_.some)
        case DecodeInputsResult.Failure(input, failure) => handleDecodeFailure(req, input, failure).pure[F]
      })
    }

    service
  }

  private def statusCodeToHttp4sStatus(code: tapir.StatusCode): Status =
    Status.fromInt(code).right.getOrElse(throw new IllegalArgumentException(s"Invalid status code: $code"))

  private def makeResponse[O](defaultStatusCode: org.http4s.Status, output: EndpointOutput[O], v: O): Response[F] = {
    val responseValues = new OutputToHttp4sResponse[F](serverOptions).apply(output, v)
    val statusCode = responseValues.statusCode.map(statusCodeToHttp4sStatus).getOrElse(defaultStatusCode)

    val headers = Headers(responseValues.headers: _*)
    responseValues.body match {
      case Some(entity) => Response(status = statusCode, headers = headers, body = entity)
      case None         => Response(status = statusCode, headers = headers)
    }
  }

  private def handleDecodeFailure[I](req: Request[F], input: EndpointInput.Single[_], failure: DecodeFailure): Option[Response[F]] = {
    val handling = serverOptions.decodeFailureHandler(req, input, failure)
    handling match {
      case DecodeFailureHandling.NoMatch => None
      case DecodeFailureHandling.RespondWithResponse(statusCode, body, codec) =>
        val (entity, header) = new OutputToHttp4sResponse(serverOptions).rawValueToEntity(codec.meta, codec.encode(body))
        Some(Response(status = statusCodeToHttp4sStatus(statusCode), headers = Headers(header), body = entity))
    }
  }
}
