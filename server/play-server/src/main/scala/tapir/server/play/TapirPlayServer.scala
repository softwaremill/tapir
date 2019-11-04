package tapir.server.play

import java.nio.charset.Charset

import akka.stream.Materializer
import play.BuiltInComponents
import play.api.BuiltInComponents
import play.api.http.HttpEntity
import tapir.{DecodeFailure, DecodeResult, Endpoint, EndpointIO, EndpointInput}
import play.api.mvc._
import play.api.routing.Router
import play.components.BodyParserComponents
import tapir.internal.server.{DecodeInputs, DecodeInputsResult, InputValues}
import tapir.server.{DecodeFailureHandling, ServerDefaults}
import tapir.internal.{SeqToParams, _}
import tapir.model.StatusCodes

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

trait TapirPlayServer {

  implicit class RichPlayServerEndpoint[I, E, O](e: Endpoint[I, E, O, Nothing]) {
    def toRoute(
        logic: I => Future[Either[E, O]]
    )(implicit mat: Materializer, pc: PlayComponents2, serverOptions: PlayServerOptions): PartialFunction[RequestHeader, Handler] = {
      def valuesToResponse(values: DecodeInputsResult.Values): Future[Result] = {
        val i = SeqToParams(InputValues(e.input, values)).asInstanceOf[I]
        logic(i)
          .map {
            case Right(result) => OutputToPlayResponse(ServerDefaults.successStatusCode, e.output, result)
            case Left(err)     => OutputToPlayResponse(ServerDefaults.errorStatusCode, e.errorOutput, err)
          }
      }
      def handleDecodeFailure(
          e: Endpoint[_, _, _, _],
          req: Request[RawBuffer],
          input: EndpointInput.Single[_],
          failure: DecodeFailure
      ): Result = {
        val handling = serverOptions.decodeFailureHandler(req, input, failure)
        handling match {
          case DecodeFailureHandling.NoMatch =>
            serverOptions.loggingOptions.decodeFailureNotHandledMsg(e, failure, input).foreach(println(_))
            Result(header = ResponseHeader(StatusCodes.NotFound), body = HttpEntity.NoEntity)
          case DecodeFailureHandling.RespondWithResponse(output, value) =>
            serverOptions.loggingOptions.decodeFailureHandledMsg(e, failure, input, value).foreach {
              case (msg, Some(t)) => println(s"$msg $t")
              case (msg, None)    => println(msg)
            }

            OutputToPlayResponse(ServerDefaults.errorStatusCode, output, value)
        }
      }

      val res = PartialFunction { requestHeader: RequestHeader =>
        pc.defaultActionBuilder.async(pc.playBodyParsers.raw) { request =>
          decodeBody(request, DecodeInputs(e.input, new PlayDecodeInputContext(request))).flatMap {
            case values: DecodeInputsResult.Values          => valuesToResponse(values)
            case DecodeInputsResult.Failure(input, failure) => Future.successful(handleDecodeFailure(e, request, input, failure))
          }
        }
      }
      res
    }

    def decodeBody(request: Request[RawBuffer], result: DecodeInputsResult)(implicit mat: Materializer): Future[DecodeInputsResult] = {
      result match {
        case values: DecodeInputsResult.Values =>
          values.bodyInput match {
            case Some(bodyInput @ EndpointIO.Body(codec, _)) =>
              PlayRequestToRawBody
                .apply(codec.meta.rawValueType, request.body, request.charset.map(Charset.forName), request)
                .map { rawBody =>
                  val decodeResult = codec.decode(DecodeInputs.rawBodyValueToOption(rawBody, codec.meta.isOptional))
                  decodeResult match {
                    case DecodeResult.Value(bodyV) => values.setBodyInputValue(bodyV)
                    case failure: DecodeFailure    => DecodeInputsResult.Failure(bodyInput, failure): DecodeInputsResult
                  }
                }
            case None => Future(values)
          }
        case failure: DecodeInputsResult.Failure => Future(failure)
      }
    }

  }
}

trait PlayComponents2 {
  def defaultActionBuilder: ActionBuilder[Request, AnyContent]
  def playBodyParsers: PlayBodyParsers
}

object PlayComponents2 {
  def apply[T](implicit mat: Materializer): PlayComponents2 = new PlayComponents2() {
    override def defaultActionBuilder: ActionBuilder[Request, AnyContent] = DefaultActionBuilder.apply(playBodyParsers.anyContent)

    override def playBodyParsers: PlayBodyParsers = PlayBodyParsers.apply()
  }
}
