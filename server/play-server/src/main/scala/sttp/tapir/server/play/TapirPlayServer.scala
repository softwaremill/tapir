package sttp.tapir.server.play

import java.nio.charset.Charset

import akka.stream.Materializer
import akka.util.ByteString
import play.api.http.HttpEntity
import play.api.mvc._
import play.api.routing.Router.Routes
import sttp.tapir.internal.SeqToParams
import sttp.tapir.server.internal.{DecodeInputs, DecodeInputsResult, InputValues, InputValuesResult}
import sttp.tapir.server.ServerDefaults.StatusCodes
import sttp.tapir.server.{DecodeFailureContext, DecodeFailureHandling, ServerDefaults}
import sttp.tapir.{DecodeResult, Endpoint, EndpointIO, EndpointInput}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag

trait TapirPlayServer {
  implicit class RichPlayServerEndpoint[I, E, O](e: Endpoint[I, E, O, Nothing]) {
    def toRoute(
        logic: I => Future[Either[E, O]]
    )(implicit mat: Materializer, serverOptions: PlayServerOptions): Routes = {
      def valuesToResponse(values: Seq[Any]): Future[Result] = {
        val i = SeqToParams(values).asInstanceOf[I]
        logic(i)
          .map {
            case Right(result) => OutputToPlayResponse(ServerDefaults.StatusCodes.success, e.output, result)
            case Left(err)     => OutputToPlayResponse(ServerDefaults.StatusCodes.error, e.errorOutput, err)
          }
      }
      def handleDecodeFailure(
          e: Endpoint[_, _, _, _],
          input: EndpointInput[_],
          failure: DecodeResult.Failure
      ): Result = {
        val decodeFailureCtx = DecodeFailureContext(input, failure)
        val handling = serverOptions.decodeFailureHandler(decodeFailureCtx)
        handling match {
          case DecodeFailureHandling.NoMatch =>
            serverOptions.logRequestHandling.decodeFailureNotHandled(e, decodeFailureCtx)(serverOptions.logger)
            Result(header = ResponseHeader(StatusCodes.error.code), body = HttpEntity.NoEntity)
          case DecodeFailureHandling.RespondWithResponse(output, value) =>
            serverOptions.logRequestHandling.decodeFailureNotHandled(e, decodeFailureCtx)(serverOptions.logger)
            OutputToPlayResponse(ServerDefaults.StatusCodes.error, output, value)
        }
      }

      def decodeBody(request: Request[RawBuffer], result: DecodeInputsResult)(implicit mat: Materializer): Future[DecodeInputsResult] = {
        result match {
          case values: DecodeInputsResult.Values =>
            values.bodyInput match {
              case Some(bodyInput @ EndpointIO.Body(bodyType, codec, _)) =>
                new PlayRequestToRawBody(serverOptions)
                  .apply(
                    bodyType,
                    request.charset.map(Charset.forName),
                    request,
                    request.body.asBytes().getOrElse(ByteString.apply(java.nio.file.Files.readAllBytes(request.body.asFile.toPath)))
                  )
                  .map { rawBody =>
                    codec.decode(rawBody) match {
                      case DecodeResult.Value(bodyV)     => values.setBodyInputValue(bodyV)
                      case failure: DecodeResult.Failure => DecodeInputsResult.Failure(bodyInput, failure): DecodeInputsResult
                    }
                  }
              case None => Future(values)
            }
          case failure: DecodeInputsResult.Failure => Future(failure)
        }
      }

      val res = new PartialFunction[RequestHeader, Handler] {
        override def isDefinedAt(x: RequestHeader): Boolean = {
          val decodeInputResult = DecodeInputs(e.input, new PlayDecodeInputContext(x, 0, serverOptions))
          val handlingResult = decodeInputResult match {
            case DecodeInputsResult.Failure(input, failure) =>
              val decodeFailureCtx = DecodeFailureContext(input, failure)
              serverOptions.logRequestHandling.decodeFailureNotHandled(e, decodeFailureCtx)(serverOptions.logger)
              serverOptions.decodeFailureHandler(decodeFailureCtx) != DecodeFailureHandling.noMatch
            case DecodeInputsResult.Values(_, _) => true
          }
          handlingResult
        }

        override def apply(v1: RequestHeader): Handler = {
          serverOptions.defaultActionBuilder.async(serverOptions.playBodyParsers.raw) { request =>
            decodeBody(request, DecodeInputs(e.input, new PlayDecodeInputContext(v1, 0, serverOptions))).flatMap {
              case values: DecodeInputsResult.Values =>
                InputValues(e.input, values) match {
                  case InputValuesResult.Values(values, _)       => valuesToResponse(values)
                  case InputValuesResult.Failure(input, failure) => Future.successful(handleDecodeFailure(e, input, failure))
                }
              case DecodeInputsResult.Failure(input, failure) =>
                Future.successful(handleDecodeFailure(e, input, failure))
            }
          }
        }
      }
      res
    }

    def toRouteRecoverErrors(logic: I => Future[O])(
        implicit eIsThrowable: E <:< Throwable,
        eClassTag: ClassTag[E],
        mat: Materializer,
        serverOptions: PlayServerOptions
    ): Routes = {
      e.toRoute { i: I =>
        logic(i).map(Right(_)).recover {
          case ex if eClassTag.runtimeClass.isInstance(ex) => Left(ex.asInstanceOf[E])
        }
      }
    }
  }
}
