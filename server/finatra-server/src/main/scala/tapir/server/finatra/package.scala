package tapir.server
import com.twitter.finagle.http.{Request, Response}
import com.twitter.inject.Logging
import com.twitter.util.Future
import tapir.{DecodeFailure, DecodeResult, Endpoint, EndpointIO, EndpointInput}
import tapir.internal.SeqToParams
import tapir.internal.server.{DecodeInputs, DecodeInputsResult, InputValues}
import tapir.internal._

package object finatra {
  implicit class RichFinatraEndpoint[I, E, O](e: Endpoint[I, E, O, Nothing]) extends Logging {
    def toRoute(logic: I => Future[Either[E, O]]): FinatraRoute = {

      val handler = { request: Request =>
        def decodeBody(result: DecodeInputsResult): DecodeInputsResult = {
          result match {
            case values: DecodeInputsResult.Values =>
              values.bodyInput match {
                case Some(bodyInput @ EndpointIO.Body(codec, _)) =>
                  val rawBody = FinatraRequestToRawBody(codec.meta.rawValueType, request)

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
              case Right(result) => OutputToFinatraResponse(e.output, result)
              case Left(_)       => ???
            }
            .onFailure {
              case e: Exception =>
                error(e)
            }
        }

        def handleDecodeFailure[I](
            e: Endpoint[_, _, _, _],
            req: Request,
            input: EndpointInput.Single[_],
            failure: DecodeFailure
        ): Future[Response] = ???

        decodeBody(DecodeInputs(e.input, new FinatraDecodeInputsContext(request))) match {
          case values: DecodeInputsResult.Values          => valuesToResponse(values)
          case DecodeInputsResult.Failure(input, failure) => handleDecodeFailure(e, request, input, failure)
        }
      }

      FinatraRoute(handler, e.input.path)
    }
  }
}
