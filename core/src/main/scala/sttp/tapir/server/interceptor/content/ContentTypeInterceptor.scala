package sttp.tapir.server.interceptor.content

import sttp.model.{ContentTypeRange, StatusCode}
import sttp.monad.MonadError
import sttp.tapir.internal._
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.interceptor.{EndpointInterceptor, ValuedEndpointOutput}
import sttp.tapir.{Endpoint, EndpointIO, StreamBodyIO, _}

class ContentTypeInterceptor[F[_], B] extends EndpointInterceptor[F, B] {

  override def onDecodeSuccess[I](
      request: ServerRequest,
      endpoint: Endpoint[I, _, _, _],
      i: I,
      next: Option[ValuedEndpointOutput[_]] => F[ServerResponse[B]]
  )(implicit monad: MonadError[F]): F[ServerResponse[B]] =
    request.acceptsContentTypes match {
      case _ @(Right(Nil) | Right(ContentTypeRange.AnyRange :: Nil)) => next(None)
      case Right(ranges) =>
        val supportedMediaTypes = endpoint.output.traverseOutputs {
          case EndpointIO.Body(bodyType, codec, _) =>
            Vector(charset(bodyType).map(ch => codec.format.mediaType.charset(ch.name())).getOrElse(codec.format.mediaType))
          case EndpointIO.StreamBodyWrapper(StreamBodyIO(_, codec, _, charset)) =>
            Vector(charset.map(ch => codec.format.mediaType.charset(ch.name())).getOrElse(codec.format.mediaType))
        }

        val hasMatchingRepresentation = supportedMediaTypes.exists(mt => ranges.exists(mt.matches))

        if (hasMatchingRepresentation) next(None)
        else next(Some(ValuedEndpointOutput(statusCode(StatusCode.UnsupportedMediaType), ())))

      case Left(_) => next(Some(ValuedEndpointOutput(statusCode(StatusCode.BadRequest), ())))
    }
}
