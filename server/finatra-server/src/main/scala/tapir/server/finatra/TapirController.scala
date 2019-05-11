package tapir.server.finatra
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.Controller
import com.twitter.util.Future
import tapir.internal._
import tapir.internal.server.{DecodeInputs, DecodeInputsResult, InputValues}
import tapir.{DecodeFailure, DecodeResult, Endpoint, EndpointIO, EndpointInput}

trait TapirController { self: Controller =>
  implicit class RichFinatraEndpoint[I, E, O](e: Endpoint[I, E, O, Nothing]) {
    def toRoute(logic: I => Future[Either[E, O]]): Unit = {

      any(e.input.path) { request: Request =>
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
        ): Option[Response] = ???

        decodeBody(DecodeInputs(e.input, new FinatraDecodeInputsContext(request))) match {
          case values: DecodeInputsResult.Values          => valuesToResponse(values)
          case DecodeInputsResult.Failure(input, failure) => handleDecodeFailure(e, request, input, failure)
        }
      }
    }
  }

}
