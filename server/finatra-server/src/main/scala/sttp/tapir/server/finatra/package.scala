package sttp.tapir.server

import java.nio.charset.Charset

import com.github.ghik.silencer.silent
import com.twitter.finagle.http.{Method, Request, Response, Status}
import com.twitter.util.logging.Logging
import com.twitter.util.Future
import sttp.tapir.EndpointInput.{FixedMethod, PathCapture}
import sttp.tapir.internal.{SeqToParams, _}
import sttp.tapir.server.internal.{DecodeInputs, DecodeInputsResult, InputValues, InputValuesResult}
import sttp.tapir.{DecodeResult, Endpoint, EndpointIO, EndpointInput}

import scala.reflect.ClassTag
import scala.util.control.NonFatal

package object finatra {
  implicit class RichFinatraEndpoint[I, E, O](e: Endpoint[I, E, O, Nothing]) extends Logging {
    def toRoute(logic: I => Future[Either[E, O]])(implicit serverOptions: FinatraServerOptions): FinatraRoute = {
      val handler = { request: Request =>
        def decodeBody(result: DecodeInputsResult): Future[DecodeInputsResult] = {
          result match {
            case values: DecodeInputsResult.Values =>
              values.bodyInput match {
                case Some(bodyInput @ EndpointIO.Body(bodyType, codec, _)) =>
                  new FinatraRequestToRawBody(serverOptions)
                    .apply(bodyType, request.content, request.charset.map(Charset.forName), request)
                    .map { rawBody =>
                      val decodeResult = codec.decode(rawBody)
                      decodeResult match {
                        case DecodeResult.Value(bodyV)     => values.setBodyInputValue(bodyV)
                        case failure: DecodeResult.Failure => DecodeInputsResult.Failure(bodyInput, failure): DecodeInputsResult
                      }
                    }
                case None => Future.value(values)
              }
            case failure: DecodeInputsResult.Failure => Future.value(failure)
          }
        }

        def valuesToResponse(values: List[Any]): Future[Response] = {
          val i = SeqToParams(values).asInstanceOf[I]

          logic(i)
            .map {
              case Right(result) => OutputToFinatraResponse(Status(ServerDefaults.StatusCodes.success.code), e.output, result)
              case Left(err)     => OutputToFinatraResponse(Status(ServerDefaults.StatusCodes.error.code), e.errorOutput, err)
            }
            .map { result =>
              serverOptions.logRequestHandling.requestHandled(e, result.statusCode)
              result
            }
            .onFailure {
              case NonFatal(ex) =>
                serverOptions.logRequestHandling.logicException(e, ex)
                error(ex)
            }
        }

        def handleDecodeFailure(
            e: Endpoint[_, _, _, _],
            input: EndpointInput[_],
            failure: DecodeResult.Failure
        ): Response = {
          val decodeFailureCtx = DecodeFailureContext(input, failure)
          val handling = serverOptions.decodeFailureHandler(decodeFailureCtx)

          handling match {
            case DecodeFailureHandling.NoMatch =>
              serverOptions.logRequestHandling.decodeFailureNotHandled(e, decodeFailureCtx)
              Response(Status.BadRequest)
            case DecodeFailureHandling.RespondWithResponse(output, value) =>
              serverOptions.logRequestHandling.decodeFailureHandled(e, decodeFailureCtx, value)
              OutputToFinatraResponse(Status(ServerDefaults.StatusCodes.error.code), output, value)
          }
        }

        decodeBody(DecodeInputs(e.input, new FinatraDecodeInputsContext(request))).flatMap {
          case values: DecodeInputsResult.Values =>
            InputValues(e.input, values) match {
              case InputValuesResult.Values(values, _)       => valuesToResponse(values)
              case InputValuesResult.Failure(input, failure) => Future.value(handleDecodeFailure(e, input, failure))
            }
          case DecodeInputsResult.Failure(input, failure) => Future.value(handleDecodeFailure(e, input, failure))
        }
      }

      FinatraRoute(handler, httpMethod(e), path(e.input))
    }

    @silent("never used")
    def toRouteRecoverErrors(logic: I => Future[O])(
        implicit eIsThrowable: E <:< Throwable,
        eClassTag: ClassTag[E]
    ): FinatraRoute = {
      e.toRoute { i: I =>
        logic(i).map(Right(_)).handle {
          case ex if eClassTag.runtimeClass.isInstance(ex) => Left(ex.asInstanceOf[E])
        }
      }
    }
  }

  private[finatra] def path(input: EndpointInput[_]): String = {
    val p = input
      .asVectorOfBasicInputs()
      .collect {
        case segment: EndpointInput.FixedPath[_] => segment.show
        case PathCapture(Some(name), _, _)       => s"/:$name"
        case PathCapture(_, _, _)                => "/:param"
        case EndpointInput.PathsCapture(_, _)    => "/:*"
      }
      .mkString
    if (p.isEmpty) "/:*" else p
  }

  private[finatra] def httpMethod(endpoint: Endpoint[_, _, _, _]): Method = {
    endpoint.input
      .asVectorOfBasicInputs()
      .collectFirst {
        case FixedMethod(m, _, _) => Method(m.method)
      }
      .getOrElse(Method("ANY"))
  }
}
