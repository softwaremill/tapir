package tapir.server.http4s

import cats.data._
import cats.effect.{ContextShift, Sync}
import cats.implicits._
import org.http4s.{EntityBody, Headers, HttpRoutes, Request, Response, Status}
import tapir.internal.SeqToParams
import tapir.internal.server.{DecodeInputs, DecodeInputsResult, InputValues}
import tapir.model.Method
import tapir.server.{DecodeFailureHandling, StatusMapper}
import tapir.typelevel.ParamsAsArgs
import tapir.{DecodeFailure, DecodeResult, Endpoint, EndpointIO, EndpointInput}

class EndpointToHttp4sServer[F[_]: Sync: ContextShift](serverOptions: Http4sServerOptions[F]) {

  def toRoutes[I, E, O, FN[_]](e: Endpoint[I, E, O, EntityBody[F]])(
      logic: FN[F[Either[E, O]]],
      statusMapper: StatusMapper[O],
      errorStatusMapper: StatusMapper[E])(implicit paramsAsArgs: ParamsAsArgs.Aux[I, FN]): HttpRoutes[F] = {

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
        paramsAsArgs
          .applyFn(logic, i)
          .map {
            case Right(result) =>
              makeResponse(statusCodeToHttp4sStatus(statusMapper(result)), e.output, result)
            case Left(err) =>
              makeResponse(statusCodeToHttp4sStatus(errorStatusMapper(err)), e.errorOutput, err)
          }
      }

      val methodMatches = http4sMethodToTapirMethodMap.get(req.method).contains(e.method)

      if (methodMatches) {
        OptionT(decodeBody(DecodeInputs(e.input, new Http4sDecodeInputsContext[F](req))).flatMap {
          case values: DecodeInputsResult.Values          => valuesToResponse(values).map(_.some)
          case DecodeInputsResult.Failure(input, failure) => handleDecodeFailure(req, input, failure).pure[F]
        })
      } else {
        OptionT.none
      }
    }

    service
  }

  private val http4sMethodToTapirMethodMap: Map[org.http4s.Method, Method] = {
    import org.http4s.Method._
    import tapir.model.Method
    Map(
      GET -> Method.GET,
      POST -> Method.POST,
      DELETE -> Method.DELETE,
      PUT -> Method.PUT,
      OPTIONS -> Method.OPTIONS,
      PATCH -> Method.PATCH,
      CONNECT -> Method.CONNECT
    )
  }

  private def statusCodeToHttp4sStatus(code: tapir.StatusCode): Status =
    Status.fromInt(code).right.getOrElse(throw new IllegalArgumentException(s"Invalid status code: $code"))

  private def makeResponse[O](statusCode: org.http4s.Status, output: EndpointIO[O], v: O): Response[F] = {
    val responseValues = new OutputToHttp4sResponse[F](serverOptions).apply(output, v)

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
