package tapir.server
import java.nio.charset.Charset

import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.inject.Logging
import com.twitter.util.Future
import tapir.DecodeResult.{Error, Mismatch, Missing, Multiple}
import tapir.internal.{SeqToParams, _}
import tapir.internal.server.{DecodeInputs, DecodeInputsResult, InputValues}
import tapir.{DecodeFailure, DecodeResult, Endpoint, EndpointIO, EndpointInput}

import scala.reflect.ClassTag
import scala.util.control.NonFatal

package object finatra {
  implicit class RichFinatraEndpoint[I, E, O](e: Endpoint[I, E, O, Nothing]) extends Logging {
    def toRoute(logic: I => Future[Either[E, O]]): FinatraRoute = {

      val handler = { request: Request =>
        def decodeBody(result: DecodeInputsResult): DecodeInputsResult = {
          result match {
            case values: DecodeInputsResult.Values =>
              values.bodyInput match {
                case Some(bodyInput @ EndpointIO.Body(codec, _)) =>
                  val rawBody =
                    FinatraRequestToRawBody(codec.meta.rawValueType, request.content, request.charset.map(Charset.forName), request)

                  codec.safeDecode(Some(rawBody)) match {
                    case DecodeResult.Value(bodyV) => values.value(bodyInput, bodyV)
                    case failure: DecodeFailure    => DecodeInputsResult.Failure(bodyInput, failure): DecodeInputsResult
                  }
                case None => values
              }
            case failure: DecodeInputsResult.Failure => failure
          }
        }

        def valuesToResponse(values: DecodeInputsResult.Values): Future[Response] = {
          val i = SeqToParams(InputValues(e.input, values.values)).asInstanceOf[I]
          logic(i)
            .map {
              case Right(result) => OutputToFinatraResponse(e.output, result).toResponse
              case Left(err)     => OutputToFinatraResponse(e.errorOutput, err, None, Status.BadRequest).toResponse
            }
            .onFailure {
              case NonFatal(e) =>
                error(e)
            }
        }

        def handleDecodeFailure(
            e: Endpoint[_, _, _, _],
            req: Request,
            input: EndpointInput.Single[_],
            failure: DecodeFailure
        ): Future[Response] = {
          failure match {
            case Missing                    => error(s"No decode failure message")
            case Multiple(errs: Seq[_])     => errs.foreach(e => error(s"DecodeError: $e"))
            case Error(original, e)         => error(s"DecodeError: original: $original", e)
            case Mismatch(expected, actual) => error(s"Expected: $expected Actual: $actual")
          }
          Future.value(Response(Status.BadRequest))
        }

        decodeBody(DecodeInputs(e.input, new FinatraDecodeInputsContext(request))) match {
          case values: DecodeInputsResult.Values          => valuesToResponse(values)
          case DecodeInputsResult.Failure(input, failure) => handleDecodeFailure(e, request, input, failure)
        }
      }

      FinatraRoute(handler, e.input.path)
    }

    def toRouteRecoverErrors(logic: I => Future[O])(
        implicit eIsThrowable: E <:< Throwable,
        eClassTag: ClassTag[E]
    ): FinatraRoute = {
      e.toRoute { i: I =>
        logic(i).map(Right(_)).handle {
          case ex if eClassTag.runtimeClass.isInstance(ex) => Left(e.asInstanceOf[E])
        }
      }
    }
  }
}
