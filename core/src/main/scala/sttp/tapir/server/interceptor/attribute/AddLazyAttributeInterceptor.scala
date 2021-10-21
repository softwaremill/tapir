package sttp.tapir.server.interceptor.attribute

import sttp.monad.MonadError
import sttp.tapir.internal._
import sttp.tapir.model.{AttributeKey, ServerRequest, ServerResponse}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.{DecodeResult, Endpoint, EndpointInput, EndpointOutput}
import sttp.tapir.server.interceptor.{
  BeforeDecodeResult,
  DecodeFailureContext,
  DecodeSuccessContext,
  EndpointHandler,
  EndpointInterceptor,
  RequestHandler,
  RequestInterceptor,
  RequestResult,
  Responder,
  ValuedEndpointOutput
}
import sttp.tapir.server.interpreter.{BodyListener, DecodeBasicInputs, DecodeBasicInputsResult, InputValue, InputValueResult}
import sttp.monad.syntax._

import java.util.concurrent.atomic.AtomicReference
import scala.reflect.ClassTag

class AddLazyAttributeInterceptor[F[_], I, E, O: ClassTag](
    input: EndpointInput[I],
    errorOutput: EndpointOutput[E],
    compute: I => F[Either[E, O]]
) extends RequestInterceptor[F] {
  private val oKey = AttributeKey[O]

  override def apply[B](responder: Responder[F, B], requestHandler: EndpointInterceptor[F] => RequestHandler[F, B]): RequestHandler[F, B] =
    new RequestHandler[F, B] {
      override def apply(request: ServerRequest)(implicit monad: MonadError[F]): F[RequestResult[B]] = {
        // The cell lazily computes (on first access) the result of running the computation wrapped in a Some(_) if the
        // input can be decoded, and None otherwise.
        val cell = new Cell[F, Option[Either[E, O]]](() => {
          extract(request, input) match {
            case DecodeResult.Value(i)   => compute(i).map(Some(_))
            case _: DecodeResult.Failure =>
              // If an input can't be decoded, the attribute will be necessarily missing. To get a proper response
              // via the decode failure handler, invoked through .onDecodeFailure of endpoint interceptors, the input
              // should be repeated in the endpoint before the attribute-extracting input. But this should happen anyway
              // to get proper documentation.
              (None: Option[Either[E, O]]).unit
          }
        })

        requestHandler(new AddAttributeFromCell(cell, oKey, errorOutput)).apply(request)
      }
    }

  private def extract(request: ServerRequest, input: EndpointInput[I]): DecodeResult[I] = {
    DecodeBasicInputs(input, request) match {
      case values @ DecodeBasicInputsResult.Values(_, None) =>
        InputValue(input, values) match {
          case InputValueResult.Value(params, _)    => DecodeResult.Value(params.asAny.asInstanceOf[I])
          case InputValueResult.Failure(_, failure) => failure
        }
      case DecodeBasicInputsResult.Values(_, Some(_)) =>
        throw new IllegalArgumentException("Bodies can't be decoded while computing an attribute")
      case DecodeBasicInputsResult.Failure(_, failure) => failure
    }

  }
}

object AddLazyAttributeInterceptor {
  def apply[F[_], I, E, O: ClassTag](
      input: EndpointInput[I],
      errorOutput: EndpointOutput[E],
      compute: I => F[Either[E, O]]
  ) = ???
}

private[attribute] class Cell[F[_]: MonadError, O](compute: () => F[O]) {
  private val storage = new AtomicReference[O]()
  def resolve(): F[O] = {
    val v = storage.get()
    if (v == null) {
      compute().map { o =>
        storage.set(o)
        o
      }
    } else v.unit
  }
}

class AddAttributeFromCell[F[_], I, E, O: ClassTag](
    cell: Cell[F, Option[Either[E, O]]],
    oKey: AttributeKey[O],
    errorOutput: EndpointOutput[E]
) extends EndpointInterceptor[F] {
  override def apply[B](responder: Responder[F, B], endpointHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def beforeDecode(request: ServerRequest, serverEndpoint: ServerEndpoint[_, _, _, _, F])(implicit
          monad: MonadError[F]
      ): F[BeforeDecodeResult[F, B]] = {
        val attributes = endpointExtractsAttributes(serverEndpoint.endpoint) // TODO: cache
        if (attributes.contains(oKey)) {
          cell.resolve().flatMap {
            case None           => endpointHandler.beforeDecode(request, serverEndpoint)
            case Some(Left(e))  => responder(request, ValuedEndpointOutput(errorOutput, e)).map(BeforeDecodeResult.Response(_))
            case Some(Right(o)) => endpointHandler.beforeDecode(request.withAttribute(oKey, o), serverEndpoint)
          }
        } else {
          endpointHandler.beforeDecode(request, serverEndpoint)
        }
      }

      override def onDecodeSuccess[II](
          ctx: DecodeSuccessContext[F, II]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] =
        endpointHandler.onDecodeSuccess(ctx)

      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] =
        endpointHandler.onDecodeFailure(ctx)

      private def endpointExtractsAttributes(e: Endpoint[_, _, _, _]): Vector[AttributeKey[Any]] = {
        e.input.traverseInputs { case EndpointInput.ExtractAttribute(_, key, _) =>
          Vector(key)
        }
      }
    }
}
